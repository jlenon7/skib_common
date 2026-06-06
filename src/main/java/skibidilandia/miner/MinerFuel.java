package skibidilandia.miner;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Define quais itens servem de combustível e quantos ciclos cada um rende.
 */
public final class MinerFuel {

    private static final Map<Material, Integer> CYCLES = new EnumMap<>(Material.class);
    static {
        CYCLES.put(Material.COAL, 8);
        CYCLES.put(Material.CHARCOAL, 8);
        CYCLES.put(Material.COAL_BLOCK, 80);
        CYCLES.put(Material.BLAZE_ROD, 12);
        CYCLES.put(Material.DRIED_KELP_BLOCK, 20);
    }

    private MinerFuel() {
    }

    public static boolean isFuel(ItemStack item) {
        return item != null && CYCLES.containsKey(item.getType());
    }

    /** Quantos ciclos um único item desse material rende. 0 se não for combustível. */
    public static int cyclesFor(Material material) {
        return CYCLES.getOrDefault(material, 0);
    }
}
