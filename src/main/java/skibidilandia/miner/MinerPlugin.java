package skibidilandia.miner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Liga o sistema de mineradoras: itens, registro, listeners, geração e o comando admin temporário.
 */
public class MinerPlugin implements CommandExecutor, TabCompleter {

    private static final long AUTOSAVE_TICKS = 20L * 60 * 5; // 5 min

    private final JavaPlugin plugin;
    private MinerRegistry registry;
    private GenerationTask generationTask;
    private BukkitRunnable autosaveTask;

    private MachineRegistry machineRegistry;
    private MachineTask machineTask;

    public MinerPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        MinerItems.init(plugin);

        registry = new MinerRegistry(plugin);
        registry.load();

        plugin.getServer().getPluginManager().registerEvents(new MinerListeners(registry), plugin);

        generationTask = new GenerationTask(registry);
        generationTask.runTaskTimer(plugin, 20L, 20L);

        // Máquinas auxiliares (colhetadeira / compactadora) — reusam a infra da mineradora.
        MachineItems.init(plugin);
        machineRegistry = new MachineRegistry(plugin);
        machineRegistry.load();
        plugin.getServer().getPluginManager().registerEvents(new MachineListeners(machineRegistry), plugin);
        machineTask = new MachineTask(machineRegistry, registry);
        machineTask.runTaskTimer(plugin, 20L, 20L);

        autosaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                registry.save();
                machineRegistry.save();
            }
        };
        autosaveTask.runTaskTimer(plugin, AUTOSAVE_TICKS, AUTOSAVE_TICKS);

        if (plugin.getCommand("minerador") != null) {
            plugin.getCommand("minerador").setExecutor(this);
            plugin.getCommand("minerador").setTabCompleter(this);
        }
        if (plugin.getCommand("maquina") != null) {
            MachineCommand machineCommand = new MachineCommand();
            plugin.getCommand("maquina").setExecutor(machineCommand);
            plugin.getCommand("maquina").setTabCompleter(machineCommand);
        }
    }

    public void shutdown() {
        if (generationTask != null) {
            generationTask.cancel();
        }
        if (machineTask != null) {
            machineTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (registry != null) {
            registry.save();
        }
        if (machineRegistry != null) {
            machineRegistry.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.miner.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /minerador dar <1-6|colhetadeira|compactadora> [jogador]");
            return true;
        }

        // O segundo argumento pode ser um nível de mineradora (1-6) ou o id de uma máquina auxiliar.
        MachineType machineType = MachineType.fromId(args[1]);
        MinerTier tier = null;
        if (machineType == null) {
            int level;
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Inválido. Use um número de 1 a 6, ou: colhetadeira / compactadora.");
                return true;
            }
            tier = MinerTier.fromLevel(level);
            if (tier == null) {
                sender.sendMessage(ChatColor.RED + "Nível inválido. Use um número de 1 a 6.");
                return true;
            }
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jogador não encontrado: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /minerador dar <1-6|colhetadeira|compactadora> <jogador>");
            return true;
        }

        if (machineType != null) {
            target.getInventory().addItem(MachineItems.create(machineType));
            sender.sendMessage(ChatColor.GREEN + "Entregue: " + machineType.getDisplayName() + " para " + target.getName());
        } else {
            target.getInventory().addItem(MinerItems.create(tier));
            sender.sendMessage(ChatColor.GREEN + "Entregue: " + tier.getDisplayName() + " para " + target.getName());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if ("dar".startsWith(args[0].toLowerCase())) {
                out.add("dar");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar")) {
            String prefix = args[1].toLowerCase();
            for (int i = 1; i <= 6; i++) {
                String lvl = Integer.toString(i);
                if (lvl.startsWith(prefix)) {
                    out.add(lvl);
                }
            }
            for (MachineType type : MachineType.values()) {
                if (type.getId().startsWith(prefix)) {
                    out.add(type.getId());
                }
            }
        }
        return out;
    }
}
