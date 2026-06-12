package skibidilandia.minemagic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Espada do Guerreiro — uma espada de combate com uma passiva e três habilidades
 * ativas disparadas pelas teclas numéricas.
 *
 * <ul>
 *   <li><b>Passiva</b>: ao acertar um inimigo (golpe melee), 20% de chance de
 *       provocar uma explosão de 1 TNT no alvo. O portador é blindado contra a
 *       própria explosão.</li>
 *   <li><b>Tecla 1</b>: arremessa a espada na direção da mira (mecânica do
 *       Mjolnir — voa, fere o primeiro alvo e volta à mão). Apertar de novo em
 *       voo chama a espada de volta.</li>
 *   <li><b>Tecla 2</b>: postura defensiva — Resistência + Lentidão por 10s.</li>
 *   <li><b>Tecla 3</b>: domo de bedrock 20x20x20 que prende todos por perto por
 *       20s e some, devolvendo os blocos originais.</li>
 * </ul>
 *
 * <p>As teclas usam {@link PlayerItemHeldEvent}: com a espada em mãos, apertar a
 * tecla 1, 2 ou 3 cancela a troca de slot (a espada continua selecionada) e
 * dispara a habilidade correspondente.
 */
public class WarriorSwordListeners implements Listener {

    // --- Passiva: explosão ---
    private static final double EXPLOSION_CHANCE = 0.20D;
    private static final float TNT_POWER = 4.0F; // TNT comum (só para o visual + quebra de blocos)
    private static final boolean EXPLOSION_SET_FIRE = false;
    private static final boolean EXPLOSION_BREAK_BLOCKS = true;
    /** Dano (capado) aplicado a quem estiver no raio da explosão. 3 corações. */
    private static final double BLAST_DAMAGE = 6.0D;
    /** Raio (blocos) em que a explosão aplica o dano capado. */
    private static final double BLAST_RADIUS = 4.0D;
    /** Cooldown (ms) entre explosões da passiva. */
    private static final long EXPLOSION_COOLDOWN_MS = 3_000L;

    // --- Tecla 1: arremesso (mecânica do Mjolnir) ---
    private static final double OUTBOUND_SPEED = 1.5D;
    private static final double RETURN_SPEED = 1.9D;
    private static final double MAX_RANGE = 40.0D;
    private static final double HIT_SIZE = 0.6D;
    private static final double CATCH_DISTANCE = 1.6D;
    private static final double IMPACT_DAMAGE = 12.0D;
    private static final double TRAIL_STEP = 0.4D;

    // --- Tecla 2: postura defensiva ---
    private static final int EMPOWER_DURATION_TICKS = 200;   // 10s
    private static final long EMPOWER_COOLDOWN_MS = 12_000L;
    private static final int RESISTANCE_AMPLIFIER = 2;        // Resistência III
    private static final int SLOWNESS_AMPLIFIER = 1;          // Lentidão II

    // --- Tecla 3: domo de bedrock ---
    private static final int DOME_HALF = 10;                  // ~20x20x20
    private static final long DOME_DURATION_TICKS = 400L;     // 20s
    private static final long DOME_CHECK_TICKS = 20L;         // verifica a expiração a cada 1s
    private static final long DOME_COOLDOWN_MS = 30_000L;
    private static final int DOME_LIGHT_OFFSET = 6;           // distância (blocos) das luzes ao centro
    private static final int DOME_LIGHT_Y_INSET = 3;          // afastamento das luzes ao teto/piso

    private final JavaPlugin plugin;
    private final Random random = new Random();

    /** Espadas atualmente em voo, por dono. */
    private final Map<UUID, ThrownSword> inFlight = new HashMap<>();
    /** True enquanto a explosão controlada da passiva roda (suprime o dano bruto dela). */
    private boolean suppressExplosionDamage = false;
    /** True enquanto aplicamos o dano capado da explosão (evita re-disparar a passiva). */
    private boolean applyingBlastDamage = false;
    /** Fim do cooldown da explosão da passiva (ms epoch), por jogador. */
    private final Map<UUID, Long> explosionCooldown = new HashMap<>();
    /** Fim do cooldown da postura defensiva (ms epoch), por jogador. */
    private final Map<UUID, Long> empowerCooldown = new HashMap<>();
    /** Fim do cooldown do domo (ms epoch), por jogador. */
    private final Map<UUID, Long> domeCooldown = new HashMap<>();
    /** Domos de bedrock ativos (para restaurar no desligamento). */
    private final List<Dome> activeDomes = new ArrayList<>();

    public WarriorSwordListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Habilidades ativas (teclas 1, 2 e 3)
    // =========================================================================

