package skibidilandia;

import org.bukkit.plugin.java.JavaPlugin;

import skibidilandia.blueprint.BlueprintPlugin;
import skibidilandia.enchants.EnchantsPlugin;
import skibidilandia.fuckbedrocks.FuckBedrocksPlugin;
import skibidilandia.furnacetools.FurnaceToolsPlugin;
import skibidilandia.minemagic.MineMagicPlugin;
import skibidilandia.miner.MinerPlugin;
import skibidilandia.plugins.ChatPlugin;
import skibidilandia.tnttools.TNTToolsPlugin;

public class SkibCommon extends JavaPlugin {
    private MinerPlugin minerPlugin;
    private MineMagicPlugin mineMagicPlugin;

    @Override
    public void onEnable() {
        ChatPlugin chatPlugin = new ChatPlugin(this);
        chatPlugin.register();

        minerPlugin = new MinerPlugin(this);
        minerPlugin.register();

        BlueprintPlugin blueprintPlugin = new BlueprintPlugin(this);
        blueprintPlugin.register();

        FurnaceToolsPlugin furnaceToolsPlugin = new FurnaceToolsPlugin(this);
        furnaceToolsPlugin.register();

        FuckBedrocksPlugin fuckBedrocksPlugin = new FuckBedrocksPlugin(this);
        fuckBedrocksPlugin.register();

        TNTToolsPlugin tntToolsPlugin = new TNTToolsPlugin(this);
        tntToolsPlugin.register();

        mineMagicPlugin = new MineMagicPlugin(this);
        mineMagicPlugin.register();

        EnchantsPlugin enchantsPlugin = new EnchantsPlugin(this, furnaceToolsPlugin.getSmelting());
        enchantsPlugin.register();
    }

    @Override
    public void onDisable() {
        if (minerPlugin != null) {
            minerPlugin.shutdown();
        }
        if (mineMagicPlugin != null) {
            mineMagicPlugin.shutdown();
        }
    }
}
