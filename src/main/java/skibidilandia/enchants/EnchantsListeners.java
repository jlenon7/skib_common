package skibidilandia.enchants;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import skibidilandia.furnacetools.FurnaceSmelting;
import skibidilandia.furnacetools.FurnaceToolItems;
import skibidilandia.mcmmo.McmmoXp;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Comportamento do plugin de encantamentos.
 *
 * Aplicação (bigorna): Livro Encantado de Hexa + picareta/pá -> a ferramenta sai
 * com o Hexa. O resultado é montado manualmente (igual ao conserto da Picareta
 * Quebra-Bedrock) para ter controle total sobre o consumo das entradas e do XP,
 * já que o vanilla não conhece esse encantamento.
 *
 * Efeito (mineração): ao quebrar um bloco com uma ferramenta Hexa, quebra também
 * a área 3x3 ao redor, no plano da face que está sendo minerada (alarga o túnel).
 *
 * Combo com a fornalha: se a ferramenta Hexa também for uma ferramenta da fornalha
 * com auto-fundição ligada, os drops da área 3x3 são fundidos antes de cair — assim
 * o efeito não funde só o bloco central (que a ferramenta da fornalha já trata), mas
 * a área inteira.
 */
public class EnchantsListeners implements Listener {

    private final JavaPlugin plugin;
    /** Tabela de fundição da fornalha; pode ser null se aquele sistema não estiver ligado. */
    private final FurnaceSmelting smelting;

    public EnchantsListeners(JavaPlugin plugin, FurnaceSmelting smelting) {
        this.plugin = plugin;
        this.smelting = smelting;
    }

    /** Níveis de XP para aplicar o Hexa na bigorna. */
    private static final int APPLY_LEVEL_COST = 30;
    /** Alcance do raytrace para descobrir a face que está sendo minerada. */
    private static final double REACH = 6.0;

    // =========================================================================
    //  Aplicação do Hexa na bigorna
    // =========================================================================

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack tool = inv.getItem(0);
        ItemStack book = inv.getItem(1);
        if (tool == null || !EnchantsItems.isSupportedTool(tool.getType())
                || !EnchantsItems.isHexaBook(book)) {
            return;
        }
        if (EnchantsItems.hasHexa(tool)) {
            event.setResult(null); // já tem Hexa; nada a fazer
            return;
        }

        ItemStack result = EnchantsItems.applyHexa(tool);
        String rename = inv.getRenameText();
        if (rename != null && !rename.isEmpty()) {
            ItemMeta meta = result.getItemMeta();
            meta.setDisplayName(rename);
            result.setItemMeta(meta);
        }

