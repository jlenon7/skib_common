package skibidilandia.furnacetools;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Liga o sistema de ferramentas da fornalha: itens, tabela de fundição,
 * listener de quebra e o comando admin para entregar as ferramentas.
 */
public class FurnaceToolsPlugin implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final FurnaceSmelting smelting = new FurnaceSmelting();

    public FurnaceToolsPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        FurnaceToolItems.init(plugin);

        int mapped = smelting.build(plugin.getServer());
        plugin.getLogger().info("[FurnaceTools] " + mapped + " receitas de fundição carregadas.");

        plugin.getServer().getPluginManager()
                .registerEvents(new FurnaceToolListeners(smelting), plugin);

        if (plugin.getCommand("furnacetool") != null) {
            plugin.getCommand("furnacetool").setExecutor(this);
            plugin.getCommand("furnacetool").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.furnacetools.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /furnacetool dar <picareta|machado|pa> [jogador]");
            return true;
        }

        FurnaceToolType type = FurnaceToolType.fromArg(args[1]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Ferramenta inválida. Use: picareta, machado ou pa.");
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
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /furnacetool dar <tipo> <jogador>");
            return true;
        }

        target.getInventory().addItem(FurnaceToolItems.create(type));
        sender.sendMessage(ChatColor.GREEN + "Entregue: " + type.getDisplayName() + " para " + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("dar"), args[0]);
        }
        if (args.length == 2) {
            return filter(Arrays.asList("picareta", "machado", "pa"), args[1]);
        }
        if (args.length == 3) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[2]);
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
