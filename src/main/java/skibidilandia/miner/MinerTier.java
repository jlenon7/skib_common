package skibidilandia.miner;

import org.bukkit.ChatColor;

/**
 * Os 6 tipos de mineradora à venda. O "tier" define até qual minério ela produz
 * (acumulativo: tier N gera todos os minérios de tier 1..N).
 * Exceção: a NETHERITE não é acumulativa — gera um conjunto fixo (ver MinerGeneration).
 */
public enum MinerTier {
    COAL(1, "Mineradora de Carvão", ChatColor.DARK_GRAY, "mineradora_carvao"),
    COPPER(2, "Mineradora de Cobre", ChatColor.GOLD, "mineradora_cobre"),
    IRON(3, "Mineradora de Ferro", ChatColor.WHITE, "mineradora_ferro"),
    GOLD(4, "Mineradora de Ouro", ChatColor.YELLOW, "mineradora_ouro"),
    DIAMOND(5, "Mineradora de Diamante", ChatColor.AQUA, "mineradora_diamante"),
    NETHERITE(6, "Mineradora de Netherite", ChatColor.DARK_RED, "mineradora_netherite");

    private final int level;
    private final String displayName;
    private final ChatColor color;
    private final String modelId;

    MinerTier(int level, String displayName, ChatColor color, String modelId) {
        this.level = level;
        this.displayName = displayName;
        this.color = color;
        this.modelId = modelId;
    }

    /** Id do modelo no resource pack (namespace "skib"). */
    public String getModelId() {
        return modelId;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    /** Retorna o tier pelo nível (1..6), ou null se inválido. */
    public static MinerTier fromLevel(int level) {
        for (MinerTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return null;
    }
}
