package skibidilandia.miner;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Toda a lógica de "o que/quanto/quão rápido" a mineradora gera.
 * Tabelas hardcoded por enquanto (depois podem ir pro config.yml).
 */
public final class MinerGeneration {

    private static final Random RANDOM = new Random();

    /** Intervalo base de um ciclo, em ticks (20 ticks = 1s). Fixo em 30s. */
    private static final int BASE_INTERVAL_TICKS = 30 * 20;

    /** Um minério gerável: o material, o tier em que é desbloqueado e seu peso de sorteio. */
    public static final class Ore {
        final Material material;
        final int oreTier;
        final int weight;

        Ore(Material material, int oreTier, int weight) {
            this.material = material;
            this.oreTier = oreTier;
            this.weight = weight;
        }
    }

    /** Tabela mestre de minérios (cru) das mineradoras acumulativas (tiers 1..5). */
    private static final List<Ore> ORES = new ArrayList<>();
    static {
        ORES.add(new Ore(Material.COAL, 1, 100));
        ORES.add(new Ore(Material.RAW_COPPER, 2, 70));
        ORES.add(new Ore(Material.RAW_IRON, 3, 50));
        ORES.add(new Ore(Material.RAW_GOLD, 4, 25));
        ORES.add(new Ore(Material.REDSTONE, 4, 40));
        ORES.add(new Ore(Material.LAPIS_LAZULI, 4, 35));
        ORES.add(new Ore(Material.DIAMOND, 5, 12));
        // A esmeralda agora faz parte da mineradora de diamante (tier 5).
        ORES.add(new Ore(Material.EMERALD, 5, 8));
    }

    /**
     * Mineradora de Netherite (tier 6): NÃO acumulativa. Só gera ouro, quartzo e
     * netherite, com o ouro sendo de longe o mais comum. O oreTier de cada item
     * controla a "distância" usada no sorteio de quantidade (quanto menor, mais sai).
     */
    private static final List<Ore> NETHERITE_ORES = new ArrayList<>();
    static {
        NETHERITE_ORES.add(new Ore(Material.RAW_GOLD, 4, 100));       // o mais comum, em quantidade
        NETHERITE_ORES.add(new Ore(Material.QUARTZ, 5, 45));          // intermediário
        NETHERITE_ORES.add(new Ore(Material.ANCIENT_DEBRIS, 6, 6));   // raro, sempre de 1 em 1
    }

    /**
     * Tabela de VELOCIDADE por nível (1..7) -> pesos [1x, 2x, 3x].
     * Sorteia o multiplicador que divide o intervalo do ciclo.
     */
    private static final int[][] SPEED = {
            {100, 0, 0},   // nível 1
            {85, 15, 0},   // nível 2
            {70, 25, 5},   // nível 3
            {60, 30, 10},  // nível 4
            {50, 35, 15},  // nível 5
            {30, 45, 25},  // nível 6 (Netherite — a mais rápida)
    };

    /**
     * Tabela de QUANTIDADE por distância (nível − tier do minério, 0..5+) -> pesos [1x, 2x, 3x].
     */
    private static final int[][] QUANTITY = {
            {100, 0, 0},   // distância 0
            {70, 30, 0},   // distância 1
            {55, 35, 10},  // distância 2
            {45, 40, 15},  // distância 3
            {35, 45, 20},  // distância 4
            {25, 50, 25},  // distância 5+
    };

    private MinerGeneration() {
    }

    /** Intervalo (em ticks) do próximo ciclo: base fixa de 30s, dividida pelo bônus de velocidade do nível. */
    public static int nextCycleTicks(int level) {
        int speedMult = rollMultiplier(SPEED[level - 1]);
        return Math.max(20, BASE_INTERVAL_TICKS / speedMult);
    }

    /** Sorteia qual minério sai, dado o nível da mineradora (entre os de oreTier <= nível). */
    public static Ore rollOre(int level) {
        // A mineradora de Netherite é especial: conjunto fixo, não acumulativo.
        if (level == MinerTier.NETHERITE.getLevel()) {
            return rollFrom(NETHERITE_ORES, Integer.MAX_VALUE);
        }
        return rollFrom(ORES, level);
    }

    /** Sorteia um minério de {@code pool}, considerando só os de oreTier <= maxTier. */
    private static Ore rollFrom(List<Ore> pool, int maxTier) {
        int total = 0;
        for (Ore ore : pool) {
            if (ore.oreTier <= maxTier) {
                total += ore.weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        int pick = RANDOM.nextInt(total);
        for (Ore ore : pool) {
            if (ore.oreTier <= maxTier) {
                pick -= ore.weight;
                if (pick < 0) {
                    return ore;
                }
            }
        }
        return null;
    }

    /** Sorteia a quantidade (1, 2 ou 3) com base na distância nível−tier. */
    public static int rollQuantity(int level, int oreTier) {
        int distance = level - oreTier;
        if (distance < 0) {
            distance = 0;
        }
        if (distance >= QUANTITY.length) {
            distance = QUANTITY.length - 1;
        }
        return rollMultiplier(QUANTITY[distance]);
    }

    /** Dado pesos [w1, w2, w3], retorna 1, 2 ou 3. */
    private static int rollMultiplier(int[] weights) {
        int total = weights[0] + weights[1] + weights[2];
        if (total <= 0) {
            return 1;
        }
        int pick = RANDOM.nextInt(total);
        if (pick < weights[0]) {
            return 1;
        }
        if (pick < weights[0] + weights[1]) {
            return 2;
        }
        return 3;
    }
}
