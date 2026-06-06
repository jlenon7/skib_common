package skibidilandia.blueprint;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Cria e identifica os dois itens do sistema: a varinha de seleção e o
 * blueprint. A construção inteira fica gravada no PDC do blueprint (chave
 * {@code blueprint_data}), então o item é autossuficiente.
 */
public final class BlueprintItems {

    public static final Material WAND_MATERIAL = Material.BLAZE_ROD;
    public static final Material BLUEPRINT_MATERIAL = Material.PAPER;

    private static NamespacedKey wandKey;
    private static NamespacedKey dataKey;

    private BlueprintItems() {
    }

    public static void init(JavaPlugin plugin) {
        wandKey = new NamespacedKey(plugin, "blueprint_wand");
        dataKey = new NamespacedKey(plugin, "blueprint_data");
    }

    /** A varinha que define os dois cantos da seleção. */
    public static ItemStack createWand() {
        ItemStack item = new ItemStack(WAND_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Varinha de Blueprint");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Clique " + ChatColor.YELLOW + "esquerdo" + ChatColor.GRAY + " num bloco: canto 1",
                ChatColor.GRAY + "Clique " + ChatColor.YELLOW + "direito" + ChatColor.GRAY + " num bloco: canto 2",
                ChatColor.DARK_GRAY + "Depois use /blueprint criar <nome>"
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() != WAND_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    /** Monta o item de blueprint a partir de uma construção capturada. */
    public static ItemStack createBlueprint(String name, BlueprintData data) {
        ItemStack item = new ItemStack(BLUEPRINT_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Blueprint: " + ChatColor.YELLOW + name);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Tamanho: " + ChatColor.WHITE
                        + data.getSizeX() + "x" + data.getSizeY() + "x" + data.getSizeZ(),
                ChatColor.GRAY + "Blocos: " + ChatColor.WHITE + data.getBlockCount(),
                ChatColor.DARK_GRAY + "Clique com o botão direito no chão",
                ChatColor.DARK_GRAY + "para gerar a construção (gasta o blueprint)."
        ));
        meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, data.serialize());
        item.setItemMeta(meta);
        return item;
    }

    /** Retorna a construção se o item for um blueprint válido, ou null. */
    public static BlueprintData readData(ItemStack item) {
        if (item == null || item.getType() != BLUEPRINT_MATERIAL || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(dataKey, PersistentDataType.STRING);
        return raw == null ? null : BlueprintData.deserialize(raw);
    }
}
