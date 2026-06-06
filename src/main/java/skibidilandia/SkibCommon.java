package skibidilandia;

import org.bukkit.plugin.java.JavaPlugin;

import skibidilandia.blueprint.BlueprintPlugin;
import skibidilandia.miner.MinerPlugin;
import skibidilandia.plugins.ChatPlugin;

public class SkibCommon extends JavaPlugin {
    private MinerPlugin minerPlugin;

    @Override
    public void onEnable() {
        ChatPlugin chatPlugin = new ChatPlugin(this);
        chatPlugin.register();

        minerPlugin = new MinerPlugin(this);
        minerPlugin.register();

        BlueprintPlugin blueprintPlugin = new BlueprintPlugin(this);
        blueprintPlugin.register();
    }

    @Override
    public void onDisable() {
        if (minerPlugin != null) {
            minerPlugin.shutdown();
        }
    }
}
