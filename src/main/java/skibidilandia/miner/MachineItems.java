package skibidilandia.miner;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Cria e identifica os itens-máquina (Colhetadeira/Compactadora). O tipo fica gravado
 * no PDC do item (chave "machine_type"), sobrevive a soltar/pegar/colocar. O bloco base
 * é definido por cada {@link MachineType}.
 */
public final class MachineItems {

    private static NamespacedKey typeKey;

    private MachineItems() {
    }

    public static void init(JavaPlugin plugin) {
        typeKey = new NamespacedKey(plugin, "machine_type");
    }

    public static NamespacedKey typeKey() {
        return typeKey;
    }

    /** Constrói o ItemStack de uma máquina. */
    public static ItemStack create(MachineType type) {
        ItemStack item = new ItemStack(type.getBaseBlock());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getColor() + "" + ChatColor.BOLD + type.getDisplayName());
        List<String> lore = new ArrayList<>();
        for (String line : type.getLoreLines()) {
            lore.add(ChatColor.GRAY + line);
        }
        lore.add(ChatColor.DARK_GRAY + "Coloque no chão e clique para abrir.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Retorna o tipo se o item for uma máquina, ou null caso contrário. */
    public static MachineType readType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String name = pdc.get(typeKey, PersistentDataType.STRING);
        return MachineType.fromName(name);
    }
}
