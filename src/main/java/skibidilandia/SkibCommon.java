package skibidilandia;

import org.bukkit.plugin.java.JavaPlugin;

import skibidilandia.plugins.ChatPlugin;
import skibidilandia.plugins.TpPlugin;

public class SkibCommon extends JavaPlugin {
    @Override
    public void onEnable() {
        TpPlugin tpPlugin = new TpPlugin(this);
        ChatPlugin chatPlugin = new ChatPlugin(this);

        tpPlugin.register();
        chatPlugin.register();
    }
}
