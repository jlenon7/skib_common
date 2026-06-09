package skibidilandia.minemagic;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Comportamento do Cajado do Elfo Negro.
 *
 * Disparo:
 *  - Ao clicar com o botão direito, o jogador marca uma zona (o bloco que está
 *    mirando ou, se mirar para o céu, um ponto a {@link #MAX_RANGE} blocos na
 *    direção do olhar). Sobre essa zona desaba uma chuva de flechas durante
 *    {@link #STORM_DURATION_TICKS}: a cada {@link #ARROW_INTERVAL_TICKS} as
 *    flechas surgem no céu seguindo uma espiral que gira em torno do centro,
 *    cobrindo toda a área enquanto despencam.
 *  - Cada jogador só pode ter uma tempestade ativa por vez; clicar de novo
 *    enquanto uma está em curso não a reinicia.
 *
 * Não arremessar o tridente:
 *  - Ao soltar, o vanilla tentaria arremessar o tridente. Cancelamos o
 *    {@link ProjectileLaunchEvent} do {@link Trident} cujo atirador segura o
 *    Cajado do Elfo Negro — o item permanece na mão e nada é arremessado.
 */
public class DarkElfListeners implements Listener {

    /** Quanto tempo (ticks) a chuva de flechas dura. */
    private static final long STORM_DURATION_TICKS = 100L;
    /** Intervalo (ticks) entre cada leva de flechas. */
    private static final long ARROW_INTERVAL_TICKS = 2L;
    /** Quantas flechas surgem por leva (pontos consecutivos da espiral). */
    private static final int ARROWS_PER_TICK = 3;
    /** Alcance máximo (blocos) ao mirar a zona quando o olhar não atinge bloco. */
    private static final double MAX_RANGE = 50.0D;
    /** Altura (blocos) acima da zona de onde as flechas caem. */
    private static final double SKY_HEIGHT = 22.0D;
    /** Raio (blocos) da zona coberta pela espiral. */
    private static final double SPIRAL_RADIUS = 5.0D;
    /** Avanço angular (radianos) entre flechas consecutivas da espiral. */
    private static final double ANGLE_STEP = 0.55D;
    /** Voltas que a espiral completa antes de o raio reiniciar (braço da espiral). */
    private static final double TURNS_PER_ARM = 2.0D;
    /** Velocidade inicial (para baixo) das flechas. */
    private static final double ARROW_SPEED = 2.2D;

    /** Ruído angular (radianos) somado ao ângulo da espiral por flecha. */
    private static final double ANGLE_JITTER = 0.9D;
    /** Ruído radial (blocos) somado/subtraído ao raio da espiral por flecha. */
    private static final double RADIUS_JITTER = 1.7D;
    /** Variação extra de altura (blocos) para que as flechas cheguem em tempos diferentes. */
    private static final double HEIGHT_JITTER = 7.0D;
    /** Divergência de trajetória passada ao spawn (espalha a direção da queda). */
    private static final float ARROW_SPREAD = 4.0f;

    private final JavaPlugin plugin;
    /** Fonte de aleatoriedade para tornar a queda das flechas imprevisível. */
    private final Random random = new Random();

    /** Jogadores com uma tempestade de flechas em curso. */
    private final Map<UUID, BukkitRunnable> storms = new HashMap<>();

    public DarkElfListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Início da tempestade
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
        if (!MineMagicItems.isDarkElfStaff(player.getInventory().getItemInMainHand())) {
            return;
        }
        startStorm(player);
    }

    /** Marca a zona visada e arma a tempestade de flechas, se não houver uma. */
    private void startStorm(Player player) {
        UUID id = player.getUniqueId();
        if (storms.containsKey(id)) {
            return; // já há uma tempestade em curso
        }
        Location center = aimLocation(player);
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.7f);

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
                for (int i = 0; i < ARROWS_PER_TICK; i++) {
                    rainArrow(world, center, player, spawned++);
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
     * Faz uma flecha despencar sobre a zona. A espiral (índice {@code n}) só dá
     * o viés de cobertura para a área toda ser varrida; sobre ele há forte ruído
     * por flecha — ângulo, raio, altura e divergência da trajetória — para que o
     * ponto de queda seja imprevisível e não dê para "decorar" o padrão e desviar.
     */
    private void rainArrow(World world, Location center, Player shooter, int n) {
        double angle = n * ANGLE_STEP + jitter(ANGLE_JITTER);
        double armPhase = ((n * ANGLE_STEP) / (TURNS_PER_ARM * 2.0D * Math.PI)) % 1.0D;
        double radius = SPIRAL_RADIUS * armPhase + jitter(RADIUS_JITTER);
        radius = Math.max(0.0D, Math.min(SPIRAL_RADIUS, radius)); // mantém dentro da zona

        double height = SKY_HEIGHT + random.nextDouble() * HEIGHT_JITTER;
        Location spawn = center.clone().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius);

        // Spread embaralha a direção da queda — somado ao ruído de posição, cada
        // flecha cai em um ponto distinto.
        Arrow arrow = world.spawnArrow(spawn, new Vector(0.0D, -1.0D, 0.0D), (float) ARROW_SPEED, ARROW_SPREAD);
        arrow.setShooter(shooter);
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    /** Ruído simétrico em [-amount, +amount]. */
    private double jitter(double amount) {
        return (random.nextDouble() * 2.0D - 1.0D) * amount;
    }

    /**
     * Centro da zona: o bloco mirado (se houver) ou um ponto a {@link #MAX_RANGE}
     * blocos na direção do olhar do jogador.
     */
    private Location aimLocation(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                eye, eye.getDirection(), MAX_RANGE, FluidCollisionMode.NEVER, true);
        if (result != null && result.getHitPosition() != null) {
            Vector hit = result.getHitPosition();
            return new Location(player.getWorld(), hit.getX(), hit.getY(), hit.getZ());
        }
        return eye.clone().add(eye.getDirection().multiply(MAX_RANGE));
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
        // O item continua na mão ao soltar; se for o Cajado do Elfo Negro, não arremessa.
        if (MineMagicItems.isDarkElfStaff(shooter.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /** Cancela todas as tempestades em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = storms.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
