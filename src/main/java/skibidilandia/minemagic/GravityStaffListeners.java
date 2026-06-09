package skibidilandia.minemagic;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Comportamento do Cajado da Gravidade.
 *
 * Atração (botão direito):
 *  - Arremessa para o alto e puxa em sua direção todos os mobs e jogadores
 *    dentro de uma caixa de 10x10x10 blocos ao redor de quem usa.
 *
 * Repulsão (shift + botão direito):
 *  - Arremessa para o alto e empurra para longe (como uma onda de repulsão)
 *    todos os mobs e jogadores dentro da mesma caixa de 10x10x10 blocos.
 *
 * Não arremessar o tridente:
 *  - A base do cajado é um tridente; cancelamos o {@link ProjectileLaunchEvent}
 *    de quem segura o Cajado da Gravidade — o item permanece na mão.
 */
public class GravityStaffListeners implements Listener {

    /** Meia-aresta da caixa de efeito (5 => caixa de 10x10x10 blocos). */
    private static final double RANGE = 5.0D;

    /** Empurrão horizontal ao puxar as entidades em direção ao jogador. */
    private static final double PULL_HORIZONTAL = 0.9D;
    /** Componente vertical (arremesso para o alto) ao puxar. */
    private static final double PULL_UP = 0.55D;

    /** Empurrão horizontal ao repelir as entidades para longe do jogador. */
    private static final double PUSH_HORIZONTAL = 1.3D;
    /** Componente vertical (arremesso para o alto) ao repelir. */
    private static final double PUSH_UP = 0.6D;

    private final JavaPlugin plugin;

    public GravityStaffListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // só a mão principal; evita o disparo duplicado da off-hand
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!MineMagicItems.isGravityStaff(player.getInventory().getItemInMainHand())) {
            return;
        }

        if (player.isSneaking()) {
            repulse(player);
        } else {
            pull(player);
        }
    }

    /** Arremessa para o alto e puxa todas as entidades próximas em direção ao jogador. */
    private void pull(Player player) {
        Location center = player.getLocation();
        for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(center, RANGE, RANGE, RANGE)) {
            if (entity.equals(player)) {
                continue; // não afeta quem usa o cajado
            }
            Vector horizontal = horizontalTowards(entity.getLocation(), center);
            entity.setVelocity(horizontal.multiply(PULL_HORIZONTAL).setY(PULL_UP));
        }
        player.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.6f);
    }

    /** Arremessa para o alto e empurra todas as entidades próximas para longe do jogador. */
    private void repulse(Player player) {
        Location center = player.getLocation();
        for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(center, RANGE, RANGE, RANGE)) {
            if (entity.equals(player)) {
                continue; // não afeta quem usa o cajado
            }
            Vector horizontal = horizontalTowards(center, entity.getLocation());
            entity.setVelocity(horizontal.multiply(PUSH_HORIZONTAL).setY(PUSH_UP));
        }
        player.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.7f);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.8f);
    }

    /**
     * Vetor horizontal unitário apontando de {@code from} para {@code to}. Quando
     * estão (quase) na mesma coluna o resultado é o vetor zero, deixando apenas o
     * arremesso vertical agir (evita normalizar um vetor nulo => NaN).
     */
    private static Vector horizontalTowards(Location from, Location to) {
        Vector diff = new Vector(to.getX() - from.getX(), 0.0D, to.getZ() - from.getZ());
        if (diff.lengthSquared() < 1.0e-6D) {
            return new Vector(0.0D, 0.0D, 0.0D);
        }
        return diff.normalize();
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
        if (MineMagicItems.isGravityStaff(shooter.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /** Sem tarefas em segundo plano; presente por simetria com os outros cajados. */
    public void shutdown() {
    }
}
