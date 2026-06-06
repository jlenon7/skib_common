package skibidilandia.miner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Guarda todas as mineradoras colocadas (Location -> estado) e persiste em miners.yml.
 */
public class MinerRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, MinerData> miners = new HashMap<>();

    public MinerRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "miners.yml");
    }

    private static String keyOf(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private static Location locationOf(String key) {
        String[] parts = key.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void add(Location loc, MinerData data) {
        miners.put(keyOf(loc), data);
    }

    public MinerData get(Location loc) {
        return miners.get(keyOf(loc));
    }

    public boolean isMiner(Location loc) {
        return miners.containsKey(keyOf(loc));
    }

    public MinerData remove(Location loc) {
        return miners.remove(keyOf(loc));
    }

    /** Itera todas as mineradoras com a Location reconstruída (pula mundos não carregados). */
    public void forEach(java.util.function.BiConsumer<Location, MinerData> action) {
        for (Map.Entry<String, MinerData> entry : miners.entrySet()) {
            Location loc = locationOf(entry.getKey());
            if (loc != null) {
                action.accept(loc, entry.getValue());
            }
        }
    }

    public Collection<MinerData> all() {
        return miners.values();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("miners");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            MinerData data = new MinerData(sec.getInt("level", 1));
            data.setTicksUntilNextCycle(sec.getInt("ticks", data.getTicksUntilNextCycle()));
            data.setCycleLengthTicks(sec.getInt("cycleLength", data.getCycleLengthTicks()));
            data.setFuelCyclesRemaining(sec.getInt("fuelCycles", 0));
            ConfigurationSection out = sec.getConfigurationSection("output");
            if (out != null) {
                for (String slot : out.getKeys(false)) {
                    try {
                        int idx = Integer.parseInt(slot);
                        if (idx >= 0 && idx < MinerData.OUTPUT_SIZE) {
                            data.getOutput()[idx] = out.getItemStack(slot);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            // migração: combustível que ficava no slot dedicado agora vai pra saída
            ItemStack oldFuel = sec.getItemStack("fuel");
            if (oldFuel != null && oldFuel.getType() != org.bukkit.Material.AIR) {
                data.addOutput(oldFuel.getType(), oldFuel.getAmount());
            }
            miners.put(key, data);
        }
        plugin.getLogger().info("Carregadas " + miners.size() + " mineradoras.");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, MinerData> entry : miners.entrySet()) {
            String base = "miners." + entry.getKey() + ".";
            MinerData data = entry.getValue();
            config.set(base + "level", data.getLevel());
            config.set(base + "ticks", data.getTicksUntilNextCycle());
            config.set(base + "cycleLength", data.getCycleLengthTicks());
            config.set(base + "fuelCycles", data.getFuelCyclesRemaining());
            ItemStack[] output = data.getOutput();
            for (int i = 0; i < output.length; i++) {
                if (output[i] != null && output[i].getType() != org.bukkit.Material.AIR) {
                    config.set(base + "output." + i, output[i]);
                }
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar miners.yml", ex);
        }
    }
}
