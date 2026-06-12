package skibidilandia.miner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Guarda todas as máquinas auxiliares colocadas (Location -> estado) e persiste em
 * machines.yml. Espelha o {@link MinerRegistry}, mas para colhetadeiras/compactadoras.
 */
public class MachineRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, MachineData> machines = new HashMap<>();

    public MachineRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.yml");
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

    public void add(Location loc, MachineData data) {
        machines.put(keyOf(loc), data);
    }

    public MachineData get(Location loc) {
        return machines.get(keyOf(loc));
    }

    public boolean isMachine(Location loc) {
        return machines.containsKey(keyOf(loc));
    }

    public MachineData remove(Location loc) {
        return machines.remove(keyOf(loc));
    }

    /** Itera todas as máquinas com a Location reconstruída (pula mundos não carregados). */
    public void forEach(java.util.function.BiConsumer<Location, MachineData> action) {
        for (Map.Entry<String, MachineData> entry : machines.entrySet()) {
            Location loc = locationOf(entry.getKey());
            if (loc != null) {
                action.accept(loc, entry.getValue());
            }
        }
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("machines");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            MachineType type = MachineType.fromName(sec.getString("type"));
            if (type == null) {
                continue; // tipo desconhecido (versão antiga/removido)
            }
            MachineData data = new MachineData(type);
            data.setTicksUntilNextWork(sec.getInt("ticks", data.getTicksUntilNextWork()));
            ConfigurationSection store = sec.getConfigurationSection("storage");
            if (store != null) {
                for (String slot : store.getKeys(false)) {
                    try {
                        int idx = Integer.parseInt(slot);
                        if (idx >= 0 && idx < MachineData.STORAGE_SIZE) {
                            data.getStorage()[idx] = store.getItemStack(slot);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            machines.put(key, data);
        }
        plugin.getLogger().info("Carregadas " + machines.size() + " máquinas auxiliares.");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, MachineData> entry : machines.entrySet()) {
            String base = "machines." + entry.getKey() + ".";
            MachineData data = entry.getValue();
            config.set(base + "type", data.getType().name());
            config.set(base + "ticks", data.getTicksUntilNextWork());
            ItemStack[] storage = data.getStorage();
            for (int i = 0; i < storage.length; i++) {
                if (storage[i] != null && storage[i].getType() != Material.AIR) {
                    config.set(base + "storage." + i, storage[i]);
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
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar machines.yml", ex);
        }
    }
}
