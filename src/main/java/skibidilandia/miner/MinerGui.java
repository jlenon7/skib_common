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
 * GUI da mineradora: linha de info no topo, área de saída no meio,
 * barra de progresso do ciclo na fileira de baixo.
 */
public final class MinerGui {

    public static final int SIZE = 54;
    public static final int OUTPUT_START = 9;     // slots 9..44 (36 slots)
    public static final int OUTPUT_END = 44;
    public static final int INFO_SLOT = 4;
    public static final int PROGRESS_START = 45;  // barra de progresso: slots 45..53 (9 slots)
    public static final int PROGRESS_END = 53;

    private MinerGui() {
    }

    /** Holder que liga o inventário aberto de volta à mineradora. */
    public static final class Holder implements InventoryHolder {
        private final MinerData data;
        private final Location location;
        private Inventory inventory;

        Holder(MinerData data, Location location) {
            this.data = data;
            this.location = location;
        }

        public MinerData getData() {
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

    /** Abre a GUI da mineradora pro player. */
    public static void open(Player player, MinerData data, Location location) {
        Holder holder = new Holder(data, location);
        MinerTier tier = MinerTier.fromLevel(data.getLevel());
        String title = (tier != null ? tier.getColor() + tier.getDisplayName() : "Mineradora");
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = filler();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(INFO_SLOT, infoItem(data));

        // saída
        ItemStack[] output = data.getOutput();
        for (int i = 0; i < output.length; i++) {
            inv.setItem(OUTPUT_START + i, output[i]);
        }

        renderProgress(inv, data);
        data.setOpenView(inv);

        player.openInventory(inv);
    }

    /** Repinta a área de saída, a info e a barra (chamado quando a geração adiciona itens). */
    public static void repaint(MinerData data) {
        Inventory inv = data.getOpenView();
        if (inv == null) {
            return;
        }
        ItemStack[] output = data.getOutput();
        for (int i = 0; i < output.length; i++) {
            inv.setItem(OUTPUT_START + i, output[i]);
        }
        inv.setItem(INFO_SLOT, infoItem(data));
        renderProgress(inv, data);
        pushUpdate(inv);
    }

    /**
     * Atualização leve (a cada segundo): só o item de info e a barra de progresso.
     * NÃO toca na área de saída pra não reverter itens que o player esteja movendo.
     */
    public static void repaintStatus(MinerData data) {
        Inventory inv = data.getOpenView();
        if (inv == null) {
            return;
        }
        inv.setItem(INFO_SLOT, infoItem(data));
        renderProgress(inv, data);
        pushUpdate(inv);
    }

    /** Força os clientes que estão olhando a janela a re-renderizar (senão a tooltip/contagem "congela"). */
    private static void pushUpdate(Inventory inv) {
        for (HumanEntity viewer : inv.getViewers()) {
            if (viewer instanceof Player) {
                ((Player) viewer).updateInventory();
            }
        }
    }

    /**
     * Lê de volta a saída e o combustível do inventário pra dentro do MinerData
     * (chamado quando o player fecha ou clica).
     */
    public static void syncFromInventory(MinerData data, Inventory inv) {
        ItemStack[] output = data.getOutput();
        for (int i = 0; i < output.length; i++) {
            output[i] = inv.getItem(OUTPUT_START + i);
        }
    }

    private static boolean hasFuel(MinerData data) {
        return data.getFuelCyclesRemaining() > 0 || data.hasFuel();
    }

    private static int secondsRemaining(MinerData data) {
        return (int) Math.ceil(Math.max(0, data.getTicksUntilNextCycle()) / 20.0);
    }

    private static ItemStack infoItem(MinerData data) {
        MinerTier tier = MinerTier.fromLevel(data.getLevel());
        ItemStack item = new ItemStack(MinerItems.BASE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD
                + (tier != null ? tier.getDisplayName() : "Mineradora"));
        String fuelLine;
        if (data.getFuelCyclesRemaining() > 0) {
            fuelLine = ChatColor.GREEN + "Ligada " + ChatColor.GRAY + "("
                    + data.getFuelCyclesRemaining() + " ciclos restantes)";
        } else if (data.hasFuel()) {
            fuelLine = ChatColor.YELLOW + "Pronta para iniciar";
        } else {
            fuelLine = ChatColor.RED + "Sem combustível";
        }
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Nível: " + ChatColor.WHITE + data.getLevel());
        lore.add(ChatColor.GRAY + "Status: " + fuelLine);
        if (hasFuel(data)) {
            lore.add(ChatColor.GRAY + "Próximo ciclo em: " + ChatColor.WHITE + secondsRemaining(data) + "s");
        }
        lore.add(ChatColor.DARK_GRAY + "Coloque combustível (carvão, etc.) em qualquer slot.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Desenha a barra de progresso do ciclo na fileira de baixo (slots 45..53). */
    private static void renderProgress(Inventory inv, MinerData data) {
        int totalSlots = PROGRESS_END - PROGRESS_START + 1; // 9
        boolean fueled = hasFuel(data);

        int length = Math.max(1, data.getCycleLengthTicks());
        int remaining = Math.max(0, data.getTicksUntilNextCycle());
        double fraction = fueled ? (double) (length - remaining) / length : 0.0;
        if (fraction < 0) fraction = 0;
        if (fraction > 1) fraction = 1;
        int filled = (int) Math.round(fraction * totalSlots);

        String label;
        if (!fueled) {
            label = ChatColor.RED + "Sem combustível";
        } else {
            label = ChatColor.GREEN + "Próximo ciclo em " + ChatColor.WHITE + secondsRemaining(data) + "s "
                    + ChatColor.GRAY + "(" + (int) Math.round(fraction * 100) + "%)";
        }

        for (int i = 0; i < totalSlots; i++) {
            Material mat;
            if (!fueled) {
                mat = Material.RED_STAINED_GLASS_PANE;
            } else {
                mat = (i < filled) ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            }
            inv.setItem(PROGRESS_START + i, namedPane(mat, label));
        }
    }

    private static ItemStack namedPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        return namedPane(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
}
