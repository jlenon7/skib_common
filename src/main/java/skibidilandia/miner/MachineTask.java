package skibidilandia.miner;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Roda a cada 20 ticks (1s). Para cada máquina com chunk carregado, conta o tempo até o
 * próximo trabalho e, ao chegar a zero, executa a ação do tipo (colher / compactar).
 */
public class MachineTask extends BukkitRunnable {

    /** Quanto combustível deixar em cada mineradora por material, pra não secar o ciclo. */
    private static final int FUEL_RESERVE = 64;

    /** Plantações com idade (Ageable): colhe no máximo e replanta (volta pra idade 0). */
    private static final Set<Material> AGEABLE_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA);

    /** Plantas verticais: colhe os segmentos acima da base (deixa o de baixo pra rebrotar). */
    private static final Set<Material> VERTICAL_CROPS = EnumSet.of(
            Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO);

    private final MachineRegistry registry;
    private final MinerRegistry minerRegistry;

    public MachineTask(MachineRegistry registry, MinerRegistry minerRegistry) {
        this.registry = registry;
        this.minerRegistry = minerRegistry;
    }

    @Override
    public void run() {
        registry.forEach((loc, data) -> {
            World world = loc.getWorld();
            if (world == null) {
                return;
            }
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return; // só trabalha com o próprio chunk carregado
            }
            int remaining = data.getTicksUntilNextWork() - 20;
            if (remaining > 0) {
                data.setTicksUntilNextWork(remaining);
                return;
            }
            data.setTicksUntilNextWork(data.getType().getWorkIntervalTicks());
            work(loc, data);
        });
    }

    private void work(Location loc, MachineData data) {
        // se alguém está com a GUI aberta, o inventário é a fonte da verdade
        if (data.getOpenView() != null) {
            MachineGui.syncFromInventory(data, data.getOpenView());
        }

        switch (data.getType()) {
            case HARVESTER:
                harvest(loc, data);
                break;
            case COMPACTOR:
                compact(loc, data);
                break;
            default:
                break;
        }

        MachineGui.repaint(data);
    }

    // ------------------------------------------------------------------ COLHETADEIRA

    private void harvest(Location loc, MachineData data) {
        World world = loc.getWorld();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        int r = data.getType().getRadius();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = bx + dx;
                int z = bz + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue; // não força carregar chunks vizinhos
                }
                for (int dy = -r; dy <= r; dy++) {
                    int y = by + dy;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                        continue;
                    }
                    tryHarvestBlock(data, world.getBlockAt(x, y, z));
                }
            }
        }
    }

    private void tryHarvestBlock(MachineData data, Block block) {
        Material type = block.getType();

        if (AGEABLE_CROPS.contains(type)) {
            BlockData bd = block.getBlockData();
            if (!(bd instanceof Ageable)) {
                return;
            }
            Ageable age = (Ageable) bd;
            if (age.getAge() < age.getMaximumAge()) {
                return; // ainda não madura
            }
            List<ItemStack> drops = new ArrayList<>(block.getDrops());
            if (drops.isEmpty() || !data.canFit(drops)) {
                return;
            }
            store(data, drops);
            age.setAge(0);
            block.setBlockData(age, false); // replanta no lugar
            return;
        }

        if (type == Material.PUMPKIN || type == Material.MELON) {
            if (!hasAdjacentStem(block, type)) {
                return; // só colhe se cresceu de uma plantação (ignora decoração)
            }
            harvestAndClear(data, block);
            return;
        }

        if (VERTICAL_CROPS.contains(type)) {
            // só colhe segmentos acima da base (o bloco de baixo é do mesmo tipo)
            if (block.getRelative(BlockFace.DOWN).getType() != type) {
                return;
            }
            harvestAndClear(data, block);
        }
    }

    /** Colhe o bloco inteiro (guarda os drops e remove), só se a colheita couber. */
    private void harvestAndClear(MachineData data, Block block) {
        List<ItemStack> drops = new ArrayList<>(block.getDrops());
        if (drops.isEmpty() || !data.canFit(drops)) {
            return;
        }
        store(data, drops);
        block.setType(Material.AIR, false);
    }

    private void store(MachineData data, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop != null && drop.getType() != Material.AIR) {
                data.addOutput(drop.getType(), drop.getAmount());
            }
        }
    }

    private boolean hasAdjacentStem(Block fruit, Material fruitType) {
        Material stem = fruitType == Material.PUMPKIN ? Material.PUMPKIN_STEM : Material.MELON_STEM;
        Material attached = fruitType == Material.PUMPKIN
                ? Material.ATTACHED_PUMPKIN_STEM : Material.ATTACHED_MELON_STEM;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material neighbor = fruit.getRelative(face).getType();
            if (neighbor == stem || neighbor == attached) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ COMPACTADORA

    private void compact(Location loc, MachineData data) {
        World world = loc.getWorld();
        int r = data.getType().getRadius();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        // 1. puxa minério cru das mineradoras no raio
        minerRegistry.forEach((mloc, miner) -> {
            if (mloc.getWorld() != world) {
                return;
            }
            if (Math.abs(mloc.getBlockX() - bx) > r
                    || Math.abs(mloc.getBlockY() - by) > r
                    || Math.abs(mloc.getBlockZ() - bz) > r) {
                return;
            }
            pullFromMiner(data, miner);
        });

        // 2. compacta tudo o que der na storage
        MachineCompacting.compact(data);
    }

    private void pullFromMiner(MachineData data, MinerData miner) {
        // se a mineradora está aberta, o inventário é a fonte da verdade
        if (miner.getOpenView() != null) {
            MinerGui.syncFromInventory(miner, miner.getOpenView());
        }

        boolean changed = false;
        Map<Material, Integer> reserve = new EnumMap<>(Material.class);
        ItemStack[] out = miner.getOutput();
        for (int i = 0; i < out.length; i++) {
            ItemStack stack = out[i];
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Material mat = stack.getType();
            if (!MachineCompacting.isOre(mat)) {
                continue;
            }
            int pullable = stack.getAmount();
            // deixa uma reserva de combustível na mineradora (ex: carvão é minério E combustível)
            if (MinerFuel.isFuel(stack)) {
                int rem = reserve.getOrDefault(mat, FUEL_RESERVE);
                int leave = Math.min(rem, pullable);
                pullable -= leave;
                reserve.put(mat, rem - leave);
            }
            if (pullable <= 0) {
                continue;
            }
            int leftover = data.addOutput(mat, pullable);
            int stored = pullable - leftover;
            if (stored > 0) {
                stack.setAmount(stack.getAmount() - stored);
                if (stack.getAmount() <= 0) {
                    out[i] = null;
                }
                changed = true;
            }
        }

        if (changed && miner.getOpenView() != null) {
            MinerGui.repaint(miner);
        }
    }
}
