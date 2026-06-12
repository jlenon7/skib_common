package skibidilandia.tnttools;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Comportamento dos itens do TNTTools.
 *
 * Armadura TNT:
 *  - Agachar duas vezes rapidamente (shift duplo) dispara 1 explosão por peça da
 *    armadura vestida (conjunto completo = 4). O dono não é ferido. Usamos shift
 *    duplo (e não um único agachar) porque agachar é comum demais e dispararia
 *    explosões sem querer. Há um cooldown curto para evitar spam.
 *
 * Conserto (armadura):
 *  - Só na bigorna, com 1 Cristal do End. Restaura 10% da durabilidade máxima e
 *    custa exatamente 10 níveis de XP. O preço e o consumo são tratados na mão.
 *
 * Blindagem do dono:
 *  - {@code createExplosion} é síncrono: os eventos de dano disparam dentro da
 *    própria chamada. Marcamos o dono como imune logo antes de explodir e
 *    desmarcamos logo depois; em {@link EntityDamageEvent} cancelamos o dano de
 *    explosão para quem estiver marcado.
 */
public class TNTToolsListeners implements Listener {

    /** Potência de cada explosão (TNT comum = 4.0). */
    private static final float EXPLOSION_POWER = 3.0f;
    private static final boolean EXPLOSION_SET_FIRE = false;
    private static final boolean EXPLOSION_BREAK_BLOCKS = true;
    /** Atraso (ticks) entre explosões encadeadas, para um efeito sequencial. */
    private static final long EXPLOSION_DELAY_TICKS = 4L;

    /** Conserto na bigorna. */
    private static final int REPAIR_LEVEL_COST = 10;
    private static final double REPAIR_PERCENT = 0.10;

    /** Cooldown da habilidade da armadura (ms). */
    private static final long ARMOR_COOLDOWN_MS = 1500L;
    /** Durabilidade gasta em cada peça da armadura a cada uso da habilidade. */
    private static final int ARMOR_ABILITY_WEAR = 5;
    /** Janela (ms) para o segundo agachar contar como "shift duplo". */
    private static final long DOUBLE_SNEAK_MS = 400L;

    private final JavaPlugin plugin;

    /** Jogadores imunes à explosão neste instante (donos da explosão em curso). */
    private final Set<UUID> explosionImmune = new HashSet<>();
    /** Último uso da habilidade da armadura por jogador. */
    private final Map<UUID, Long> armorCooldown = new java.util.HashMap<>();
    /** Instante do último agachar (início) por jogador, para detectar shift duplo. */
    private final Map<UUID, Long> lastSneak = new java.util.HashMap<>();

    public TNTToolsListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Armadura TNT — habilidade no shift duplo (agachar 2x rápido)
    // =========================================================================

    @EventHandler
    public void onArmorAbility(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return; // só nos importamos com o início do agachar, não o soltar
        }
        Player player = event.getPlayer();
        int pieces = countTntArmor(player);
        if (pieces <= 0) {
            return; // sem armadura TNT: agachar é só agachar
        }

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();

        Long previousSneak = lastSneak.get(id);
        if (previousSneak == null || now - previousSneak > DOUBLE_SNEAK_MS) {
            lastSneak.put(id, now); // primeiro agachar: arma o gatilho
            return;
        }
        // segundo agachar dentro da janela: dispara a habilidade
        lastSneak.remove(id);

        Long lastUse = armorCooldown.get(id);
        if (lastUse != null && now - lastUse < ARMOR_COOLDOWN_MS) {
            return;
        }
        armorCooldown.put(id, now);

