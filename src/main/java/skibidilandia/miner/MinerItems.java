package skibidilandia.miner;

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
 * Cria e identifica o item-mineradora. O tier fica gravado no PDC do item
 * (sobrevive a soltar/pegar/colocar). O bloco base é sempre LODESTONE.
 */
public final class MinerItems {

    public static final Material BASE_BLOCK = Material.LODESTONE;

    private static NamespacedKey tierKey;

    private MinerItems() {
    }

    public static void init(JavaPlugin plugin) {
        tierKey = new NamespacedKey(plugin, "miner_tier");
    }

    public static NamespacedKey tierKey() {
        return tierKey;
    }

    /** Constrói o ItemStack da mineradora de um tier. */
    public static ItemStack create(MinerTier tier) {
        ItemStack item = new ItemStack(BASE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(tier.getColor() + "" + ChatColor.BOLD + tier.getDisplayName());
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Mineradora automática nível " + tier.getColor() + tier.getLevel(),
                ChatColor.DARK_GRAY + "Coloque no chão e abasteça com combustível.",
                ChatColor.DARK_GRAY + "Produz minério automaticamente."
        ));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier.getLevel());
        item.setItemMeta(meta);
        return item;
    }

    /** Retorna o tier se o item for uma mineradora, ou null caso contrário. */
    public static MinerTier readTier(ItemStack item) {
        if (item == null || item.getType() != BASE_BLOCK || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer level = pdc.get(tierKey, PersistentDataType.INTEGER);
        return level == null ? null : MinerTier.fromLevel(level);
    }
}
