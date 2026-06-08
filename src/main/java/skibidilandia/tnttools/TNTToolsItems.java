package skibidilandia.tnttools;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Cria e identifica os itens do plugin TNTTools:
 *
 *  - {@code Espada TNT}: base de diamante. Ao acertar um alvo gera uma explosão
 *    (ou várias, conforme o nível de Pilhagem/Looting). A explosão não fere o
 *    portador da espada, só quem estiver por perto.
 *
 *  - {@code Armadura TNT}: 4 peças de couro tingidas de vermelho. Cada peça
 *    vestida permite ao jogador disparar 1 explosão usando a tecla de troca de
 *    mão (configurável para "C"). Conjunto completo = 4 explosões. As explosões
 *    não ferem o próprio dono.
 *
 * A identidade de cada item vive no PDC (sobrevive a soltar/pegar/encantar).
 * Todas as 4 peças da armadura compartilham a mesma marca {@code armorKey}, para
 * que a detecção (vestir/consertar) seja uniforme.
 */
public final class TNTToolsItems {

    public static final Material SWORD_MATERIAL = Material.GOLDEN_SWORD;

    public static final Material HELMET_MATERIAL = Material.LEATHER_HELMET;
    public static final Material CHESTPLATE_MATERIAL = Material.LEATHER_CHESTPLATE;
    public static final Material LEGGINGS_MATERIAL = Material.LEATHER_LEGGINGS;
    public static final Material BOOTS_MATERIAL = Material.LEATHER_BOOTS;

    /** Vermelho "TNT" para tingir as peças de couro. */
    private static final Color TNT_RED = Color.fromRGB(0xD3, 0x2F, 0x2F);

    private static NamespacedKey swordKey;
    private static NamespacedKey armorKey;

    private TNTToolsItems() {
    }

    public static void init(JavaPlugin plugin) {
        swordKey = new NamespacedKey(plugin, "tnt_sword");
        armorKey = new NamespacedKey(plugin, "tnt_armor");
    }

    // =========================================================================
    //  Espada TNT
    // =========================================================================

    /** Constrói a Espada TNT (base de diamante, sem encantos). */
    public static ItemStack createSword() {
        ItemStack item = new ItemStack(SWORD_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Espada TNT");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Causa uma " + ChatColor.RED + "explosão" + ChatColor.GRAY + " ao acertar um alvo.",
                ChatColor.GRAY + "A explosão " + ChatColor.WHITE + "não fere você" + ChatColor.GRAY + ", só quem está por perto.",
                ChatColor.GRAY + "Pilhagem " + ChatColor.WHITE + "II" + ChatColor.GRAY + " = 2 explosões, "
                        + ChatColor.WHITE + "III" + ChatColor.GRAY + " = 3 explosões.",
                "",
                repairLoreLine()
        ));
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for a Espada TNT. */
    public static boolean isTntSword(ItemStack item) {
        if (item == null || item.getType() != SWORD_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(swordKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Armadura TNT
    // =========================================================================

    /** Constrói uma peça da Armadura TNT a partir do material informado, ou null. */
    public static ItemStack createArmorPiece(Material material) {
        String name;
        if (material == HELMET_MATERIAL) {
            name = "Capacete TNT";
        } else if (material == CHESTPLATE_MATERIAL) {
            name = "Peitoral TNT";
        } else if (material == LEGGINGS_MATERIAL) {
            name = "Calça TNT";
        } else if (material == BOOTS_MATERIAL) {
            name = "Botas TNT";
        } else {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + name);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Parte da " + ChatColor.RED + "Armadura TNT" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Agache " + ChatColor.WHITE + "duas vezes" + ChatColor.GRAY + " (shift duplo) para explodir.",
                ChatColor.GRAY + "Cada peça vestida = " + ChatColor.WHITE + "1 explosão" + ChatColor.GRAY
                        + " (conjunto = 4).",
                ChatColor.GRAY + "As explosões " + ChatColor.WHITE + "não ferem você" + ChatColor.GRAY + ".",
                "",
                repairLoreLine()
        ));
        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(TNT_RED);
        }
        meta.getPersistentDataContainer().set(armorKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** O conjunto completo das 4 peças da Armadura TNT. */
    public static List<ItemStack> createFullArmor() {
        return Arrays.asList(
                createArmorPiece(HELMET_MATERIAL),
                createArmorPiece(CHESTPLATE_MATERIAL),
                createArmorPiece(LEGGINGS_MATERIAL),
                createArmorPiece(BOOTS_MATERIAL));
    }

    /** True se o item for uma peça da Armadura TNT. */
    public static boolean isTntArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(armorKey, PersistentDataType.BYTE);
    }

    /** True para qualquer item do TNTTools (espada ou peça de armadura). */
    public static boolean isTntTool(ItemStack item) {
        return isTntSword(item) || isTntArmor(item);
    }

    private static String repairLoreLine() {
        return ChatColor.GRAY + "Conserto na bigorna: " + ChatColor.LIGHT_PURPLE + "Cristal do End"
                + ChatColor.GRAY + " (+10% durab., " + ChatColor.GREEN + "10 níveis de XP" + ChatColor.GRAY + ").";
    }
}
