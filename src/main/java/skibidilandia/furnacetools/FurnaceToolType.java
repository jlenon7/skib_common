package skibidilandia.furnacetools;

import org.bukkit.Material;

/**
 * As três ferramentas da fornalha. Todas usam a base de FERRO, então herdam
 * naturalmente a eficiência de mineração (tier ferro) e a durabilidade (250)
 * da picareta de ferro — exatamente como pedido.
 *
 * Cada tipo só intercepta o que a sua base de ferro consegue quebrar de forma
 * "correta" (a picareta de minérios/pedra, o machado de madeira, a pá de
 * areia/terra/argila), porque os drops são calculados via
 * {@link org.bukkit.block.Block#getDrops} com a própria ferramenta.
 */
public enum FurnaceToolType {
    PICKAXE(Material.IRON_PICKAXE, "Picareta da Fornalha", "picareta_fornalha"),
    AXE(Material.IRON_AXE, "Machado da Fornalha", "machado_fornalha"),
    SHOVEL(Material.IRON_SHOVEL, "Pá da Fornalha", "pa_fornalha");

    private final Material material;
    private final String displayName;
    private final String modelId;

    FurnaceToolType(Material material, String displayName, String modelId) {
        this.material = material;
        this.displayName = displayName;
        this.modelId = modelId;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Id do modelo no resource pack (namespace "skib"). */
    public String getModelId() {
        return modelId;
    }

    /** Resolve o tipo a partir do nome guardado no PDC, ou null se inválido. */
    public static FurnaceToolType fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (FurnaceToolType type : values()) {
            if (type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }

    /** Resolve o tipo a partir do argumento de comando (pt/en), ou null. */
    public static FurnaceToolType fromArg(String arg) {
        if (arg == null) {
            return null;
        }
        switch (arg.toLowerCase()) {
            case "picareta":
            case "pickaxe":
                return PICKAXE;
            case "machado":
            case "axe":
                return AXE;
            case "pa":
            case "pá":
            case "shovel":
                return SHOVEL;
            default:
                return null;
        }
    }
}
