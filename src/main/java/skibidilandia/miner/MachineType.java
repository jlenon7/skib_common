package skibidilandia.miner;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Os tipos de "máquina" auxiliares da linha de mineradoras. Diferente das mineradoras
 * (que geram minério), estas trabalham sobre o mundo/outras máquinas num raio fixo e
 * guardam o resultado num inventário interno próprio.
 *
 * - COLHETADEIRA: colhe plantações maduras num raio e replanta.
 * - COMPACTADORA: puxa minério cru das mineradoras num raio e compacta em blocos.
 *
 * Cada tipo é UM item próprio (sem tiers). O tipo fica gravado no PDC do item
 * (chave "machine_type", guarda o {@link #name()}) e o bloco base identifica visualmente.
 */
public enum MachineType {
    HARVESTER("colhetadeira", "Colhetadeira", ChatColor.GREEN, Material.HAY_BLOCK, 10, 100,
            "Colhe plantações maduras (trigo, cenoura, batata,",
            "cana, fungo, etc.) num raio de 10 blocos e",
            "guarda a colheita aqui dentro."),
    COMPACTOR("compactadora", "Compactadora", ChatColor.AQUA, Material.IRON_BLOCK, 10, 40,
            "Puxa o minério cru das mineradoras num raio de",
            "10 blocos e compacta em blocos aqui dentro.");

    private final String id;               // usado no comando /maquina dar <id>
    private final String displayName;
    private final ChatColor color;
    private final Material baseBlock;
    private final int radius;
    private final int workIntervalTicks;   // intervalo entre trabalhos (em ticks)
    private final String[] loreLines;

    MachineType(String id, String displayName, ChatColor color, Material baseBlock,
                int radius, int workIntervalTicks, String... loreLines) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.baseBlock = baseBlock;
        this.radius = radius;
        this.workIntervalTicks = workIntervalTicks;
        this.loreLines = loreLines;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public Material getBaseBlock() {
        return baseBlock;
    }

    public int getRadius() {
        return radius;
    }

    public int getWorkIntervalTicks() {
        return workIntervalTicks;
    }

    public String[] getLoreLines() {
        return loreLines;
    }

    /** Retorna o tipo pelo id do comando (ex: "colhetadeira"), ou null. */
    public static MachineType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (MachineType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /** Retorna o tipo persistido pelo {@link #name()}, ou null se desconhecido. */
    public static MachineType fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
