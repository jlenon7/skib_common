package skibidilandia.minemagic;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comportamento do Cajado de Cura.
 *
 * Seleção de alvos (somente jogadores):
 *  - Bater (golpe melee) em um JOGADOR com o cajado o marca/desmarca como alvo.
 *    O golpe não causa dano (cancelamos o {@link EntityDamageByEntityEvent}); ele
 *    só serve para selecionar. A lista de alvos é gravada no próprio cajado (PDC)
 *    e os nomes aparecem na descrição do item.
 *
 * Cura em rajada (segurar botão direito):
 *  - Enquanto a mão estiver levantada ({@link Player#isHandRaised()}) e o cajado
 *    na mão principal, todos os alvos selecionados que estiverem dentro de uma
 *    caixa de 30x30x30 blocos ao redor do jogador são curados a cada
 *    {@link #HEAL_INTERVAL_TICKS}.
 *
 * Limpar alvos (agachar 3x rápido):
 *  - Apertar shift 3 vezes dentro de {@link #TRIPLE_SNEAK_MS} com o cajado na mão
 *    remove todos os alvos selecionados de uma vez.
 *
 * Não arremessar o tridente:
 *  - Cancelamos o {@link ProjectileLaunchEvent} de quem segura o Cajado de Cura.
 */
public class HealStaffListeners implements Listener {

    /** Meia-aresta da caixa de alcance da cura (15 => caixa de 30x30x30 blocos). */
    private static final double RANGE = 15.0D;

    /** Intervalo (ticks) entre pulsos de cura enquanto segura o botão. */
    private static final long HEAL_INTERVAL_TICKS = 10L;
    /** Carência inicial (ticks) antes de exigir a mão levantada. */
    private static final int RAISE_GRACE_TICKS = 4;
    /** Vida restaurada por pulso (2.0 = 1 coração). */
    private static final double HEAL_AMOUNT = 2.0D;

    /** Janela (ms) para contar os 3 agachares que limpam os alvos. */
    private static final long TRIPLE_SNEAK_MS = 1000L;

    private final JavaPlugin plugin;

    /** Jogadores com um pulso de cura em curso. */
    private final Map<UUID, BukkitRunnable> healing = new HashMap<>();
    /** Horários dos agachares recentes de cada jogador (para o gatilho triplo). */
    private final Map<UUID, long[]> sneakTimes = new HashMap<>();

    public HealStaffListeners(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Seleção de alvos (bater num jogador com o cajado)
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
        if (!MineMagicItems.isHealStaff(staff)) {
            return;
        }
        // O Cajado de Cura nunca machuca; o golpe só seleciona.
        event.setCancelled(true);

        if (!(event.getEntity() instanceof Player)) {
            player.sendMessage(ChatColor.RED + "O Cajado de Cura só seleciona " + ChatColor.WHITE + "jogadores" + ChatColor.RED + ".");
            return;
        }
        Player target = (Player) event.getEntity();
        if (target.equals(player)) {
            return; // não dá para bater em si mesmo, mas por garantia
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
    //  Limpar alvos (agachar 3x rápido)
    // =========================================================================

    @EventHandler
    public void onTripleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return; // só o início do agachar conta
        }
        Player player = event.getPlayer();
        ItemStack staff = player.getInventory().getItemInMainHand();
        if (!MineMagicItems.isHealStaff(staff)) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        long[] times = sneakTimes.computeIfAbsent(id, k -> new long[3]);
        // desliza a janela: [t-2, t-1, t-0]
        times[0] = times[1];
        times[1] = times[2];
        times[2] = now;

        if (times[0] != 0L && now - times[0] <= TRIPLE_SNEAK_MS) {
            sneakTimes.remove(id); // consumiu os 3 agachares
            clearTargets(player, staff);
        }
    }

    /** Remove todos os alvos do cajado, se houver algum. */
    private void clearTargets(Player player, ItemStack staff) {
        if (MineMagicItems.getHealTargets(staff).isEmpty()) {
            return; // nada para limpar; evita spam
        }
        MineMagicItems.setHealTargets(staff, new ArrayList<>(), new ArrayList<>());
        player.getInventory().setItemInMainHand(staff);
        player.sendMessage(ChatColor.YELLOW + "Todos os alvos do Cajado de Cura foram " + ChatColor.RED + "removidos" + ChatColor.YELLOW + ".");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    // =========================================================================
    //  Início da cura (segurar botão direito)
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
        if (!MineMagicItems.isHealStaff(player.getInventory().getItemInMainHand())) {
            return;
        }
        startHealing(player);
    }

    /** Arma a tarefa de cura para o jogador, se ainda não houver uma. */
    private void startHealing(Player player) {
        UUID id = player.getUniqueId();
        if (healing.containsKey(id)) {
            return; // já está curando
        }
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Player online = plugin.getServer().getPlayer(id);
                if (online == null || !online.isOnline()
                        || !MineMagicItems.isHealStaff(online.getInventory().getItemInMainHand())) {
                    stopHealing(id);
                    return;
                }
                // Após a carência, soltar o botão (mão abaixada) encerra a cura.
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

    /**
     * Cura os jogadores-alvo do cajado que estiverem dentro da caixa de
     * {@link #RANGE} ao redor de quem usa. Alvos offline ou fora de alcance são
     * apenas pulados (continuam selecionados).
     */
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
                continue; // fora da caixa de 30x30x30
            }
            double max = maxHealth(target);
            double current = target.getHealth();
            if (current >= max) {
                continue; // já está com a vida cheia
            }
            target.setHealth(Math.min(max, current + HEAL_AMOUNT));
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1, 0), 2, 0.3, 0.4, 0.3, 0);
            healedAny = true;
        }
        if (healedAny) {
            player.getWorld().playSound(center, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.6f);
        }
    }

    /** True se {@code loc} está dentro da caixa de meia-aresta {@link #RANGE} em torno de {@code center}. */
    private static boolean inRange(Location center, Location loc) {
        if (center.getWorld() == null || !center.getWorld().equals(loc.getWorld())) {
            return false;
        }
        return Math.abs(loc.getX() - center.getX()) <= RANGE
                && Math.abs(loc.getY() - center.getY()) <= RANGE
                && Math.abs(loc.getZ() - center.getZ()) <= RANGE;
    }

    /** Vida máxima do alvo (via atributo; cai para a vida atual se não houver atributo). */
    private static double maxHealth(Player entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : entity.getHealth();
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
        if (MineMagicItems.isHealStaff(shooter.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /** Cancela todas as curas em curso (chamado no desligamento). */
    public void shutdown() {
        for (Iterator<BukkitRunnable> it = healing.values().iterator(); it.hasNext(); ) {
            it.next().cancel();
            it.remove();
        }
        sneakTimes.clear();
    }
}
