package skibidilandia.miner;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import skibidilandia.SkibModel;

import java.util.Arrays;

/**
 * Cria e identifica o item-mineradora. O tier fica gravado no PDC do item
 * (sobrevive a soltar/pegar/colocar). O bloco base é uma FORNALHA mantida acesa,
 * para o bloco colocado combinar com o ícone (fornalha em chamas).
 */
public final class MinerItems {

    public static final Material BASE_BLOCK = Material.FURNACE;

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
        SkibModel.apply(item, tier.getModelId());
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

    /**
     * Mantém a fornalha-mineradora acesa (lit=true) sem combustível. A fornalha só
     * apaga o blockstate quando o seu litTime transiciona de >0 para 0; como nunca
     * damos litTime, o estado lit=true fica estável. Chamado na colocação e a cada
     * ciclo do GenerationTask (defensivo p/ reload de chunk ou hopper que acendeu).
     */
    public static void ensureLit(Block block) {
        if (block == null || block.getType() != BASE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Lightable) {
            Lightable lightable = (Lightable) data;
            if (!lightable.isLit()) {
                lightable.setLit(true);
                block.setBlockData(lightable, false);
            }
        }
    }
}
