package skibidilandia.furnacetools;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coração do plugin: ao quebrar um bloco com uma ferramenta da fornalha, os
 * drops são fundidos antes de cair no chão.
 *
 * Decisões importantes:
 *  - Fortuna (e qualquer outro encanto) vale normalmente, porque pedimos os
 *    drops à própria ferramenta via {@link Block#getDrops(ItemStack, org.bukkit.entity.Entity)}.
 *  - Toque Suave (Silk Touch) NÃO tem efeito: removemos esse encanto de uma
 *    cópia da ferramenta antes de calcular os drops, então a pedra continua
 *    largando pedregulho (-> pedra), o minério continua largando o bruto
 *    (-> lingote), etc.
 *  - A ferramenta toma dano normalmente, pois não cancelamos a quebra — só
 *    trocamos os itens que caem.
 */
public class FurnaceToolListeners implements Listener {

    private final FurnaceSmelting smelting;

    public FurnaceToolListeners(FurnaceSmelting smelting) {
        this.smelting = smelting;
    }

    /** Limite de troncos derrubados por corte, para não travar o servidor. */
    private static final int MAX_TREE_LOGS = 256;

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        FurnaceToolType type = FurnaceToolItems.readType(tool);
        if (type == null) {
            return;
        }
        // No criativo o vanilla não solta drops; deixamos como está.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        boolean enabled = FurnaceToolItems.isEnabled(tool);
        Block origin = event.getBlock();
        // O machado derruba a árvore inteira mesmo com a auto-fundição desligada
        // (ligada -> carvão; desligada -> madeira).
        boolean fellsTree = type == FurnaceToolType.AXE && Tag.LOGS.isTagged(origin.getType());

        // Com auto-fundição desligada e sem ser corte de árvore, age como ferro comum.
        if (!enabled && !fellsTree) {
            return;
        }

        // --- bloco de origem (o que o jogador realmente quebrou) ---
        if (enabled) {
            boolean hadSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);
            Collection<ItemStack> drops = origin.getDrops(stripSilk(tool), player);
            boolean smeltedAnything = false;
            List<ItemStack> output = new ArrayList<>(drops.size());
            for (ItemStack drop : drops) {
                if (drop == null || drop.getType() == Material.AIR) {
                    continue;
                }
                ItemStack result = smelting.smelt(drop.getType());
                if (result != null) {
                    result.setAmount(result.getAmount() * drop.getAmount());
                    output.add(result);
                    smeltedAnything = true;
                } else {
                    output.add(drop);
                }
            }
            // Só assumimos o controle se há algo a fundir ou se há Toque Suave a anular;
            // caso contrário o drop do vanilla já seria idêntico.
            if (smeltedAnything || hadSilkTouch) {
                event.setDropItems(false);
                dropAll(origin, output);
            }
        }

        // --- corte de árvore (só o machado, em troncos) ---
        if (fellsTree) {
            fellTree(origin, tool, player, enabled);
        }
    }

    /**
     * Derruba todos os troncos conectados ao de origem (busca em 26 vizinhos,
     * pegando galhos diagonais). O tronco de origem é ignorado aqui — o evento
     * de quebra já cuidou dele. Cada tronco extra solta o mesmo que soltaria se
     * fosse quebrado normalmente (fundido se a auto-fundição estiver ligada) e
     * gasta durabilidade da ferramenta; ao "quebrar" o machado, o corte para.
     */
    private void fellTree(Block origin, ItemStack tool, Player player, boolean enabled) {
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);

        int felled = 0;
        while (!queue.isEmpty() && felled < MAX_TREE_LOGS) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (!visited.add(neighbor)) {
                            continue;
                        }
                        if (!Tag.LOGS.isTagged(neighbor.getType())) {
                            continue;
                        }
                        // Gasta durabilidade antes de quebrar; se quebraria o machado, para.
                        if (!damageTool(tool, player)) {
                            player.getInventory().setItemInMainHand(tool);
                            return;
                        }
                        List<ItemStack> drops = computeDrops(neighbor, tool, player, enabled);
                        neighbor.setType(Material.AIR); // dispara física: as folhas decaem sozinhas
                        dropAll(neighbor, drops);
                        queue.add(neighbor);
                        felled++;
                        if (felled >= MAX_TREE_LOGS) {
                            player.getInventory().setItemInMainHand(tool);
                            return;
                        }
                    }
                }
            }
        }
        player.getInventory().setItemInMainHand(tool);
    }

    /**
     * Drops de um bloco usando a ferramenta. Se {@code enabled}, anula o Toque
     * Suave e funde o que tiver receita de fornalha (Fortuna já vem aplicada
     * pelo getDrops). Se não, devolve os drops normais do vanilla.
     */
    private List<ItemStack> computeDrops(Block block, ItemStack tool, Player player, boolean enabled) {
        ItemStack calcTool = enabled ? stripSilk(tool) : tool;
        Collection<ItemStack> drops = block.getDrops(calcTool, player);
        List<ItemStack> output = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) {
                continue;
            }
            ItemStack result = enabled ? smelting.smelt(drop.getType()) : null;
            if (result != null) {
                result.setAmount(result.getAmount() * drop.getAmount());
                output.add(result);
            } else {
                output.add(drop);
            }
        }
        return output;
    }

    /** Devolve uma cópia da ferramenta sem Toque Suave (que é ignorado). */
    private static ItemStack stripSilk(ItemStack tool) {
        if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return tool;
        }
        ItemStack copy = tool.clone();
        copy.removeEnchantment(Enchantment.SILK_TOUCH);
        return copy;
    }

    private static void dropAll(Block block, List<ItemStack> stacks) {
        World world = block.getWorld();
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack stack : stacks) {
            world.dropItemNaturally(at, stack);
        }
    }

    /**
     * Aplica 1 de dano à ferramenta respeitando Inquebrável (Unbreaking).
     * Retorna false se o dano quebraria a ferramenta (nesse caso não aplica o
     * dano — deixamos o vanilla dar o golpe final no tronco de origem).
     */
    private static boolean damageTool(ItemStack tool, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return true;
        }
        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) {
            return true; // este uso não consumiu durabilidade
        }
        Damageable damageable = (Damageable) meta;
        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= tool.getType().getMaxDurability()) {
            return false; // quebraria o machado
        }
        damageable.setDamage(newDamage);
        tool.setItemMeta(meta);
        return true;
    }

    /**
     * Agachar + botão direito (no ar ou num bloco) liga/desliga a auto-fundição.
     * Exigir o agachamento preserva os usos normais do machado (descascar tronco)
     * e da pá (criar caminho de terra) num clique direito sem agachar.
     */
    @EventHandler
    public void onToggle(PlayerInteractEvent event) {
        // só a mão principal (o evento dispara 2x: principal + secundária)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (FurnaceToolItems.readType(tool) == null) {
            return;
        }

        // Assumimos o clique: nada de descascar/criar caminho/abrir bloco aqui.
        event.setCancelled(true);

        boolean enabled = !FurnaceToolItems.isEnabled(tool);
        player.getInventory().setItemInMainHand(FurnaceToolItems.setEnabled(tool, enabled));

        if (enabled) {
            player.sendMessage(ChatColor.GOLD + "Auto-fundição " + ChatColor.GREEN + "LIGADA" + ChatColor.GOLD + ".");
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.2f);
        } else {
            player.sendMessage(ChatColor.GOLD + "Auto-fundição " + ChatColor.RED + "DESLIGADA" + ChatColor.GOLD + ".");
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 0.8f);
        }
    }
}
