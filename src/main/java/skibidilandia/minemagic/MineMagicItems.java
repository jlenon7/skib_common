package skibidilandia.minemagic;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Cria e identifica os itens mágicos do plugin MineMagic:
 *
 *  - {@code Cajado de Fogo}: base de tridente. Ao segurar o botão direito o
 *    jogador faz a animação de carregar o tridente e cospe bolas de fogo em
 *    rajada enquanto segura. O tridente em si nunca é arremessado.
 *
 *  - {@code Cajado do Necromante}: base de bastão de blaze. Ao clicar com o
 *    botão direito invoca 3 zumbis com armadura e espada de ferro que lutam ao
 *    lado do invocador e nunca o atacam.
 *
 * A identidade de cada item vive no PDC (sobrevive a soltar/pegar/encantar).
 */
public final class MineMagicItems {

    public static final Material FIRE_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material NECRO_STAFF_MATERIAL = Material.BLAZE_ROD;

    private static NamespacedKey fireStaffKey;
    private static NamespacedKey necroStaffKey;

    private MineMagicItems() {
    }

    public static void init(JavaPlugin plugin) {
        fireStaffKey = new NamespacedKey(plugin, "fire_staff");
        necroStaffKey = new NamespacedKey(plugin, "necro_staff");
    }

    // =========================================================================
    //  Cajado de Fogo
    // =========================================================================

    /** Constrói o Cajado de Fogo (base de tridente, indestrutível). */
    public static ItemStack createFireStaff() {
        ItemStack item = new ItemStack(FIRE_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Cajado de Fogo");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Segure o " + ChatColor.RED + "botão direito" + ChatColor.GRAY
                        + " para lançar uma",
                ChatColor.GRAY + "rajada de " + ChatColor.GOLD + "bolas de fogo" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                        + " como um tridente."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(fireStaffKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado de Fogo. */
    public static boolean isFireStaff(ItemStack item) {
        if (item == null || item.getType() != FIRE_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(fireStaffKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Cajado do Necromante
    // =========================================================================

    /** Constrói o Cajado do Necromante (base de bastão de blaze). */
    public static ItemStack createNecromancerStaff() {
        ItemStack item = new ItemStack(NECRO_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Cajado do Necromante");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Botão direito: invoca " + ChatColor.GREEN + "3 zumbis" + ChatColor.GRAY,
                ChatColor.GRAY + "com armadura e espada de " + ChatColor.WHITE + "ferro" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Eles lutam ao seu lado e " + ChatColor.WHITE + "não atacam você" + ChatColor.GRAY + "."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(necroStaffKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado do Necromante. */
    public static boolean isNecromancerStaff(ItemStack item) {
        if (item == null || item.getType() != NECRO_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(necroStaffKey, PersistentDataType.BYTE);
    }
}
