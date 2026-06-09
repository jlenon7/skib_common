package skibidilandia.minemagic;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Comportamento do Mjolnir (base de martelo/maça).
 *
 * Arremesso (botão direito):
 *  - O martelo sai da mão e "voa" na direção do olhar. Não há entidade visível
 *    do item: o projétil é um ponto rastreado a cada tick, marcado por uma
 *    trilha de faíscas elétricas bem evidente.
 *
 * Impacto:
 *  - A cada tick o trajeto é testado com um ray trace que detecta blocos e
 *    entidades vivas. Ao bater em um <b>alvo</b> (criatura), ele recebe dano e
 *    um {@link World#strikeLightning raio} cai sobre ele. Ao bater em uma
 *    <b>estrutura</b> (bloco), o raio cai no ponto de impacto. Em ambos os casos
 *    — e ao atingir o alcance máximo — o martelo entra em fase de retorno.
 *
 * Retorno:
 *  - O martelo volta perseguindo a posição atual do dono; ao chegar perto, o
 *    Mjolnir reentra no inventário. Clicar com o botão direito enquanto ele está
 *    em voo o faz voltar imediatamente.
 *
 * Golpe corpo a corpo (botão esquerdo):
 *  - Bater em uma criatura segurando o Mjolnir também invoca um raio sobre ela.
 */
public class MjolnirListeners implements Listener {

    /** Velocidade (blocos/tick) na ida. */
    private static final double OUTBOUND_SPEED = 1.4D;
    /** Velocidade (blocos/tick) na volta. */
    private static final double RETURN_SPEED = 1.8D;
    /** Alcance máximo (blocos) antes de voltar mesmo sem bater em nada. */
    private static final double MAX_RANGE = 48.0D;
    /** Raio de colisão do martelo no ray trace. */
    private static final double HIT_SIZE = 0.6D;
    /** Distância (blocos) do dono para considerar o martelo "de volta à mão". */
    private static final double CATCH_DISTANCE = 1.6D;
    /** Dano causado ao alvo atingido pelo martelo arremessado. */
    private static final double IMPACT_DAMAGE = 10.0D;
    /** Espaçamento (blocos) entre faíscas ao longo da trilha. */
    private static final double TRAIL_STEP = 0.3D;

    private final JavaPlugin plugin;

    /** Jogadores com um martelo em voo. */
    private final Map<UUID, ThrownHammer> inFlight = new HashMap<>();

    public MjolnirListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Arremesso / recall (botão direito)
    // =========================================================================

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // só a mão principal; evita disparo duplicado da off-hand
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!MineMagicItems.isMjolnir(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        ThrownHammer flying = inFlight.get(player.getUniqueId());
        if (flying != null) {
            flying.recall(); // já há um martelo em voo: chama de volta imediatamente
            return;
        }
        throwHammer(player);
    }

    /** Tira o martelo da mão e inicia o voo. */
    private void throwHammer(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack thrown = MineMagicItems.createMjolnir();
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        player.getWorld().playSound(eye, Sound.ITEM_TRIDENT_THROW, 1.0f, 0.8f);

        ThrownHammer hammer = new ThrownHammer(
                player.getUniqueId(),
                eye.clone().add(dir.clone().multiply(0.8)),
                dir.clone().multiply(OUTBOUND_SPEED),
                thrown);
        inFlight.put(player.getUniqueId(), hammer);
        hammer.runTaskTimer(plugin, 1L, 1L);
    }

    // =========================================================================
    //  Golpe corpo a corpo (botão esquerdo)
    // =========================================================================

    @EventHandler
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getDamager();
        if (!MineMagicItems.isMjolnir(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        event.getEntity().getWorld().strikeLightning(event.getEntity().getLocation());
    }

    // =========================================================================
    //  Projétil
    // =========================================================================

    /** O martelo em voo: um ponto rastreado tick a tick, com trilha de faíscas. */
    private final class ThrownHammer extends BukkitRunnable {

        private final UUID ownerId;
        private final ItemStack thrown;
        private final Vector velocity;
        private Location current;
        private boolean returning = false;
        private double travelled = 0.0D;

        ThrownHammer(UUID ownerId, Location start, Vector velocity, ItemStack thrown) {
            this.ownerId = ownerId;
            this.current = start;
            this.velocity = velocity;
            this.thrown = thrown;
        }

        /** Faz o martelo voltar imediatamente (chamado pelo recall). */
        void recall() {
            returning = true;
        }

        @Override
        public void run() {
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner == null || !owner.isOnline()) {
                // Dono saiu: devolve o martelo ao mundo onde ele estava.
                current.getWorld().dropItemNaturally(current, thrown);
                finish(null);
                return;
            }

            if (!returning) {
                World world = current.getWorld();
                RayTraceResult hit = world.rayTrace(
                        current, velocity.clone().normalize(), velocity.length(),
                        FluidCollisionMode.NEVER, true, HIT_SIZE,
                        e -> e instanceof LivingEntity && !e.equals(owner));

                if (hit != null && hit.getHitEntity() instanceof LivingEntity) {
                    LivingEntity target = (LivingEntity) hit.getHitEntity();
                    trail(current, target.getLocation());
                    current = target.getLocation();
                    target.damage(IMPACT_DAMAGE, owner);
                    impactEffect(world, current, owner);
                    returning = true;
                } else if (hit != null && hit.getHitBlock() != null) {
                    Vector p = hit.getHitPosition();
                    Location impact = new Location(world, p.getX(), p.getY(), p.getZ());
                    trail(current, impact);
                    current = impact;
                    impactEffect(world, current, owner);
                    returning = true;
                } else {
                    Location next = current.clone().add(velocity);
                    trail(current, next);
                    current = next;
                    travelled += velocity.length();
                    if (travelled >= MAX_RANGE) {
                        returning = true;
                    }
                }
                return;
            }

            // Fase de retorno: persegue o dono.
            Location goal = owner.getLocation().add(0, 1.0, 0);
            Vector toOwner = goal.toVector().subtract(current.toVector());
            if (toOwner.length() <= CATCH_DISTANCE) {
                giveBack(owner, thrown);
                finish(owner);
                return;
            }
            Location next = current.clone().add(toOwner.normalize().multiply(RETURN_SPEED));
            trail(current, next);
            current = next;
        }

        private void finish(Player owner) {
            inFlight.remove(ownerId);
            cancel();
            if (owner != null) {
                owner.getWorld().playSound(owner.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Efeito do impacto do martelo arremessado: três raios em volta do ponto e
     * uma explosão de TNT no local. Usado só no arremesso (o golpe corpo a corpo
     * continua invocando um único raio).
     */
    private void impactEffect(World world, Location loc, Player owner) {
        world.strikeLightning(loc);
        world.strikeLightning(loc.clone().add(1.5, 0, 1.5));
        world.strikeLightning(loc.clone().add(-1.5, 0, -1.5));
        world.createExplosion(loc, 4.0F, false, true, owner);
    }

    /** Desenha uma trilha contínua e densa de faíscas elétricas entre dois pontos. */
    private void trail(Location from, Location to) {
        World world = from.getWorld();
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.0001D) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, to, 6, 0.08, 0.08, 0.08, 0.0);
            return;
        }
        Vector dir = delta.normalize();
        for (double d = 0; d <= length; d += TRAIL_STEP) {
            Location point = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 3, 0.06, 0.06, 0.06, 0.0);
        }
        // Núcleo brilhante na cabeça do projétil, para destacar a posição.
        world.spawnParticle(Particle.END_ROD, to, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Devolve o Mjolnir ao inventário do dono (ou o larga aos pés se cheio). */
    private void giveBack(Player owner, ItemStack thrown) {
        Map<Integer, ItemStack> overflow = owner.getInventory().addItem(thrown);
        for (ItemStack leftover : overflow.values()) {
            owner.getWorld().dropItemNaturally(owner.getLocation(), leftover);
        }
    }

    /** Cancela todos os voos em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<ThrownHammer> it = inFlight.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
