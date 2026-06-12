package skibidilandia.minemagic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cajado do Curandeiro — habilidades guardadas no PDC, trocadas pelas teclas
 * numéricas, sem recarga:
 *
 * <ul>
 *   <li><b>Tecla 1 — Cura</b>: bater (golpe melee) em um jogador o marca/desmarca
 *       como alvo; segurar o botão direito cura os alvos dentro de 30x30x30 blocos.</li>
 *   <li><b>Tecla 2 — Gravidade</b>: botão direito puxa tudo até você; {@code shift +
 *       botão direito} repele tudo, numa caixa de 10x10x10 blocos.</li>
 *   <li><b>Tecla 3 — Limpar alvos</b>: esvazia a lista de cura (não troca de modo).</li>
 * </ul>
 *
 * <p>A troca usa o {@link PlayerItemHeldEvent}: ao apertar a tecla 1, 2 ou 3 com o
 * cajado em mãos o evento é cancelado (o cajado continua selecionado) e apenas a
 * habilidade muda. O arremesso do tridente é sempre bloqueado.
 */
public class HealerStaffListeners implements Listener {

    // --- Cura ---
    private static final double HEAL_RANGE = 15.0D; // meia-aresta => caixa de 30x30x30
    private static final long HEAL_INTERVAL_TICKS = 10L;
    private static final int RAISE_GRACE_TICKS = 4;
    private static final double HEAL_AMOUNT = 2.0D; // 2.0 = 1 coração por pulso

    // --- Gravidade ---
    private static final double GRAVITY_RANGE = 5.0D; // meia-aresta => caixa de 10x10x10
    private static final double PULL_HORIZONTAL = 0.9D;
    private static final double PULL_UP = 0.55D;
    private static final double PUSH_HORIZONTAL = 1.3D;
    private static final double PUSH_UP = 0.6D;

    private final JavaPlugin plugin;

    /** Jogadores com um pulso de cura em curso. */
    private final Map<UUID, BukkitRunnable> healing = new HashMap<>();

    public HealerStaffListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Troca de habilidade (teclas 1, 2 e 3)
    // =========================================================================

