package skibidilandia.blueprint;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trata os dois cliques: a varinha (define os cantos da seleção) e o blueprint
 * (cola a construção no chão e gasta o item). A seleção de cada jogador fica em
 * memória — não precisa persistir.
 */
public class BlueprintListeners implements Listener {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public Location getPos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2.get(player.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // o clique direito dispara 2x (mão principal + secundária); só a principal
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        Player player = event.getPlayer();

        if (BlueprintItems.isWand(item)) {
            handleWand(event, player);
            return;
        }

        BlueprintData data = BlueprintItems.readData(item);
        if (data != null && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null) {
            event.setCancelled(true);
            handlePaste(player, event.getClickedBlock(), data);
        }
    }

    private void handleWand(PlayerInteractEvent event, Player player) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            pos1.put(player.getUniqueId(), block.getLocation());
            player.sendMessage(ChatColor.AQUA + "Canto 1 definido: " + describe(block.getLocation()));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            pos2.put(player.getUniqueId(), block.getLocation());
            player.sendMessage(ChatColor.AQUA + "Canto 2 definido: " + describe(block.getLocation()));
        }
    }

    private void handlePaste(Player player, Block clicked, BlueprintData data) {
        // canto mínimo da construção fica logo em cima do bloco clicado
        Location base = clicked.getLocation().add(0, 1, 0);
        int placed = data.paste(base);
        if (placed <= 0) {
            player.sendMessage(ChatColor.RED + "Não foi possível gerar a construção aqui.");
            return;
        }
        consumeOne(player);
        player.sendMessage(ChatColor.GREEN + "Construção gerada! " + ChatColor.GRAY
                + "(" + placed + " blocos)");
    }

    /** Tira uma unidade do blueprint da mão principal. */
    private void consumeOne(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private static String describe(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
