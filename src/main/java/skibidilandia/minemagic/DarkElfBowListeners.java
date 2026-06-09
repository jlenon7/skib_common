package skibidilandia.minemagic;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Comportamento do Arco do Elfo Negro.
 *
 * Metralhadora de flechas:
 *  - Ao segurar o botão direito o arco faz a animação normal de puxar (a mão fica
 *    "levantada"). Só depois de {@link #FULL_DRAW_TICKS} — quando o arco está
 *    totalmente puxado — é que ele começa a metralhar: enquanto a mão estiver
 *    levantada ({@link Player#isHandRaised()}) e o arco na mão principal, uma rajada
 *    de {@link #ARROWS_PER_VOLLEY} flechas em leque sai a cada
 *    {@link #FIRE_INTERVAL_TICKS}, sempre na {@link #ARROW_SPEED potência máxima}.
 *  - O Minecraft não emite evento contínuo de "segurando"; por isso a interação
 *    inicial apenas ARMA uma tarefa repetida (com atraso da carga) que checa
 *    {@code isHandRaised()} a cada tick de intervalo e se cancela quando solta.
 *  - Como um arco só puxa se houver munição, o jogador precisa de ao menos 1 flecha
 *    no inventário — mas ela nunca é consumida: o leque é mágico e o disparo vanilla
 *    do arco (ao soltar) é cancelado em {@link #onShootBow(EntityShootBowEvent)}.
 */
public class DarkElfBowListeners implements Listener {

    /** Intervalo (ticks) entre rajadas de flechas enquanto segura o botão. */
    private static final long FIRE_INTERVAL_TICKS = 3L;
    /** Tempo (ticks) para o arco terminar de puxar (ficar "no talo") antes da 1ª leva. */
    private static final long FULL_DRAW_TICKS = 20L;
    /** Flechas por rajada (uma no centro e uma para cada lado). */
    private static final int ARROWS_PER_VOLLEY = 3;
    /** Desvio (graus) de cada flecha lateral em relação à do centro. */
    private static final double SPREAD_DEGREES = 10.0D;
    /** Velocidade das flechas — a de um arco totalmente carregado (potência máxima). */
    private static final double ARROW_SPEED = 3.0D;

    private final JavaPlugin plugin;
    /** Jogadores com uma rajada de flechas em curso. */
    private final Map<UUID, BukkitRunnable> firing = new HashMap<>();

    public DarkElfBowListeners(JavaPlugin plugin) {
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
        if (!MineMagicItems.isDarkElfBow(player.getInventory().getItemInMainHand())) {
            return;
        }
        startFiring(player);
    }

    /** Arma a tarefa de rajada para o jogador, se ainda não houver uma. */
    private void startFiring(Player player) {
        UUID id = player.getUniqueId();
        if (firing.containsKey(id)) {
            return; // já está disparando
        }
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Player online = plugin.getServer().getPlayer(id);
                if (online == null || !online.isOnline()
                        || !MineMagicItems.isDarkElfBow(online.getInventory().getItemInMainHand())) {
                    stopFiring(id);
                    return;
                }
                // Soltar o botão (mão abaixada) encerra a rajada. Como a 1ª leva só
                // ocorre depois de FULL_DRAW_TICKS, a mão já está levantada quando
                // esta tarefa roda pela primeira vez — não precisa de carência.
                if (!online.isHandRaised()) {
                    stopFiring(id);
                    return;
                }
                shootVolley(online);
            }
        };
        firing.put(id, task);
        // Espera o arco terminar a animação de puxar (potência máxima) antes da
        // primeira leva; depois metralha a cada FIRE_INTERVAL_TICKS.
        task.runTaskTimer(plugin, FULL_DRAW_TICKS, FIRE_INTERVAL_TICKS);
    }

    private void stopFiring(UUID id) {
        BukkitRunnable task = firing.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /** Dispara um leque de {@link #ARROWS_PER_VOLLEY} flechas na direção da mira. */
    private void shootVolley(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Location spawn = eye.clone().add(eye.getDirection().multiply(0.5D));
        Vector forward = eye.getDirection().normalize().multiply(ARROW_SPEED);

        double angle = Math.toRadians(SPREAD_DEGREES);
        spawnArrow(world, spawn, player, forward);
        spawnArrow(world, spawn, player, rotateY(forward, angle));
        spawnArrow(world, spawn, player, rotateY(forward, -angle));

        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 0.7f, 0.8f);
    }

    /** Cria uma flecha mágica (não recolhível) com a velocidade dada. */
    private void spawnArrow(World world, Location spawn, Player shooter, Vector velocity) {
        Arrow arrow = world.spawnArrow(spawn, velocity, (float) velocity.length(), 0.0f);
        arrow.setVelocity(velocity); // preserva exatamente a magnitude/direção (potência máxima)
        arrow.setShooter(shooter);
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    /** Gira o vetor no plano horizontal (em torno do eixo Y) pelo ângulo dado. */
    private Vector rotateY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    // =========================================================================
    //  Bloqueia o disparo vanilla do arco
    // =========================================================================

    /** Ao soltar, o arco tentaria atirar uma flecha vanilla; cancelamos para não
     *  gastar munição nem duplicar o tiro — o leque é todo conjurado pela tarefa. */
    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (MineMagicItems.isDarkElfBow(event.getBow())) {
            event.setCancelled(true);
        }
    }

    /** Cancela todas as rajadas em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = firing.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
