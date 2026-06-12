package skibidilandia.minemagic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Adagas do Assassino — uma arma de combate com quatro habilidades ativas
 * disparadas direto pelas teclas numéricas (cast instantâneo):
 *
 * <ul>
 *   <li><b>Tecla 1</b>: arremessa um leque de adagas na direção da mira.</li>
 *   <li><b>Tecla 2</b>: lança um clone das sombras 5 blocos à frente. O clone dura
 *       10s e replica as habilidades 1 e 3 contra inimigos próximos. Enquanto o
 *       clone vive, apertar 2 de novo troca de lugar com ele.</li>
 *   <li><b>Tecla 3</b>: tornado de adagas — um turbilhão que fere e arremessa quem
 *       está por perto.</li>
 *   <li><b>Tecla 4 (especial)</b>: teleporta para as costas de um jogador mirado e
 *       deixa um clone no lugar de origem. Por 10s, apertar 4 troca de lugar com o
 *       clone. Quando os 10s passam ou o especial é reativado, entra em recarga de
 *       20s.</li>
 * </ul>
 *
 * <p>As teclas usam {@link PlayerItemHeldEvent}: com as adagas em mãos, apertar a
 * tecla 1-4 cancela a troca de slot (as adagas continuam selecionadas) e dispara a
 * habilidade. Passos de roleta (±1 slot) são ignorados, então rolar o mouse navega
 * o inventário normalmente — só os saltos diretos por tecla disparam habilidades.
 * Por isso convém manter as adagas num slot alto (5-9).
 */
public class AssassinListeners implements Listener {

    // --- Tecla 1: leque de adagas ---
    private static final int DAGGER_COUNT = 3;
    private static final double DAGGER_FAN_RAD = 0.12D;   // abertura entre adagas do leque
    private static final double DAGGER_STEP_SPEED = 1.6D; // blocos por tick em voo
    private static final double DAGGER_RANGE = 28.0D;     // alcance máximo
    private static final double DAGGER_HIT_SIZE = 0.5D;   // raio de acerto
    private static final double DAGGER_DAMAGE = 5.0D;     // 2.5 corações por adaga
    private static final long THROW_COOLDOWN_MS = 3_000L; // igual ao tornado (tecla 3)

    // --- Tecla 3: tornado de adagas ---
    private static final long TORNADO_COOLDOWN_MS = 3_000L;
    private static final int TORNADO_DURATION_TICKS = 24;
    private static final int TORNADO_STEP_TICKS = 3;      // intervalo entre levas
    private static final int TORNADO_ARMS = 3;            // adagas por leva
    private static final double TORNADO_ANGLE_STEP = 0.7D;
    private static final double TORNADO_RADIUS = 4.5D;
    private static final double TORNADO_PULSE_DAMAGE = 3.0D;
    private static final double TORNADO_PUSH = 0.7D;
    private static final double TORNADO_LIFT = 0.45D;

    // --- Clone das sombras (teclas 2 e 4) ---
    private static final double CLONE_THROW_DISTANCE = 5.0D;
    private static final int CLONE_LIFESPAN_TICKS = 200;  // 10s
    private static final long CLONE_TICK = 5L;            // resolução da tarefa do clone
    private static final int CLONE_ACTION_INTERVAL = 20;  // 1s entre ataques do clone
    private static final int CLONE_TORNADO_EVERY = 3;     // tornado a cada 3 ataques
    private static final double CLONE_RANGE = 18.0D;

    // --- Tecla 4: especial ---
    private static final double SPECIAL_RANGE = 30.0D;
    private static final double BEHIND_OFFSET = 1.3D;
    private static final long SPECIAL_COOLDOWN_MS = 20_000L;

    // --- Marca de execução (aplicada pelo especial) ---
    private static final long MARK_TICK = 5L;                  // resolução da tarefa da marca
    private static final int EXECUTION_DURATION_TICKS = 100;   // 5s até a explosão
    private static final double EXECUTION_BASE_DAMAGE = 4.0D;  // dano mínimo da explosão
    private static final double EXECUTION_DAMAGE_FACTOR = 0.75D; // fração do dano acumulado vira explosão
    private static final double EXECUTION_AOE_RADIUS = 3.0D;   // respingo nos inimigos próximos