        event.setResult(result);
        inv.setRepairCost(APPLY_LEVEL_COST); // exibe o custo na bigorna
    }

    @EventHandler
    public void onAnvilTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!(event.getInventory() instanceof AnvilInventory)) {
            return;
        }
        if (event.getRawSlot() != 2) { // slot de resultado
            return;
        }
        AnvilInventory inv = (AnvilInventory) event.getInventory();
        ItemStack result = inv.getItem(2);
        ItemStack book = inv.getItem(1);
        // só assumimos quando é a nossa receita (ferramenta Hexa + Livro de Hexa)
        if (!EnchantsItems.hasHexa(result) || !EnchantsItems.isHexaBook(book)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative && player.getLevel() < APPLY_LEVEL_COST) {
            player.sendMessage(ChatColor.RED + "Você precisa de " + APPLY_LEVEL_COST
                    + " níveis de XP para aplicar o Hexa.");
            return;
        }

        ItemStack enchanted = result.clone();

        // consome as entradas (ferramenta no slot 0, 1 livro do slot 1)
        inv.setItem(0, null);
        int remaining = book.getAmount() - 1;
        if (remaining <= 0) {
            inv.setItem(1, null);
        } else {
            book.setAmount(remaining);
            inv.setItem(1, book);
        }
        if (!creative) {
            player.setLevel(player.getLevel() - APPLY_LEVEL_COST);
        }

        // entrega o resultado
        if (event.isShiftClick()) {
            giveOrDrop(player, enchanted);
        } else {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                player.setItemOnCursor(enchanted);
            } else {
                giveOrDrop(player, enchanted);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.updateInventory();
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    // =========================================================================
    //  Efeito Hexa — quebra 3x3 no plano da face minerada
    // =========================================================================

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!EnchantsItems.hasHexa(tool)) {
            return;
        }

        Block center = event.getBlock();
        BlockFace face = resolveFace(player, center);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        // Eixo da face = profundidade; os outros dois eixos formam o plano 3x3.
        // O bloco central já é quebrado pelo vanilla; tratamos só os 8 vizinhos.
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                Block block = neighbor(center, face, a, b);
                if (!canBreak(block)) {
                    continue;
                }
                if (creative) {
                    block.setType(Material.AIR);
                    continue;
                }
                breakBlock(block, tool, player); // funde a área se a ferramenta for da fornalha
                tool = applyDamage(tool, player);
                if (tool == null) {
                    player.getInventory().setItemInMainHand(null);
                    return; // a ferramenta quebrou no meio da área
                }
            }
        }
        if (!creative) {
            player.getInventory().setItemInMainHand(tool);
        }
    }

    /**
     * Quebra um bloco da área Hexa. Se a ferramenta for uma ferramenta da fornalha
     * com auto-fundição ligada, funde os drops (anulando Toque Suave, igual ao bloco
     * central que a própria ferramenta da fornalha trata); senão, quebra normalmente
     * respeitando Fortuna/Toque Suave da ferramenta.
     */
    private void breakBlock(Block block, ItemStack tool, Player player) {
        // Estado capturado antes de quebrar: o mcMMO usa o material para creditar o
        // XP de mineração/lenhador/escavação que esta quebra (sem BlockBreakEvent)
        // não daria sozinha.
        BlockState before = block.getState();

        boolean smeltable = smelting != null
                && FurnaceToolItems.readType(tool) != null
                && FurnaceToolItems.isEnabled(tool);
        if (!smeltable) {
            block.breakNaturally(tool); // dropa respeitando a ferramenta (fortuna/toque suave)
            McmmoXp.blockBreak(player, before);
            return;
        }

        // Toque Suave é ignorado pela fornalha (mesma regra do bloco central).
        ItemStack calcTool = tool;
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            calcTool = tool.clone();
            calcTool.removeEnchantment(Enchantment.SILK_TOUCH);
        }
        Collection<ItemStack> drops = block.getDrops(calcTool, player);
        block.setType(Material.AIR);
        McmmoXp.blockBreak(player, before);

        World world = block.getWorld();
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) {
                continue;
            }
            ItemStack result = smelting.smelt(drop.getType());
            if (result != null) {
                result.setAmount(result.getAmount() * drop.getAmount());
                world.dropItemNaturally(at, result);
            } else {
                world.dropItemNaturally(at, drop);
            }
        }
    }

    /** Bloco vizinho no plano perpendicular à face (profundidade = eixo da face). */
    private static Block neighbor(Block center, BlockFace face, int a, int b) {
        switch (face) {
            case UP:
            case DOWN:
                return center.getRelative(a, 0, b); // plano X-Z
            case EAST:
            case WEST:
                return center.getRelative(0, a, b); // plano Y-Z
            case NORTH:
            case SOUTH:
            default:
                return center.getRelative(a, b, 0); // plano X-Y
        }
    }

    /** Descobre a face que o jogador está minerando (raytrace, com fallback pela mira). */
    private static BlockFace resolveFace(Player player, Block center) {
        RayTraceResult ray = player.rayTraceBlocks(REACH);
        if (ray != null && center.equals(ray.getHitBlock()) && ray.getHitBlockFace() != null) {
            return ray.getHitBlockFace();
        }
        // fallback: eixo dominante da direção do olhar
        Vector dir = player.getEyeLocation().getDirection();
        double ax = Math.abs(dir.getX());
        double ay = Math.abs(dir.getY());
        double az = Math.abs(dir.getZ());
        if (ay >= ax && ay >= az) {
            return dir.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        }
        if (ax >= az) {
            return dir.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return dir.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /** Só quebra blocos sólidos quebráveis (pula ar, líquidos e blocos indestrutíveis). */
    private static boolean canBreak(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) {
            return false;
        }
        return type.getHardness() >= 0; // bedrock/barreira têm dureza < 0
    }

    /**
     * Aplica 1 de dano à ferramenta respeitando Inquebrável (Unbreaking). Retorna
     * a ferramenta atualizada, ou {@code null} se o uso a quebrou.
     */
    private static ItemStack applyDamage(ItemStack tool, Player player) {
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return tool;
        }
        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) {
            return tool; // este uso não consumiu durabilidade
        }
        Damageable damageable = (Damageable) meta;
        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= tool.getType().getMaxDurability()) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return null; // a ferramenta quebrou
        }
        damageable.setDamage(newDamage);
        tool.setItemMeta(meta);
        return tool;
    }
}
