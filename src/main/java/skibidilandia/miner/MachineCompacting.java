package skibidilandia.miner;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

/**
 * Receitas de compactação da Compactadora: qual minério cru vira qual bloco e quantos
 * são necessários. Também define o que conta como "minério" que a Compactadora puxa
 * das mineradoras.
 */
public final class MachineCompacting {

    /** Uma receita: material-bloco resultante + quantidade do cru necessária por bloco. */
    public static final class Recipe {
        public final Material block;
        public final int count;

        Recipe(Material block, int count) {
            this.block = block;
            this.count = count;
        }
    }

    private static final Map<Material, Recipe> RECIPES = new EnumMap<>(Material.class);
    static {
        RECIPES.put(Material.COAL, new Recipe(Material.COAL_BLOCK, 9));
        RECIPES.put(Material.RAW_COPPER, new Recipe(Material.RAW_COPPER_BLOCK, 9));
        RECIPES.put(Material.RAW_IRON, new Recipe(Material.RAW_IRON_BLOCK, 9));
        RECIPES.put(Material.RAW_GOLD, new Recipe(Material.RAW_GOLD_BLOCK, 9));
        RECIPES.put(Material.REDSTONE, new Recipe(Material.REDSTONE_BLOCK, 9));
        RECIPES.put(Material.LAPIS_LAZULI, new Recipe(Material.LAPIS_BLOCK, 9));
        RECIPES.put(Material.DIAMOND, new Recipe(Material.DIAMOND_BLOCK, 9));
        RECIPES.put(Material.EMERALD, new Recipe(Material.EMERALD_BLOCK, 9));
        RECIPES.put(Material.QUARTZ, new Recipe(Material.QUARTZ_BLOCK, 4));
    }

    private MachineCompacting() {
    }

    /** Esse material é um minério que a Compactadora puxa/compacta? */
    public static boolean isOre(Material material) {
        return RECIPES.containsKey(material);
    }

    /**
     * Compacta tudo o que for possível na storage da máquina: para cada minério com
     * quantidade suficiente, remove os crus e adiciona os blocos correspondentes,
     * devolvendo o resto (não múltiplo) como cru.
     */
    public static void compact(MachineData data) {
        for (Map.Entry<Material, Recipe> entry : RECIPES.entrySet()) {
            Material raw = entry.getKey();
            Recipe recipe = entry.getValue();
            int total = data.countOf(raw);
            if (total < recipe.count) {
                continue;
            }
            int blocks = total / recipe.count;
            int remainder = total % recipe.count;
            data.removeAll(raw);
            int leftoverBlocks = data.addOutput(recipe.block, blocks);
            // o que não coube como bloco volta a ser cru (tenta de novo no próximo ciclo)
            int rawBack = remainder + leftoverBlocks * recipe.count;
            if (rawBack > 0) {
                data.addOutput(raw, rawBack);
            }
        }
    }
}
