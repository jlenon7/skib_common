package skibidilandia.miner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI das máquinas auxiliares: linha de info no topo e a storage interna logo abaixo.
 * Mais simples que a da mineradora (sem combustível nem barra de progresso).
 */
public final class MachineGui {

    public static final int SIZE = 54;
    public static final int STORAGE_START = 9;    // slots 9..53 (45 slots)
    public static final int STORAGE_END = 53;
    public static final int INFO_SLOT = 4;

    private MachineGui() {
    }

    /** Holder que liga o inventário aberto de volta à máquina. */
    public static final class Holder implements InventoryHolder {
        private final MachineData data;
        private final Location location;
        private Inventory inventory;

        Holder(MachineData data, Location location) {
            this.data = data;
            this.location = location;
        }

        public MachineData getData() {
            return data;
        }

        public Location getLocation() {
            return location;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Abre a GUI da máquina pro player. */
    public static void open(Player player, MachineData data, Location location) {
        Holder holder = new Holder(data, location);
        MachineType type = data.getType();
        String title = type.getColor() + type.getDisplayName();
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = filler();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(INFO_SLOT, infoItem(data));

        ItemStack[] storage = data.getStorage();
        for (int i = 0; i < storage.length; i++) {
            inv.setItem(STORAGE_START + i, storage[i]);
        }

        data.setOpenView(inv);
        player.openInventory(inv);
    }

    /** Repinta a storage e a info (chamado quando o trabalho adiciona itens). No-op se ninguém olha. */
    public static void repaint(MachineData data) {
        Inventory inv = data.getOpenView();
        if (inv == null) {
            return;
        }
        ItemStack[] storage = data.getStorage();
        for (int i = 0; i < storage.length; i++) {
            inv.setItem(STORAGE_START + i, storage[i]);
        }
        inv.setItem(INFO_SLOT, infoItem(data));
        for (HumanEntity viewer : inv.getViewers()) {
            if (viewer instanceof Player) {
                ((Player) viewer).updateInventory();
            }
        }
    }

    /** Lê de volta a storage do inventário pra dentro do MachineData (no fechar/clicar). */
    public static void syncFromInventory(MachineData data, Inventory inv) {
        ItemStack[] storage = data.getStorage();
        for (int i = 0; i < storage.length; i++) {
            storage[i] = inv.getItem(STORAGE_START + i);
        }
    }

    private static int countStored(MachineData data) {
        int total = 0;
        for (ItemStack stack : data.getStorage()) {
            if (stack != null && stack.getType() != Material.AIR) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static ItemStack infoItem(MachineData data) {
        MachineType type = data.getType();
        ItemStack item = new ItemStack(type.getBaseBlock());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getColor() + "" + ChatColor.BOLD + type.getDisplayName());
        List<String> lore = new ArrayList<>();
        for (String line : type.getLoreLines()) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add(ChatColor.GRAY + "Raio: " + ChatColor.WHITE + type.getRadius() + " blocos");
        lore.add(ChatColor.GRAY + "Guardado: " + ChatColor.WHITE + countStored(data) + " itens");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
