package skibidilandia.minemagic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comportamento do Cajado do Necromante.
 *
 * Invocação:
 *  - Botão direito invoca {@link #MINION_COUNT} zumbis com armadura e espada de
 *    ferro ao redor do invocador. O capacete de ferro evita que peguem fogo no
 *    dia. Cada zumbi guarda no PDC o UUID do dono.
 *  - Há recarga ({@link #COOLDOWN_MS}) e um teto de zumbis vivos por dono
 *    ({@link #MAX_MINIONS}) para evitar exércitos infinitos. Os zumbis somem
 *    sozinhos após {@link #LIFESPAN_MS}.
 *
 * "Não atacam o invocador":
 *  - {@link EntityTargetLivingEntityEvent} cancela qualquer mira de um zumbi-servo
 *    no próprio dono (ou em outro servo do mesmo dono).
 *  - {@link EntityDamageByEntityEvent} cancela qualquer dano de um servo no dono
 *    (rede de segurança caso a mira escape) e entre servos do mesmo dono.
 *
 * "Ajudam na batalha":
 *  - Quando o dono fere algo, todos os seus servos passam a mirar nesse alvo.
 *  - Quando algo fere o dono, os servos passam a mirar no agressor.
 *
 * Uma varredura periódica remove servos expirados/órfãos e desfaz qualquer mira
 * no dono que tenha escapado.
 */
public class NecromancerListeners implements Listener {

    private static final int MINION_COUNT = 3;
    private static final int MAX_MINIONS = 6;
    private static final long COOLDOWN_MS = 30_000L;
    private static final long LIFESPAN_MS = 120_000L;
    /** Raio (blocos) em que os zumbis nascem ao redor do invocador. */
    private static final double SPAWN_RADIUS = 1.6D;

    private final JavaPlugin plugin;
    private final org.bukkit.NamespacedKey ownerKey;
    private final org.bukkit.NamespacedKey minionKey;

    /** Servos vivos por dono: dono -> (servo -> instante de invocação ms). */
    private final Map<UUID, Map<UUID, Long>> minionsByOwner = new HashMap<>();
    /** Recarga do cajado por jogador (instante em que libera). */
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    private BukkitTask sweepTask;

    public NecromancerListeners(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new org.bukkit.NamespacedKey(plugin, "necro_owner");
        this.minionKey = new org.bukkit.NamespacedKey(plugin, "necro_minion");
    }

    /** Inicia a varredura periódica e limpa servos órfãos de execuções anteriores. */
    public void start() {
        purgeAllMinions();
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
    }

    // =========================================================================
    //  Invocação
    // =========================================================================

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!MineMagicItems.isNecromancerStaff(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        summon(player);
    }

    private void summon(Player player) {
        UUID ownerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long until = cooldownUntil.get(ownerId);
        if (until != null && now < until) {
            long secs = (until - now + 999) / 1000;
            player.sendMessage(org.bukkit.ChatColor.RED + "O Cajado do Necromante recarrega em " + secs + "s.");
            return;
        }

        Map<UUID, Long> mine = minionsByOwner.computeIfAbsent(ownerId, k -> new HashMap<>());
        int free = MAX_MINIONS - mine.size();
        if (free <= 0) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Você já tem zumbis demais invocados.");
            return;
        }
        int toSpawn = Math.min(MINION_COUNT, free);

        World world = player.getWorld();
        Location base = player.getLocation();
        for (int i = 0; i < toSpawn; i++) {
            double angle = (2 * Math.PI * i) / toSpawn;
            Location loc = base.clone().add(Math.cos(angle) * SPAWN_RADIUS, 0, Math.sin(angle) * SPAWN_RADIUS);
            Zombie zombie = spawnMinion(world, loc, player);
            mine.put(zombie.getUniqueId(), now);
        }

        cooldownUntil.put(ownerId, now + COOLDOWN_MS);
        world.playSound(base, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.6f);
        world.spawnParticle(Particle.SOUL, base.clone().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.02);
        player.sendMessage(org.bukkit.ChatColor.DARK_PURPLE + "Você invocou " + toSpawn + " zumbi(s) necromante(s).");
    }

    /** Cria e configura um zumbi-servo equipado, marcado com o dono. */
    private Zombie spawnMinion(World world, Location loc, Player owner) {
        return world.spawn(loc, Zombie.class, zombie -> {
            zombie.setBaby(false);
            zombie.setShouldBurnInDay(false);
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(false);

            EntityEquipment eq = zombie.getEquipment();
            eq.setHelmet(new ItemStack(Material.IRON_HELMET));
            eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            eq.setBoots(new ItemStack(Material.IRON_BOOTS));
            eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            // nada do equipamento cai ao morrer
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);

            zombie.customName(Component.text("Zumbi do Necromante").color(NamedTextColor.DARK_GREEN));
            zombie.setCustomNameVisible(true);

            PersistentDataContainer pdc = zombie.getPersistentDataContainer();
            pdc.set(minionKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());

            // começa ajudando: mira no alvo atual do dono, se houver
            Entity looked = owner.getTargetEntity(20);
            if (looked instanceof LivingEntity && !isMinionOf(looked, owner.getUniqueId())) {
                zombie.setTarget((LivingEntity) looked);
            }
        });
    }

    // =========================================================================
    //  Proteção do invocador
    // =========================================================================

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!isMinion(event.getEntity())) {
            return;
        }
        LivingEntity target = event.getTarget();
        if (target == null) {
            return;
        }
        UUID ownerId = getOwnerId(event.getEntity());
        if (ownerId == null) {
            return;
        }
        // nunca o dono, nem outro servo do mesmo dono
        if (target.getUniqueId().equals(ownerId) || isMinionOf(target, ownerId)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        // Servo nunca fere o dono nem outro servo do mesmo dono.
        if (isMinion(damager)) {
            UUID ownerId = getOwnerId(damager);
            if (ownerId != null && (victim.getUniqueId().equals(ownerId) || isMinionOf(victim, ownerId))) {
                event.setCancelled(true);
                return;
            }
        }

        // Dono ataca algo -> servos miram no alvo.
        Player attackerOwner = resolveOwnerPlayer(damager);
        if (attackerOwner != null && victim instanceof LivingEntity
                && !victim.getUniqueId().equals(attackerOwner.getUniqueId())
                && !isMinionOf(victim, attackerOwner.getUniqueId())) {
            commandMinions(attackerOwner.getUniqueId(), (LivingEntity) victim);
        }

        // Dono é atacado -> servos miram no agressor.
        if (victim instanceof Player && minionsByOwner.containsKey(victim.getUniqueId())) {
            Entity root = resolveRootDamager(damager);
            if (root instanceof LivingEntity
                    && !root.getUniqueId().equals(victim.getUniqueId())
                    && !isMinionOf(root, victim.getUniqueId())) {
                commandMinions(victim.getUniqueId(), (LivingEntity) root);
            }
        }
    }

    /** Manda todos os servos vivos do dono mirarem em {@code target}. */
    private void commandMinions(UUID ownerId, LivingEntity target) {
        Map<UUID, Long> mine = minionsByOwner.get(ownerId);
        if (mine == null) {
            return;
        }
        for (UUID minionId : mine.keySet()) {
            Entity e = plugin.getServer().getEntity(minionId);
            if (e instanceof Zombie && e.isValid()) {
                ((Zombie) e).setTarget(target);
            }
        }
    }

    // =========================================================================
    //  Varredura periódica
    // =========================================================================

    private void sweep() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Long>> entry : new ArrayList<>(minionsByOwner.entrySet())) {
            UUID ownerId = entry.getKey();
            Player owner = plugin.getServer().getPlayer(ownerId);
            boolean ownerOffline = owner == null || !owner.isOnline();

            Map<UUID, Long> mine = entry.getValue();
            for (UUID minionId : new ArrayList<>(mine.keySet())) {
                Entity e = plugin.getServer().getEntity(minionId);
                if (!(e instanceof Zombie) || !e.isValid()) {
                    mine.remove(minionId);
                    continue;
                }
                Zombie zombie = (Zombie) e;
                long age = now - mine.get(minionId);
                if (ownerOffline || age > LIFESPAN_MS) {
                    removeMinion(zombie);
                    mine.remove(minionId);
                    continue;
                }
                // rede de segurança: nunca deixe um servo mirando no dono
                if (zombie.getTarget() != null && zombie.getTarget().getUniqueId().equals(ownerId)) {
                    zombie.setTarget(null);
                }
            }
            if (mine.isEmpty()) {
                minionsByOwner.remove(ownerId);
            }
        }
    }

    /** Remove um servo com um pequeno efeito visual. */
    private void removeMinion(Zombie zombie) {
        World world = zombie.getWorld();
        world.spawnParticle(Particle.SMOKE, zombie.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.01);
        zombie.remove();
    }

    // =========================================================================
    //  Utilidades de identidade
    // =========================================================================

    private boolean isMinion(Entity entity) {
        if (!(entity instanceof Zombie)) {
            return false;
        }
        return entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);
    }

    private UUID getOwnerId(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isMinionOf(Entity entity, UUID ownerId) {
        return isMinion(entity) && ownerId.equals(getOwnerId(entity));
    }

    /** Se a entidade (ou o atirador, se projétil) for um jogador com servos, devolve-o. */
    private Player resolveOwnerPlayer(Entity damager) {
        Entity root = resolveRootDamager(damager);
        if (root instanceof Player && minionsByOwner.containsKey(root.getUniqueId())) {
            return (Player) root;
        }
        return null;
    }

    /** Resolve a fonte real do dano: o atirador, se for um projétil. */
    private Entity resolveRootDamager(Entity damager) {
        if (damager instanceof Projectile) {
            Object shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity) {
                return (Entity) shooter;
            }
        }
        return damager;
    }

    // =========================================================================
    //  Limpeza
    // =========================================================================

    /** Remove TODOS os zumbis-servos carregados (órfãos de execuções anteriores). */
    private void purgeAllMinions() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                if (isMinion(zombie)) {
                    zombie.remove();
                }
            }
        }
        minionsByOwner.clear();
    }

    /** Cancela a varredura e remove todos os servos (chamado no desligamento). */
    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        purgeAllMinions();
    }
}
