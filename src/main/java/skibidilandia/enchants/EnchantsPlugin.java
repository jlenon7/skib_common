package skibidilandia.enchants;

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
 * Liga o plugin de encantamentos: registra os itens, o listener e o comando admin
 * para entregar o Livro Encantado de Hexa.
 *
 * Por enquanto o único encantamento é o Hexa (picareta/pá, quebra 3x3).
 */
public class EnchantsPlugin implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public EnchantsPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        EnchantsItems.init(plugin);

        plugin.getServer().getPluginManager()
                .registerEvents(new EnchantsListeners(plugin), plugin);

        if (plugin.getCommand("enchants") != null) {
            plugin.getCommand("enchants").setExecutor(this);
            plugin.getCommand("enchants").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.enchants.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /enchants dar hexa [jogador] [quantidade]");
            return true;
        }
        if (!args[1].equalsIgnoreCase("hexa")) {
            sender.sendMessage(ChatColor.RED + "Encantamento inválido. Use: hexa.");
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
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /enchants dar hexa <jogador>");
            return true;
        }

        int amount = args.length >= 4 ? parsePositive(args[3], 1) : 1;
        ItemStack book = EnchantsItems.createHexaBook(amount);
        target.getInventory().addItem(book);
        sender.sendMessage(ChatColor.GREEN + "Entregue: "
                + ChatColor.stripColor(book.getItemMeta().getDisplayName())
                + " x" + amount + " para " + target.getName());
        return true;
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
            return filter(Arrays.asList("hexa"), args[1]);
        }
        if (args.length == 3) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[2]);
        }
        if (args.length == 4) {
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
