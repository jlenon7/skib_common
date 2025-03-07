package skibidilandia.plugins;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import skibidilandia.helpers.IslandRadius;

public class TpPlugin implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;

    private final Set<String> islandNames;

    private final Map<String, IslandRadius> islands;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> teleportTasks = new HashMap<>();

    private static final long COOLDOWN_TIME = 5 * 60 * 1000;
    private static final long TELEPORT_DELAY = 3;

    public TpPlugin(JavaPlugin plugin) {
       this.plugin = plugin;
       this.islands = new HashMap<>();

        islands.put("cerejeira", new IslandRadius(-2069, -907, -2418, -721));
        islands.put("gelo", new IslandRadius(-2601, 56, -2315, 60));
        islands.put("everest", new IslandRadius(-1388, 592, 1272, -1367));
        islands.put("p_altas", new IslandRadius(-1805, 2062, -2451, 1321));
        islands.put("p_baixas", new IslandRadius(2027, 1908, 1821, 1652));
        islands.put("f_negra", new IslandRadius(-356, 2632, -444, 2304));
        islands.put("amazonia", new IslandRadius(2866, 405, 2599, 42));
        islands.put("deserto", new IslandRadius(2037, -1557, 2876, -2367));
        islands.put("savana", new IslandRadius(809, -1913, 1491, -2397));
        islands.put("cogumelos", new IslandRadius(-215, -1991, -1021, -2134));
        islands.put("nether", new IslandRadius(-2472, -2544, -1945, -1997));

        islandNames = islands.keySet();
    }

    public void register() {
        plugin.getCommand("tpisland").setExecutor(this);
        plugin.getCommand("tpisland").setTabCompleter(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        Long currentTime = System.currentTimeMillis();
        String commandName = command.getName();

        if (commandName.equalsIgnoreCase("tpisland")) {
            if (args.length == 0 || !islandNames.contains(args[0].toLowerCase())) {
                player.sendMessage(ChatColor.RED + "Uso correto: /tpisland <nome da ilha>");
                player.sendMessage(ChatColor.YELLOW + "Ilhas disponíveis: " + String.join(", ", islandNames));
                return true;
            }

            String islandName = String.join(" ", args);
            IslandRadius islandRadius = islands.get(islandName);

            if (islandRadius == null) { 
              player.sendMessage(ChatColor.RED + "A ilha de nome " + islandName + " não existe");
              return true;
            }

            if (cooldowns.containsKey(playerUUID)) {
                long lastUsed = cooldowns.get(playerUUID);
                long timePassed = currentTime - lastUsed;

                if (timePassed < COOLDOWN_TIME) {
                    long timeLeft = (COOLDOWN_TIME - timePassed) / 1000;
                    player.sendMessage(ChatColor.RED + "Você precisa esperar " + timeLeft + " segundos antes de usar este comando novamente.");
                    return true;
                }
            }

            player.sendMessage(ChatColor.YELLOW + "Teleportando em " + TELEPORT_DELAY + " segundos. Não se mova!");

            Integer taskID = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (teleportTasks.containsKey(playerUUID)) {
                    Location randomLocation = islandRadius.getRandomLocation(player.getWorld());
                    player.teleport(randomLocation);
                    player.sendMessage(ChatColor.GREEN + "Você foi teleportado para uma area aleatória da ilha!");
                    cooldowns.put(playerUUID, currentTime);
                    teleportTasks.remove(playerUUID);
                }
            }, TELEPORT_DELAY * 20L);

            teleportTasks.put(playerUUID, taskID);

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            for (String island : islandNames) {
                if (island.toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(island);
                }
            }

            return suggestions;
        }

        return null;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerUUID = player.getUniqueId();

            if (teleportTasks.containsKey(playerUUID)) {
                cancelTeleport(player, "Você tomou dano! Teleporte cancelado.");
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (teleportTasks.containsKey(playerUUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                cancelTeleport(player, "Você se moveu! Teleporte cancelado.");
            }
        }
    }

    private void cancelTeleport(Player player, String message) {
        UUID playerUUID = player.getUniqueId();

        if (teleportTasks.containsKey(playerUUID)) {
            int taskID = teleportTasks.get(playerUUID);
            Bukkit.getScheduler().cancelTask(taskID);
            teleportTasks.remove(playerUUID);
            player.sendMessage(ChatColor.RED + message);
        }
    }
}
