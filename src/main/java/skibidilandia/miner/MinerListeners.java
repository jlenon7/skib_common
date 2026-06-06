package skibidilandia.miner;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
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

public class MinerListeners implements Listener {

    private final MinerRegistry registry;

    public MinerListeners(MinerRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        MinerTier tier = MinerItems.readTier(event.getItemInHand());
        if (tier == null) {
            return;
        }
        // A mineradora de Netherite só funciona no Nether.
        if (tier == MinerTier.NETHERITE
                && event.getBlock().getWorld().getEnvironment() != World.Environment.NETHER) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + tier.getDisplayName()
                    + ChatColor.GRAY + " só pode ser colocada no Nether.");
            return;
        }
        registry.add(event.getBlock().getLocation(), new MinerData(tier.getLevel()));
        event.getPlayer().sendMessage(ChatColor.GREEN + tier.getDisplayName()
                + ChatColor.GRAY + " posicionada! Clique com o botão direito para abrir.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!registry.isMiner(loc)) {
            return;
        }
        MinerData data = registry.get(loc);

        // fecha quem estiver com a GUI aberta (o close sincroniza a saída de volta)
        if (data.getOpenView() != null) {
            for (HumanEntity viewer : new ArrayList<>(data.getOpenView().getViewers())) {
                viewer.closeInventory();
            }
        }

        registry.remove(loc);
        event.setDropItems(false);
        event.getBlock().setType(org.bukkit.Material.AIR);

        Location drop = loc.clone().add(0.5, 0.5, 0.5);
        MinerTier tier = MinerTier.fromLevel(data.getLevel());
        if (tier != null) {
            loc.getWorld().dropItemNaturally(drop, MinerItems.create(tier));
        }
        for (ItemStack stack : data.getOutput()) {
            if (stack != null && stack.getType() != org.bukkit.Material.AIR) {
                loc.getWorld().dropItemNaturally(drop, stack);
            }
        }
        event.getPlayer().sendMessage(ChatColor.YELLOW + "Mineradora recolhida.");
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
        if (!registry.isMiner(block.getLocation())) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            return; // permite colocar bloco em cima etc.
        }
        event.setCancelled(true);
        MinerData data = registry.get(block.getLocation());
        MinerGui.open(event.getPlayer(), data, block.getLocation());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof MinerGui.Holder)) {
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw < top.getSize();
        if (!inTop) {
            return; // cliques no inventário do player são livres
        }

        // info / filler (fileira de cima e de baixo): nunca mexer
        boolean isInfoRow = raw < 9;
        boolean isBottomRow = raw >= 45;
        if (isInfoRow || isBottomRow) {
            event.setCancelled(true);
        }
        // saída (slots 9..44): livre — aqui o player guarda combustível e retira minério
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof MinerGui.Holder)) {
            return;
        }
        MinerGui.Holder holder = (MinerGui.Holder) inv.getHolder();
        MinerData data = holder.getData();
        MinerGui.syncFromInventory(data, inv);

        // só limpa se o inventário fechado é REALMENTE a view atual
        // (evita que um close de uma janela antiga apague uma janela nova)
        if (data.getOpenView() == inv) {
            data.setOpenView(null);
        }
    }
}