        Location at = player.getLocation().add(0, 1, 0);
        explodeProtectingOwner(player, at, pieces);
        wearArmor(player);
    }

    /** Gasta durabilidade de cada peça TNT vestida; remove a que quebrar. */
    private static void wearArmor(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(damageArmorPiece(inv.getHelmet(), player));
        inv.setChestplate(damageArmorPiece(inv.getChestplate(), player));
        inv.setLeggings(damageArmorPiece(inv.getLeggings(), player));
        inv.setBoots(damageArmorPiece(inv.getBoots(), player));
    }

    /**
     * Aplica {@link #ARMOR_ABILITY_WEAR} de dano a uma peça TNT (respeitando
     * Inquebrável). Retorna a peça atualizada, ou {@code null} se ela quebrou.
     * Peças que não são da Armadura TNT passam intactas.
     */
    private static ItemStack damageArmorPiece(ItemStack piece, Player player) {
        if (!TNTToolsItems.isTntArmor(piece)) {
            return piece;
        }
        ItemMeta meta = piece.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return piece;
        }
        int wear = ARMOR_ABILITY_WEAR;
        int unbreaking = piece.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0) {
            int survived = 0;
            for (int i = 0; i < wear; i++) {
                if (ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0) {
                    survived++;
                }
            }
            wear = survived;
        }
        if (wear <= 0) {
            return piece;
        }
        Damageable damageable = (Damageable) meta;
        int newDamage = damageable.getDamage() + wear;
        if (newDamage >= piece.getType().getMaxDurability()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return null; // a peça quebrou
        }
        damageable.setDamage(newDamage);
        piece.setItemMeta(meta);
        return piece;
    }

    /** Conta quantas peças da Armadura TNT o jogador está vestindo (0-4). */
    private static int countTntArmor(Player player) {
        PlayerInventory inv = player.getInventory();
        int count = 0;
        if (TNTToolsItems.isTntArmor(inv.getHelmet())) count++;
        if (TNTToolsItems.isTntArmor(inv.getChestplate())) count++;
        if (TNTToolsItems.isTntArmor(inv.getLeggings())) count++;
        if (TNTToolsItems.isTntArmor(inv.getBoots())) count++;
        return count;
    }

    // =========================================================================
    //  Explosões com o dono blindado
    // =========================================================================

    /** Agenda {@code count} explosões em {@code loc}, sem ferir {@code owner}. */
    private void explodeProtectingOwner(Player owner, Location loc, int count) {
        for (int i = 0; i < count; i++) {
            if (i == 0) {
                doExplosion(owner, loc);
            } else {
                plugin.getServer().getScheduler().runTaskLater(
                        plugin, () -> doExplosion(owner, loc), i * EXPLOSION_DELAY_TICKS);
            }
        }
    }

    /** Uma explosão síncrona com o dono marcado como imune durante a chamada. */
    private void doExplosion(Player owner, Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        UUID id = owner.getUniqueId();
        boolean added = explosionImmune.add(id);
        try {
            world.createExplosion(loc, EXPLOSION_POWER, EXPLOSION_SET_FIRE, EXPLOSION_BREAK_BLOCKS, owner);
        } finally {
            if (added) {
                explosionImmune.remove(id);
            }
        }
    }

    /** Cancela o dano de explosão para o dono da explosão em curso. */
    @EventHandler(ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        if (explosionImmune.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    //  Conserto na bigorna — 1 Cristal do End, +10% durab., 10 níveis de XP
    // =========================================================================

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack tool = inv.getItem(0);
        ItemStack material = inv.getItem(1);
        if (!TNTToolsItems.isTntTool(tool) || material == null
                || material.getType() != Material.END_CRYSTAL) {
            return;
        }
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable) || ((Damageable) meta).getDamage() <= 0) {
            event.setResult(null); // nada a consertar
            return;
        }

        ItemStack result = tool.clone();
        ItemMeta resultMeta = result.getItemMeta();
        Damageable damageable = (Damageable) resultMeta;
        int restore = (int) Math.ceil(tool.getType().getMaxDurability() * REPAIR_PERCENT);
        damageable.setDamage(Math.max(0, damageable.getDamage() - restore));
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
        // só assumimos quando é a nossa receita (item TNT + cristal do end)
        if (!TNTToolsItems.isTntTool(result) || material == null
                || material.getType() != Material.END_CRYSTAL) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative && player.getLevel() < REPAIR_LEVEL_COST) {
            player.sendMessage(ChatColor.RED + "Você precisa de " + REPAIR_LEVEL_COST
                    + " níveis de XP para consertar com o Cristal do End.");
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
            deliver(player, repaired);
        } else {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                player.setItemOnCursor(repaired);
            } else {
                deliver(player, repaired);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.updateInventory();
    }

    private static void deliver(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }
}
