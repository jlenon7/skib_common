package skibidilandia.minemagic;

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
 * Liga o sistema MineMagic: registra os itens, os listeners do Cajado de Fogo e
 * do Cajado do Necromante, e o comando admin para entregar os cajados.
 */
public class MineMagicPlugin implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private FireStaffListeners fireStaffListeners;
    private NecromancerListeners necromancerListeners;
    private LightningStaffListeners lightningStaffListeners;
    private DarkElfListeners darkElfListeners;
    private GravityStaffListeners gravityStaffListeners;
    private HealStaffListeners healStaffListeners;
    private ElfBowListeners elfBowListeners;
    private DarkElfBowListeners darkElfBowListeners;
    private MjolnirListeners mjolnirListeners;

    public MineMagicPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        MineMagicItems.init(plugin);

        fireStaffListeners = new FireStaffListeners(plugin);
        necromancerListeners = new NecromancerListeners(plugin);
        lightningStaffListeners = new LightningStaffListeners(plugin);
        darkElfListeners = new DarkElfListeners(plugin);
        gravityStaffListeners = new GravityStaffListeners(plugin);
        healStaffListeners = new HealStaffListeners(plugin);
        elfBowListeners = new ElfBowListeners(plugin);
        darkElfBowListeners = new DarkElfBowListeners(plugin);
        mjolnirListeners = new MjolnirListeners(plugin);
        plugin.getServer().getPluginManager().registerEvents(fireStaffListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(necromancerListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(lightningStaffListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(darkElfListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(gravityStaffListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(healStaffListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(elfBowListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(darkElfBowListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(mjolnirListeners, plugin);
        necromancerListeners.start();

        if (plugin.getCommand("minemagic") != null) {
            plugin.getCommand("minemagic").setExecutor(this);
            plugin.getCommand("minemagic").setTabCompleter(this);
        }
    }

    /** Cancela tarefas e remove servos (chamado no onDisable). */
    public void shutdown() {
        if (fireStaffListeners != null) {
            fireStaffListeners.shutdown();
        }
        if (necromancerListeners != null) {
            necromancerListeners.shutdown();
        }
        if (lightningStaffListeners != null) {
            lightningStaffListeners.shutdown();
        }
        if (darkElfListeners != null) {
            darkElfListeners.shutdown();
        }
        if (gravityStaffListeners != null) {
            gravityStaffListeners.shutdown();
        }
        if (healStaffListeners != null) {
            healStaffListeners.shutdown();
        }
        if (elfBowListeners != null) {
            elfBowListeners.shutdown();
        }
        if (darkElfBowListeners != null) {
            darkElfBowListeners.shutdown();
        }
        if (mjolnirListeners != null) {
            mjolnirListeners.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.minemagic.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /minemagic dar <cajadofogo|cajadonecromante|cajadoraio|cajadoelfo|cajadogravidade|cajadocura|arcoelfico|arcoelfonegro|mjolnir> [jogador]");
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
            sender.sendMessage(ChatColor.RED + "Especifique um jogador: /minemagic dar <item> <jogador>");
            return true;
        }

        ItemStack item = build(args[1]);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Item inválido. Use: cajadofogo, cajadonecromante, cajadoraio, cajadoelfo, cajadogravidade, cajadocura, arcoelfico, arcoelfonegro ou mjolnir.");
            return true;
        }

        target.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "Entregue: " + ChatColor.WHITE + args[1].toLowerCase()
                + ChatColor.GREEN + " para " + target.getName() + ".");
        return true;
    }

    /** Constrói o cajado pedido, ou null se o argumento for inválido. */
    private static ItemStack build(String arg) {
        switch (arg.toLowerCase()) {
            case "cajadofogo":
            case "fogo":
            case "firestaff":
            case "fire":
                return MineMagicItems.createFireStaff();
            case "cajadonecromante":
            case "necromante":
            case "necrostaff":
            case "necro":
                return MineMagicItems.createNecromancerStaff();
            case "cajadoraio":
            case "raio":
            case "lightningstaff":
            case "lightning":
                return MineMagicItems.createLightningStaff();
            case "cajadoelfo":
            case "elfo":
            case "elfonegro":
            case "darkelfstaff":
            case "darkelf":
                return MineMagicItems.createDarkElfStaff();
            case "cajadogravidade":
            case "gravidade":
            case "gravitystaff":
            case "gravity":
                return MineMagicItems.createGravityStaff();
            case "cajadocura":
            case "cura":
            case "healstaff":
            case "heal":
                return MineMagicItems.createHealStaff();
            case "arcoelfico":
            case "arco":
            case "elfbow":
            case "elf":
                return MineMagicItems.createElfBow();
            case "arcoelfonegro":
            case "arconegro":
            case "darkelfbow":
            case "arcoelfo":
                return MineMagicItems.createDarkElfBow();
            case "mjolnir":
            case "martelo":
            case "hammer":
            case "thor":
                return MineMagicItems.createMjolnir();
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
            return filter(Arrays.asList("cajadofogo", "cajadonecromante", "cajadoraio", "cajadoelfo", "cajadogravidade", "cajadocura", "arcoelfico", "arcoelfonegro", "mjolnir"), args[1]);
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
