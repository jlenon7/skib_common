package skibidilandia.fuckbedrocks;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Cria e identifica os dois itens do plugin FuckBedrocks:
 *
 *  - {@code Picareta Quebra-Bedrock}: base de OURO, então herda naturalmente a
 *    eficiência (velocidade de mineração) e a durabilidade (32) da picareta de
 *    ouro — uma "cópia" dela, como pedido. A capacidade de quebrar bedrock é
 *    adicionada pelos listeners; a base de ouro continua valendo para tudo o
 *    mais (encantos, durabilidade, etc.).
 *
 *  - {@code TNT Nuclear}: base de TNT comum. A marca fica no PDC do item; ao ser
 *    ativado vira um {@code TNTPrimed}, e a mesma marca é copiada para a entidade
 *    para que o listener saiba que aquela explosão é a nuclear.
 *
 * A identidade dos dois itens vive no PDC (sobrevive a soltar/pegar/encantar).
 */
public final class FuckBedrocksItems {

    public static final Material PICKAXE_MATERIAL = Material.GOLDEN_PICKAXE;
    public static final Material NUKE_MATERIAL = Material.TNT;
    public static final Material NUKE_MINECART_MATERIAL = Material.TNT_MINECART;

    private static NamespacedKey pickaxeKey;
    private static NamespacedKey nukeKey;

    private FuckBedrocksItems() {
    }

    public static void init(JavaPlugin plugin) {
        pickaxeKey = new NamespacedKey(plugin, "bedrock_pickaxe");
        nukeKey = new NamespacedKey(plugin, "nuke_tnt");
    }

    /** Constrói a Picareta Quebra-Bedrock (base de ouro, sem encantos). */
    public static ItemStack createPickaxe() {
        ItemStack item = new ItemStack(PICKAXE_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Picareta Quebra-Bedrock");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Quebra e coleta " + ChatColor.WHITE + "bedrock" + ChatColor.GRAY + ".",
                ChatColor.DARK_GRAY + "Eficiência e durabilidade de ouro.",
                "",
                ChatColor.GRAY + "Conserto na bigorna:",
                ChatColor.GRAY + "1x " + ChatColor.LIGHT_PURPLE + "Estrela do Nether"
                        + ChatColor.GRAY + " + " + ChatColor.GREEN + "30 níveis de XP" + ChatColor.GRAY + "."
        ));
        meta.getPersistentDataContainer().set(pickaxeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for a Picareta Quebra-Bedrock. */
    public static boolean isBedrockPickaxe(ItemStack item) {
        if (item == null || item.getType() != PICKAXE_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(pickaxeKey, PersistentDataType.BYTE);
    }

    /** Constrói o TNT Nuclear (base de TNT comum) na quantidade pedida. */
    public static ItemStack createNuke(int amount) {
        ItemStack item = new ItemStack(NUKE_MATERIAL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "TNT Nuclear");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Explosão " + ChatColor.RED + "10x maior" + ChatColor.GRAY + " que um TNT normal.",
                ChatColor.GRAY + "Destrói " + ChatColor.WHITE + "bedrock" + ChatColor.GRAY + " e mata",
                ChatColor.GRAY + "instantaneamente quem estiver na explosão.",
                "",
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "CUIDADO."
        ));
        meta.getPersistentDataContainer().set(nukeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o TNT Nuclear. */
    public static boolean isNukeTnt(ItemStack item) {
        if (item == null || item.getType() != NUKE_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(nukeKey, PersistentDataType.BYTE);
    }

    /**
     * Constrói o Carrinho com TNT Nuclear. Usa a MESMA marca ({@code nukeKey})
     * do TNT Nuclear, então ao explodir cai no mesmo efeito nuclear.
     */
    public static ItemStack createNukeMinecart() {
        ItemStack item = new ItemStack(NUKE_MINECART_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Carrinho com TNT Nuclear");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Explosão " + ChatColor.RED + "10x maior" + ChatColor.GRAY + " que um TNT normal.",
                ChatColor.GRAY + "Destrói " + ChatColor.WHITE + "bedrock" + ChatColor.GRAY + " e mata",
                ChatColor.GRAY + "instantaneamente quem estiver na explosão.",
                "",
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "CUIDADO."
        ));
        meta.getPersistentDataContainer().set(nukeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Carrinho com TNT Nuclear. */
    public static boolean isNukeMinecart(ItemStack item) {
        if (item == null || item.getType() != NUKE_MINECART_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(nukeKey, PersistentDataType.BYTE);
    }

    /** Marca a entidade (o TNTPrimed) como sendo a nuclear. */
    public static void tagNukeEntity(Entity entity) {
        entity.getPersistentDataContainer().set(nukeKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** True se a entidade veio de um TNT Nuclear. */
    public static boolean isNukeEntity(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(nukeKey, PersistentDataType.BYTE);
    }
}