    private final JavaPlugin plugin;
    private final Random random = new Random();

    /** Marca o ArmorStand como um clone, e guarda o dono. */
    private final NamespacedKey cloneKey;
    private final NamespacedKey cloneOwnerKey;

    /** Fim do cooldown (ms epoch) da habilidade 1 / 3 / 4, por jogador. */
    private final Map<UUID, Long> throwCooldown = new HashMap<>();
    private final Map<UUID, Long> tornadoCooldown = new HashMap<>();
    private final Map<UUID, Long> specialCooldown = new HashMap<>();

    /** Clone da tecla 2 vivo, por dono. */
    private final Map<UUID, ShadowClone> throwClones = new HashMap<>();
    /** Clone da tecla 4 (especial armado) vivo, por dono. */
    private final Map<UUID, ShadowClone> specialClones = new HashMap<>();
    /** Marca de execução ativa, por alvo (UUID da entidade marcada). */
    private final Map<UUID, ExecutionMark> marks = new HashMap<>();

    public AssassinListeners(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cloneKey = new NamespacedKey(plugin, "assassin_clone");
        this.cloneOwnerKey = new NamespacedKey(plugin, "assassin_clone_owner");
    }

    /** Remove clones órfãos deixados por um desligamento anterior. */
    public void start() {
        purgeAllClones();
    }

    // =========================================================================
    //  Habilidades ativas (teclas 1, 2, 3 e 4)
    // =========================================================================

    @EventHandler
    public void onAbilityKey(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack daggers = player.getInventory().getItem(event.getPreviousSlot());
        if (!MineMagicItems.isAssassinDaggers(daggers)) {
            return; // não está com as adagas em mãos: troca normal de slot
        }
        int slot = event.getNewSlot();
        if (slot > 3) {
            return; // teclas 5-9: deixa trocar de item normalmente
        }
        if (isScrollStep(event.getPreviousSlot(), slot)) {
            return; // roleta do mouse: deixa navegar livremente até os slots baixos
        }
        // Salto direto (tecla 1/2/3/4): mantém as adagas e dispara a habilidade.
        event.setCancelled(true);
        switch (slot) {
            case 0:
                throwDaggersAbility(player);
                break;
            case 1:
                shadowThrow(player);
                break;
            case 2:
                tornadoAbility(player);
                break;
            case 3:
                special(player);
                break;
            default:
                break;
        }
    }

    /**
     * Distingue um passo de roleta (slot adjacente, com wrap 0↔8) de um salto
     * direto por tecla numérica. O servidor não diz qual input gerou o evento, mas
     * a roleta sempre move ±1 slot — então deixamos passar os passos de ±1.
     */
    private static boolean isScrollStep(int prev, int now) {
        int step = now - prev;
        if (step > 4) {
            step -= 9;
        } else if (step < -4) {
            step += 9;
        }
        return Math.abs(step) == 1;
    }

    // =========================================================================
    //  Tecla 1: leque de adagas
    // =========================================================================

    private void throwDaggersAbility(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = throwCooldown.get(id);
        if (until != null && now < until) {
            sendCooldown(player, until - now);
            return;
        }
        throwCooldown.put(id, now + THROW_COOLDOWN_MS);
        Location eye = player.getEyeLocation();
        throwDaggers(player.getWorld(), eye, eye.getDirection().normalize(), player);
    }

    /** Arremessa {@link #DAGGER_COUNT} adagas em leque a partir de {@code from} na direção {@code dir}. */
    private void throwDaggers(World world, Location from, Vector dir, Player owner) {
        Location origin = from.clone().add(dir.clone().multiply(0.6));
        for (int i = 0; i < DAGGER_COUNT; i++) {
            double angle = (i - (DAGGER_COUNT - 1) / 2.0D) * DAGGER_FAN_RAD;
            Vector v = rotateAroundY(dir.clone(), angle).normalize().multiply(DAGGER_STEP_SPEED);
            new ThrownDagger(owner.getUniqueId(), origin.clone(), v).runTaskTimer(plugin, 0L, 1L);
        }
        world.playSound(origin, Sound.ITEM_TRIDENT_THROW, 0.7f, 1.6f);
    }

