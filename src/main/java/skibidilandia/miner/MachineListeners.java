package skibidilandia.miner;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public class MachineListeners implements Listener {

    private final MachineRegistry registry;

    public MachineListeners(MachineRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        MachineType type = MachineItems.readType(event.getItemInHand());
        if (type == null) {
            return;
        }
        registry.add(event.getBlock().getLocation(), new MachineData(type));
        event.getPlayer().sendMessage(ChatColor.GREEN + type.getDisplayName()
                + ChatColor.GRAY + " posicionada! Clique com o botão direito para abrir.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!registry.isMachine(loc)) {
            return;
        }
        MachineData data = registry.get(loc);

        // fecha quem estiver com a GUI aberta (o close sincroniza a storage de volta)
        if (data.getOpenView() != null) {
            for (HumanEntity viewer : new ArrayList<>(data.getOpenView().getViewers())) {
                viewer.closeInventory();
            }
        }

        registry.remove(loc);
        event.setDropItems(false);
        event.getBlock().setType(org.bukkit.Material.AIR);

        Location drop = loc.clone().add(0.5, 0.5, 0.5);
        loc.getWorld().dropItemNaturally(drop, MachineItems.create(data.getType()));
        for (ItemStack stack : data.getStorage()) {
            if (stack != null && stack.getType() != org.bukkit.Material.AIR) {
                loc.getWorld().dropItemNaturally(drop, stack);
            }
        }
        event.getPlayer().sendMessage(ChatColor.YELLOW + data.getType().getDisplayName() + " recolhida.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        // o clique dispara 2x (mão principal + secundária); só tratamos a principal
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!registry.isMachine(block.getLocation())) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            return; // permite colocar bloco em cima etc.
        }
        event.setCancelled(true);
        MachineData data = registry.get(block.getLocation());
        MachineGui.open(event.getPlayer(), data, block.getLocation());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof MachineGui.Holder)) {
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw < top.getSize();
        if (!inTop) {
            return; // cliques no inventário do player são livres
        }
        // só a fileira de info (slots 0..8) é travada; a storage (9..53) é livre
        if (raw < MachineGui.STORAGE_START) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof MachineGui.Holder)) {
            return;
        }
        MachineGui.Holder holder = (MachineGui.Holder) inv.getHolder();
        MachineData data = holder.getData();
        MachineGui.syncFromInventory(data, inv);

        // só limpa se o inventário fechado é REALMENTE a view atual
        if (data.getOpenView() == inv) {
            data.setOpenView(null);
        }
    }
}
