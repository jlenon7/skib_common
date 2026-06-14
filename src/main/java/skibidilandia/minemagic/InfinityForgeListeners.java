package skibidilandia.minemagic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Forja do Infinito — a estação de nivelamento das armas mágicas.
 *
 * <p>A Forja é um bloco (smithing table) que se coloca no chão. Clicar com o botão
 * direito no bloco abre uma GUI com dois slots de entrada (a arma e a
 * {@link MineMagicItems#createInfinityGem Gema do Infinito}) e um botão de fusão.
 * Fundir consome uma gema e libera a próxima habilidade da arma
 * ({@link MineMagicItems#unlockNextAbility}). A Forja é uma estação compartilhada:
 * fica no mundo e qualquer jogador a usa — só precisa ter a gema. Os itens deixados
 * nos slots de entrada voltam ao jogador ao fechar o menu.
 *
 * <p>O bloco colocado é marcado como Forja no PDC do <em>chunk</em> (as smithing
 * tables não têm block entity), então a marca persiste e quebrá-lo devolve o item.
 */
public class InfinityForgeListeners implements Listener {

    private final JavaPlugin plugin;
    /** Guarda, por chunk, as posições "x,y,z" dos blocos que são Forja. */
    private final NamespacedKey forgeBlocksKey;

    public InfinityForgeListeners(JavaPlugin plugin) {
        this.plugin = plugin;
        this.forgeBlocksKey = new NamespacedKey(plugin, "forge_blocks");
    }

    public void shutdown() {
        // Fecha as forjas abertas para que o onClose devolva os itens das entradas.
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ForgeMenu) {
                player.closeInventory();
            }
        }
    }

    // =========================================================================
    //  Bloco da Forja: colocar, quebrar e abrir
    // =========================================================================

    /** Ao colocar a Forja, marca o bloco para que ele abra o menu de fusão. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!MineMagicItems.isInfinityForge(event.getItemInHand())) {
            return;
        }
        if (event.getBlockPlaced().getType() != Material.SMITHING_TABLE) {
            return;
        }
        markForge(event.getBlockPlaced());
    }

    /** Ao quebrar uma Forja, devolve o item da Forja (e não uma smithing table comum). */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isForgeBlock(block)) {
            return;
        }
        unmarkForge(block);
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    MineMagicItems.createInfinityForge());
        }
    }

    /** Clique direito no bloco da Forja abre o menu de fusão (em vez da smithing table vanilla). */
    @EventHandler
    public void onOpen(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!isForgeBlock(block)) {
            return;
        }
        event.setCancelled(true); // não abre a estação vanilla nem coloca bloco contra a face
        Player player = event.getPlayer();
        player.openInventory(new ForgeMenu().getInventory());
        player.playSound(block.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
    }

    // =========================================================================
    //  Interação com a GUI
    // =========================================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ForgeMenu) || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (inTop) {
            if (raw == ForgeMenu.FUSE_SLOT) {
                event.setCancelled(true);
                doFuse(player, top);
                return;
            }
            if (raw == ForgeMenu.WEAPON_SLOT || raw == ForgeMenu.GEM_SLOT) {
                // Permite colocar/retirar/trocar com o cursor; bloqueia ações que
                // espalhariam itens ou puxariam o vidro decorativo.
                InventoryAction a = event.getAction();
                if (a == InventoryAction.MOVE_TO_OTHER_INVENTORY
                        || a == InventoryAction.HOTBAR_SWAP
                        || a == InventoryAction.HOTBAR_MOVE_AND_READD
                        || a == InventoryAction.COLLECT_TO_CURSOR) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true); // qualquer outro slot (vidro / botão) é travado
            return;
        }

        // Clique no inventário do jogador (parte de baixo).
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true); // roteamos o shift-click manualmente p/ o slot certo
            ItemStack clicked = event.getCurrentItem();
            if (isEmpty(clicked)) {
                return;
            }
            if (MineMagicItems.isUpgradeable(clicked) && isEmpty(top.getItem(ForgeMenu.WEAPON_SLOT))) {
                top.setItem(ForgeMenu.WEAPON_SLOT, clicked.clone());
                event.setCurrentItem(null);
            } else if (MineMagicItems.isInfinityGem(clicked) && isEmpty(top.getItem(ForgeMenu.GEM_SLOT))) {
                top.setItem(ForgeMenu.GEM_SLOT, clicked.clone());
                event.setCurrentItem(null);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ForgeMenu)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && raw != ForgeMenu.WEAPON_SLOT && raw != ForgeMenu.GEM_SLOT) {
                event.setCancelled(true); // arrastar não pode tocar nos slots decorativos
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof ForgeMenu) || !(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        returnItem(player, top.getItem(ForgeMenu.WEAPON_SLOT));
        returnItem(player, top.getItem(ForgeMenu.GEM_SLOT));
        top.setItem(ForgeMenu.WEAPON_SLOT, null);
        top.setItem(ForgeMenu.GEM_SLOT, null);
    }

    // =========================================================================
    //  Fusão
    // =========================================================================

    private void doFuse(Player player, Inventory inv) {
        ItemStack weapon = inv.getItem(ForgeMenu.WEAPON_SLOT);
        ItemStack gem = inv.getItem(ForgeMenu.GEM_SLOT);
        if (!MineMagicItems.isUpgradeable(weapon)) {
            fail(player, "Coloque uma arma mágica no slot da esquerda.");
            return;
        }
        if (!MineMagicItems.isInfinityGem(gem)) {
            fail(player, "Coloque uma Gema do Infinito no slot da direita.");
            return;
        }
        int max = MineMagicItems.getMaxAbilityLevel(weapon);
        if (MineMagicItems.getAbilityLevel(weapon) >= max) {
            fail(player, "Esta arma já tem todas as habilidades liberadas.");
            return;
        }
        int newLevel = MineMagicItems.unlockNextAbility(weapon);
        if (newLevel < 0) {
            fail(player, "Não foi possível fundir esta arma.");
            return;
        }
        inv.setItem(ForgeMenu.WEAPON_SLOT, weapon);
        int left = gem.getAmount() - 1;
        if (left > 0) {
            gem.setAmount(left);
            inv.setItem(ForgeMenu.GEM_SLOT, gem);
        } else {
            inv.setItem(ForgeMenu.GEM_SLOT, null);
        }

        String ability = MineMagicItems.abilityName(weapon, newLevel - 1);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Fusão concluída! "
                + ChatColor.RESET + ChatColor.GRAY + "Habilidade liberada: "
                + ChatColor.GREEN + ability + ChatColor.GRAY + " (" + newLevel + "/" + max + ").");
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0),
                40, 0.4, 0.6, 0.4, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.4f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
    }

    private void fail(Player player, String message) {
        player.sendMessage(ChatColor.RED + message);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
    }

    // =========================================================================
    //  Utilidades
    // =========================================================================

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    // -------- marca de Forja no PDC do chunk ----------------------------------

    private static String posKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static Set<String> readSet(String raw) {
        Set<String> set = new LinkedHashSet<>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(";")) {
                if (!part.isEmpty()) {
                    set.add(part);
                }
            }
        }
        return set;
    }

    private void markForge(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        Set<String> set = readSet(pdc.get(forgeBlocksKey, PersistentDataType.STRING));
        if (set.add(posKey(block))) {
            pdc.set(forgeBlocksKey, PersistentDataType.STRING, String.join(";", set));
        }
    }

    private void unmarkForge(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        String raw = pdc.get(forgeBlocksKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        Set<String> set = readSet(raw);
        if (set.remove(posKey(block))) {
            if (set.isEmpty()) {
                pdc.remove(forgeBlocksKey);
            } else {
                pdc.set(forgeBlocksKey, PersistentDataType.STRING, String.join(";", set));
            }
        }
    }

    private boolean isForgeBlock(Block block) {
        if (block == null || block.getType() != Material.SMITHING_TABLE) {
            return false;
        }
        Chunk chunk = block.getChunk();
        String raw = chunk.getPersistentDataContainer().get(forgeBlocksKey, PersistentDataType.STRING);
        return raw != null && !raw.isEmpty() && readSet(raw).contains(posKey(block));
    }

    /** Devolve {@code item} ao jogador; o que não couber cai no chão. */
    private void returnItem(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    // =========================================================================
    //  Menu (InventoryHolder próprio para identificar a forja)
    // =========================================================================

    /** A GUI da Forja do Infinito: arma à esquerda, gema à direita, botão no meio. */
    public static final class ForgeMenu implements InventoryHolder {
        static final int SIZE = 27;
        static final int WEAPON_SLOT = 11;
        static final int FUSE_SLOT = 13;
        static final int GEM_SLOT = 15;

        private final Inventory inventory;

        ForgeMenu() {
            inventory = Bukkit.createInventory(this, SIZE,
                    Component.text("Forja do Infinito").color(NamedTextColor.AQUA));
            ItemStack pane = filler();
            for (int i = 0; i < SIZE; i++) {
                inventory.setItem(i, pane);
            }
            inventory.setItem(WEAPON_SLOT, null);
            inventory.setItem(GEM_SLOT, null);
            inventory.setItem(FUSE_SLOT, fuseButton());
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private static ItemStack filler() {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = pane.getItemMeta();
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
            return pane;
        }

        private static ItemStack fuseButton() {
            ItemStack button = new ItemStack(Material.ANVIL);
            ItemMeta meta = button.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Fundir");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Coloque uma " + ChatColor.WHITE + "arma mágica" + ChatColor.GRAY
                            + " à esquerda",
                    ChatColor.GRAY + "e uma " + ChatColor.LIGHT_PURPLE + "Gema do Infinito" + ChatColor.GRAY
                            + " à direita,",
                    ChatColor.GRAY + "depois clique aqui para liberar a",
                    ChatColor.GRAY + "próxima habilidade da arma.",
                    "",
                    ChatColor.DARK_GRAY + "Consome 1 gema por fusão."));
            button.setItemMeta(meta);
            return button;
        }
    }
}