    @EventHandler
    public void onAbilityKey(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack sword = player.getInventory().getItem(event.getPreviousSlot());
        if (!MineMagicItems.isWarriorSword(sword)) {
            return; // não está com a espada em mãos: troca normal de slot
        }
        int slot = event.getNewSlot();
        if (slot > 2) {
            return; // teclas 4-9: deixa trocar de item normalmente
        }
        event.setCancelled(true); // mantém a espada selecionada; a tecla só ativa a habilidade
        switch (slot) {
            case 0:
                // Passa o slot e a espada já validada: durante o PlayerItemHeldEvent o
                // getItemInMainHand() pode já refletir o NOVO slot (vazio), então não dá
                // para reler a espada pela mão — usamos o item do slot anterior.
                throwSword(player, event.getPreviousSlot(), sword);
                break;
            case 1:
                empower(player);
                break;
            case 2:
                bedrockDome(player);
                break;
            default:
                break;
        }
    }

    // =========================================================================
    //  Passiva: explosão ao atacar (20%)
    // =========================================================================

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (applyingBlastDamage) {
            return; // nosso dano capado não deve re-rolar/re-disparar a explosão
        }
        // Só um golpe de verdade (melee/varredura) deve explodir.
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getDamager();
        if (!MineMagicItems.isWarriorSword(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (random.nextDouble() >= EXPLOSION_CHANCE) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = explosionCooldown.get(id);
        if (until != null && now < until) {
            return; // ainda em cooldown: golpe normal, sem explosão
        }
        explosionCooldown.put(id, now + EXPLOSION_COOLDOWN_MS);
        Location at = event.getEntity().getLocation().add(0, 1, 0); // no corpo do alvo
        warriorExplosion(player, at);
    }

    /**
     * Explosão controlada: a explosão de TNT roda só pelo visual e pela quebra de
     * blocos — o dano bruto dela é suprimido por {@link #onExplosionDamage}. Em
     * seguida aplica-se um dano capado de {@link #BLAST_DAMAGE} (3 corações) a quem
     * estiver dentro de {@link #BLAST_RADIUS}, poupando o dono.
     */
    private void warriorExplosion(Player owner, Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        suppressExplosionDamage = true;
        try {
            world.createExplosion(loc, TNT_POWER, EXPLOSION_SET_FIRE, EXPLOSION_BREAK_BLOCKS, owner);
        } finally {
            suppressExplosionDamage = false;
        }
        applyingBlastDamage = true;
        try {
            for (Entity nearby : world.getNearbyEntities(loc, BLAST_RADIUS, BLAST_RADIUS, BLAST_RADIUS)) {
                if (nearby instanceof LivingEntity && !nearby.equals(owner)) {
                    ((LivingEntity) nearby).damage(BLAST_DAMAGE, owner);
                }
            }
        } finally {
            applyingBlastDamage = false;
        }
    }

    /** Cancela o dano bruto da explosão da passiva (o dano real é aplicado capado à parte). */
    @EventHandler(ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!suppressExplosionDamage) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    //  Tecla 2: postura defensiva (Resistência + Lentidão)
    // =========================================================================

    private void empower(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = empowerCooldown.get(id);
        if (until != null && now < until) {
            sendCooldown(player, until - now);
            return;
        }
        empowerCooldown.put(id, now + EMPOWER_COOLDOWN_MS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                EMPOWER_DURATION_TICKS, RESISTANCE_AMPLIFIER, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                EMPOWER_DURATION_TICKS, SLOWNESS_AMPLIFIER, false, true, true));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, player.getLocation().add(0, 1, 0),
                30, 0.5, 1.0, 0.5, 0.1);
        player.sendActionBar(Component.text("Postura defensiva: ", NamedTextColor.AQUA)
                .append(Component.text("Resistência + Lentidão (10s)", NamedTextColor.WHITE)));
    }

    // =========================================================================
    //  Tecla 3: domo de bedrock
    // =========================================================================

    private void bedrockDome(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = domeCooldown.get(id);
        if (until != null && now < until) {
            sendCooldown(player, until - now);
            return;
        }
        domeCooldown.put(id, now + DOME_COOLDOWN_MS);

        World world = player.getWorld();
        Block origin = player.getLocation().getBlock();
        int cx = origin.getX();
        int cy = origin.getY();
        int cz = origin.getZ();
        int minY = Math.max(world.getMinHeight(), cy - DOME_HALF);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + DOME_HALF);

        List<DomeBlock> saved = new ArrayList<>();
        for (int x = cx - DOME_HALF; x <= cx + DOME_HALF; x++) {
            for (int z = cz - DOME_HALF; z <= cz + DOME_HALF; z++) {
                for (int y = minY; y <= maxY; y++) {
                    boolean shell = x == cx - DOME_HALF || x == cx + DOME_HALF
                            || z == cz - DOME_HALF || z == cz + DOME_HALF
                            || y == minY || y == maxY;
                    if (!shell) {
                        continue; // só a casca externa: o interior fica oco (prende, não soterra)
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BEDROCK) {
                        continue; // já é bedrock: nada a guardar/restaurar
                    }
                    // Guarda local + dados (cópia) para restaurar o bloco original mais tarde.
                    saved.add(new DomeBlock(block.getLocation(), block.getBlockData()));
                    block.setType(Material.BEDROCK, false); // sem física ao colocar
                }
            }
        }

        // Luzes internas: blocos LIGHT invisíveis (nível 15) espalhados pelo interior,
        // para que dê para enxergar lá dentro. Entram na mesma lista de restauração,
        // então somem junto com o domo. Só ocupam espaço vazio — nunca substituem terreno.
        int[] lightOffsets = {-DOME_LIGHT_OFFSET, 0, DOME_LIGHT_OFFSET};
        int[] lightYs = {minY + DOME_LIGHT_Y_INSET, cy, maxY - DOME_LIGHT_Y_INSET};
        for (int dx : lightOffsets) {
            for (int dz : lightOffsets) {
                for (int ly : lightYs) {
                    Block block = world.getBlockAt(cx + dx, ly, cz + dz);
                    if (!block.getType().isAir()) {
                        continue; // só ilumina espaço aberto; não substitui blocos sólidos
                    }
                    saved.add(new DomeBlock(block.getLocation(), block.getBlockData()));
                    block.setType(Material.LIGHT, false);
                }
            }
        }

        Dome dome = new Dome(saved);
        activeDomes.add(dome);
        dome.runTaskTimer(plugin, DOME_CHECK_TICKS, DOME_CHECK_TICKS);

        Location center = player.getLocation();
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        player.sendActionBar(Component.text("Domo de bedrock erguido! ", NamedTextColor.DARK_GRAY)
                .append(Component.text("(20s)", NamedTextColor.GRAY)));
    }

    /** Local + dados originais de um bloco substituído por bedrock no domo. */
    private static final class DomeBlock {
        private final Location loc;
        private final BlockData data;

        DomeBlock(Location loc, BlockData data) {
            this.loc = loc;
            this.data = data;
        }
    }

    /**
     * Uma cúpula de bedrock viva: tarefa repetida que conta o tempo e, ao expirar,
     * devolve cada bloco original. Usa o mesmo padrão de {@code runTaskTimer} já
     * comprovado no resto do plugin (em vez de um único {@code runTaskLater}).
     */
    private final class Dome extends BukkitRunnable {
        private final List<DomeBlock> original;
        private long elapsed = 0L;
        private boolean restored = false;

        Dome(List<DomeBlock> original) {
            this.original = original;
        }

        @Override
        public void run() {
            elapsed += DOME_CHECK_TICKS;
            if (elapsed >= DOME_DURATION_TICKS) {
                restore();
            }
        }

        void restore() {
            if (restored) {
                return;
            }
            restored = true;
            for (DomeBlock db : original) {
                // Re-busca o bloco pela posição (carrega o chunk se preciso) e devolve os dados.
                db.loc.getBlock().setBlockData(db.data, false);
            }
            activeDomes.remove(this);
            cancel();
        }
    }

    // =========================================================================
    //  Tecla 1: arremesso da espada (mecânica do Mjolnir)
    // =========================================================================

    private void throwSword(Player player, int slot, ItemStack sword) {
        UUID id = player.getUniqueId();
        ThrownSword flying = inFlight.get(id);
        if (flying != null) {
            flying.recall(); // já há uma espada em voo: chama de volta
            return;
        }
        // Tira a espada do slot (a espada do guerreiro nunca empilha, mas tratamos o geral).
        ItemStack thrown = sword.clone();
        thrown.setAmount(1);
        int remaining = sword.getAmount() - 1;
        if (remaining <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack rest = sword.clone();
            rest.setAmount(remaining);
            player.getInventory().setItem(slot, rest);
        }

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        player.getWorld().playSound(eye, Sound.ITEM_TRIDENT_THROW, 1.0f, 1.2f);

        ThrownSword flight = new ThrownSword(
                id,
                eye.clone().add(dir.clone().multiply(0.8)),
                dir.clone().multiply(OUTBOUND_SPEED),
                thrown,
                slot);
        inFlight.put(id, flight);
        flight.runTaskTimer(plugin, 1L, 1L);
    }

    /** A espada em voo: um ponto rastreado tick a tick, com trilha de partículas. */
    private final class ThrownSword extends BukkitRunnable {

        private final UUID ownerId;
        private final ItemStack thrown;
        private final Vector velocity;
        private final int originSlot;
        private Location current;
        private boolean returning = false;
        private double travelled = 0.0D;

        ThrownSword(UUID ownerId, Location start, Vector velocity, ItemStack thrown, int originSlot) {
            this.ownerId = ownerId;
            this.current = start;
            this.velocity = velocity;
            this.thrown = thrown;
            this.originSlot = originSlot;
        }

        void recall() {
            returning = true;
        }

        @Override
        public void run() {
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner == null || !owner.isOnline()) {
                current.getWorld().dropItemNaturally(current, thrown);
                finish(null);
                return;
            }
            // Dono mudou de mundo (portal, /tp): perseguir as coordenadas dele em
            // outro mundo faria a espada voar para sempre. Devolve na hora.
            if (!owner.getWorld().equals(current.getWorld())) {
                giveBack(owner, thrown, originSlot);
                finish(owner);
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
                    impactEffect(world, current);
                    returning = true;
                } else if (hit != null && hit.getHitBlock() != null) {
                    Vector p = hit.getHitPosition();
                    Location impact = new Location(world, p.getX(), p.getY(), p.getZ());
                    trail(current, impact);
                    current = impact;
                    impactEffect(world, current);
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
                giveBack(owner, thrown, originSlot);
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
                owner.getWorld().playSound(owner.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.2f);
            }
        }
    }

    /** Efeito do impacto da espada arremessada. */
    private void impactEffect(World world, Location loc) {
        world.spawnParticle(Particle.SWEEP_ATTACK, loc, 3, 0.4, 0.4, 0.4, 0.0);
        world.spawnParticle(Particle.CRIT, loc, 25, 0.3, 0.3, 0.3, 0.4);
        world.playSound(loc, Sound.ITEM_TRIDENT_HIT, 1.0f, 0.9f);
    }

    /** Desenha uma trilha de partículas de corte entre dois pontos. */
    private void trail(Location from, Location to) {
        World world = from.getWorld();
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.0001D) {
            world.spawnParticle(Particle.CRIT, to, 4, 0.06, 0.06, 0.06, 0.0);
            return;
        }
        Vector dir = delta.normalize();
        for (double d = 0; d <= length; d += TRAIL_STEP) {
            Location point = from.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.CRIT, point, 2, 0.04, 0.04, 0.04, 0.0);
        }
        world.spawnParticle(Particle.END_ROD, to, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Devolve a espada ao inventário do dono. Tenta o slot de onde ela foi
     * arremessada; se esse slot já estiver ocupado, cai no primeiro slot livre
     * ({@code addItem}); se o inventário estiver cheio, larga aos pés.
     */
    private void giveBack(Player owner, ItemStack thrown, int originSlot) {
        ItemStack atOrigin = owner.getInventory().getItem(originSlot);
        if (atOrigin == null || atOrigin.getType() == Material.AIR) {
            owner.getInventory().setItem(originSlot, thrown);
            return;
        }
        Map<Integer, ItemStack> overflow = owner.getInventory().addItem(thrown);
        for (ItemStack leftover : overflow.values()) {
            owner.getWorld().dropItemNaturally(owner.getLocation(), leftover);
        }
    }

    // =========================================================================
    //  Utilidades
    // =========================================================================

    private void sendCooldown(Player player, long remainingMs) {
        player.sendActionBar(Component.text("Recarregando... ", NamedTextColor.RED)
                .append(Component.text(String.format("%.1fs", remainingMs / 1000.0), NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        empowerCooldown.remove(id);
        domeCooldown.remove(id);
        explosionCooldown.remove(id);
        // A espada em voo se resolve sozinha no próximo tick (dono offline -> dropa).
    }

    /**
     * Cancela voos em curso, <b>devolve a espada</b> e restaura os domos ativos
     * (chamado no desligamento/reload). Sem a devolução, uma espada em voo no
     * restart sumia para sempre (ela já saíra do inventário no arremesso). Com o
     * dono online o item volta ao inventário; offline, cai onde estava voando.
     */
    public void shutdown() {
        for (Iterator<ThrownSword> it = inFlight.values().iterator(); it.hasNext(); ) {
            ThrownSword sword = it.next();
            sword.cancel();
            Player owner = plugin.getServer().getPlayer(sword.ownerId);
            if (owner != null && owner.isOnline()) {
                giveBack(owner, sword.thrown, sword.originSlot);
            } else {
                sword.current.getWorld().dropItemNaturally(sword.current, sword.thrown);
            }
            it.remove();
        }
        for (Dome dome : new ArrayList<>(activeDomes)) {
            dome.restore(); // devolve os blocos e cancela a tarefa do domo
        }
        activeDomes.clear();
    }
}
