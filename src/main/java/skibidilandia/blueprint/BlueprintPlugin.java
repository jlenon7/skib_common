package skibidilandia.blueprint;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Liga o sistema de blueprints: itens, listeners e o comando /blueprint.
 *
 * Fluxo: pegue a varinha (/blueprint varinha), marque os dois cantos da sua
 * construção, rode /blueprint criar <nome> para receber o item de blueprint, e
 * clique com o botão direito no chão para gerar a construção (gasta o item).
 */
public class BlueprintPlugin implements CommandExecutor {

    private final JavaPlugin plugin;
    private BlueprintListeners listeners;

    public BlueprintPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        BlueprintItems.init(plugin);

        listeners = new BlueprintListeners();
        plugin.getServer().getPluginManager().registerEvents(listeners, plugin);

        if (plugin.getCommand("blueprint") != null) {
            plugin.getCommand("blueprint").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores podem usar /blueprint.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.isOp() && !player.hasPermission("skib.blueprint.admin")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "varinha":
            case "wand":
                player.getInventory().addItem(BlueprintItems.createWand());
                player.sendMessage(ChatColor.GREEN + "Varinha de Blueprint entregue.");
                return true;
            case "criar":
            case "create":
                handleCreate(player, args);
                return true;
            default:
                sendHelp(player);
                return true;
        }
    }

    private void handleCreate(Player player, String[] args) {
        Location p1 = listeners.getPos1(player);
        Location p2 = listeners.getPos2(player);
        if (p1 == null || p2 == null) {
            player.sendMessage(ChatColor.RED + "Marque os dois cantos com a varinha primeiro.");
            return;
        }
        if (!p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage(ChatColor.RED + "Os dois cantos precisam estar no mesmo mundo.");
            return;
        }

        String name = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "Sem nome";

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        BlueprintData data = BlueprintData.capture(p1.getWorld(), minX, minY, minZ, maxX, maxY, maxZ);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Seleção grande demais (máx. "
                    + BlueprintData.MAX_BLOCKS + " blocos). Diminua a área.");
            return;
        }
        if (data.getBlockCount() == 0) {
            player.sendMessage(ChatColor.RED + "A seleção está vazia (só ar).");
            return;
        }

        player.getInventory().addItem(BlueprintItems.createBlueprint(name, data));
        player.sendMessage(ChatColor.GREEN + "Blueprint \"" + name + "\" criado! "
                + ChatColor.GRAY + data.getBlockCount() + " blocos.");
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Blueprints ===");
        player.sendMessage(ChatColor.YELLOW + "/blueprint varinha" + ChatColor.GRAY + " - recebe a varinha de seleção");
        player.sendMessage(ChatColor.GRAY + "  Clique esquerdo = canto 1, clique direito = canto 2");
        player.sendMessage(ChatColor.YELLOW + "/blueprint criar <nome>" + ChatColor.GRAY + " - cria o blueprint da seleção");
        player.sendMessage(ChatColor.GRAY + "  Com o blueprint na mão, clique direito no chão para gerar.");
    }
}
