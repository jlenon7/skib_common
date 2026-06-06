package skibidilandia.miner;

import org.bukkit.ChatColor;

/**
 * Os 6 tipos de mineradora à venda. O "tier" define até qual minério ela produz
 * (acumulativo: tier N gera todos os minérios de tier 1..N).
 * Exceção: a NETHERITE não é acumulativa — gera um conjunto fixo (ver MinerGeneration).
 */
public enum MinerTier {
    COAL(1, "Mineradora de Carvão", ChatColor.DARK_GRAY),
    COPPER(2, "Mineradora de Cobre", ChatColor.GOLD),
    IRON(3, "Mineradora de Ferro", ChatColor.WHITE),
    GOLD(4, "Mineradora de Ouro", ChatColor.YELLOW),
    DIAMOND(5, "Mineradora de Diamante", ChatColor.AQUA),
    NETHERITE(6, "Mineradora de Netherite", ChatColor.DARK_RED);

    private final int level;
    private final String displayName;
    private final ChatColor color;

    MinerTier(int level, String displayName, ChatColor color) {
        this.level = level;
        this.displayName = displayName;
        this.color = color;
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
