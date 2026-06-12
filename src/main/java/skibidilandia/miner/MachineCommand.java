package skibidilandia.miner;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Comando admin temporário pra entregar as máquinas auxiliares (enquanto a loja não existe):
 * /maquina dar <colhetadeira|compactadora> [jogador]
 */
public class MachineCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.miner.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /maquina dar <colhetadeira|compactadora> [jogador]");
            return true;
        }

        MachineType type = MachineType.fromId(args[1]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Máquina inválida. Use: colhetadeira ou compactadora.");
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
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /maquina dar <tipo> <jogador>");
            return true;
        }

        target.getInventory().addItem(MachineItems.create(type));
        sender.sendMessage(ChatColor.GREEN + "Entregue: " + type.getDisplayName() + " para " + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if ("dar".startsWith(args[0].toLowerCase())) {
                out.add("dar");
            }
        } else if (args.length == 2) {
            for (MachineType type : MachineType.values()) {
                if (type.getId().startsWith(args[1].toLowerCase())) {
                    out.add(type.getId());
                }
            }
        }
        return out;
    }
}
