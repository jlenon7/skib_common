package skibidilandia.minemagic;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Liga o sistema MineMagic: registra os itens, os listeners do Cajado de Fogo e
 * do Cajado do Necromante, e o comando admin para entregar os cajados.
 */
public class MineMagicPlugin implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private MageStaffListeners mageStaffListeners;
    private HealerStaffListeners healerStaffListeners;
    private NecromancerListeners necromancerListeners;
    private ElfBowListeners elfBowListeners;
    private MjolnirListeners mjolnirListeners;
    private WarriorSwordListeners warriorSwordListeners;
    private AssassinListeners assassinListeners;
    private InfinityForgeListeners infinityForgeListeners;

    public MineMagicPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        MineMagicItems.init(plugin);

        mageStaffListeners = new MageStaffListeners(plugin);
        healerStaffListeners = new HealerStaffListeners(plugin);
        necromancerListeners = new NecromancerListeners(plugin);
        elfBowListeners = new ElfBowListeners(plugin);
        mjolnirListeners = new MjolnirListeners(plugin);
        warriorSwordListeners = new WarriorSwordListeners(plugin);
        assassinListeners = new AssassinListeners(plugin);
        infinityForgeListeners = new InfinityForgeListeners(plugin);
        plugin.getServer().getPluginManager().registerEvents(mageStaffListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(healerStaffListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(necromancerListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(elfBowListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(mjolnirListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(warriorSwordListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(assassinListeners, plugin);
        plugin.getServer().getPluginManager().registerEvents(infinityForgeListeners, plugin);
        necromancerListeners.start();
        assassinListeners.start();

        if (plugin.getCommand("minemagic") != null) {
            plugin.getCommand("minemagic").setExecutor(this);
            plugin.getCommand("minemagic").setTabCompleter(this);
        }
    }

    /** Cancela tarefas e remove servos (chamado no onDisable). */
    public void shutdown() {
        if (mageStaffListeners != null) {
            mageStaffListeners.shutdown();
        }
        if (healerStaffListeners != null) {
            healerStaffListeners.shutdown();
        }
        if (necromancerListeners != null) {
            necromancerListeners.shutdown();
        }
        if (elfBowListeners != null) {
            elfBowListeners.shutdown();
        }
        if (mjolnirListeners != null) {
            mjolnirListeners.shutdown();
        }
        if (warriorSwordListeners != null) {
            warriorSwordListeners.shutdown();
        }
        if (assassinListeners != null) {
            assassinListeners.shutdown();
        }
        if (infinityForgeListeners != null) {
            infinityForgeListeners.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("skib.minemagic.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "dar":
                return handleDar(sender, args);
            case "souls":
            case "almas":
                return handleSouls(sender, args);
            case "fundir":
            case "gema":
                return handleFundir(sender, args);
            case "nivel":
            case "level":
                return handleNivel(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Uso:");
        sender.sendMessage(ChatColor.YELLOW + "  /minemagic dar <cajadomago|cajadocurandeiro|cajadonecromante|arcoelfo|mjolnir|espadaguerreiro|adagas|gema|forja> [jogador]");
        sender.sendMessage(ChatColor.YELLOW + "  /minemagic souls add <tipo> <quantidade> [jogador]" + ChatColor.GRAY + " — adiciona almas ao cajado na mão");
        sender.sendMessage(ChatColor.YELLOW + "  /minemagic souls clear [jogador]" + ChatColor.GRAY + " — zera as almas do cajado na mão");
        sender.sendMessage(ChatColor.YELLOW + "  /minemagic souls list [jogador]" + ChatColor.GRAY + " — lista as almas do cajado na mão");
        sender.sendMessage(ChatColor.YELLOW + "  /minemagic fundir [jogador]" + ChatColor.GRAY + " — libera a próxima habilidade da arma na mão (1 gema)");
        sender.sendMessage(ChatColor.YELLOW + "  /minemagic nivel <1-4|max> [jogador]" + ChatColor.GRAY + " — define o nível de habilidades da arma na mão");
    }

    /** /minemagic fundir [jogador] — libera a próxima habilidade da arma na mão (atalho de teste da gema). */
    private boolean handleFundir(CommandSender sender, String[] args) {
        Player target = resolvePlayer(sender, args, 1);
        if (target == null) {
            return true;
        }
        ItemStack weapon = heldUpgradeable(sender, target);
        if (weapon == null) {
            return true;
        }
        if (MineMagicItems.getAbilityLevel(weapon) >= MineMagicItems.getMaxAbilityLevel(weapon)) {
            sender.sendMessage(ChatColor.RED + "A arma de " + target.getName()
                    + " já tem todas as habilidades liberadas.");
            return true;
        }
        int newLevel = MineMagicItems.unlockNextAbility(weapon);
        target.getInventory().setItemInMainHand(weapon);
        sender.sendMessage(ChatColor.GREEN + "Habilidade liberada: " + ChatColor.WHITE
                + MineMagicItems.abilityName(weapon, newLevel - 1) + ChatColor.GREEN + " (nível " + newLevel
                + "/" + MineMagicItems.getMaxAbilityLevel(weapon) + ") para " + target.getName() + ".");
        return true;
    }

    /** /minemagic nivel <1-4|max> [jogador] — define direto o nível de habilidades da arma na mão. */
    private boolean handleNivel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /minemagic nivel <1-4|max> [jogador]");
            return true;
        }
        Player target = resolvePlayer(sender, args, 2);
        if (target == null) {
            return true;
        }
        ItemStack weapon = heldUpgradeable(sender, target);
        if (weapon == null) {
            return true;
        }
        int max = MineMagicItems.getMaxAbilityLevel(weapon);
        int level;
        if (args[1].equalsIgnoreCase("max")) {
            level = max;
        } else {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Nível inválido: " + args[1] + " (use 1-" + max + " ou max).");
                return true;
            }
        }
        MineMagicItems.setAbilityLevel(weapon, level);
        target.getInventory().setItemInMainHand(weapon);
        sender.sendMessage(ChatColor.GREEN + "Nível de habilidades da arma de " + target.getName()
                + " definido para " + ChatColor.WHITE + MineMagicItems.getAbilityLevel(weapon) + "/" + max + ".");
        return true;
    }

    /** Arma upgradeável na mão principal do alvo; manda erro e devolve null se não estiver. */
    private ItemStack heldUpgradeable(CommandSender sender, Player target) {
        ItemStack weapon = target.getInventory().getItemInMainHand();
        if (!MineMagicItems.isUpgradeable(weapon)) {
            sender.sendMessage(ChatColor.RED + target.getName()
                    + " precisa segurar uma arma mágica (adagas, arco, cajado do mago/curandeiro ou espada) na mão.");
            return null;
        }
        return weapon;
    }

    /** /minemagic souls <add|clear|list> ... — gerencia as almas do Cajado do Necromante na mão. */
    private boolean handleSouls(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "add":
                return handleSoulsAdd(sender, args);
            case "clear":
                return handleSoulsClear(sender, args);
            case "list":
                return handleSoulsList(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleDar(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
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
            sender.sendMessage(ChatColor.RED + "Item inválido. Use: cajadomago, cajadocurandeiro, cajadonecromante, arcoelfo, mjolnir, espadaguerreiro, adagas, gema ou forja.");
            return true;
        }

        target.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "Entregue: " + ChatColor.WHITE + args[1].toLowerCase()
                + ChatColor.GREEN + " para " + target.getName() + ".");
        return true;
    }

    /** /minemagic souls add <tipo> <quantidade> [jogador] — adiciona almas ao cajado na mão. */
    private boolean handleSoulsAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /minemagic souls add <tipo> <quantidade> [jogador]");
            sender.sendMessage(ChatColor.GRAY + "Ex.: /minemagic souls add ZOMBIE 64");
            return true;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Tipo inválido: " + args[2]
                    + ChatColor.GRAY + " (ex.: ZOMBIE, SKELETON, BLAZE, ENDER_DRAGON)");
            return true;
        }
        if (!MineMagicItems.isCollectibleType(type)) {
            sender.sendMessage(ChatColor.RED + "Tipo não coletável: " + MineMagicItems.prettyName(type)
                    + ChatColor.GRAY + " (use um dos 17 tipos do cajado, ex.: ZOMBIE, WITHER_SKELETON).");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Quantidade inválida: " + args[3]);
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "A quantidade deve ser maior que zero.");
            return true;
        }
        Player target = resolvePlayer(sender, args, 4);
        if (target == null) {
            return true;
        }
        ItemStack staff = heldStaff(sender, target);
        if (staff == null) {
            return true;
        }
        MineMagicItems.addSouls(staff, type, amount);
        target.getInventory().setItemInMainHand(staff);
        sender.sendMessage(ChatColor.GREEN + "Adicionadas " + ChatColor.WHITE + amount
                + " alma(s) de " + MineMagicItems.prettyName(type) + ChatColor.GREEN
                + " ao cajado de " + target.getName() + ".");
        return true;
    }

    /** /minemagic souls clear [jogador] — zera as almas do cajado na mão do alvo. */
    private boolean handleSoulsClear(CommandSender sender, String[] args) {
        Player target = resolvePlayer(sender, args, 2);
        if (target == null) {
            return true;
        }
        ItemStack staff = heldStaff(sender, target);
        if (staff == null) {
            return true;
        }
        MineMagicItems.clearSouls(staff);
        target.getInventory().setItemInMainHand(staff);
        sender.sendMessage(ChatColor.GREEN + "Almas do Cajado do Necromante de "
                + target.getName() + " foram zeradas.");
        return true;
    }

    /** /minemagic souls list [jogador] — lista as almas do cajado na mão do alvo. */
    private boolean handleSoulsList(CommandSender sender, String[] args) {
        Player target = resolvePlayer(sender, args, 2);
        if (target == null) {
            return true;
        }
        ItemStack staff = heldStaff(sender, target);
        if (staff == null) {
            return true;
        }
        Map<EntityType, Integer> souls = MineMagicItems.getSouls(staff);
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Almas do cajado de " + target.getName() + ":");
        if (souls.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  (nenhuma)");
        } else {
            int total = 0;
            Map<String, Integer> sorted = new TreeMap<>();
            for (Map.Entry<EntityType, Integer> e : souls.entrySet()) {
                sorted.put(MineMagicItems.prettyName(e.getKey()), e.getValue());
                total += e.getValue();
            }
            for (Map.Entry<String, Integer> e : sorted.entrySet()) {
                sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + e.getKey()
                        + ChatColor.GRAY + ": " + ChatColor.AQUA + e.getValue());
            }
            sender.sendMessage(ChatColor.DARK_GRAY + "  total: " + total
                    + " (" + sorted.size() + " tipos)");
        }
        EntityType sel = MineMagicItems.getSelType(staff);
        if (sel != null) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "Seleção: " + ChatColor.WHITE
                    + MineMagicItems.prettyName(sel) + " x" + MineMagicItems.getSelQty(staff));
        }
        return true;
    }

    /** Resolve o jogador-alvo em args[idx], ou o próprio sender. Manda erro e devolve null se falhar. */
    private Player resolvePlayer(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            Player p = Bukkit.getPlayerExact(args[idx]);
            if (p == null) {
                sender.sendMessage(ChatColor.RED + "Jogador não encontrado: " + args[idx]);
            }
            return p;
        }
        if (sender instanceof Player) {
            return (Player) sender;
        }
        sender.sendMessage(ChatColor.RED + "Especifique um jogador.");
        return null;
    }

    /** Cajado do Necromante na mão principal do alvo; manda erro e devolve null se não estiver. */
    private ItemStack heldStaff(CommandSender sender, Player target) {
        ItemStack staff = target.getInventory().getItemInMainHand();
        if (!MineMagicItems.isNecromancerStaff(staff)) {
            sender.sendMessage(ChatColor.RED + target.getName()
                    + " precisa segurar o Cajado do Necromante na mão principal.");
            return null;
        }
        return staff;
    }

    /** Constrói o cajado pedido, ou null se o argumento for inválido. */
    private static ItemStack build(String arg) {
        switch (arg.toLowerCase()) {
            case "cajadomago":
            case "mago":
            case "magestaff":
            case "mage":
                return MineMagicItems.createMageStaff();
            case "cajadocurandeiro":
            case "curandeiro":
            case "healerstaff":
            case "healer":
                return MineMagicItems.createHealerStaff();
            case "cajadonecromante":
            case "necromante":
            case "necrostaff":
            case "necro":
                return MineMagicItems.createNecromancerStaff();
            case "arcoelfo":
            case "arcodoelfo":
            case "arcoelfico":
            case "elfo":
            case "arco":
            case "elfbow":
            case "elf":
                return MineMagicItems.createElfBow();
            case "mjolnir":
            case "martelo":
            case "hammer":
            case "thor":
                return MineMagicItems.createMjolnir();
            case "espadaguerreiro":
            case "espada":
            case "guerreiro":
            case "warriorsword":
            case "warrior":
                return MineMagicItems.createWarriorSword();
            case "adagas":
            case "adagasassassino":
            case "assassino":
            case "assassin":
            case "daggers":
                return MineMagicItems.createAssassinDaggers();
            case "gema":
            case "gemainfinito":
            case "gemadoinfinito":
            case "infinitygem":
            case "gem":
                return MineMagicItems.createInfinityGem(1);
            case "forja":
            case "forjainfinito":
            case "forjadoinfinito":
            case "infinityforge":
            case "forge":
                return MineMagicItems.createInfinityForge();
            default:
                return null;
        }
    }

    private static final List<String> ITEM_ARGS = Arrays.asList(
            "cajadomago", "cajadocurandeiro", "cajadonecromante", "arcoelfo",
            "mjolnir", "espadaguerreiro", "adagas", "gema", "forja");
    private static final List<String> SOUL_TYPE_HINTS = Arrays.asList(
            "ZOMBIE", "SKELETON", "HORSE", "BLAZE", "GHAST", "SPIDER", "ENDER_DRAGON",
            "GIANT", "RAVAGER", "VEX", "BREEZE", "ELDER_GUARDIAN", "ENDERMAN", "PHANTOM",
            "WARDEN", "WITCH", "WITHER_SKELETON");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("dar", "souls", "fundir", "nivel"), args[0]);
        }
        String sub = args[0].toLowerCase();
        boolean souls = sub.equals("souls") || sub.equals("almas");
        if (args.length == 2) {
            if (sub.equals("dar")) {
                return filter(ITEM_ARGS, args[1]);
            }
            if (souls) {
                return filter(Arrays.asList("add", "clear", "list"), args[1]);
            }
            if (sub.equals("fundir") || sub.equals("gema")) {
                return filter(onlinePlayerNames(), args[1]);
            }
            if (sub.equals("nivel") || sub.equals("level")) {
                return filter(Arrays.asList("1", "2", "3", "4", "max"), args[1]);
            }
            return new ArrayList<>();
        }
        if (args.length == 3) {
            if (sub.equals("dar")) {
                return filter(onlinePlayerNames(), args[2]);
            }
            if (sub.equals("nivel") || sub.equals("level")) {
                return filter(onlinePlayerNames(), args[2]);
            }
            if (souls) {
                String op = args[1].toLowerCase();
                if (op.equals("add")) {
                    return filter(SOUL_TYPE_HINTS, args[2]);
                }
                // clear / list: terceiro arg é o jogador (opcional)
                return filter(onlinePlayerNames(), args[2]);
            }
            return new ArrayList<>();
        }
        if (args.length == 4 && souls && args[1].equalsIgnoreCase("add")) {
            return filter(Arrays.asList("1", "10", "64", "100"), args[3]);
        }
        if (args.length == 5 && souls && args[1].equalsIgnoreCase("add")) {
            return filter(onlinePlayerNames(), args[4]);
        }
        return new ArrayList<>();
    }

    private static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
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
