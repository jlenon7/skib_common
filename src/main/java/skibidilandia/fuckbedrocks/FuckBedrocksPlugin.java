package skibidilandia.fuckbedrocks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Liga o sistema FuckBedrocks: registra os itens, o listener e o comando admin
 * para entregar a Picareta Quebra-Bedrock e o TNT Nuclear.
 */
public class FuckBedrocksPlugin implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public FuckBedrocksPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        FuckBedrocksItems.init(plugin);

        plugin.getServer().getPluginManager()
                .registerEvents(new FuckBedrocksListeners(plugin), plugin);

        if (plugin.getCommand("fuckbedrocks") != null) {
            plugin.getCommand("fuckbedrocks").setExecutor(this);
            plugin.getCommand("fuckbedrocks").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.fuckbedrocks.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /fuckbedrocks dar <picareta|tnt> [jogador] [quantidade]");
            return true;
        }

        ItemStack item = build(args[1]);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Item inválido. Use: picareta ou tnt.");
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
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /fuckbedrocks dar <item> <jogador>");
            return true;
        }

        // quantidade opcional (só faz sentido para o TNT, que empilha)
        if (args.length >= 4 && isNukeArg(args[1])) {
            int amount = parsePositive(args[3], 1);
            item = FuckBedrocksItems.createNuke(amount);
        }

        target.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "Entregue: "
                + ChatColor.stripColor(item.getItemMeta().getDisplayName())
                + " x" + item.getAmount() + " para " + target.getName());
        return true;
    }

    /** Constrói o item a partir do argumento (1 unidade por padrão), ou null. */
    private static ItemStack build(String arg) {
        switch (arg.toLowerCase()) {
            case "picareta":
            case "pickaxe":
            case "pick":
                return FuckBedrocksItems.createPickaxe();
            case "tnt":
            case "nuke":
            case "nuclear":
                return FuckBedrocksItems.createNuke(1);
            case "carrinho":
            case "minecart":
            case "tntminecart":
                return FuckBedrocksItems.createNukeMinecart();
            default:
                return null;
        }
    }

    private static boolean isNukeArg(String arg) {
        String lower = arg.toLowerCase();
        return lower.equals("tnt") || lower.equals("nuke") || lower.equals("nuclear");
    }

    private static int parsePositive(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? Math.min(value, 64) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("dar"), args[0]);
        }
        if (args.length == 2) {
            return filter(Arrays.asList("picareta", "tnt", "carrinho"), args[1]);
        }
        if (args.length == 3) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[2]);
        }
        if (args.length == 4 && isNukeArg(args[1])) {
            return filter(Arrays.asList("1", "8", "16", "32", "64"), args[3]);
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
