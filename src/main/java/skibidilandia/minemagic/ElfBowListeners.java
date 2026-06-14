package skibidilandia.minemagic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import skibidilandia.mcmmo.McmmoXp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Comportamento do <b>Arco do Elfo</b> — um único arco com três habilidades
 * acionadas pelas teclas numéricas (estilo dos cajados de classe). As habilidades
 * 1 e 2 são <i>buffs</i> de 10s que modificam os disparos do arco e podem ser
 * combinados; a 3 é uma conjuração instantânea que aproveita os buffs ativos.
 *
 * <ul>
 *   <li><b>Tecla 1 — Explosivo</b>: por 10s, toda flecha disparada
 *       {@link #onArrowHit explode e causa sangramento} ao atingir algo.</li>
 *   <li><b>Tecla 2 — Espiral</b>: por 10s, cada disparo solta
 *       {@link #SPIRAL_ARROWS} flechas em espiral. Combina com a tecla 1
 *       (as flechas da espiral também explodem/sangram).</li>
 *   <li><b>Tecla 3 — Chuva de flechas</b>: uma espiral de flechas chove sobre a
 *       zona visada (como o antigo Cajado do Elfo Negro). Aproveita as teclas 1 e 2
 *       ativas — as flechas da chuva explodem/sangram e caem mais densas.</li>
 * </ul>
 *
 * <p>A troca de tecla usa o {@link PlayerItemHeldEvent}: com o arco em mãos (num
 * slot 5-9) apertar 1-3 cancela a troca de slot e dispara a habilidade — o arco
 * continua selecionado para puxar e atirar.
 */
public class ElfBowListeners implements Listener {

    // --- Buffs (teclas 1 e 2) ---
    /** Duração (ms) dos buffs de explosão e espiral. */
    private static final long BUFF_DURATION_MS = 10_000L;
    /** Flechas por disparo enquanto o buff Espiral está ativo. */
    private static final int SPIRAL_ARROWS = 6;
    /** Abertura (graus) inicial do cone da espiral. */
    private static final double SPIRAL_BASE_DEGREES = 8.0D;
    /** Quanto (graus) o cone abre por flecha, dando o aspecto de espiral. */
    private static final double SPIRAL_STEP_DEGREES = 3.0D;

    // --- Explosão + sangramento (impacto das flechas marcadas) ---
    /** Força da explosão de cada flecha explosiva (não quebra blocos nem incendeia). */
    private static final float EXPLOSION_POWER = 2.0f;
    /** Duração (ticks) do sangramento aplicado ao alvo. */
    private static final int BLEED_DURATION_TICKS = 100; // 5s
    /** Intervalo (ticks) entre as picadas do sangramento. */
    private static final long BLEED_INTERVAL_TICKS = 20L; // 1s
    /** Dano por picada do sangramento (meio coração). */
    private static final double BLEED_DAMAGE = 1.0D;

    // --- Chuva de flechas (tecla 3) — portado do Cajado do Elfo Negro ---
    private static final long STORM_DURATION_TICKS = 100L;
    private static final long ARROW_INTERVAL_TICKS = 2L;
    private static final int ARROWS_PER_TICK = 3;
    /** Com o buff Espiral ativo, a chuva cai mais densa. */
    private static final int ARROWS_PER_TICK_SPIRAL = 5;
    private static final double RAIN_MAX_RANGE = 50.0D;
    private static final double RAIN_SKY_HEIGHT = 22.0D;
    private static final double RAIN_SPIRAL_RADIUS = 5.0D;
    private static final double RAIN_ANGLE_STEP = 0.55D;
    private static final double RAIN_TURNS_PER_ARM = 2.0D;
    private static final double RAIN_ARROW_SPEED = 2.2D;
    private static final double RAIN_ANGLE_JITTER = 0.9D;
    private static final double RAIN_RADIUS_JITTER = 1.7D;
    private static final double RAIN_HEIGHT_JITTER = 7.0D;
    private static final float RAIN_ARROW_SPREAD = 4.0f;

    private final JavaPlugin plugin;
    private final Random random = new Random();

    /** Marca, no PDC da flecha, que ela deve explodir/sangrar ao atingir algo. */
    private final NamespacedKey explosiveArrowKey;

    /** Instante (ms) até o qual o buff de explosão está ativo, por jogador. */
    private final Map<UUID, Long> explosiveUntil = new HashMap<>();
    /** Instante (ms) até o qual o buff de espiral está ativo, por jogador. */
    private final Map<UUID, Long> spiralUntil = new HashMap<>();
    /** Jogadores com uma chuva de flechas em curso. */
    private final Map<UUID, BukkitRunnable> storms = new HashMap<>();
    /** Alvos atualmente sangrando (por uid), para refrescar em vez de empilhar. */
    private final Map<UUID, BukkitRunnable> bleeding = new HashMap<>();

    public ElfBowListeners(JavaPlugin plugin) {
        this.plugin = plugin;
        this.explosiveArrowKey = new NamespacedKey(plugin, "elf_explosive_arrow");
    }

    // =========================================================================
    //  Habilidades por tecla (1, 2 e 3)
    // =========================================================================

    @EventHandler
    public void onAbilityKey(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // O arco continua no slot anterior: leia de lá (nunca getItemInMainHand aqui).
        ItemStack bow = player.getInventory().getItem(event.getPreviousSlot());
        if (!MineMagicItems.isElfBow(bow)) {
            return; // não está com o arco em mãos: troca normal de slot
        }
        int slot = event.getNewSlot();
        if (slot > 2) {
            return; // teclas 4-9: deixa trocar de item normalmente
        }
        if (isScrollStep(event.getPreviousSlot(), slot)) {
            return; // roleta do mouse: navegação livre até os slots baixos
        }
        // Salto direto (tecla 1-3): mantém o arco selecionado e dispara a habilidade.
        event.setCancelled(true);
        if (!MineMagicItems.isAbilityUnlocked(bow, slot)) {
            notifyLocked(player, bow, slot);
            return;
        }
        switch (slot) {
            case 0: activateExplosive(player); break;
            case 1: activateSpiral(player); break;
            default: castRain(player); break;
        }
    }

    /** Avisa que a habilidade da tecla {@code slot+1} ainda está bloqueada. */
    private void notifyLocked(Player player, ItemStack item, int slot) {
        player.sendActionBar(Component.text("Tecla " + (slot + 1) + " bloqueada ("
                + MineMagicItems.abilityName(item, slot) + ") — funda uma Gema do Infinito na Forja.",
                NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    /** Liga (ou renova) o buff de explosão + sangramento por {@link #BUFF_DURATION_MS}. */
    private void activateExplosive(Player player) {
        explosiveUntil.put(player.getUniqueId(), nowMillis() + BUFF_DURATION_MS);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.7f);
        player.sendActionBar(Component.text("Flechas explosivas + sangramento (10s)", NamedTextColor.RED));
    }

    /** Liga (ou renova) o buff de espiral por {@link #BUFF_DURATION_MS}. */
    private void activateSpiral(Player player) {
        spiralUntil.put(player.getUniqueId(), nowMillis() + BUFF_DURATION_MS);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.6f);
        player.sendActionBar(Component.text("Disparo em espiral — 6 flechas (10s)", NamedTextColor.GREEN));
    }

    private boolean isExplosiveActive(Player player) {
        Long until = explosiveUntil.get(player.getUniqueId());
        return until != null && nowMillis() < until;
    }

    private boolean isSpiralActive(Player player) {
        Long until = spiralUntil.get(player.getUniqueId());
        return until != null && nowMillis() < until;
    }

    // =========================================================================
    //  Disparo do arco (aplica os buffs ativos)
    // =========================================================================

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!MineMagicItems.isElfBow(event.getBow())) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow)) {
            return; // p.ex. bola de neve não recebe os efeitos
        }
        Player player = (Player) event.getEntity();
        AbstractArrow center = (AbstractArrow) event.getProjectile();
        boolean explosive = isExplosiveActive(player);
        boolean spiral = isSpiralActive(player);

        if (explosive) {
            markExplosive(center);
        }
        if (spiral) {
            shootSpiral(player, center, explosive);
            player.getWorld().playSound(center.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.4f);
        }
    }

    /**
     * Solta as flechas extras da espiral em volta da flecha central, abrindo o cone
     * gradualmente para dar o aspecto de espiral. Junto com a central são
     * {@link #SPIRAL_ARROWS} flechas.
     */
    private void shootSpiral(Player player, AbstractArrow center, boolean explosive) {
        Vector velocity = center.getVelocity();
        double speed = velocity.length();
        if (speed < 1.0e-4D) {
            return;
        }
        Vector forward = velocity.clone().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0e-6D) {
            right = new Vector(1.0D, 0.0D, 0.0D); // olhando reto para cima/baixo
        }
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();

        World world = player.getWorld();
        Location spawn = center.getLocation();
        int extra = SPIRAL_ARROWS - 1; // a central já existe
        for (int i = 0; i < extra; i++) {
            double azimuth = (i / (double) extra) * 2.0D * Math.PI;
            double cone = Math.toRadians(SPIRAL_BASE_DEGREES + i * SPIRAL_STEP_DEGREES);
            Vector dir = forward.clone().multiply(Math.cos(cone))
                    .add(right.clone().multiply(Math.cos(azimuth) * Math.sin(cone)))
                    .add(up.clone().multiply(Math.sin(azimuth) * Math.sin(cone)));
            Vector v = dir.normalize().multiply(speed);

            Arrow arrow = world.spawnArrow(spawn, v, (float) v.length(), 0.0f);
            arrow.setVelocity(v);
            arrow.setShooter(player);
            arrow.setCritical(center.isCritical());
            arrow.setDamage(center.getDamage());
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            if (explosive) {
                markExplosive(arrow);
            }
        }
    }

    private void markExplosive(AbstractArrow arrow) {
        arrow.getPersistentDataContainer().set(explosiveArrowKey, PersistentDataType.BYTE, (byte) 1);
    }

    // =========================================================================
    //  Impacto: explosão + sangramento
    // =========================================================================

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow)) {
            return;
        }
        AbstractArrow arrow = (AbstractArrow) event.getEntity();
        if (!arrow.getPersistentDataContainer().has(explosiveArrowKey, PersistentDataType.BYTE)) {
            return;
        }
        Player shooter = arrow.getShooter() instanceof Player ? (Player) arrow.getShooter() : null;
        Entity hit = event.getHitEntity();
        if (hit != null && shooter != null && hit.equals(shooter)) {
            return; // não explode na cara do próprio atirador
        }
        World world = arrow.getWorld();
        Location at = arrow.getLocation();

        // Snapshot da vida dos seres vivos no raio do estouro. O mcMMO não dá XP de
        // archery por dano de explosão, então creditamos manualmente proporcional ao
        // dano que a explosão causar a cada um (delta de vida antes/depois).
        Map<LivingEntity, Double> healthBefore = new HashMap<>();
        if (shooter != null) {
            for (LivingEntity le : world.getNearbyLivingEntities(at, EXPLOSION_POWER * 2.0D)) {
                if (!le.equals(shooter)) {
                    healthBefore.put(le, le.getHealth());
                }
            }
        }

        // Explosão que fere quem está perto sem destruir o terreno nem atear fogo.
        world.createExplosion(at, EXPLOSION_POWER, false, false, shooter);

        if (shooter != null) {
            for (Map.Entry<LivingEntity, Double> entry : healthBefore.entrySet()) {
                LivingEntity le = entry.getKey();
                boolean dead = le.isDead() || !le.isValid();
                double dealt = entry.getValue() - (dead ? 0.0D : le.getHealth());
                McmmoXp.combat(shooter, le, "ARCHERY", dealt);
            }
        }

        if (hit instanceof LivingEntity) {
            applyBleed((LivingEntity) hit, shooter);
        }
        arrow.remove();
    }

    /** Aplica (ou renova) o sangramento: dano periódico com partículas vermelhas. */
    private void applyBleed(LivingEntity victim, Player source) {
        UUID id = victim.getUniqueId();
        stopBleed(id); // refrescar reinicia o relógio
        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= BLEED_DURATION_TICKS || !victim.isValid() || victim.isDead()) {
                    stopBleed(id);
                    return;
                }
                elapsed += BLEED_INTERVAL_TICKS;
                victim.setNoDamageTicks(0); // garante que a picada não seja absorvida pela invulnerabilidade
                if (source != null && source.isOnline()) {
                    victim.damage(BLEED_DAMAGE, source);
                } else {
                    victim.damage(BLEED_DAMAGE);
                }
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0.0D, 1.0D, 0.0D),
                        10, 0.3D, 0.5D, 0.3D, new Particle.DustOptions(Color.RED, 1.2f));
            }
        };
        bleeding.put(id, task);
        task.runTaskTimer(plugin, BLEED_INTERVAL_TICKS, BLEED_INTERVAL_TICKS);
    }

    private void stopBleed(UUID id) {
        BukkitRunnable task = bleeding.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    // =========================================================================
    //  Chuva de flechas (tecla 3)
    // =========================================================================

    /** Marca a zona visada e arma a chuva de flechas, se não houver uma em curso. */
    private void castRain(Player player) {
        UUID id = player.getUniqueId();
        if (storms.containsKey(id)) {
            return; // já há uma chuva em curso
        }
        Location center = aimLocation(player);
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.7f);
        player.sendActionBar(Component.text("Chuva de flechas!", NamedTextColor.DARK_GREEN));

        boolean explosive = isExplosiveActive(player);
        int perTick = isSpiralActive(player) ? ARROWS_PER_TICK_SPIRAL : ARROWS_PER_TICK;
        BukkitRunnable task = new BukkitRunnable() {
            long elapsed = 0L;
            int spawned = 0;

            @Override
            public void run() {
                if (elapsed >= STORM_DURATION_TICKS) {
                    stopStorm(id);
                    return;
                }
                elapsed += ARROW_INTERVAL_TICKS;
                for (int i = 0; i < perTick; i++) {
                    rainArrow(world, center, player, spawned++, explosive);
                }
                world.playSound(center, Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.4f);
            }
        };
        storms.put(id, task);
        task.runTaskTimer(plugin, 0L, ARROW_INTERVAL_TICKS);
    }

    private void stopStorm(UUID id) {
        BukkitRunnable task = storms.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Faz uma flecha despencar sobre a zona seguindo uma espiral com ruído, para
     * varrer a área toda de forma imprevisível. Marca como explosiva se o buff da
     * tecla 1 estiver ativo no momento da conjuração.
     */
    private void rainArrow(World world, Location center, Player shooter, int n, boolean explosive) {
        double angle = n * RAIN_ANGLE_STEP + jitter(RAIN_ANGLE_JITTER);
        double armPhase = ((n * RAIN_ANGLE_STEP) / (RAIN_TURNS_PER_ARM * 2.0D * Math.PI)) % 1.0D;
        double radius = RAIN_SPIRAL_RADIUS * armPhase + jitter(RAIN_RADIUS_JITTER);
        radius = Math.max(0.0D, Math.min(RAIN_SPIRAL_RADIUS, radius));

        double height = RAIN_SKY_HEIGHT + random.nextDouble() * RAIN_HEIGHT_JITTER;
        Location spawn = center.clone().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius);

        Arrow arrow = world.spawnArrow(spawn, new Vector(0.0D, -1.0D, 0.0D),
                (float) RAIN_ARROW_SPEED, RAIN_ARROW_SPREAD);
        arrow.setShooter(shooter);
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        if (explosive) {
            markExplosive(arrow);
        }
    }

    /** Ruído simétrico em [-amount, +amount]. */
    private double jitter(double amount) {
        return (random.nextDouble() * 2.0D - 1.0D) * amount;
    }

    /**
     * Centro da zona: o bloco mirado (se houver) ou um ponto a {@link #RAIN_MAX_RANGE}
     * blocos na direção do olhar do jogador.
     */
    private Location aimLocation(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), RAIN_MAX_RANGE, FluidCollisionMode.NEVER, true);
        if (result != null && result.getHitPosition() != null) {
            Vector hit = result.getHitPosition();
            return new Location(player.getWorld(), hit.getX(), hit.getY(), hit.getZ());
        }
        return eye.clone().add(eye.getDirection().multiply(RAIN_MAX_RANGE));
    }

    // =========================================================================
    //  Utilidades
    // =========================================================================

    /**
     * Distingue um passo de roleta (slot adjacente, com wrap 0↔8) de um salto
     * direto por tecla numérica — a roleta sempre move ±1 slot.
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

    /** Millis sem usar {@code System.currentTimeMillis} fora deste único ponto. */
    private static long nowMillis() {
        return System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        explosiveUntil.remove(id);
        spiralUntil.remove(id);
        stopStorm(id);
    }

    /** Cancela tarefas em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = storms.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
        for (Iterator<BukkitRunnable> it = bleeding.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
