package skibidilandia.miner;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Estado em memória de UMA mineradora colocada no mundo.
 */
public class MinerData {

    public static final int OUTPUT_SIZE = 36;

    private int level;
    private int ticksUntilNextCycle;
    private int cycleLengthTicks;          // duração total do ciclo atual (pra barra de progresso)
    private int fuelCyclesRemaining;
    private final ItemStack[] output = new ItemStack[OUTPUT_SIZE];

    /** Inventário aberto no momento (null se ninguém está olhando). Não persistido. */
    private transient Inventory openView;

    public MinerData(int level) {
        this.level = level;
        startNewCycle();
    }

    /** Sorteia um novo ciclo (intervalo + duração total registrada pra barra de progresso). */
    public void startNewCycle() {
        this.cycleLengthTicks = MinerGeneration.nextCycleTicks(level);
        this.ticksUntilNextCycle = this.cycleLengthTicks;
    }

    public int getCycleLengthTicks() {
        return cycleLengthTicks;
    }

    public void setCycleLengthTicks(int cycleLengthTicks) {
        this.cycleLengthTicks = cycleLengthTicks;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getTicksUntilNextCycle() {
        return ticksUntilNextCycle;
    }

    public void setTicksUntilNextCycle(int ticks) {
        this.ticksUntilNextCycle = ticks;
    }

    public int getFuelCyclesRemaining() {
        return fuelCyclesRemaining;
    }

    public void setFuelCyclesRemaining(int cycles) {
        this.fuelCyclesRemaining = cycles;
    }

    public ItemStack[] getOutput() {
        return output;
    }

    public Inventory getOpenView() {
        return openView;
    }

    public void setOpenView(Inventory openView) {
        this.openView = openView;
    }

    /** Há pelo menos um espaço pra encaixar mais desse material? */
    public boolean hasSpaceFor(Material material) {
        for (ItemStack stack : output) {
            if (stack == null || stack.getType() == Material.AIR) {
                return true;
            }
            if (stack.getType() == material && stack.getAmount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Adiciona até {@code amount} itens à saída. Retorna quanto SOBROU (não coube). */
    public int addOutput(Material material, int amount) {
        // primeiro completa stacks existentes
        for (int i = 0; i < output.length && amount > 0; i++) {
            ItemStack stack = output[i];
            if (stack != null && stack.getType() == material) {
                int room = stack.getMaxStackSize() - stack.getAmount();
                if (room > 0) {
                    int add = Math.min(room, amount);
                    stack.setAmount(stack.getAmount() + add);
                    amount -= add;
                }
            }
        }
        // depois usa slots vazios
        for (int i = 0; i < output.length && amount > 0; i++) {
            if (output[i] == null || output[i].getType() == Material.AIR) {
                int max = material.getMaxStackSize();
                int add = Math.min(max, amount);
                output[i] = new ItemStack(material, add);
                amount -= add;
            }
        }
        return amount;
    }

    /**
     * Procura combustível em qualquer slot da saída e consome 1 unidade.
     * Retorna quantos ciclos esse combustível rende, ou 0 se não havia combustível.
     */
    public int consumeOneFuel() {
        for (int i = 0; i < output.length; i++) {
            ItemStack stack = output[i];
            if (stack != null && stack.getAmount() > 0 && MinerFuel.isFuel(stack)) {
                int cycles = MinerFuel.cyclesFor(stack.getType());
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    output[i] = null;
                }
                return cycles;
            }
        }
        return 0;
    }

    /** Há combustível em algum slot da saída? */
    public boolean hasFuel() {
        for (ItemStack stack : output) {
            if (stack != null && stack.getAmount() > 0 && MinerFuel.isFuel(stack)) {
                return true;
            }
        }
        return false;
    }
}