    @EventHandler
    public void onSwapAbility(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack staff = player.getInventory().getItem(event.getPreviousSlot());
        if (!MineMagicItems.isHealerStaff(staff)) {
            return; // não está com o cajado em mãos: troca normal de slot
        }
        int slot = event.getNewSlot();
        if (slot > 2) {
            return; // teclas 4-9: deixa trocar de item normalmente
        }
        if (isScrollStep(event.getPreviousSlot(), slot)) {
            return; // roleta do mouse: deixa navegar livremente até os slots baixos
        }
        // Salto direto (tecla 1/2/3): mantém o cajado e só escolhe a habilidade.
        event.setCancelled(true);
        if (slot == 2) {
            clearTargets(player, staff); // tecla 3: limpar alvos (não troca de modo)
            return;
        }
        boolean gravity = slot == 1;
        String mode = gravity ? MineMagicItems.HEALER_GRAVITY : MineMagicItems.HEALER_HEAL;
        if (mode.equals(MineMagicItems.getHealerMode(staff))) {
            return; // já está nesse modo
        }
        MineMagicItems.setHealerMode(staff, mode);
        player.getInventory().setItem(event.getPreviousSlot(), staff);
        stopHealing(player.getUniqueId()); // trocar de modo encerra o pulso em curso
        player.sendActionBar(Component.text("Modo: ", NamedTextColor.GREEN)
                .append(Component.text(gravity ? "Gravidade" : "Cura", NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    /**
     * Distingue um passo de roleta (slot adjacente, com wrap 0↔8) de um salto
     * direto por tecla numérica. O servidor não diz qual input gerou o evento,
     * mas a roleta sempre move ±1 slot — então deixamos passar os passos de ±1
     * (navegação livre) e só os saltos trocam de habilidade.
     */
    private static boolean isScrollStep(int prev, int now) {
        int step = now - prev;
        if (step > 4) {
            step -= 9;
        } else if (step < -4) {
            step += 9;
        }
        return Math.abs(step) == 1;
    }

    // =========================================================================
    //  Cura: seleção de alvos (bater num jogador, só no modo Cura)
    // =========================================================================

    @EventHandler(ignoreCancelled = true)
    public void onSelectHit(EntityDamageByEntityEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getDamager();
        ItemStack staff = player.getInventory().getItemInMainHand();
        if (!MineMagicItems.isHealerStaff(staff)
                || !MineMagicItems.HEALER_HEAL.equals(MineMagicItems.getHealerMode(staff))) {
            return; // no modo Gravidade o golpe é um ataque normal
        }
        // No modo Cura o cajado nunca machuca; o golpe só seleciona.
        event.setCancelled(true);

        if (!(event.getEntity() instanceof Player)) {
            player.sendMessage(ChatColor.RED + "O modo Cura só seleciona " + ChatColor.WHITE + "jogadores" + ChatColor.RED + ".");
            return;
        }
        Player target = (Player) event.getEntity();
        if (target.equals(player)) {
            return;
        }

        List<UUID> ids = MineMagicItems.getHealTargets(staff);
        boolean removed = ids.remove(target.getUniqueId());
        if (!removed) {
            ids.add(target.getUniqueId());
        }
        rewriteTargets(player, staff, ids);

        if (removed) {
            player.sendMessage(ChatColor.YELLOW + "Alvo removido: " + ChatColor.WHITE + target.getName());
            player.getWorld().playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        } else {
            player.sendMessage(ChatColor.GREEN + "Alvo selecionado: " + ChatColor.WHITE + target.getName());
            player.getWorld().playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.4f);
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
        }
    }

    /** Resolve os nomes dos UUIDs e grava a lista atualizada no cajado em mãos. */
    private void rewriteTargets(Player owner, ItemStack staff, List<UUID> ids) {
        List<String> names = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            names.add(resolveName(id));
        }
        MineMagicItems.setHealTargets(staff, ids, names);
        owner.getInventory().setItemInMainHand(staff);
    }

    /** Remove todos os alvos do cajado, se houver algum (tecla 3 / "Limpar alvos"). */
    private void clearTargets(Player player, ItemStack staff) {
        if (MineMagicItems.getHealTargets(staff).isEmpty()) {
            player.sendActionBar(Component.text("Nenhum alvo para limpar.", NamedTextColor.GRAY));
            return;
        }
        MineMagicItems.setHealTargets(staff, new ArrayList<>(), new ArrayList<>());
        player.getInventory().setItemInMainHand(staff);
        player.sendActionBar(Component.text("Todos os alvos foram removidos.", NamedTextColor.YELLOW));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    /** Nome de exibição de um UUID (online > offline > UUID curto). */
    private static String resolveName(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        String offline = Bukkit.getOfflinePlayer(id).getName();
        return offline != null ? offline : id.toString().substring(0, 8);
    }

    // =========================================================================
    //  Disparo (botão direito)
    // =========================================================================

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // só a mão principal; evita o disparo duplicado da off-hand
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack staff = player.getInventory().getItemInMainHand();
        if (!MineMagicItems.isHealerStaff(staff)) {
            return;
        }
        if (MineMagicItems.HEALER_GRAVITY.equals(MineMagicItems.getHealerMode(staff))) {
            if (player.isSneaking()) {
                repulse(player);
            } else {
                pull(player);
            }
        } else {
            startHealing(player); // modo Cura: não cancelamos (mão levantada controla o pulso)
        }
    }

    // =========================================================================
    //  Cura: pulso (segurar botão direito)
    // =========================================================================

    private void startHealing(Player player) {
        UUID id = player.getUniqueId();
        if (healing.containsKey(id)) {
            return;
        }
        BukkitRunnable task = new BukkitRunnable() {
            long ticks = 0L;

            @Override
            public void run() {
                Player online = plugin.getServer().getPlayer(id);
                if (online == null || !online.isOnline()) {
                    stopHealing(id);
                    return;
                }
                ItemStack hand = online.getInventory().getItemInMainHand();
                if (!MineMagicItems.isHealerStaff(hand)
                        || !MineMagicItems.HEALER_HEAL.equals(MineMagicItems.getHealerMode(hand))) {
                    stopHealing(id);
                    return;
                }
                if (ticks >= RAISE_GRACE_TICKS && !online.isHandRaised()) {
                    stopHealing(id);
                    return;
                }
                ticks += HEAL_INTERVAL_TICKS;
                healSelected(online);
            }
        };
        healing.put(id, task);
        task.runTaskTimer(plugin, 0L, HEAL_INTERVAL_TICKS);
    }

    private void stopHealing(UUID id) {
        BukkitRunnable task = healing.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    /** Cura os jogadores-alvo dentro da caixa de {@link #HEAL_RANGE} ao redor de quem usa. */
    private void healSelected(Player player) {
        ItemStack staff = player.getInventory().getItemInMainHand();
        List<UUID> ids = MineMagicItems.getHealTargets(staff);
        if (ids.isEmpty()) {
            return;
        }
        Location center = player.getLocation();
        boolean healedAny = false;
        for (UUID targetId : ids) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline() || target.isDead()) {
                continue; // offline/morto: mantém na lista
            }
            if (!inRange(center, target.getLocation())) {
                continue;
            }
            double max = maxHealth(target);
            double current = target.getHealth();
            if (current >= max) {
                continue;
            }
            target.setHealth(Math.min(max, current + HEAL_AMOUNT));
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1, 0), 2, 0.3, 0.4, 0.3, 0);
            healedAny = true;
        }
        if (healedAny) {
            player.getWorld().playSound(center, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.6f);
        }
    }

    private static boolean inRange(Location center, Location loc) {
        if (center.getWorld() == null || !center.getWorld().equals(loc.getWorld())) {
            return false;
        }
        return Math.abs(loc.getX() - center.getX()) <= HEAL_RANGE
                && Math.abs(loc.getY() - center.getY()) <= HEAL_RANGE
                && Math.abs(loc.getZ() - center.getZ()) <= HEAL_RANGE;
    }

    private static double maxHealth(Player entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : entity.getHealth();
    }

    // =========================================================================
    //  Gravidade
    // =========================================================================

    /** Arremessa para o alto e puxa todas as entidades próximas em direção ao jogador. */
    private void pull(Player player) {
        Location center = player.getLocation();
        for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(center, GRAVITY_RANGE, GRAVITY_RANGE, GRAVITY_RANGE)) {
            if (entity.equals(player)) {
                continue;
            }
            Vector horizontal = horizontalTowards(entity.getLocation(), center);
            entity.setVelocity(horizontal.multiply(PULL_HORIZONTAL).setY(PULL_UP));
        }
        player.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.6f);
    }

    /** Arremessa para o alto e empurra todas as entidades próximas para longe do jogador. */
    private void repulse(Player player) {
        Location center = player.getLocation();
        for (LivingEntity entity : player.getWorld().getNearbyLivingEntities(center, GRAVITY_RANGE, GRAVITY_RANGE, GRAVITY_RANGE)) {
            if (entity.equals(player)) {
                continue;
            }
            Vector horizontal = horizontalTowards(center, entity.getLocation());
            entity.setVelocity(horizontal.multiply(PUSH_HORIZONTAL).setY(PUSH_UP));
        }
        player.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.7f);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.8f);
    }

    /** Vetor horizontal unitário de {@code from} para {@code to} (zero se na mesma coluna). */
    private static Vector horizontalTowards(Location from, Location to) {
        Vector diff = new Vector(to.getX() - from.getX(), 0.0D, to.getZ() - from.getZ());
        if (diff.lengthSquared() < 1.0e-6D) {
            return new Vector(0.0D, 0.0D, 0.0D);
        }
        return diff.normalize();
    }

    // =========================================================================
    //  Bloqueia o arremesso do tridente
    // =========================================================================

    @EventHandler
    public void onTridentLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident)) {
            return;
        }
        Trident trident = (Trident) event.getEntity();
        if (!(trident.getShooter() instanceof Player)) {
            return;
        }
        Player shooter = (Player) trident.getShooter();
        if (MineMagicItems.isHealerStaff(shooter.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopHealing(event.getPlayer().getUniqueId());
    }

    /** Cancela tarefas em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = healing.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
    }
}
