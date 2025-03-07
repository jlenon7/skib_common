package skibidilandia.plugins;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatPlugin implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private static final int CHAT_RADIUS = 100;

    public ChatPlugin(JavaPlugin plugin) {
       this.plugin = plugin;
    }

    public void register() {
        plugin.getCommand("g").setExecutor(this);
        plugin.getCommand("w").setExecutor(this);
        plugin.getCommand("tell").setExecutor(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        Location senderLocation = sender.getLocation();

        String localMessage = ChatColor.GRAY + "[Local] " + ChatColor.WHITE + sender.getName() + ": " + event.getMessage();

        event.getRecipients().clear();

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.getWorld().equals(sender.getWorld()) && recipient.getLocation().distance(senderLocation) <= CHAT_RADIUS) {
                event.getRecipients().add(recipient);
                recipient.sendMessage(localMessage);
            }
        }

        event.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }

        Player player = (Player) sender;
        String commandName = command.getName();

        if (commandName.equalsIgnoreCase("g")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Uso correto: /g <mensagem>");
                return true;
            }

            String message = String.join(" ", args);
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Global] " + ChatColor.WHITE + player.getName() + ": " + message);

            return true;
        }

        if (commandName.equalsIgnoreCase("w") || commandName.equalsIgnoreCase("tell")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Uso correto: /" + commandName + " <jogador> <mensagem>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "O jogador especificado não está online.");
                return true;
            }

            String privateMessage = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            String senderMessage = ChatColor.LIGHT_PURPLE + "[Privado] " + ChatColor.WHITE + "Você → " + target.getName() + ": " + privateMessage;
            String receiverMessage = ChatColor.LIGHT_PURPLE + "[Privado] " + ChatColor.WHITE + player.getName() + " → Você: " + privateMessage;

            player.sendMessage(senderMessage);
            target.sendMessage(receiverMessage);

            return true;
        }

        return false;
    }
}
