package skibidilandia.miner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Liga o sistema de mineradoras: itens, registro, listeners, geração e o comando admin temporário.
 */
public class MinerPlugin implements CommandExecutor {

    private static final long AUTOSAVE_TICKS = 20L * 60 * 5; // 5 min

    private final JavaPlugin plugin;
    private MinerRegistry registry;
    private GenerationTask generationTask;
    private BukkitRunnable autosaveTask;

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

        autosaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                registry.save();
            }
        };
        autosaveTask.runTaskTimer(plugin, AUTOSAVE_TICKS, AUTOSAVE_TICKS);

        if (plugin.getCommand("minerador") != null) {
            plugin.getCommand("minerador").setExecutor(this);
        }
    }

    public void shutdown() {
        if (generationTask != null) {
            generationTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (registry != null) {
            registry.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.miner.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /minerador dar <1-6> [jogador]");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Nível inválido. Use um número de 1 a 6.");
            return true;
        }
        MinerTier tier = MinerTier.fromLevel(level);
        if (tier == null) {
            sender.sendMessage(ChatColor.RED + "Nível inválido. Use um número de 1 a 6.");
            return true;
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
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /minerador dar <1-6> <jogador>");
            return true;
        }

        target.getInventory().addItem(MinerItems.create(tier));
        sender.sendMessage(ChatColor.GREEN + "Entregue: " + tier.getDisplayName() + " para " + target.getName());
        return true;
    }
}
