package skibidilandia.minemagic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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
import java.util.function.Consumer;

/**
 * Cajado do Mago — duas habilidades guardadas no PDC do item, trocadas pelas
 * teclas numéricas, sem recarga:
 *
 * <ul>
 *   <li><b>Tecla 1 — Fogo</b>: segurar o botão direito conjura uma rajada de
 *       {@link LargeFireball}; {@code shift + botão direito} faz uma chuva de fogo
 *       desabar sobre a zona visada.</li>
 *   <li><b>Tecla 2 — Raio</b>: segurar o botão direito invoca raios sobre o ponto
 *       visado; {@code shift + botão direito} faz uma chuva de raios verticais
 *       desabar sobre a zona visada.</li>
 *   <li><b>Tecla 3 — Congelar</b>: botão direito prende o alvo visado (jogador ou
 *       mob) no lugar por alguns segundos (disparo único).</li>
 *   <li><b>Tecla 4 — Levitar</b>: a própria tecla 4 já lança o mago ao céu e lhe dá
 *       Queda Lenta (não é um modo nem usa botão direito), com recarga de 15s.</li>
 * </ul>
 *
 * <p>A troca usa o {@link PlayerItemHeldEvent}: ao apertar a tecla 1 ou 2 com o
 * cajado em mãos o evento é cancelado (o cajado continua selecionado) e apenas o
 * modo muda. As duas lógicas de disparo são as mesmas dos antigos Cajado de Fogo e
 * Cajado do Raio: a interação inicial não é cancelada (o tridente "carrega",
 * deixando a mão levantada) e uma tarefa repetida dispara enquanto
 * {@link Player#isHandRaised()}. O arremesso do tridente é sempre bloqueado.
 */
public class MageStaffListeners implements Listener {

    // --- Fogo: rajada ---
    private static final long FIRE_INTERVAL_TICKS = 10L; // ~500ms entre rajadas
    private static final double FIREBALL_SPEED = 1.4D;
    private static final int FIREBALLS_PER_VOLLEY = 3;
    private static final double SPAWN_HEIGHT = 2.6D;
    private static final double SPAWN_SPREAD = 1.1D;
    private static final long HOVER_TICKS = 8L;
    private static final float GHAST_YIELD = 1.0F;

    // --- Fogo: chuva (shift + botão direito) ---
    private static final long RAIN_DURATION_TICKS = 60L;
    private static final long RAIN_INTERVAL_TICKS = 4L;
    private static final int FIREBALLS_PER_DROP = 6;
    private static final double RAIN_MAX_RANGE = 50.0D;
    private static final double RAIN_RADIUS = 5.0D;
    private static final double RAIN_SKY_HEIGHT = 20.0D;
    private static final double RAIN_HEIGHT_JITTER = 7.0D;
    private static final double RAIN_FALL_SPEED = 1.0D;

    // --- Raio: chuva (shift + botão direito) ---
    private static final long THUNDER_RAIN_INTERVAL_TICKS = 6L;
    private static final int BOLTS_PER_DROP = 2;
    private static final double THUNDER_RAIN_RADIUS = 6.0D;

    // --- Raio ---
    private static final long STRIKE_INTERVAL_TICKS = 10L; // ~500ms entre descargas
    private static final double LIGHTNING_MAX_RANGE = 60.0D;
    private static final int LIGHTNING_BOLTS_PER_STRIKE = 3;
    private static final double LIGHTNING_SPREAD = 2.5D;

    // --- Congelar (tecla 3) ---
    private static final double FREEZE_MAX_RANGE = 40.0D;
    private static final double FREEZE_RAY_SIZE = 0.6D;
    private static final long FREEZE_DURATION_TICKS = 100L; // ~5s preso no lugar

    // --- Levitar (tecla 4) ---
    private static final double LAUNCH_UP_VELOCITY = 2.2D;
    private static final int SLOW_FALL_TICKS = 220; // ~11s de queda lenta
    private static final long LAUNCH_COOLDOWN_MS = 15_000L; // 15s para reusar

    /** Carência inicial (ticks) antes de exigir a mão levantada. */
    private static final int RAISE_GRACE_TICKS = 4;

    private final JavaPlugin plugin;
    private final Random random = new Random();

