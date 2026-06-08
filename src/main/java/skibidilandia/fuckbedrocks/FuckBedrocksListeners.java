package skibidilandia.fuckbedrocks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Comportamento dos dois itens do FuckBedrocks.
 *
 * Picareta Quebra-Bedrock:
 *  - Bedrock tem dureza -1 (inquebrável), então o vanilla nunca mostra progresso
 *    nem dispara o evento de quebra. Por isso a mineração é simulada na mão: cada
 *    "golpe" enquanto o jogador segura o clique esquerdo mirando a bedrock conta
 *    como progresso (via {@link PlayerAnimationEvent}). Só depois de
 *    {@link #SWINGS_TO_BREAK} golpes contínuos o bloco quebra — uma demora
 *    parecida com minerar obsidiana. Soltar o clique (parar de golpear) cancela.
 *  - A picareta toma 1 de dano (respeitando Inquebrável) ao quebrar a bedrock e
 *    quebra ao zerar a durabilidade, como uma picareta de ouro normal.
 *  - Conserto: só na bigorna, usando 1 Estrela do Nether + 30 níveis de XP.
 *
 * TNT Nuclear:
 *  - Ao explodir: explosão 10x maior (potência 4 -> 40), destrói toda a bedrock
 *    no raio e mata instantaneamente quem estiver dentro dele.
 */
public class FuckBedrocksListeners implements Listener {

    private final JavaPlugin plugin;

    public FuckBedrocksListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // --- mineração de bedrock ---
    /** Golpes contínuos necessários para quebrar a bedrock (~obsidiana). */
    private static final int SWINGS_TO_BREAK = 100;
    /** Ticks sem golpear antes de cancelar a mineração (soltou o clique). */
    private static final int MAX_IDLE_TICKS = 6;
    /** Alcance do raio de mira para confirmar que está mirando a bedrock. */
    private static final int REACH = 6;

    /** Sessão de mineração ativa por jogador. */
    private final Map<UUID, BedrockMining> mining = new HashMap<>();

    // --- conserto na bigorna ---
    /** Níveis de XP gastos para consertar a picareta na bigorna. */
    private static final int REPAIR_LEVEL_COST = 30;

    // --- TNT Nuclear ---
    /** Potência de um TNT comum e da nuclear (10x maior). */
    private static final float NORMAL_TNT_POWER = 4.0f;
    private static final float NUKE_POWER = NORMAL_TNT_POWER * 10.0f; // 40
    /** Raio (em blocos) onde a nuclear quebra bedrock e mata todo mundo. */
    private static final double NUKE_EFFECT_RADIUS = NUKE_POWER;

    /**
     * TNTs nucleares colocados e ainda não ativados. Guardamos a posição do
     * bloco; quando o {@code TNTPrimed} nasce nessa posição, transferimos a
     * marca para a entidade. Usar a posição evita depender do PDC do bloco.
     */
    private final Set<String> pendingNukes = new HashSet<>();

    /** Carrinhos com TNT Nuclear colocados/disparados e ainda não materializados. */
    private final Set<String> pendingNukeMinecarts = new HashSet<>();

    private static String key(World world, int x, int y, int z) {
        return world.getUID() + ":" + x + ":" + y + ":" + z;
    }

    private static String key(Location loc) {
        return key(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // =========================================================================
    //  Picareta Quebra-Bedrock — mineração lenta (estilo obsidiana)
    // =========================================================================

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        // No criativo o jogador já quebra bedrock nativamente.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!FuckBedrocksItems.isBedrockPickaxe(tool)) {
            return;
        }
        Block target = player.getTargetBlockExact(REACH);
        if (target == null || target.getType() != Material.BEDROCK) {
            return; // não está mirando bedrock; a sessão existente expira sozinha
        }

        BedrockMining session = mining.get(player.getUniqueId());
        if (session == null || !session.isSameBlock(target)) {
            if (session != null) {
                session.stop();
            }
            session = new BedrockMining(player.getUniqueId(), target);
            mining.put(player.getUniqueId(), session);
            session.start();
        }
        session.hit(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BedrockMining session = mining.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.stop();
        }
    }

    /**
     * Uma mineração de bedrock em andamento. O progresso avança por golpe (em
     * {@link #hit}); a tarefa periódica só atualiza a animação de rachadura e
     * cancela quando o jogador para de golpear ({@code idle > MAX_IDLE_TICKS}).
     */
    private final class BedrockMining extends BukkitRunnable {
        private final UUID uuid;
        private final Block block;
        private int progress;
        private int idle;

        BedrockMining(UUID uuid, Block block) {
            this.uuid = uuid;
            this.block = block;
        }

        boolean isSameBlock(Block other) {
            return block.equals(other);
        }

        void start() {
            runTaskTimer(plugin, 1L, 1L);
        }

        /** Conta um golpe; quebra a bedrock ao atingir a meta. */
        void hit(Player player) {
            idle = 0;
            progress++;
            sendCrack(player);
            if (progress >= SWINGS_TO_BREAK) {
                breakBedrock(player);
                stop();
            }
        }

        @Override
        public void run() {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()
                    || !FuckBedrocksItems.isBedrockPickaxe(player.getInventory().getItemInMainHand())
                    || block.getType() != Material.BEDROCK) {
                stop();
                return;
            }
            idle++;
            if (idle > MAX_IDLE_TICKS) {
                stop(); // parou de golpear
                return;
            }
            sendCrack(player); // mantém a rachadura visível
        }

        private void sendCrack(Player player) {
            float ratio = Math.min(0.99f, (float) progress / SWINGS_TO_BREAK);
            player.sendBlockDamage(block.getLocation(), ratio);
        }

        private void breakBedrock(Player player) {
            World world = block.getWorld();
            Location at = block.getLocation();
            world.dropItemNaturally(at.clone().add(0.5, 0.5, 0.5), new ItemStack(Material.BEDROCK));
            world.playSound(at, Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);
            block.setType(Material.AIR);
            ItemStack updated = applyDamage(player.getInventory().getItemInMainHand(), player);
            player.getInventory().setItemInMainHand(updated);
        }

        void stop() {
            try {
                cancel();
            } catch (IllegalStateException ignored) {
                // tarefa ainda não agendada
            }
            mining.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendBlockDamage(block.getLocation(), 0.0f); // limpa a rachadura
            }
        }
    }

    /**
     * Aplica 1 de dano à picareta respeitando Inquebrável (Unbreaking). Retorna
     * o item atualizado, ou {@code null} se o uso quebrou a ferramenta.
     */
    private ItemStack applyDamage(ItemStack tool, Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return tool;
        }
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
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return null; // a picareta quebrou
        }
        damageable.setDamage(newDamage);
        tool.setItemMeta(meta);
        return tool;
    }

    // =========================================================================
    //  Picareta Quebra-Bedrock — conserto na bigorna
    // =========================================================================

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack pick = inv.getItem(0);
        ItemStack material = inv.getItem(1);
        if (!FuckBedrocksItems.isBedrockPickaxe(pick) || material == null
                || material.getType() != Material.NETHER_STAR) {
            return;
        }
        ItemMeta meta = pick.getItemMeta();
        if (!(meta instanceof Damageable) || ((Damageable) meta).getDamage() <= 0) {
            event.setResult(null); // nada a consertar
            return;
        }

        ItemStack result = pick.clone();
        ItemMeta resultMeta = result.getItemMeta();
        ((Damageable) resultMeta).setDamage(0);
        String rename = inv.getRenameText();
        if (rename != null && !rename.isEmpty()) {
            resultMeta.setDisplayName(rename);
        }
        result.setItemMeta(resultMeta);

        event.setResult(result);
        inv.setRepairCost(REPAIR_LEVEL_COST); // exibe o custo na bigorna
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
        ItemStack material = inv.getItem(1);
        // só assumimos quando é a nossa receita (picareta + estrela do nether)
        if (!FuckBedrocksItems.isBedrockPickaxe(result) || material == null
                || material.getType() != Material.NETHER_STAR) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative && player.getLevel() < REPAIR_LEVEL_COST) {
            player.sendMessage(ChatColor.RED + "Você precisa de " + REPAIR_LEVEL_COST
                    + " níveis de XP para consertar a Picareta Quebra-Bedrock.");
            return;
        }

        ItemStack repaired = result.clone();

        // consome as entradas
        inv.setItem(0, null);
        int remaining = material.getAmount() - 1;
        if (remaining <= 0) {
            inv.setItem(1, null);
        } else {
            material.setAmount(remaining);
            inv.setItem(1, material);
        }
        if (!creative) {
            player.setLevel(player.getLevel() - REPAIR_LEVEL_COST);
        }

        // entrega o resultado
        if (event.isShiftClick()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(repaired);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        } else {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                player.setItemOnCursor(repaired);
            } else {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(repaired);
                for (ItemStack rest : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.updateInventory();
    }

    // =========================================================================
    //  TNT Nuclear
    // =========================================================================

    @EventHandler(ignoreCancelled = true)
    public void onNukePlace(BlockPlaceEvent event) {
        if (FuckBedrocksItems.isNukeTnt(event.getItemInHand())) {
            pendingNukes.add(key(event.getBlockPlaced().getLocation()));
        }
    }

    /** Limpa a marca se o jogador quebrar o TNT nuclear antes de ativá-lo. */
    @EventHandler
    public void onNukePickup(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.TNT) {
            pendingNukes.remove(key(event.getBlock().getLocation()));
        }
    }

    /**
     * Fabricar um carrinho de TNT usando o TNT Nuclear como ingrediente produz
     * o Carrinho com TNT Nuclear (a receita do vanilla casa por material, então
     * trocamos o resultado quando o TNT usado é o nuclear).
     */
    @EventHandler
    public void onNukeCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType() != Material.TNT_MINECART) {
            return;
        }
        for (ItemStack ingredient : inv.getMatrix()) {
            if (FuckBedrocksItems.isNukeTnt(ingredient)) {
                inv.setResult(FuckBedrocksItems.createNukeMinecart());
                return;
            }
        }
    }

    /** Coloca um Carrinho com TNT Nuclear no trilho: marca a posição para taguear a entidade. */
    @EventHandler
    public void onNukeMinecartPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!FuckBedrocksItems.isNukeMinecart(event.getItem())) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && Tag.RAILS.isTagged(clicked.getType())) {
            pendingNukeMinecarts.add(key(clicked.getLocation()));
        }
    }

    /**
     * Dispenser com TNT Nuclear: ativa um TNT Nuclear na frente (em vez de
     * cuspir o item), igual a acender um TNT — útil para canhões. Com o Carrinho
     * com TNT Nuclear, deixa o vanilla colocá-lo no trilho e só marca a posição.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.DISPENSER || !(block.getBlockData() instanceof Directional)) {
            return;
        }
        BlockFace face = ((Directional) block.getBlockData()).getFacing();
        ItemStack item = event.getItem();

        if (FuckBedrocksItems.isNukeTnt(item)) {
            event.setCancelled(true); // não cuspe o item; ative na frente
            consumeOne(block, true);
            Location spawn = block.getRelative(face).getLocation().add(0.5, 0.5, 0.5);
            World world = block.getWorld();
            TNTPrimed primed = world.spawn(spawn, TNTPrimed.class);
            FuckBedrocksItems.tagNukeEntity(primed);
            world.playSound(spawn, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        } else if (FuckBedrocksItems.isNukeMinecart(item)) {
            Block front = block.getRelative(face);
            if (Tag.RAILS.isTagged(front.getType())) {
                pendingNukeMinecarts.add(key(front.getLocation()));
            }
        }
    }

    /** Remove 1 unidade do item (nuclear) do baú do dispenser. */
    private void consumeOne(Block dispenser, boolean tnt) {
        if (!(dispenser.getState() instanceof Container)) {
            return;
        }
        Inventory inv = ((Container) dispenser.getState()).getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            boolean match = tnt ? FuckBedrocksItems.isNukeTnt(stack) : FuckBedrocksItems.isNukeMinecart(stack);
            if (match) {
                int amount = stack.getAmount() - 1;
                if (amount <= 0) {
                    inv.setItem(i, null);
                } else {
                    stack.setAmount(amount);
                    inv.setItem(i, stack);
                }
                return;
            }
        }
    }

    /** Quando um TNT/carrinho nuclear é ativado, transfere a marca para a entidade. */
    @EventHandler
    public void onNukeEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        String k = key(entity.getLocation());
        if (entity instanceof TNTPrimed) {
            if (pendingNukes.remove(k)) {
                FuckBedrocksItems.tagNukeEntity(entity);
            }
        } else if (entity instanceof ExplosiveMinecart) {
            if (pendingNukeMinecarts.remove(k)) {
                FuckBedrocksItems.tagNukeEntity(entity);
            }
        }
    }

    /** Aumenta a explosão da nuclear para 10x a de um TNT comum. */
    @EventHandler
    public void onNukeExplosionPrime(ExplosionPrimeEvent event) {
        if (!FuckBedrocksItems.isNukeEntity(event.getEntity())) {
            return;
        }
        event.setRadius(NUKE_POWER);
    }

    /** Quebra a bedrock e mata todo mundo dentro do raio da nuclear. */
    @EventHandler(ignoreCancelled = true)
    public void onNukeExplode(EntityExplodeEvent event) {
        if (!FuckBedrocksItems.isNukeEntity(event.getEntity())) {
            return;
        }
        Location center = event.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int r = (int) Math.ceil(NUKE_EFFECT_RADIUS);
        double rSq = NUKE_EFFECT_RADIUS * NUKE_EFFECT_RADIUS;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // destrói toda a bedrock dentro do raio (o vanilla nunca quebra bedrock)
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (block.getType() == Material.BEDROCK) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        // mata instantaneamente todo ser vivo dentro do raio
        for (Entity entity : world.getNearbyEntities(center,
                NUKE_EFFECT_RADIUS, NUKE_EFFECT_RADIUS, NUKE_EFFECT_RADIUS)) {
            if (!(entity instanceof LivingEntity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > rSq) {
                continue;
            }
            if (entity instanceof Player) {
                GameMode mode = ((Player) entity).getGameMode();
                if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                    continue;
                }
            }
            LivingEntity living = (LivingEntity) entity;
            if (living.isInvulnerable()) {
                continue;
            }
            living.setHealth(0.0);
        }
    }
}
