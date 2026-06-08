package skibidilandia.tnttools;

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
 * Liga o sistema TNTTools: registra os itens, o listener e o comando admin para
 * entregar a Espada TNT e as peças da Armadura TNT.
 */
public class TNTToolsPlugin implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public TNTToolsPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        TNTToolsItems.init(plugin);

        plugin.getServer().getPluginManager()
                .registerEvents(new TNTToolsListeners(plugin), plugin);

        if (plugin.getCommand("tnttools") != null) {
            plugin.getCommand("tnttools").setExecutor(this);
            plugin.getCommand("tnttools").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.tnttools.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW
                    + "Uso: /tnttools dar <espada|capacete|peitoral|calca|bota|armadura> [jogador]");
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
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /tnttools dar <item> <jogador>");
            return true;
        }

        List<ItemStack> items = build(args[1]);
        if (items == null) {
            sender.sendMessage(ChatColor.RED
                    + "Item inválido. Use: espada, capacete, peitoral, calca, bota ou armadura.");
            return true;
        }

        for (ItemStack item : items) {
            target.getInventory().addItem(item);
        }
        sender.sendMessage(ChatColor.GREEN + "Entregue: " + ChatColor.WHITE + args[1].toLowerCase()
                + ChatColor.GREEN + " para " + target.getName() + ".");
        return true;
    }

    /** Constrói o(s) item(ns) pedido(s), ou null se o argumento for inválido. */
    private static List<ItemStack> build(String arg) {
        switch (arg.toLowerCase()) {
            case "espada":
            case "sword":
                return Arrays.asList(TNTToolsItems.createSword());
            case "capacete":
            case "helmet":
                return Arrays.asList(TNTToolsItems.createArmorPiece(TNTToolsItems.HELMET_MATERIAL));
            case "peitoral":
            case "chestplate":
                return Arrays.asList(TNTToolsItems.createArmorPiece(TNTToolsItems.CHESTPLATE_MATERIAL));
            case "calca":
            case "calça":
            case "leggings":
                return Arrays.asList(TNTToolsItems.createArmorPiece(TNTToolsItems.LEGGINGS_MATERIAL));
            case "bota":
            case "botas":
            case "boots":
                return Arrays.asList(TNTToolsItems.createArmorPiece(TNTToolsItems.BOOTS_MATERIAL));
            case "armadura":
            case "armor":
                return TNTToolsItems.createFullArmor();
            default:
                return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("dar"), args[0]);
        }
        if (args.length == 2) {
            return filter(Arrays.asList("espada", "capacete", "peitoral", "calca", "bota", "armadura"), args[1]);
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
