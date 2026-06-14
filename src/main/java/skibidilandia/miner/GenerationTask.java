package skibidilandia.miner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Roda a cada 20 ticks (1s). Para cada mineradora com chunk carregado, conta o tempo
 * até o próximo ciclo e, ao chegar a zero, gera minério consumindo combustível.
 */
public class GenerationTask extends BukkitRunnable {

    private static final int RETRY_TICKS = 40; // tempo de espera quando pausada (cheia / sem fuel)

    /** Liga logs de diagnóstico no console (a cada 5s + a cada ciclo). */
    private static final boolean DEBUG = false;

    private final MinerRegistry registry;
    private int debugTick = 0;

    public GenerationTask(MinerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run() {
        if (DEBUG) {
            debugTick++;
            if (debugTick % 5 == 0) {
                StringBuilder sb = new StringBuilder("[Miner][debug] task viva.");
                for (MinerData d : registry.all()) {
                    sb.append(" [data#").append(System.identityHashCode(d))
                      .append(" lvl").append(d.getLevel())
                      .append(" openView=").append(d.getOpenView() != null).append("]");
                }
                Bukkit.getLogger().info(sb.toString());
            }
        }
        registry.forEach((loc, data) -> {
            World world = loc.getWorld();
            if (world == null) {
                return;
            }
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return; // só gera com chunk carregado
            }
            MinerItems.ensureLit(loc.getBlock()); // mantém a fornalha acesa
            int remaining = data.getTicksUntilNextCycle() - 20;
            if (remaining > 0) {
                data.setTicksUntilNextCycle(remaining);
                if (data.getOpenView() != null) {
                    MinerGui.repaintStatus(data); // atualiza barra de progresso + contagem a cada segundo
                }
                return;
            }
            cycle(data);
        });
    }

    private void cycle(MinerData data) {
        int level = data.getLevel();

        // se alguém está com a GUI aberta, o inventário é a fonte da verdade
        if (data.getOpenView() != null) {
            MinerGui.syncFromInventory(data, data.getOpenView());
        }

        MinerGeneration.Ore ore = MinerGeneration.rollOre(level);
        if (ore == null) {
            data.setTicksUntilNextCycle(RETRY_TICKS);
            return;
        }

        // saída cheia para esse minério? pausa sem gastar combustível
        if (!data.hasSpaceFor(ore.material)) {
            if (DEBUG) {
                Bukkit.getLogger().info("[Miner][debug] PAUSADA (saída cheia) p/ " + ore.material);
            }
            data.setTicksUntilNextCycle(RETRY_TICKS);
            return;
        }

        // garante combustível: consome 1 de qualquer slot que tenha combustível
        if (data.getFuelCyclesRemaining() <= 0) {
            int cycles = data.consumeOneFuel();
            if (cycles <= 0) {
                if (DEBUG) {
                    Bukkit.getLogger().info("[Miner][debug] PAUSADA (sem combustível)");
                }
                data.setTicksUntilNextCycle(RETRY_TICKS);
                MinerGui.repaint(data); // atualiza o status para "Sem combustível"
                return;
            }
            data.setFuelCyclesRemaining(cycles);
        }

        int quantity = MinerGeneration.rollQuantity(level, ore.oreTier);
        data.addOutput(ore.material, quantity);
        data.setFuelCyclesRemaining(data.getFuelCyclesRemaining() - 1);
        data.startNewCycle();
        if (DEBUG) {
            Bukkit.getLogger().info("[Miner][debug] CICLO: +" + quantity + "x " + ore.material
                    + " | fuelCiclos=" + data.getFuelCyclesRemaining()
                    + " | próximo em " + (data.getCycleLengthTicks() / 20) + "s");
        }
        MinerGui.repaint(data);
    }
}
