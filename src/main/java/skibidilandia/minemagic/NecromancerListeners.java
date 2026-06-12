package skibidilandia.minemagic;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cajado do Necromante — necromante estilo Solo Leveling.
 *
 * <p>Coleta de almas: quando o dono mata um mob coletável ({@link MineMagicItems#soulTypeOf}),
 * estando com o cajado no inventário, uma alma do tipo canônico é somada no PDC do cajado.
 * Variantes contam pelo tipo base (husk → ZOMBIE, stray → SKELETON, cave spider → SPIDER).
 *
 * <p>Seleção (cajado na mão principal):
 * <ul>
 *   <li><b>Shift duplo</b> abre o menu vertical (boss bars empilhadas).</li>
 *   <li><b>Scroll</b> troca a alma; <b>botão direito</b> confirma e passa para a quantidade
 *       (limitada ao teto do tipo); <b>scroll</b> ajusta; <b>botão direito</b> salva.</li>
 *   <li>Com o menu fechado, <b>botão direito</b> invoca a seleção salva.</li>
 *   <li><b>F duplo</b> recolhe todos os servos vivos e devolve as almas ao cajado.</li>
 * </ul>
 *
 * <p>Invocação: gasta almas (uma por mob). Cada tipo tem um teto de vivos simultâneos
 * ({@link MineMagicItems#cap}); tipos diferentes coexistem. Servos normais somem após
 * {@link #LIFESPAN_MS}. São agressivos com tudo por perto — menos o invocador e os servos dele.
 *
 * <p>Persistente (cavalo): só um vivo, sem recarga e sem expirar; é tamed/saddled e montado ao
 * invocar. Some quando o dono invoca outro tipo ou aperta F duas vezes (devolvendo a alma).
 */
public class NecromancerListeners implements Listener {

    private static final int MAX_TOTAL = 200; // teto global de segurança por dono
    private static final int DEFAULT_QTY = 10; // quantidade pré-selecionada ao escolher a alma
    private static final long LIFESPAN_MS = 120_000L;
    private static final long NORMAL_COOLDOWN_MS = 2_000L; // anti-spam (persistentes isentos)
    private static final double SPAWN_RADIUS = 2.0D;
    private static final double AGGRO_RADIUS = 16.0D;
    // Gigante: mob sem IA na vanilla — é guiado manualmente (pathfinding + golpe corpo a corpo).
    private static final double GIANT_REACH = 7.0D;       // braços longos: alcance generoso
    private static final double GIANT_DAMAGE = 12.0D;
    private static final double GIANT_SPEED = 1.3D;        // multiplicador do moveTo (gigante é lento)
    private static final double GIANT_KNOCKBACK = 0.9D;
    private static final long GIANT_ATTACK_COOLDOWN_MS = 1_200L;
    private static final long DOUBLE_TAP_MS = 500L; // janela do shift/F duplo
    private static final long MENU_TIMEOUT_MS = 6_000L;
    /**
     * Opções de alma visíveis por vez no menu. São 16 tipos: cada linha do menu é
     * uma boss bar e, empilhadas demais, transbordam a tela — a opção selecionada
     * (▶) some. Janela pequena mantém poucas barras e a seleção sempre visível.
     */
    private static final int SOUL_MENU_VISIBLE = 2;

    private final JavaPlugin plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey minionKey;

    /** Servos vivos por dono: dono -> (servo -> dados). */
    private final Map<UUID, Map<UUID, Minion>> minionsByOwner = new HashMap<>();
    /** Recarga anti-spam (global) por dono, só para tipos não persistentes. */
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    /** Último aperto de shift por jogador (detecção de shift duplo). */
    private final Map<UUID, Long> lastSneakPress = new HashMap<>();
    /** Último aperto de F por jogador (detecção de F duplo). */
    private final Map<UUID, Long> lastSwapPress = new HashMap<>();
    /** Menu aberto por jogador. */
    private final Map<UUID, MenuState> menus = new HashMap<>();
    /** Menu vertical em boss bars. */
    private final MenuBars menuBars;
    /** Recarga do golpe de cada gigante (mob sem IA, guiado à mão). */
    private final Map<UUID, Long> giantAttackUntil = new HashMap<>();

    private BukkitTask sweepTask;
    private BukkitTask menuTask;
    private BukkitTask giantTask;

    public NecromancerListeners(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "necro_owner");
        this.minionKey = new NamespacedKey(plugin, "necro_minion");
        this.menuBars = new MenuBars(plugin, BossBar.Color.PURPLE);
    }

    public void start() {
        purgeAllMinions();
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
        // atualiza as boss bars do menu e fecha os menus parados/inválidos a cada tick
        menuTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickMenus, 1L, 1L);
        // gigantes não têm IA na vanilla: guiamos movimento e ataque à mão, num tick rápido
        giantTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::driveGiants, 5L, 5L);
    }

    /** A cada tick, mantém as boss bars dos menus abertos vivas e fecha os parados. */
    private void tickMenus() {
        if (menus.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, MenuState> entry : new ArrayList<>(menus.entrySet())) {
            UUID id = entry.getKey();
            MenuState menu = entry.getValue();
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline() || now - menu.lastActivity > MENU_TIMEOUT_MS) {
                menus.remove(id);
                menuBars.clear(id);
                continue;
            }
            ItemStack staff = player.getInventory().getItemInMainHand();
            if (!MineMagicItems.isNecromancerStaff(staff)) {
                menus.remove(id);
                menuBars.clear(player);
                continue;
            }
            showMenu(player, staff, menu);
        }
    }

    // =========================================================================
    //  Coleta de almas
    // =========================================================================

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || isMinion(dead)) {
            return;
        }
        EntityType soul = MineMagicItems.soulTypeOf(dead);
        if (soul == null) {
            return;
        }
        int slot = findStaffSlot(killer);
        if (slot < 0) {
            return;
        }
        ItemStack staff = killer.getInventory().getItem(slot);
        MineMagicItems.addSoul(staff, soul);
        killer.getInventory().setItem(slot, staff);
        killer.playSound(killer.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.7f, 1.4f);
    }

    // =========================================================================
    //  Menu: shift duplo + scroll + botão direito  |  F duplo: recolher
    // =========================================================================

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!MineMagicItems.isNecromancerStaff(player.getInventory().getItemInMainHand())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSneakPress.get(player.getUniqueId());
        if (last != null && now - last <= DOUBLE_TAP_MS) {
            lastSneakPress.remove(player.getUniqueId());
            openMenu(player);
        } else {
            lastSneakPress.put(player.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!MineMagicItems.isNecromancerStaff(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true); // segurando o cajado, F não troca para a mão secundária
        long now = System.currentTimeMillis();
        Long last = lastSwapPress.get(player.getUniqueId());
        if (last != null && now - last <= DOUBLE_TAP_MS) {
            lastSwapPress.remove(player.getUniqueId());
            recallAll(player);
        } else {
            lastSwapPress.put(player.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onScroll(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        MenuState menu = menus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        ItemStack staff = player.getInventory().getItemInMainHand();
        if (!MineMagicItems.isNecromancerStaff(staff)) {
            closeMenu(player);
            return;
        }
        // mantém o cajado no mesmo slot e usa o scroll só para navegar
        event.setCancelled(true);
        int dir = scrollDirection(event.getPreviousSlot(), event.getNewSlot());
        if (dir == 0) {
            return;
        }
        if (menu.stage == MenuState.Stage.SELECT_SOUL) {
            int size = menu.options.size();
            menu.soulIndex = ((menu.soulIndex + dir) % size + size) % size;
        } else {
            int max = Math.max(1, Math.min(MineMagicItems.cap(menu.chosenType), ownedOf(staff, menu.chosenType)));
            menu.qty = Math.max(1, Math.min(max, menu.qty + dir));
        }
        menu.lastActivity = System.currentTimeMillis();
        showMenu(player, staff, menu);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack staff = player.getInventory().getItemInMainHand();
        if (!MineMagicItems.isNecromancerStaff(staff)) {
            return;
        }
        event.setCancelled(true);

        MenuState menu = menus.get(player.getUniqueId());
        if (menu == null) {
            // Menu fechado: agachado + botão direito abre o menu (gatilho confiável,
            // independente do timing do shift duplo); sem agachar, invoca a seleção.
            if (player.isSneaking()) {
                openMenu(player);
            } else {
                invoke(player, staff);
            }
            return;
        }

        if (menu.stage == MenuState.Stage.SELECT_SOUL) {
            menu.chosenType = menu.options.get(menu.soulIndex);
            menu.stage = MenuState.Stage.SELECT_QTY;
            int max = Math.min(MineMagicItems.cap(menu.chosenType), ownedOf(staff, menu.chosenType));
            menu.qty = Math.max(1, Math.min(DEFAULT_QTY, max));
            menu.lastActivity = System.currentTimeMillis();
            showMenu(player, staff, menu);
        } else {
            MineMagicItems.setSelection(staff, menu.chosenType, menu.qty);
            player.getInventory().setItemInMainHand(staff);
            closeMenu(player);
            player.sendActionBar(Component.text("Seleção: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(MineMagicItems.prettyName(menu.chosenType) + " x" + menu.qty,
                            NamedTextColor.WHITE))
                    .append(Component.text("  — botão direito para invocar", NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }

    /**
     * Base de tridente: o botão direito é sempre cancelado (em
     * {@link #onRightClick}), então o tridente não chega a carregar nem a ser
     * arremessado. Mesmo assim, por garantia, bloqueamos qualquer arremesso de
     * tridente de quem segura o Cajado do Necromante.
     */
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
        if (MineMagicItems.isNecromancerStaff(shooter.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private void openMenu(Player player) {
        ItemStack staff = player.getInventory().getItemInMainHand();
        List<EntityType> options = new ArrayList<>(MineMagicItems.getSouls(staff).keySet());
        if (options.isEmpty()) {
            player.sendActionBar(Component.text("Você ainda não coletou nenhuma alma.",
                    NamedTextColor.RED));
            return;
        }
        MenuState menu = new MenuState(options);
        // começa na seleção atual, se houver
        EntityType sel = MineMagicItems.getSelType(staff);
        if (sel != null) {
            int idx = options.indexOf(sel);
            if (idx >= 0) {
                menu.soulIndex = idx;
            }
        }
        menu.lastActivity = System.currentTimeMillis();
        menus.put(player.getUniqueId(), menu);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.3f);
        showMenu(player, staff, menu);
    }

    private void closeMenu(Player player) {
        menus.remove(player.getUniqueId());
        menuBars.clear(player);
    }

    private void showMenu(Player player, ItemStack staff, MenuState menu) {
        if (menu.stage == MenuState.Stage.SELECT_SOUL) {
            List<String> labels = new ArrayList<>(menu.options.size());
            for (EntityType type : menu.options) {
                labels.add(MineMagicItems.prettyName(type) + "   (" + ownedOf(staff, type) + ")");
            }
            menuBars.render(player, MenuBars.buildLines("Almas", NamedTextColor.LIGHT_PURPLE,
                    labels, menu.soulIndex, SOUL_MENU_VISIBLE));
        } else {
            int cap = MineMagicItems.cap(menu.chosenType);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Quantidade", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("  —  scroll · botão direito", NamedTextColor.DARK_GRAY)));
            lines.add(Component.text(MineMagicItems.prettyName(menu.chosenType) + "   x", NamedTextColor.WHITE)
                    .append(Component.text(Integer.toString(menu.qty), NamedTextColor.AQUA))
                    .append(Component.text("   (1-" + cap + ")", NamedTextColor.GRAY)));
            menuBars.render(player, lines);
        }
    }

    /** Direção do scroll a partir dos slots, tratando o wrap 0↔8. Retorna -1, 0 ou +1. */
    private int scrollDirection(int prev, int now) {
        int step = now - prev;
        if (step > 4) {
            step -= 9;
        } else if (step < -4) {
            step += 9;
        }
        return Integer.compare(step, 0);
    }

    // =========================================================================
    //  Invocação
    // =========================================================================

    private void invoke(Player player, ItemStack staff) {
        EntityType type = MineMagicItems.getSelType(staff);
        if (type == null) {
            player.sendActionBar(Component.text("Selecione uma alma (shift duplo).",
                    NamedTextColor.RED));
            return;
        }
        if (!MineMagicItems.isCollectibleType(type)) {
            // cajado antigo com alma de tipo removido (ex.: Ender Dragon): não invoca
            player.sendActionBar(Component.text("Tipo de alma indisponível.", NamedTextColor.RED));
            return;
        }
        if (ownedOf(staff, type) <= 0) {
            player.sendActionBar(Component.text("Sem almas de " + MineMagicItems.prettyName(type) + ".",
                    NamedTextColor.RED));
            return;
        }

        UUID ownerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        boolean persistent = MineMagicItems.isPersistent(type);

        if (!persistent) {
            Long until = cooldownUntil.get(ownerId);
            if (until != null && now < until) {
                long secs = (until - now + 999) / 1000;
                player.sendActionBar(Component.text("Recarga: " + secs + "s", NamedTextColor.RED));
                return;
            }
        }

        Map<UUID, Minion> mine = minionsByOwner.computeIfAbsent(ownerId, k -> new HashMap<>());

        // invocar um tipo diferente dispensa os persistentes anteriores (devolve as almas ao cajado)
        dismissOtherPersistent(staff, mine, type);

        int cap = MineMagicItems.cap(type);
        int capFree = cap - countOfType(mine, type);
        if (capFree <= 0) {
            player.sendActionBar(Component.text("Máximo de " + MineMagicItems.prettyName(type)
                    + " vivos (" + cap + ").", NamedTextColor.RED));
            return;
        }
        int globalFree = MAX_TOTAL - mine.size();
        if (globalFree <= 0) {
            player.sendActionBar(Component.text("Exército cheio (" + MAX_TOTAL + ").", NamedTextColor.RED));
            return;
        }

        int desired = persistent ? 1 : MineMagicItems.getSelQty(staff);
        int n = Math.min(Math.min(desired, ownedOf(staff, type)), Math.min(capFree, globalFree));
        if (n <= 0) {
            return;
        }

        World world = player.getWorld();
        Location base = player.getLocation();
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i) / n;
            Location loc = base.clone().add(Math.cos(angle) * SPAWN_RADIUS, 0, Math.sin(angle) * SPAWN_RADIUS);
            Entity minion = spawnMinion(world, loc, type, player);
            if (minion == null) {
                continue;
            }
            mine.put(minion.getUniqueId(), new Minion(type, now, persistent));
            spawned++;
            if (minion instanceof Horse) {
                mountHorse(player, (Horse) minion);
            }
        }
        if (spawned <= 0) {
            return;
        }

        MineMagicItems.consumeSouls(staff, type, spawned);
        player.getInventory().setItemInMainHand(staff);
        if (!persistent) {
            cooldownUntil.put(ownerId, now + NORMAL_COOLDOWN_MS);
        }
        world.playSound(base, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.6f);
        world.spawnParticle(Particle.SOUL, base.clone().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.02);
        player.sendActionBar(Component.text("Invocou " + spawned + "x " + MineMagicItems.prettyName(type),
                NamedTextColor.DARK_PURPLE));
    }

    /** Dispensa servos persistentes de tipos diferentes de {@code keep}, devolvendo as almas ao cajado. */
    private void dismissOtherPersistent(ItemStack staff, Map<UUID, Minion> mine, EntityType keep) {
        for (Map.Entry<UUID, Minion> entry : new ArrayList<>(mine.entrySet())) {
            Minion m = entry.getValue();
            if (!m.persistent || m.type == keep) {
                continue;
            }
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (entity != null && entity.isValid()) {
                removeMinion(entity);
                MineMagicItems.addSouls(staff, m.type, 1);
            }
            mine.remove(entry.getKey());
        }
    }

    /** Recolhe todos os servos vivos do jogador e devolve as almas ao cajado (F duplo). */
    private void recallAll(Player player) {
        UUID ownerId = player.getUniqueId();
        Map<UUID, Minion> mine = minionsByOwner.get(ownerId);
        if (mine == null || mine.isEmpty()) {
            player.sendActionBar(Component.text("Nenhum servo para recolher.", NamedTextColor.GRAY));
            return;
        }
        int slot = findStaffSlot(player);
        ItemStack staff = slot >= 0 ? player.getInventory().getItem(slot) : null;

        Map<EntityType, Integer> refund = new HashMap<>();
        int recalled = 0;
        for (Map.Entry<UUID, Minion> entry : new ArrayList<>(mine.entrySet())) {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (entity != null && entity.isValid()) {
                removeMinion(entity);
                refund.merge(entry.getValue().type, 1, Integer::sum);
                recalled++;
            }
        }
        mine.clear();
        minionsByOwner.remove(ownerId);

        if (staff != null && !refund.isEmpty()) {
            for (Map.Entry<EntityType, Integer> r : refund.entrySet()) {
                MineMagicItems.addSouls(staff, r.getKey(), r.getValue());
            }
            player.getInventory().setItem(slot, staff);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
        player.sendActionBar(Component.text("Servos recolhidos: " + recalled
                + " (almas devolvidas)", NamedTextColor.LIGHT_PURPLE));
    }

    /** Cria um servo do tipo pedido, marcado com o dono. */
    private Entity spawnMinion(World world, Location loc, EntityType type, Player owner) {
        Entity entity;
        try {
            entity = world.spawnEntity(loc, type);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        entity.setPersistent(true);
        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).setRemoveWhenFarAway(false);
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(minionKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());

        // cavalo é montaria (não agride); os demais miram em inimigos por perto.
        if (entity instanceof Mob && type != EntityType.HORSE) {
            Mob mob = (Mob) entity;
            mob.setCustomName(ChatColor.DARK_GREEN + "Servo de " + owner.getName());
            mob.setCustomNameVisible(false);
            LivingEntity near = nearestTarget(mob, owner.getUniqueId());
            if (near != null) {
                mob.setTarget(near);
            }
        }
        return entity;
    }

    // =========================================================================
    //  Montaria: cavalo
    // =========================================================================

    private void mountHorse(Player player, Horse horse) {
        horse.setTamed(true);
        horse.setOwner(player);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.addPassenger(player);
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
        if (target.getUniqueId().equals(ownerId) || isMinionOf(target, ownerId)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity src = resolveRootDamager(event.getDamager());

        // servo nunca fere o dono nem outro servo do mesmo dono
        if (isMinion(src)) {
            UUID ownerId = getOwnerId(src);
            if (ownerId != null && (victim.getUniqueId().equals(ownerId) || isMinionOf(victim, ownerId))) {
                event.setCancelled(true);
                return;
            }
        }

        // dono ataca algo -> servos miram no alvo
        if (src instanceof Player && minionsByOwner.containsKey(src.getUniqueId())
                && victim instanceof LivingEntity
                && !victim.getUniqueId().equals(src.getUniqueId())
                && !isMinionOf(victim, src.getUniqueId())) {
            commandMinions(src.getUniqueId(), (LivingEntity) victim);
        }

        // dono é atacado -> servos miram no agressor
        if (victim instanceof Player && minionsByOwner.containsKey(victim.getUniqueId())
                && src instanceof LivingEntity
                && !src.getUniqueId().equals(victim.getUniqueId())
                && !isMinionOf(src, victim.getUniqueId())) {
            commandMinions(victim.getUniqueId(), (LivingEntity) src);
        }
    }

    private void commandMinions(UUID ownerId, LivingEntity target) {
        Map<UUID, Minion> mine = minionsByOwner.get(ownerId);
        if (mine == null) {
            return;
        }
        for (Map.Entry<UUID, Minion> entry : mine.entrySet()) {
            if (entry.getValue().type == EntityType.HORSE) {
                continue;
            }
            Entity e = plugin.getServer().getEntity(entry.getKey());
            if (e instanceof Mob && e.isValid()) {
                ((Mob) e).setTarget(target);
            }
        }
    }

    // =========================================================================
    //  Gigante: IA manual (move + golpeia)
    // =========================================================================

    /**
     * O {@link EntityType#GIANT} é um resíduo da vanilla: não tem nenhuma meta de IA,
     * então {@code setTarget} não faz nada — ele fica parado. Aqui guiamos cada gigante
     * à mão: caminhamos até o alvo pelo pathfinder e desferimos um golpe corpo a corpo
     * quando ele está ao alcance.
     */
    private void driveGiants() {
        if (minionsByOwner.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Minion>> entry : new ArrayList<>(minionsByOwner.entrySet())) {
            UUID ownerId = entry.getKey();
            for (Map.Entry<UUID, Minion> me : new ArrayList<>(entry.getValue().entrySet())) {
                if (me.getValue().type != EntityType.GIANT) {
                    continue;
                }
                Entity e = plugin.getServer().getEntity(me.getKey());
                if (!(e instanceof Mob) || !e.isValid()) {
                    giantAttackUntil.remove(me.getKey());
                    continue;
                }
                driveGiant((Mob) e, ownerId, now);
            }
        }
    }

    private void driveGiant(Mob giant, UUID ownerId, long now) {
        LivingEntity target = giant.getTarget();
        if (!isValidGiantTarget(giant, target, ownerId)) {
            target = nearestTarget(giant, ownerId);
            giant.setTarget(target);
        }
        if (target == null) {
            return;
        }
        double dist = giant.getLocation().distance(target.getLocation());
        if (dist > GIANT_REACH) {
            giant.getPathfinder().moveTo(target.getLocation(), GIANT_SPEED);
            return;
        }
        // dentro do alcance: encara, golpeia (respeitando a recarga) e empurra
        giant.lookAt(target);
        Long until = giantAttackUntil.get(giant.getUniqueId());
        if (until != null && now < until) {
            return;
        }
        target.damage(GIANT_DAMAGE, giant);
        Vector kb = target.getLocation().toVector().subtract(giant.getLocation().toVector());
        if (kb.lengthSquared() > 1.0E-4) {
            kb = kb.normalize().multiply(GIANT_KNOCKBACK).setY(0.4);
            target.setVelocity(target.getVelocity().add(kb));
        }
        giant.getWorld().playSound(giant.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
        giantAttackUntil.put(giant.getUniqueId(), now + GIANT_ATTACK_COOLDOWN_MS);
    }

    /** Alvo do gigante ainda serve? (vivo, mesmo mundo, não é o dono nem um servo do dono.) */
    private boolean isValidGiantTarget(Mob giant, LivingEntity target, UUID ownerId) {
        return target != null && target.isValid() && !target.isDead()
                && target.getWorld().equals(giant.getWorld())
                && !target.getUniqueId().equals(ownerId)
                && !isMinionOf(target, ownerId);
    }

    // =========================================================================
    //  Varredura periódica: expira servos e aplica agressividade
    // =========================================================================

    private void sweep() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Minion>> entry : new ArrayList<>(minionsByOwner.entrySet())) {
            UUID ownerId = entry.getKey();
            Player owner = plugin.getServer().getPlayer(ownerId);
            boolean ownerOffline = owner == null || !owner.isOnline();

            Map<UUID, Minion> mine = entry.getValue();
            for (Map.Entry<UUID, Minion> me : new ArrayList<>(mine.entrySet())) {
                UUID minionId = me.getKey();
                Minion data = me.getValue();
                Entity e = plugin.getServer().getEntity(minionId);
                if (e == null || !e.isValid()) {
                    mine.remove(minionId);
                    continue;
                }
                long age = now - data.spawnedAt;
                boolean expired = !data.persistent && age > LIFESPAN_MS;
                if (ownerOffline || expired) {
                    removeMinion(e);
                    mine.remove(minionId);
                    continue;
                }
                if (e instanceof Mob && data.type != EntityType.HORSE) {
                    Mob mob = (Mob) e;
                    LivingEntity tgt = mob.getTarget();
                    if (tgt != null && tgt.getUniqueId().equals(ownerId)) {
                        mob.setTarget(null);
                        tgt = null;
                    }
                    if (tgt == null || !tgt.isValid()) {
                        LivingEntity near = nearestTarget(mob, ownerId);
                        if (near != null) {
                            mob.setTarget(near);
                        }
                    }
                }
            }
            if (mine.isEmpty()) {
                minionsByOwner.remove(ownerId);
            }
        }
    }

    /** Alvo mais próximo válido para o servo: nada de dono nem servos do mesmo dono. */
    private LivingEntity nearestTarget(Mob mob, UUID ownerId) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mob.getNearbyEntities(AGGRO_RADIUS, AGGRO_RADIUS, AGGRO_RADIUS)) {
            if (!(e instanceof LivingEntity) || !e.isValid()) {
                continue;
            }
            LivingEntity living = (LivingEntity) e;
            if (e.getUniqueId().equals(ownerId) || isMinionOf(e, ownerId)) {
                continue;
            }
            if (living.isInvulnerable()
                    || (living instanceof Player
                            && (((Player) living).isDead()
                                || ((Player) living).getGameMode() == GameMode.SPECTATOR))) {
                continue;
            }
            double d = mob.getLocation().distanceSquared(living.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }

    private void removeMinion(Entity entity) {
        World world = entity.getWorld();
        world.spawnParticle(Particle.SMOKE, entity.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.01);
        entity.remove();
    }

    // =========================================================================
    //  Utilidades de identidade
    // =========================================================================

    private boolean isMinion(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);
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

    private Entity resolveRootDamager(Entity damager) {
        if (damager instanceof Projectile) {
            Object shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity) {
                return (Entity) shooter;
            }
        }
        return damager;
    }

    private int countOfType(Map<UUID, Minion> mine, EntityType type) {
        int n = 0;
        for (Minion m : mine.values()) {
            if (m.type == type) {
                n++;
            }
        }
        return n;
    }

    private int ownedOf(ItemStack staff, EntityType type) {
        Integer n = MineMagicItems.getSouls(staff).get(type);
        return n == null ? 0 : n;
    }

    /** Primeiro slot do inventário com um Cajado do Necromante, ou -1. */
    private int findStaffSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (MineMagicItems.isNecromancerStaff(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    // =========================================================================
    //  Limpeza
    // =========================================================================

    private void purgeAllMinions() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isMinion(entity)) {
                    entity.remove();
                }
            }
        }
        minionsByOwner.clear();
        giantAttackUntil.clear();
    }

    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        if (menuTask != null) {
            menuTask.cancel();
            menuTask = null;
        }
        if (giantTask != null) {
            giantTask.cancel();
            giantTask = null;
        }
        giantAttackUntil.clear();
        menuBars.clearAll();
        purgeAllMinions();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastSneakPress.remove(id);
        lastSwapPress.remove(id);
        menus.remove(id);
        menuBars.clear(id);
    }

    // =========================================================================
    //  Estado do menu / servos
    // =========================================================================

    private static final class Minion {
        final EntityType type;
        final long spawnedAt;
        final boolean persistent;

        Minion(EntityType type, long spawnedAt, boolean persistent) {
            this.type = type;
            this.spawnedAt = spawnedAt;
            this.persistent = persistent;
        }
    }

    private static final class MenuState {
        enum Stage { SELECT_SOUL, SELECT_QTY }

        final List<EntityType> options;
        Stage stage = Stage.SELECT_SOUL;
        int soulIndex = 0;
        EntityType chosenType;
        int qty = 1;
        long lastActivity;

        MenuState(List<EntityType> options) {
            this.options = options;
        }
    }
}
