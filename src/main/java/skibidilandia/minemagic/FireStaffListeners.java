package skibidilandia.minemagic;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Comportamento do Cajado de Fogo.
 *
 * Disparo em rajada:
 *  - Ao segurar o botão direito o tridente faz a animação de carga normal (não
 *    cancelamos a interação, por isso a mão fica "levantada"). Enquanto a mão
 *    estiver levantada ({@link Player#isHandRaised()}) e o cajado na mão
 *    principal, uma {@link SmallFireball} é lançada a cada {@link #FIRE_INTERVAL_TICKS}.
 *  - O Minecraft não emite evento contínuo de "segurando"; por isso a interação
 *    inicial apenas ARMA uma tarefa repetida que checa {@code isHandRaised()} a
 *    cada tick de intervalo e se cancela sozinha quando o jogador solta.
 *  - Há um curto período de carência inicial ({@link #RAISE_GRACE_TICKS}) porque
 *    a mão só passa a contar como "levantada" no tick seguinte ao clique.
 *
 * Não arremessar o tridente:
 *  - Ao soltar, o vanilla tentaria arremessar o tridente. Cancelamos o
 *    {@link ProjectileLaunchEvent} do {@link Trident} cujo atirador segura o
 *    Cajado de Fogo — o item permanece na mão e nada é arremessado.
 */
public class FireStaffListeners implements Listener {

    /** Intervalo (ticks) entre bolas de fogo enquanto segura o botão. */
    private static final long FIRE_INTERVAL_TICKS = 5L;
    /** Carência inicial (ticks) antes de exigir a mão levantada. */
    private static final int RAISE_GRACE_TICKS = 4;
    /** Velocidade da bola de fogo. */
    private static final double FIREBALL_SPEED = 1.4D;

    private final JavaPlugin plugin;

    /** Jogadores com uma rajada de fogo em curso. */
    private final Map<UUID, BukkitRunnable> firing = new HashMap<>();

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

    /** Lança uma bola de fogo na direção em que o jogador olha. */
    private void shootFireball(Player player) {
        SmallFireball fireball = player.launchProjectile(SmallFireball.class,
                player.getEyeLocation().getDirection().multiply(FIREBALL_SPEED));
        fireball.setIsIncendiary(true);
        fireball.setShooter(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.2f);
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

    /** Cancela todas as rajadas em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = firing.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