    /**
     * Uma adaga arremessada: um ItemDisplay girando que voa em linha reta, fere o
     * primeiro inimigo no caminho (raytrace de entidades + blocos) e some. Substitui
     * as flechas — visual e tematicamente mais coerente com um assassino.
     */
    private final class ThrownDagger extends BukkitRunnable {
        private final UUID ownerId;
        private final Vector velocity;
        private final ItemDisplay display;
        private Location current;
        private double travelled = 0.0D;
        private float spin = 0.0f;

        ThrownDagger(UUID ownerId, Location start, Vector velocity) {
            this.ownerId = ownerId;
            this.current = start;
            this.velocity = velocity;
            this.display = start.getWorld().spawn(start, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(MineMagicItems.ASSASSIN_DAGGERS_MATERIAL));
                d.setBillboard(Display.Billboard.FIXED);
                d.setPersistent(false);
            });
        }

        @Override
        public void run() {
            World world = current.getWorld();
            Player owner = Bukkit.getPlayer(ownerId);
            Vector dir = velocity.clone().normalize();
            RayTraceResult hit = world.rayTrace(current, dir, velocity.length(),
                    FluidCollisionMode.NEVER, true, DAGGER_HIT_SIZE, e -> isEnemy(e, ownerId));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity) {
                LivingEntity victim = (LivingEntity) hit.getHitEntity();
                if (owner != null) {
                    victim.damage(DAGGER_DAMAGE, owner);
                } else {
                    victim.damage(DAGGER_DAMAGE);
                }
                world.spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.2, 0.3, 0.2, 0.3);
                world.playSound(victim.getLocation(), Sound.ITEM_TRIDENT_HIT, 0.8f, 1.4f);
                end();
                return;
            }
            if (hit != null && hit.getHitBlock() != null) {
                world.playSound(current, Sound.ITEM_TRIDENT_HIT_GROUND, 0.5f, 1.4f);
                end();
                return;
            }
            current = current.clone().add(velocity);
            spin += 45.0f;
            Location show = current.clone();
            show.setYaw(spin);
            show.setPitch(0.0f);
            display.teleport(show);
            world.spawnParticle(Particle.CRIT, current, 1, 0.02, 0.02, 0.02, 0.0);
            travelled += velocity.length();
            if (travelled >= DAGGER_RANGE) {
                end();
            }
        }

        private void end() {
            if (display.isValid()) {
                display.remove();
            }
            cancel();
        }
    }

    // =========================================================================
    //  Tecla 3: tornado de adagas
    // =========================================================================

    private void tornadoAbility(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = tornadoCooldown.get(id);
        if (until != null && now < until) {
            sendCooldown(player, until - now);
            return;
        }
        tornadoCooldown.put(id, now + TORNADO_COOLDOWN_MS);
        doTornado(player, player.getLocation());
    }

    /** Um turbilhão centrado em {@code center}: adagas giram em anel e quem está perto é ferido e arremessado. */
    private void doTornado(Player owner, Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.2f);
        UUID ownerId = owner.getUniqueId();
        ItemStack daggerBit = new ItemStack(MineMagicItems.ASSASSIN_DAGGERS_MATERIAL);

        new BukkitRunnable() {
            int t = 0;
            double angle = 0.0D;

            @Override
            public void run() {
                if (t >= TORNADO_DURATION_TICKS) {
                    cancel();
                    return;
                }
                for (int i = 0; i < TORNADO_ARMS; i++) {
                    double a = angle + i * (2.0D * Math.PI / TORNADO_ARMS);
                    Vector out = new Vector(Math.cos(a), 0.0D, Math.sin(a));
                    Location ring = center.clone().add(0, 1.0, 0).add(out.multiply(TORNADO_RADIUS));
                    world.spawnParticle(Particle.ITEM, ring, 2, 0.1, 0.3, 0.1, 0.0, daggerBit);
                    world.spawnParticle(Particle.SWEEP_ATTACK, ring, 1, 0.0, 0.0, 0.0, 0.0);
                }
                world.spawnParticle(Particle.CRIT, center.clone().add(0, 1.0, 0),
                        16, TORNADO_RADIUS / 2.0, 1.0, TORNADO_RADIUS / 2.0, 0.1);
                damageTornadoRing(center, ownerId);
                angle += TORNADO_ANGLE_STEP;
                t += TORNADO_STEP_TICKS;
            }
        }.runTaskTimer(plugin, 0L, TORNADO_STEP_TICKS);
    }

    /** Fere e arremessa as entidades dentro do raio do tornado (menos o dono e os clones dele). */
    private void damageTornadoRing(Location center, UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        for (Entity e : center.getWorld().getNearbyEntities(center, TORNADO_RADIUS, TORNADO_RADIUS, TORNADO_RADIUS)) {
            if (!isEnemy(e, ownerId)) {
                continue;
            }
            LivingEntity victim = (LivingEntity) e;
            if (owner != null) {
                victim.damage(TORNADO_PULSE_DAMAGE, owner);
            } else {
                victim.damage(TORNADO_PULSE_DAMAGE);
            }
            Vector push = victim.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (push.lengthSquared() > 1.0e-6D) {
                push.normalize().multiply(TORNADO_PUSH).setY(TORNADO_LIFT);
                victim.setVelocity(victim.getVelocity().add(push));
            }
        }
    }

    // =========================================================================
    //  Tecla 2: clone das sombras (arremesso / troca de lugar)
    // =========================================================================

    private void shadowThrow(Player player) {
        UUID id = player.getUniqueId();
        ShadowClone existing = throwClones.get(id);
        if (existing != null && existing.stand.isValid()) {
            if (existing.swapUsed) {
                // Já trocou: a sombra continua ativa até sumir, mas não dá pra trocar de novo.
                player.sendActionBar(Component.text("A sombra ainda está ativa — aguarde ela sumir.",
                        NamedTextColor.GRAY));
                return;
            }
            // Gasta a troca de lugar (uso único); a sombra permanece pelo resto dos 10s.
            swapWith(player, existing);
            existing.swapUsed = true;
            return;
        }
        throwClones.remove(id);
        Location spot = aheadLocation(player, CLONE_THROW_DISTANCE);
        ShadowClone clone = spawnClone(player, spot, false);
        if (clone == null) {
            return;
        }
        throwClones.put(id, clone);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        world.spawnParticle(Particle.SMOKE, spot.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.02);
        player.sendActionBar(Component.text("Clone das sombras lançado ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("(aperte 2 em 10s p/ trocar de lugar)", NamedTextColor.GRAY)));
    }

    // =========================================================================
    //  Tecla 4: especial (teleporta para as costas + clone)
    // =========================================================================

    private void special(Player player) {
        UUID id = player.getUniqueId();
        ShadowClone armed = specialClones.get(id);
        if (armed != null && armed.stand.isValid()) {
            if (armed.swapUsed) {
                // Já trocou: a sombra continua ativa até sumir; o especial está em recarga.
                Long cd = specialCooldown.get(id);
                if (cd != null) {
                    sendCooldown(player, cd - System.currentTimeMillis());
                }
                return;
            }
            // Reativação (uso único): troca de lugar, a sombra permanece e a recarga de 20s começa.
            swapWith(player, armed);
            armed.swapUsed = true;
            specialCooldown.put(id, System.currentTimeMillis() + SPECIAL_COOLDOWN_MS);
            player.sendActionBar(Component.text("Especial reativado ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("— recarga 20s", NamedTextColor.GRAY)));
            return;
        }
        long now = System.currentTimeMillis();
        Long until = specialCooldown.get(id);
        if (until != null && now < until) {
            sendCooldown(player, until - now);
            return;
        }
        LivingEntity target = aimEnemyTarget(player);
        if (target == null) {
            player.sendActionBar(Component.text("Mire em um inimigo para o especial.", NamedTextColor.RED));
            return;
        }
        Location origin = player.getLocation().clone();
        Location behind = behindLocation(target);
        World world = player.getWorld();
        world.spawnParticle(Particle.SMOKE, origin.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
        player.teleport(behind);
        world.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        world.spawnParticle(Particle.PORTAL, behind.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);

        ShadowClone clone = spawnClone(player, origin, true);
        if (clone != null) {
            specialClones.put(id, clone);
        }
        applyMark(player, target);
        player.sendActionBar(Component.text("Especial: às costas de " + target.getName() + " ",
                NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("(aperte 4 em 10s p/ trocar de lugar)", NamedTextColor.GRAY)));
    }

    /** Aplica a marca de execução no alvo: acumula o dano sofrido por 5s e explode no fim. */
    private void applyMark(Player owner, LivingEntity target) {
        UUID tid = target.getUniqueId();
        ExecutionMark old = marks.remove(tid);
        if (old != null) {
            old.cancel();
        }
        ExecutionMark mark = new ExecutionMark(tid, owner.getUniqueId());
        marks.put(tid, mark);
        mark.runTaskTimer(plugin, MARK_TICK, MARK_TICK);
        World world = target.getWorld();
        world.playSound(target.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.6f);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
        owner.sendMessage(Component.text("Marca de execução em ", NamedTextColor.DARK_RED)
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.text(" — cause dano em 5s para uma explosão maior!", NamedTextColor.GRAY)));
    }

    /** Inimigo (jogador ou mob) na linha de mira, ou null. */
    private LivingEntity aimEnemyTarget(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye, eye.getDirection(), SPECIAL_RANGE, 0.6D,
                e -> isAimTarget(e, player.getUniqueId()));
        if (result != null && result.getHitEntity() instanceof LivingEntity) {
            return (LivingEntity) result.getHitEntity();
        }
        return null;
    }

    /**
     * Alvo válido para o especial: mais permissivo que {@link #isEnemy} — aceita
     * jogadores em Creative e entidades invulneráveis (para que dê para testar o
     * teleporte/clone/marca). Só exclui o próprio caster, clones, ArmorStands,
     * mortos e espectadores.
     */
    private boolean isAimTarget(Entity e, UUID casterId) {
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand || !e.isValid()) {
            return false;
        }
        if (e.getUniqueId().equals(casterId) || isCloneOf(e, casterId)) {
            return false;
        }
        if (((LivingEntity) e).isDead()) {
            return false;
        }
        if (e instanceof Player) {
            return ((Player) e).getGameMode() != GameMode.SPECTATOR;
        }
        return true;
    }

    /** Ponto logo atrás do alvo (oposto ao lado para o qual ele olha), virado para as costas dele. */
    private Location behindLocation(LivingEntity target) {
        Vector face = target.getEyeLocation().getDirection().setY(0);
        if (face.lengthSquared() < 1.0e-6D) {
            face = new Vector(0, 0, 1);
        }
        face.normalize();
        Location behind = target.getLocation().clone().subtract(face.clone().multiply(BEHIND_OFFSET));
        behind.setDirection(face); // olha na mesma direção do alvo: encara as costas dele
        // Segurança: se o ponto estiver dentro de um bloco, fica na posição do alvo.
        if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) {
            Location safe = target.getLocation().clone();
            safe.setDirection(face);
            return safe;
        }
        return behind;
    }

    // =========================================================================
    //  Clone das sombras: criação, comportamento e troca de lugar
    // =========================================================================

    /** Cria um clone das sombras (ArmorStand vestido) em {@code loc}, marcado com o dono. */
    private ShadowClone spawnClone(Player owner, Location loc, boolean special) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        ArmorStand stand = world.spawn(loc, ArmorStand.class, a -> {
            a.setInvulnerable(true);
            a.setGravity(false);
            a.setArms(true);
            a.setBasePlate(false);
            a.setPersistent(false);
            a.setRemoveWhenFarAway(true);
            a.setCustomName(ChatColor.DARK_GRAY + "Sombra de " + owner.getName());
            a.setCustomNameVisible(true);
            a.getPersistentDataContainer().set(cloneKey, PersistentDataType.BYTE, (byte) 1);
            a.getPersistentDataContainer().set(cloneOwnerKey, PersistentDataType.STRING,
                    owner.getUniqueId().toString());
            if (a.getEquipment() != null) {
                a.getEquipment().setItemInMainHand(MineMagicItems.createAssassinDaggers());
                a.getEquipment().setHelmet(playerHead(owner));
                a.getEquipment().setChestplate(dyedLeather(Material.LEATHER_CHESTPLATE));
                a.getEquipment().setLeggings(dyedLeather(Material.LEATHER_LEGGINGS));
                a.getEquipment().setBoots(dyedLeather(Material.LEATHER_BOOTS));
            }
        });
        ShadowClone clone = new ShadowClone(stand, owner.getUniqueId(), special);
        clone.runTaskTimer(plugin, CLONE_TICK, CLONE_TICK);
        return clone;
    }

    /** Troca a posição do jogador com a do clone (escape/reposicionamento). */
    private void swapWith(Player player, ShadowClone clone) {
        Location playerLoc = player.getLocation().clone();
        Location cloneLoc = clone.stand.getLocation().clone();
        Location dest = cloneLoc.clone();
        dest.setYaw(playerLoc.getYaw());
        dest.setPitch(playerLoc.getPitch());
        World world = player.getWorld();
        world.spawnParticle(Particle.SMOKE, playerLoc.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        player.teleport(dest);
        clone.stand.teleport(playerLoc);
        world.spawnParticle(Particle.SMOKE, dest.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        world.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.3f);
    }

    /** Tarefa de vida e comportamento de um clone. */
    private final class ShadowClone extends BukkitRunnable {
        private final ArmorStand stand;
        private final UUID ownerId;
        private final boolean special;
        /** A troca de lugar é de uso único: depois de gasta, o clone segue vivo mas não troca mais. */
        private boolean swapUsed = false;
        private int ticks = 0;
        private int sinceAction = 0;
        private int actionCount = 0;

        ShadowClone(ArmorStand stand, UUID ownerId, boolean special) {
            this.stand = stand;
            this.ownerId = ownerId;
            this.special = special;
        }

        @Override
        public void run() {
            if (!stand.isValid()) {
                expire();
                return;
            }
            ticks += CLONE_TICK;
            stand.getWorld().spawnParticle(Particle.SMOKE,
                    stand.getLocation().add(0, 1.0, 0), 2, 0.2, 0.4, 0.2, 0.005);
            if (ticks >= CLONE_LIFESPAN_TICKS) {
                expire();
                return;
            }
            sinceAction += CLONE_TICK;
            if (sinceAction < CLONE_ACTION_INTERVAL) {
                return;
            }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline()) {
                return; // sem dono online: só paira (a vida segue contando)
            }
            sinceAction = 0;
            LivingEntity enemy = nearestEnemy(stand.getLocation(), ownerId, CLONE_RANGE);
            if (enemy == null) {
                return;
            }
            Location from = stand.getEyeLocation();
            faceToward(stand, enemy.getLocation());
            Vector dir = enemy.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            if (dir.lengthSquared() < 1.0e-6D) {
                return;
            }
            throwDaggers(stand.getWorld(), from, dir.normalize(), owner);
            actionCount++;
            if (actionCount % CLONE_TORNADO_EVERY == 0) {
                doTornado(owner, stand.getLocation());
            }
        }

        /** Encerra o clone: cancela a tarefa, remove o ArmorStand e libera o estado. */
        private void expire() {
            cancel();
            boolean wasSpecial = specialClones.remove(ownerId, this);
            throwClones.remove(ownerId, this);
            removeStand(this);
            if (wasSpecial && !swapUsed) {
                // Sombra do especial sumiu sem reativação: começa a recarga de 20s.
                // (Se a troca já foi usada, a recarga já começou na reativação.)
                specialCooldown.put(ownerId, System.currentTimeMillis() + SPECIAL_COOLDOWN_MS);
            }
        }
    }

    /** Remove o ArmorStand do clone com um efeito de fumaça. */
    private void removeStand(ShadowClone clone) {
        if (clone.stand.isValid()) {
            World world = clone.stand.getWorld();
            world.spawnParticle(Particle.SMOKE, clone.stand.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
            world.playSound(clone.stand.getLocation(), Sound.ENTITY_ENDERMAN_DEATH, 0.7f, 0.8f);
            clone.stand.remove();
        }
    }

    // =========================================================================
    //  Marca de execução
    // =========================================================================

    /** Acumula na marca o dano que o assassino dono causa ao alvo marcado. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMarkedDamage(EntityDamageByEntityEvent event) {
        ExecutionMark mark = marks.get(event.getEntity().getUniqueId());
        if (mark == null) {
            return;
        }
        Entity damager = resolveRootDamager(event.getDamager());
        if (damager != null && damager.getUniqueId().equals(mark.ownerId)) {
            mark.accumulated += event.getFinalDamage();
        }
    }

    /** Resolve a fonte real do dano: projétil -> atirador. */
    private Entity resolveRootDamager(Entity damager) {
        if (damager instanceof Projectile) {
            Object shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity) {
                return (Entity) shooter;
            }
        }
        return damager;
    }

    /**
     * Marca de execução: por {@link #EXECUTION_DURATION_TICKS} acumula o dano que o
     * dono causa ao alvo e, ao expirar, detona uma explosão cujo dano cresce com o
     * total acumulado.
     */
    private final class ExecutionMark extends BukkitRunnable {
        private final UUID targetId;
        private final UUID ownerId;
        private double accumulated = 0.0D;
        private int ticks = 0;

        ExecutionMark(UUID targetId, UUID ownerId) {
            this.targetId = targetId;
            this.ownerId = ownerId;
        }

        @Override
        public void run() {
            Entity e = plugin.getServer().getEntity(targetId);
            if (!(e instanceof LivingEntity) || e.isDead() || !e.isValid()) {
                marks.remove(targetId, this);
                cancel();
                return;
            }
            LivingEntity target = (LivingEntity) e;
            ticks += MARK_TICK;
            target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    target.getLocation().add(0, 1.0, 0), 4, 0.4, 0.6, 0.4, 0.01);
            if (ticks >= EXECUTION_DURATION_TICKS) {
                marks.remove(targetId, this);
                cancel();
                detonate(target);
            }
        }

        private void detonate(LivingEntity target) {
            double damage = EXECUTION_BASE_DAMAGE + accumulated * EXECUTION_DAMAGE_FACTOR;
            World world = target.getWorld();
            Location loc = target.getLocation().add(0, 1.0, 0);
            world.spawnParticle(Particle.EXPLOSION, loc, 1);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 40, 0.6, 0.8, 0.6, 0.1);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
            Player owner = Bukkit.getPlayer(ownerId);
            damageMarked(target, damage, owner);
            for (Entity near : world.getNearbyEntities(loc, EXECUTION_AOE_RADIUS, EXECUTION_AOE_RADIUS, EXECUTION_AOE_RADIUS)) {
                if (near.equals(target) || !isEnemy(near, ownerId)) {
                    continue;
                }
                damageMarked((LivingEntity) near, damage * 0.5D, owner);
            }
        }

        private void damageMarked(LivingEntity victim, double amount, Player owner) {
            if (owner != null) {
                victim.damage(amount, owner);
            } else {
                victim.damage(amount);
            }
        }
    }

    /** LivingEntity inimigo mais próximo de {@code center} (ignora o dono, clones e ArmorStands). */
    private LivingEntity nearestEnemy(Location center, UUID ownerId, double range) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : center.getWorld().getNearbyEntities(center, range, range, range)) {
            if (!isEnemy(e, ownerId)) {
                continue;
            }
            double d = e.getLocation().distanceSquared(center);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    /** Alvo válido para adagas/tornado: vivo, não-ArmorStand, não o dono nem clone dele. */
    private boolean isEnemy(Entity e, UUID ownerId) {
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand || !e.isValid()) {
            return false;
        }
        if (e.getUniqueId().equals(ownerId) || isCloneOf(e, ownerId)) {
            return false;
        }
        LivingEntity living = (LivingEntity) e;
        if (living.isInvulnerable() || living.isDead()) {
            return false;
        }
        if (living instanceof Player) {
            Player p = (Player) living;
            return p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE;
        }
        return true;
    }

    private boolean isCloneOf(Entity e, UUID ownerId) {
        String raw = e.getPersistentDataContainer().get(cloneOwnerKey, PersistentDataType.STRING);
        return ownerId.toString().equals(raw);
    }

    // =========================================================================
    //  Utilidades
    // =========================================================================

    /** Gira {@code v} ao redor do eixo Y por {@code angle} radianos. */
    private static Vector rotateAroundY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    /** Vira o corpo do ArmorStand para encarar {@code target} (apenas cosmético). */
    private static void faceToward(ArmorStand stand, Location target) {
        Location loc = stand.getLocation();
        double dx = target.getX() - loc.getX();
        double dz = target.getZ() - loc.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        stand.setRotation(yaw, 0.0f);
    }

    /**
     * Ponto a até {@code dist} blocos à frente do jogador, recuando se houver um
     * bloco no caminho. Mantém a altura dos pés de quem lança.
     */
    private Location aheadLocation(Player player, double dist) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eye, dir, dist, FluidCollisionMode.NEVER, true);
        double d = dist;
        if (hit != null && hit.getHitPosition() != null) {
            d = Math.max(1.0D, eye.toVector().distance(hit.getHitPosition()) - 0.5D);
        }
        Location loc = eye.clone().add(dir.clone().multiply(d));
        loc.setY(player.getLocation().getY());
        loc.setDirection(new Vector(dir.getX(), 0, dir.getZ()));
        return loc;
    }

    private ItemStack playerHead(Player owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(owner);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack dyedLeather(Material material) {
        ItemStack piece = new ItemStack(material);
        if (piece.getItemMeta() instanceof LeatherArmorMeta) {
            LeatherArmorMeta meta = (LeatherArmorMeta) piece.getItemMeta();
            meta.setColor(Color.fromRGB(20, 20, 24)); // preto-azulado de sombra
            piece.setItemMeta(meta);
        }
        return piece;
    }

    private void sendCooldown(Player player, long remainingMs) {
        player.sendActionBar(Component.text("Recarregando... ", NamedTextColor.RED)
                .append(Component.text(String.format("%.1fs", remainingMs / 1000.0), NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    // =========================================================================
    //  Limpeza
    // =========================================================================

    private void purgeAllClones() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(cloneKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        forgetClone(throwClones.remove(id));
        ShadowClone special = specialClones.remove(id);
        forgetClone(special);
        throwCooldown.remove(id);
        tornadoCooldown.remove(id);
        specialCooldown.remove(id);
        // Cancela marcas que o jogador aplicou ou que recaíam sobre ele.
        for (Map.Entry<UUID, ExecutionMark> entry : new ArrayList<>(marks.entrySet())) {
            ExecutionMark mark = entry.getValue();
            if (entry.getKey().equals(id) || mark.ownerId.equals(id)) {
                mark.cancel();
                marks.remove(entry.getKey());
            }
        }
    }

    private void forgetClone(ShadowClone clone) {
        if (clone == null) {
            return;
        }
        clone.cancel();
        removeStand(clone);
    }

    /** Cancela tarefas e remove clones vivos (chamado no desligamento). */
    public void shutdown() {
        for (ShadowClone clone : new ArrayList<>(throwClones.values())) {
            clone.cancel();
        }
        for (ShadowClone clone : new ArrayList<>(specialClones.values())) {
            clone.cancel();
        }
        for (ExecutionMark mark : new ArrayList<>(marks.values())) {
            mark.cancel();
        }
        marks.clear();
        throwClones.clear();
        specialClones.clear();
        purgeAllClones();
    }
}
