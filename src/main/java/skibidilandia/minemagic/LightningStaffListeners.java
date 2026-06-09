package skibidilandia.minemagic;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
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
import java.util.UUID;

/**
 * Comportamento do Cajado do Raio.
 *
 * Disparo simples:
 *  - Ao clicar com o botão direito, um raio cai do céu sobre o ponto que o
 *    jogador está mirando (o bloco visado ou, se mirar para o céu, um ponto a
 *    {@link #MAX_RANGE} blocos na direção do olhar).
 *
 * Disparo em rajada:
 *  - Igual ao Cajado de Fogo: ao segurar o botão direito o tridente faz a
 *    animação de carga (não cancelamos a interação). Enquanto a mão estiver
 *    levantada ({@link Player#isHandRaised()}) e o cajado na mão principal, um
 *    raio é invocado a cada {@link #STRIKE_INTERVAL_TICKS}.
 *  - O Minecraft não emite evento contínuo de "segurando"; por isso a interação
 *    inicial apenas ARMA uma tarefa repetida que checa {@code isHandRaised()} a
 *    cada tick de intervalo e se cancela sozinha quando o jogador solta.
 *  - Há um curto período de carência inicial ({@link #RAISE_GRACE_TICKS}) porque
 *    a mão só passa a contar como "levantada" no tick seguinte ao clique.
 *
 * Não arremessar o tridente:
 *  - Ao soltar, o vanilla tentaria arremessar o tridente. Cancelamos o
 *    {@link ProjectileLaunchEvent} do {@link Trident} cujo atirador segura o
 *    Cajado do Raio — o item permanece na mão e nada é arremessado.
 */
public class LightningStaffListeners implements Listener {

    /** Intervalo (ticks) entre raios enquanto segura o botão. */
    private static final long STRIKE_INTERVAL_TICKS = 8L;
    /** Carência inicial (ticks) antes de exigir a mão levantada. */
    private static final int RAISE_GRACE_TICKS = 4;
    /** Alcance máximo (blocos) do raio quando o jogador mira para longe/céu. */
    private static final double MAX_RANGE = 60.0D;

    private final JavaPlugin plugin;

    /** Jogadores com uma rajada de raios em curso. */
    private final Map<UUID, BukkitRunnable> firing = new HashMap<>();

    public LightningStaffListeners(JavaPlugin plugin) {
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
        if (!MineMagicItems.isLightningStaff(player.getInventory().getItemInMainHand())) {
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
            int ticks = 0;

            @Override
            public void run() {
                Player online = plugin.getServer().getPlayer(id);
                if (online == null || !online.isOnline()
                        || !MineMagicItems.isLightningStaff(online.getInventory().getItemInMainHand())) {
                    stopFiring(id);
                    return;
                }
                // Após a carência, soltar o botão (mão abaixada) encerra a rajada.
                if (ticks >= RAISE_GRACE_TICKS && !online.isHandRaised()) {
                    stopFiring(id);
                    return;
                }
                ticks += STRIKE_INTERVAL_TICKS;
                strike(online);
            }
        };
        firing.put(id, task);
        task.runTaskTimer(plugin, 0L, STRIKE_INTERVAL_TICKS);
    }

    private void stopFiring(UUID id) {
        BukkitRunnable task = firing.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /** Invoca um raio sobre o ponto visado pelo jogador. */
    private void strike(Player player) {
        Location target = aimLocation(player);
        target.getWorld().strikeLightning(target);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.0f);
    }

    /**
     * Ponto em que o raio deve cair: o bloco mirado (se houver) ou um ponto a
     * {@link #MAX_RANGE} blocos na direção do olhar do jogador.
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
        // O item continua na mão ao soltar; se for o Cajado do Raio, não arremessa.
        if (MineMagicItems.isLightningStaff(shooter.getInventory().getItemInMainHand())) {
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
