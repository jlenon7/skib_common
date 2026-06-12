package skibidilandia.miner;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Estado em memória de UMA máquina (colhetadeira/compactadora) colocada no mundo.
 * Guarda o tipo, o inventário interno e o contador até o próximo trabalho.
 */
public class MachineData {

    public static final int STORAGE_SIZE = 45;

    private final MachineType type;
    private int ticksUntilNextWork;
    private final ItemStack[] storage = new ItemStack[STORAGE_SIZE];

    /** Inventário aberto no momento (null se ninguém está olhando). Não persistido. */
    private transient Inventory openView;

    public MachineData(MachineType type) {
        this.type = type;
        this.ticksUntilNextWork = type.getWorkIntervalTicks();
    }

    public MachineType getType() {
        return type;
    }

    public int getTicksUntilNextWork() {
        return ticksUntilNextWork;
    }

    public void setTicksUntilNextWork(int ticks) {
        this.ticksUntilNextWork = ticks;
    }

    public ItemStack[] getStorage() {
        return storage;
    }

    public Inventory getOpenView() {
        return openView;
    }

    public void setOpenView(Inventory openView) {
        this.openView = openView;
    }

    /** Adiciona até {@code amount} itens à storage. Retorna quanto SOBROU (não coube). */
    public int addOutput(Material material, int amount) {
        // primeiro completa stacks existentes
        for (int i = 0; i < storage.length && amount > 0; i++) {
            ItemStack stack = storage[i];
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
        for (int i = 0; i < storage.length && amount > 0; i++) {
            if (storage[i] == null || storage[i].getType() == Material.AIR) {
                int max = material.getMaxStackSize();
                int add = Math.min(max, amount);
                storage[i] = new ItemStack(material, add);
                amount -= add;
            }
        }
        return amount;
    }

    /**
     * Verifica (sem alterar nada) se TODOS os drops cabem na storage juntos.
     * Usado para só colher quando há espaço para guardar a colheita inteira.
     */
    public boolean canFit(List<ItemStack> items) {
        Material[] mats = new Material[storage.length];
        int[] amounts = new int[storage.length];
        for (int i = 0; i < storage.length; i++) {
            ItemStack s = storage[i];
            if (s != null && s.getType() != Material.AIR) {
                mats[i] = s.getType();
                amounts[i] = s.getAmount();
            }
        }
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) {
                continue;
            }
            int amt = it.getAmount();
            Material m = it.getType();
            int max = m.getMaxStackSize();
            for (int i = 0; i < mats.length && amt > 0; i++) {
                if (mats[i] == m) {
                    int room = max - amounts[i];
                    int add = Math.min(room, amt);
                    amounts[i] += add;
                    amt -= add;
                }
            }
            for (int i = 0; i < mats.length && amt > 0; i++) {
                if (mats[i] == null) {
                    mats[i] = m;
                    int add = Math.min(max, amt);
                    amounts[i] = add;
                    amt -= add;
                }
            }
            if (amt > 0) {
                return false;
            }
        }
        return true;
    }

    /** Quantos itens desse material existem na storage. */
    public int countOf(Material material) {
        int total = 0;
        for (ItemStack stack : storage) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Remove tudo desse material da storage. */
    public void removeAll(Material material) {
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null && storage[i].getType() == material) {
                storage[i] = null;
            }
        }
    }
}
