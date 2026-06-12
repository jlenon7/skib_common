package skibidilandia.enchants;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Itens do plugin de encantamentos. Por enquanto só existe o encantamento Hexa.
 *
 * O Hexa não é um {@link org.bukkit.enchantments.Enchantment} de verdade (esses
 * exigiriam registro via datapack/registry); ele vive no PDC do item — tanto no
 * Livro Encantado de Hexa quanto na ferramenta que o recebe. A lore só serve de
 * vitrine; a identidade verdadeira é a chave no PDC (sobrevive a soltar/pegar).
 *
 * O Hexa só pode ser aplicado em picaretas e pás (ver {@link #isSupportedTool}).
 */
public final class EnchantsItems {

    /** Linha de lore exibida (como um encantamento vanilla) na ferramenta e no livro. */
    private static final String HEXA_LORE = ChatColor.GRAY + "Hexa";

    private static NamespacedKey hexaBookKey;
    private static NamespacedKey hexaToolKey;

    private EnchantsItems() {
    }

    public static void init(JavaPlugin plugin) {
        hexaBookKey = new NamespacedKey(plugin, "hexa_book");
        hexaToolKey = new NamespacedKey(plugin, "hexa_enchant");
    }

    /** True se o material é uma picareta ou uma pá (qualquer material base). */
    public static boolean isSupportedTool(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL");
    }

    // =========================================================================
    //  Livro Encantado de Hexa
    // =========================================================================

    /** Cria o Livro Encantado de Hexa na quantidade pedida. */
    public static ItemStack createHexaBook(int amount) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Livro Encantado de Hexa");
        meta.setLore(Arrays.asList(
                HEXA_LORE,
                "",
                ChatColor.GRAY + "Aplique numa " + ChatColor.WHITE + "picareta"
                        + ChatColor.GRAY + " ou " + ChatColor.WHITE + "pá" + ChatColor.GRAY + " na bigorna.",
                ChatColor.GRAY + "Quebra uma área " + ChatColor.WHITE + "3x3" + ChatColor.GRAY
                        + " de blocos de uma vez."
        ));
        meta.getPersistentDataContainer().set(hexaBookKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Livro Encantado de Hexa. */
    public static boolean isHexaBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(hexaBookKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Encantamento Hexa na ferramenta
    // =========================================================================

    /**
     * Devolve uma cópia da ferramenta com o encantamento Hexa aplicado (marca no
     * PDC, brilho de encantamento e a linha de lore). Não modifica o original.
     */
    public static ItemStack applyHexa(ItemStack tool) {
        ItemStack result = tool.clone();
        ItemMeta meta = result.getItemMeta();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!lore.contains(HEXA_LORE)) {
            lore.add(0, HEXA_LORE); // encantamentos aparecem no topo da lore, como no vanilla
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true); // brilho sem precisar de um encanto vanilla
        meta.getPersistentDataContainer().set(hexaToolKey, PersistentDataType.BYTE, (byte) 1);

        result.setItemMeta(meta);
        return result;
    }

    /** True se a ferramenta tem o encantamento Hexa. */
    public static boolean hasHexa(ItemStack tool) {
        if (tool == null || !tool.hasItemMeta()) {
            return false;
        }
        return tool.getItemMeta().getPersistentDataContainer().has(hexaToolKey, PersistentDataType.BYTE);
    }
}
