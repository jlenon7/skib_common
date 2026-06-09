package skibidilandia.minemagic;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
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
 * Comportamento do Cajado de Fogo.
 *
 * Disparo em rajada:
 *  - Ao segurar o botão direito o tridente faz a animação de carga normal (não
 *    cancelamos a interação, por isso a mão fica "levantada"). Enquanto a mão
 *    estiver levantada ({@link Player#isHandRaised()}) e o cajado na mão
 *    principal, uma rajada de {@link LargeFireball} (com explosão de ghast) é
 *    conjurada acima do jogador a cada {@link #FIRE_INTERVAL_TICKS} e despachada
 *    na direção da mira.
 *  - O Minecraft não emite evento contínuo de "segurando"; por isso a interação
 *    inicial apenas ARMA uma tarefa repetida que checa {@code isHandRaised()} a
 *    cada tick de intervalo e se cancela sozinha quando o jogador solta.
 *  - Há um curto período de carência inicial ({@link #RAISE_GRACE_TICKS}) porque
 *    a mão só passa a contar como "levantada" no tick seguinte ao clique.
 *
 * Chuva de fogo (shift + botão direito):
 *  - Se o jogador estiver agachado ({@link Player#isSneaking()}) ao clicar com o
 *    botão direito, em vez da rajada uma chuva de {@link LargeFireball} (explosão
 *    de ghast) desaba do céu sobre a zona visada durante {@link #RAIN_DURATION_TICKS},
 *    com pontos de queda aleatórios para não dar para prever/desviar.
 *
 * Não arremessar o tridente:
 *  - Ao soltar, o vanilla tentaria arremessar o tridente. Cancelamos o
 *    {@link ProjectileLaunchEvent} do {@link Trident} cujo atirador segura o
 *    Cajado de Fogo — o item permanece na mão e nada é arremessado.
 */
public class FireStaffListeners implements Listener {

    /** Intervalo (ticks) entre rajadas de fogo enquanto segura o botão. */
    private static final long FIRE_INTERVAL_TICKS = 5L;
    /** Carência inicial (ticks) antes de exigir a mão levantada. */
    private static final int RAISE_GRACE_TICKS = 4;
    /** Velocidade com que as bolas de fogo são despachadas. */
    private static final double FIREBALL_SPEED = 1.4D;

    /** Quantas bolas de fogo são conjuradas acima do jogador por rajada. */
    private static final int FIREBALLS_PER_VOLLEY = 3;
    /** Altura (blocos) acima do jogador onde as bolas surgem. */
    private static final double SPAWN_HEIGHT = 2.6D;
    /** Espaçamento lateral (blocos) entre as bolas conjuradas. */
    private static final double SPAWN_SPREAD = 1.1D;
    /** Quanto tempo (ticks) as bolas pairam acima do jogador antes do disparo. */
    private static final long HOVER_TICKS = 8L;
    /** Poder da explosão — mesmo valor que o ghast usa para suas bolas de fogo. */
    private static final float GHAST_YIELD = 1.0F;

    // --- Chuva de fogo (shift + botão direito) ---
    /** Quanto tempo (ticks) a chuva de fogo dura. */
    private static final long RAIN_DURATION_TICKS = 60L;
    /** Intervalo (ticks) entre cada leva de bolas de fogo. */
    private static final long RAIN_INTERVAL_TICKS = 4L;
    /** Quantas bolas de fogo caem por leva. */
    private static final int FIREBALLS_PER_DROP = 2;
    /** Alcance máximo (blocos) ao mirar a zona quando o olhar não atinge bloco. */
    private static final double RAIN_MAX_RANGE = 50.0D;
    /** Raio (blocos) da zona atingida pela chuva. */
    private static final double RAIN_RADIUS = 5.0D;
    /** Altura (blocos) acima da zona de onde as bolas caem. */
    private static final double RAIN_SKY_HEIGHT = 20.0D;
    /** Variação extra de altura (blocos) para que cheguem em tempos diferentes. */
    private static final double RAIN_HEIGHT_JITTER = 7.0D;
    /** Velocidade inicial (para baixo) das bolas que caem. */
    private static final double RAIN_FALL_SPEED = 1.0D;

    private final JavaPlugin plugin;
    /** Fonte de aleatoriedade para tornar a queda das bolas imprevisível. */
    private final Random random = new Random();

    /** Jogadores com uma rajada de fogo em curso. */
    private final Map<UUID, BukkitRunnable> firing = new HashMap<>();
    /** Jogadores com uma chuva de fogo em curso. */
    private final Map<UUID, BukkitRunnable> raining = new HashMap<>();

    public FireStaffListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Início da rajada
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
        if (!MineMagicItems.isFireStaff(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (player.isSneaking()) {
            startRain(player); // shift + botão direito: chuva de fogo sobre a zona visada
        } else {
            startFiring(player);
        }
    }

    /** Arma a tarefa de rajada para o jogador, se ainda não houver uma. */
    private void startFiring(Player player) {
        UUID id = player.getUniqueId();
        if (firing.containsKey(id)) {
            return; // já está disparando
        }
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Player online = plugin.getServer().getPlayer(id);
                if (online == null || !online.isOnline()
                        || !MineMagicItems.isFireStaff(online.getInventory().getItemInMainHand())) {
                    stopFiring(id);
                    return;
                }
                // Após a carência, soltar o botão (mão abaixada) encerra a rajada.
                if (ticks >= RAISE_GRACE_TICKS && !online.isHandRaised()) {
                    stopFiring(id);
                    return;
                }
                ticks += FIRE_INTERVAL_TICKS;
                shootFireball(online);
            }
        };
        firing.put(id, task);
        task.runTaskTimer(plugin, 0L, FIRE_INTERVAL_TICKS);
    }

    private void stopFiring(UUID id) {
        BukkitRunnable task = firing.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    // =========================================================================
    //  Chuva de fogo (shift + botão direito)
    // =========================================================================

    /** Marca a zona visada e arma a chuva de fogo, se não houver uma em curso. */
    private void startRain(Player player) {
        UUID id = player.getUniqueId();
        if (raining.containsKey(id)) {
            return; // já há uma chuva em curso
        }
        Location center = aimLocation(player);
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

    /**
     * Faz uma bola de fogo grande despencar sobre um ponto aleatório da zona.
     * O ponto é sorteado uniformemente no disco (ângulo livre, raio com
     * {@code sqrt}) e a altura varia, para que a queda seja imprevisível.
     */
    private void dropFireball(World world, Location center, Player shooter) {
        double angle = random.nextDouble() * 2.0D * Math.PI;
        double radius = RAIN_RADIUS * Math.sqrt(random.nextDouble());
        double height = RAIN_SKY_HEIGHT + random.nextDouble() * RAIN_HEIGHT_JITTER;
        Location spawn = center.clone().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius);

        Vector down = new Vector(0.0D, -1.0D, 0.0D);
        // Mesma bola de fogo do ghast (LargeFireball). A aceleração/velocidade de
        // queda têm de ser aplicadas DEPOIS de a entidade entrar no mundo — o
        // consumer de spawn roda antes do add e descarta a velocidade, deixando a
        // bola à deriva (sem despencar nem explodir como a do ghast).
        LargeFireball fireball = world.spawn(spawn, LargeFireball.class, fb -> {
            fb.setShooter(shooter);
            fb.setIsIncendiary(true);
            fb.setYield(GHAST_YIELD);
        });
        fireball.setDirection(down);                               // aceleração para baixo (como o ghast mira)
        fireball.setVelocity(down.clone().multiply(RAIN_FALL_SPEED));
    }

    /**
     * Centro da zona: o bloco mirado (se houver) ou um ponto a
     * {@link #RAIN_MAX_RANGE} blocos na direção do olhar do jogador.
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

    /**
     * Conjura {@link #FIREBALLS_PER_VOLLEY} bolas de fogo grandes pairando acima
     * do jogador e, após {@link #HOVER_TICKS}, despacha todas na direção em que o
     * jogador olha. As bolas explodem como as do ghast ({@link LargeFireball} com
     * {@link LargeFireball#setYield(float)} igual ao do ghast).
     */
    private void shootFireball(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        // Vetor lateral (perpendicular à mira no plano horizontal) para espalhar as bolas.
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

        // Após pairar, despacha a rajada na direção da mira.
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
        // O item continua na mão ao soltar; se for o Cajado de Fogo, não arremessa.
        if (MineMagicItems.isFireStaff(shooter.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /** Cancela todas as rajadas e chuvas em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = firing.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
        for (Iterator<BukkitRunnable> it = raining.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