    /** Jogadores com uma rajada (fogo) ou rajada de raios em curso. */
    private final Map<UUID, BukkitRunnable> firing = new HashMap<>();
    /** Jogadores com uma chuva de fogo em curso. */
    private final Map<UUID, BukkitRunnable> raining = new HashMap<>();
    /** Alvos atualmente congelados (por uid), para não empilhar tarefas. */
    private final Map<UUID, BukkitRunnable> frozen = new HashMap<>();
    /** Instante (millis) em que cada jogador poderá usar Levitar de novo. */
    private final Map<UUID, Long> launchCooldownUntil = new HashMap<>();

    public MageStaffListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Troca de habilidade (teclas 1 e 2)
    // =========================================================================

    @EventHandler
    public void onSwapAbility(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack staff = player.getInventory().getItem(event.getPreviousSlot());
        if (!MineMagicItems.isMageStaff(staff)) {
            return; // não está com o cajado em mãos: troca normal de slot
        }
        int slot = event.getNewSlot();
        if (slot > 3) {
            return; // teclas 5-9: deixa trocar de item normalmente
        }
        if (isScrollStep(event.getPreviousSlot(), slot)) {
            return; // roleta do mouse: deixa navegar livremente até os slots baixos
        }
        // Salto direto (tecla 1-4): mantém o cajado selecionado.
        event.setCancelled(true);
        if (!MineMagicItems.isAbilityUnlocked(staff, slot)) {
            notifyLocked(player, staff, slot);
            return;
        }
        if (slot == 3) {
            // Tecla 4 — Levitar: ação instantânea com recarga; não muda o modo do
            // cajado, então o jogador já cai de volta podendo disparar fogo/raio.
            tryLaunchUp(player);
            return;
        }
        String mode = modeForSlot(slot);
        if (mode.equals(MineMagicItems.getMageMode(staff))) {
            return; // já está nesse modo
        }
        MineMagicItems.setMageMode(staff, mode);
        player.getInventory().setItem(event.getPreviousSlot(), staff);
        stopFiring(player.getUniqueId()); // trocar de modo encerra a rajada em curso
        player.sendActionBar(Component.text("Modo: ", NamedTextColor.BLUE)
                .append(Component.text(labelForMode(mode), NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    /** Habilidade associada à tecla numérica (slot 0-2 → teclas 1-3). */
    private static String modeForSlot(int slot) {
        switch (slot) {
            case 1:  return MineMagicItems.MAGE_LIGHTNING;
            case 2:  return MineMagicItems.MAGE_FREEZE;
            default: return MineMagicItems.MAGE_FIRE;
        }
    }

    private static String labelForMode(String mode) {
        switch (mode) {
            case MineMagicItems.MAGE_LIGHTNING: return "Raio";
            case MineMagicItems.MAGE_FREEZE:    return "Congelar";
            default:                            return "Fogo";
        }
    }

    /** Avisa que a habilidade da tecla {@code slot+1} ainda está bloqueada. */
    private void notifyLocked(Player player, ItemStack item, int slot) {
        player.sendActionBar(Component.text("Tecla " + (slot + 1) + " bloqueada ("
                + MineMagicItems.abilityName(item, slot) + ") — funda uma Gema do Infinito na Forja.",
                NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    /**
     * Distingue um passo de roleta (slot adjacente, com wrap 0↔8) de um salto
     * direto por tecla numérica. O servidor não diz qual input gerou o evento,
     * mas a roleta sempre move ±1 slot — então deixamos passar os passos de ±1
     * (navegação livre) e só os saltos trocam de habilidade.
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
    //  Disparo (botão direito)
    // =========================================================================

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // só a mão principal; evita o disparo duplicado da off-hand
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack staff = player.getInventory().getItemInMainHand();
        if (!MineMagicItems.isMageStaff(staff)) {
            return;
        }
        // Não cancelamos a interação: o tridente "carrega" e a mão fica levantada.
        String mode = MineMagicItems.getMageMode(staff);
        if (MineMagicItems.MAGE_LIGHTNING.equals(mode)) {
            if (player.isSneaking()) {
                startThunderRain(player); // shift + botão direito: chuva de raios
            } else {
                startHold(player, MineMagicItems.MAGE_LIGHTNING, STRIKE_INTERVAL_TICKS, this::strike);
            }
        } else if (MineMagicItems.MAGE_FREEZE.equals(mode)) {
            freezeTarget(player); // disparo único: congela o alvo visado
        } else if (player.isSneaking()) {
            startRain(player); // shift + botão direito: chuva de fogo
        } else {
            startHold(player, MineMagicItems.MAGE_FIRE, FIRE_INTERVAL_TICKS, this::shootFireball);
        }
    }

    /**
     * Arma uma tarefa repetida que executa {@code action} enquanto o jogador
     * segura o botão direito (mão levantada) com o cajado no modo {@code mode}.
     */
    private void startHold(Player player, String mode, long interval, Consumer<Player> action) {
        UUID id = player.getUniqueId();
        if (firing.containsKey(id)) {
            return; // já está disparando
        }
        BukkitRunnable task = new BukkitRunnable() {
            long ticks = 0L;

            @Override
            public void run() {
                Player online = plugin.getServer().getPlayer(id);
                if (online == null || !online.isOnline()) {
                    stopFiring(id);
                    return;
                }
                ItemStack hand = online.getInventory().getItemInMainHand();
                if (!MineMagicItems.isMageStaff(hand) || !mode.equals(MineMagicItems.getMageMode(hand))) {
                    stopFiring(id);
                    return;
                }
                // Após a carência, soltar o botão (mão abaixada) encerra a rajada.
                if (ticks >= RAISE_GRACE_TICKS && !online.isHandRaised()) {
                    stopFiring(id);
                    return;
                }
                ticks += interval;
                action.accept(online);
            }
        };
        firing.put(id, task);
        task.runTaskTimer(plugin, 0L, interval);
    }

    private void stopFiring(UUID id) {
        BukkitRunnable task = firing.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    // =========================================================================
    //  Raio
    // =========================================================================

    /** Invoca {@link #LIGHTNING_BOLTS_PER_STRIKE} raios ao redor do ponto visado. */
    private void strike(Player player) {
        Location target = aimLocation(player, LIGHTNING_MAX_RANGE);
        World world = target.getWorld();
        for (int i = 0; i < LIGHTNING_BOLTS_PER_STRIKE; i++) {
            Location bolt = target.clone().add(
                    (random.nextDouble() - 0.5D) * 2.0D * LIGHTNING_SPREAD,
                    0.0D,
                    (random.nextDouble() - 0.5D) * 2.0D * LIGHTNING_SPREAD);
            world.strikeLightning(bolt);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.0f);
    }

    /** Marca a zona visada e arma a chuva de raios, se não houver uma em curso. */
    private void startThunderRain(Player player) {
        UUID id = player.getUniqueId();
        if (raining.containsKey(id)) {
            return;
        }
        Location center = aimLocation(player, RAIN_MAX_RANGE);
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.6f);

        BukkitRunnable task = new BukkitRunnable() {
            long elapsed = 0L;

            @Override
            public void run() {
                if (elapsed >= RAIN_DURATION_TICKS) {
                    stopRain(id);
                    return;
                }
                elapsed += THUNDER_RAIN_INTERVAL_TICKS;
                for (int i = 0; i < BOLTS_PER_DROP; i++) {
                    dropLightning(world, center);
                }
            }
        };
        raining.put(id, task);
        task.runTaskTimer(plugin, 0L, THUNDER_RAIN_INTERVAL_TICKS);
    }

    /** Despenca um raio (vertical, do céu) sobre um ponto aleatório da zona. */
    private void dropLightning(World world, Location center) {
        double angle = random.nextDouble() * 2.0D * Math.PI;
        double radius = THUNDER_RAIN_RADIUS * Math.sqrt(random.nextDouble());
        Location spawn = center.clone().add(
                Math.cos(angle) * radius,
                0.0D,
                Math.sin(angle) * radius);
        world.strikeLightning(spawn);
    }

    // =========================================================================
    //  Congelar (tecla 3)
    // =========================================================================

    /** Congela o alvo visado (jogador ou mob), prendendo-o por {@link #FREEZE_DURATION_TICKS}. */
    private void freezeTarget(Player caster) {
        LivingEntity target = aimEntity(caster, FREEZE_MAX_RANGE);
        if (target == null) {
            caster.sendActionBar(Component.text("Nenhum alvo na mira", NamedTextColor.GRAY));
            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        UUID id = target.getUniqueId();
        stopFreeze(id); // recongelar reinicia o relógio em vez de empilhar

        Location anchor = target.getLocation();
        World world = target.getWorld();
        world.playSound(anchor, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.4f);
        if (target instanceof Player) {
            ((Player) target).sendActionBar(Component.text("Você foi congelado!", NamedTextColor.AQUA));
        }
        caster.sendActionBar(Component.text("Congelou ", NamedTextColor.AQUA)
                .append(Component.text(targetName(target), NamedTextColor.WHITE)));

        BukkitRunnable task = new BukkitRunnable() {
            long elapsed = 0L;

            @Override
            public void run() {
                if (elapsed >= FREEZE_DURATION_TICKS || !target.isValid()) {
                    stopFreeze(id);
                    return;
                }
                elapsed++;
                // Mantém o alvo cravado no ponto de origem (sem pular nem andar).
                target.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                Location now = target.getLocation();
                if (now.distanceSquared(anchor) > 0.04D) {
                    Location fixed = anchor.clone();
                    fixed.setYaw(now.getYaw());
                    fixed.setPitch(now.getPitch());
                    target.teleport(fixed);
                }
                target.setFreezeTicks(target.getMaxFreezeTicks()); // visual de gelo
                world.spawnParticle(Particle.SNOWFLAKE, now.clone().add(0.0D, 1.0D, 0.0D), 6,
                        0.3D, 0.6D, 0.3D, 0.01D);
            }
        };
        frozen.put(id, task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private void stopFreeze(UUID id) {
        BukkitRunnable task = frozen.remove(id);
        if (task != null) {
            task.cancel();
            org.bukkit.entity.Entity target = plugin.getServer().getEntity(id);
            if (target != null) {
                target.setFreezeTicks(0);
            }
        }
    }

    private static String targetName(LivingEntity target) {
        if (target instanceof Player) {
            return ((Player) target).getName();
        }
        return target.getType().name().toLowerCase().replace('_', ' ');
    }

    /** Primeira criatura viva (que não o lançador) na linha de visada. */
    private LivingEntity aimEntity(Player caster, double maxRange) {
        Location eye = caster.getEyeLocation();
        RayTraceResult result = caster.getWorld().rayTraceEntities(
                eye, eye.getDirection(), maxRange, FREEZE_RAY_SIZE,
                e -> e instanceof LivingEntity && !e.equals(caster));
        if (result != null && result.getHitEntity() instanceof LivingEntity) {
            return (LivingEntity) result.getHitEntity();
        }
        return null;
    }

    // =========================================================================
    //  Levitar (tecla 4)
    // =========================================================================

    /** Aplica a recarga de 15s e, se liberada, lança o mago para o alto. */
    private void tryLaunchUp(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = launchCooldownUntil.get(id);
        if (until != null && now < until) {
            long remaining = (until - now + 999L) / 1000L;
            player.sendActionBar(Component.text("Levitar recarregando: " + remaining + "s",
                    NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        launchCooldownUntil.put(id, now + LAUNCH_COOLDOWN_MS);
        launchUp(player);
    }

    /** Lança o mago para cima e concede Queda Lenta para uma visão ampla. */
    private void launchUp(Player player) {
        player.setVelocity(player.getVelocity().setY(LAUNCH_UP_VELOCITY));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, true, true));
        player.setFallDistance(0.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30,
                0.4D, 0.1D, 0.4D, 0.1D);
        player.sendActionBar(Component.text("Queda lenta", NamedTextColor.AQUA));
    }

    // =========================================================================
    //  Fogo: rajada
    // =========================================================================

    /**
     * Conjura {@link #FIREBALLS_PER_VOLLEY} bolas de fogo pairando acima do jogador
     * e, após {@link #HOVER_TICKS}, despacha todas na direção da mira.
     */
    private void shootFireball(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Vector side = new Vector(-direction.getZ(), 0.0D, direction.getX());
        if (side.lengthSquared() < 1.0e-6D) {
            side = new Vector(1.0D, 0.0D, 0.0D); // olhando reto para cima/baixo
        }
        side.normalize();

        Location base = player.getLocation().add(0.0D, SPAWN_HEIGHT, 0.0D);
        List<LargeFireball> volley = new ArrayList<>(FIREBALLS_PER_VOLLEY);
        for (int i = 0; i < FIREBALLS_PER_VOLLEY; i++) {
            double offset = (i - (FIREBALLS_PER_VOLLEY - 1) / 2.0D) * SPAWN_SPREAD;
            Location spawn = base.clone().add(side.clone().multiply(offset));
            LargeFireball fireball = player.getWorld().spawn(spawn, LargeFireball.class, fb -> {
                fb.setShooter(player);
                fb.setIsIncendiary(true);
                fb.setYield(GHAST_YIELD);
                fb.setGravity(false);
                fb.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
                fb.setAcceleration(new Vector(0.0D, 0.0D, 0.0D)); // pairam até o disparo
            });
            volley.add(fireball);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        new BukkitRunnable() {
            @Override
            public void run() {
                Vector launch = player.isOnline()
                        ? player.getEyeLocation().getDirection().normalize()
                        : direction;
                for (LargeFireball fireball : volley) {
                    if (fireball.isValid()) {
                        fireball.setDirection(launch.clone());
                        fireball.setVelocity(launch.clone().multiply(FIREBALL_SPEED));
                    }
                }
                player.getWorld().playSound(base, Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
            }
        }.runTaskLater(plugin, HOVER_TICKS);
    }

    // =========================================================================
    //  Fogo: chuva (shift + botão direito)
    // =========================================================================

    /** Marca a zona visada e arma a chuva de fogo, se não houver uma em curso. */
    private void startRain(Player player) {
        UUID id = player.getUniqueId();
        if (raining.containsKey(id)) {
            return;
        }
        Location center = aimLocation(player, RAIN_MAX_RANGE);
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_GHAST_WARN, 1.0f, 0.6f);

        BukkitRunnable task = new BukkitRunnable() {
            long elapsed = 0L;

            @Override
            public void run() {
                if (elapsed >= RAIN_DURATION_TICKS) {
                    stopRain(id);
                    return;
                }
                elapsed += RAIN_INTERVAL_TICKS;
                for (int i = 0; i < FIREBALLS_PER_DROP; i++) {
                    dropFireball(world, center, player);
                }
            }
        };
        raining.put(id, task);
        task.runTaskTimer(plugin, 0L, RAIN_INTERVAL_TICKS);
    }

    private void stopRain(UUID id) {
        BukkitRunnable task = raining.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /** Faz uma bola de fogo despencar sobre um ponto aleatório da zona. */
    private void dropFireball(World world, Location center, Player shooter) {
        double angle = random.nextDouble() * 2.0D * Math.PI;
        double radius = RAIN_RADIUS * Math.sqrt(random.nextDouble());
        double height = RAIN_SKY_HEIGHT + random.nextDouble() * RAIN_HEIGHT_JITTER;
        Location spawn = center.clone().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius);

        Vector down = new Vector(0.0D, -1.0D, 0.0D);
        LargeFireball fireball = world.spawn(spawn, LargeFireball.class, fb -> {
            fb.setShooter(shooter);
            fb.setIsIncendiary(true);
            fb.setYield(GHAST_YIELD);
        });
        fireball.setDirection(down);
        fireball.setVelocity(down.clone().multiply(RAIN_FALL_SPEED));
    }

    /**
     * Ponto visado: o bloco mirado (se houver) ou um ponto a {@code maxRange}
     * blocos na direção do olhar do jogador.
     */
    private Location aimLocation(Player player, double maxRange) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), maxRange, FluidCollisionMode.NEVER, true);
        if (result != null && result.getHitPosition() != null) {
            Vector hit = result.getHitPosition();
            return new Location(player.getWorld(), hit.getX(), hit.getY(), hit.getZ());
        }
        return eye.clone().add(eye.getDirection().multiply(maxRange));
    }

    // =========================================================================
    //  Bloqueia o arremesso do tridente
    // =========================================================================

    @EventHandler
    public void onTridentLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident)) {
            return;
        }
        Trident trident = (Trident) event.getEntity();
        if (!(trident.getShooter() instanceof Player)) {
            return;
        }
        Player shooter = (Player) trident.getShooter();
        if (MineMagicItems.isMageStaff(shooter.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopFiring(id);
        stopRain(id);
        stopFreeze(id); // se o jogador estava congelado, libera a tarefa
        launchCooldownUntil.remove(id);
    }

    /** Cancela tarefas em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = firing.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
        for (Iterator<BukkitRunnable> it = raining.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
        for (Iterator<BukkitRunnable> it = frozen.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
