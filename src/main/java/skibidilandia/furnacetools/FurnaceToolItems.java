package skibidilandia.furnacetools;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Cria e identifica as ferramentas da fornalha. O tipo e o estado (ligado/
 * desligado) ficam gravados no PDC do item (sobrevive a soltar/pegar/encantar),
 * então qualquer encantamento que o jogador adicionar depois continua valendo —
 * a base continua sendo a mesma ferramenta de ferro.
 */
public final class FurnaceToolItems {

    private static NamespacedKey typeKey;
    private static NamespacedKey enabledKey;

    private FurnaceToolItems() {
    }

    public static void init(JavaPlugin plugin) {
        typeKey = new NamespacedKey(plugin, "furnace_tool_type");
        enabledKey = new NamespacedKey(plugin, "furnace_tool_enabled");
    }

    /** Constrói a ferramenta da fornalha de um tipo (base de ferro, sem encantos, ligada). */
    public static ItemStack create(FurnaceToolType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + type.getDisplayName());
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE, (byte) 1);
        applyLore(meta, true);
        item.setItemMeta(meta);
        return item;
    }

    /** Retorna o tipo se o item for uma ferramenta da fornalha, ou null. */
    public static FurnaceToolType readType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String key = pdc.get(typeKey, PersistentDataType.STRING);
        return FurnaceToolType.fromKey(key);
    }

    /** Se a auto-fundição está ligada. Ferramentas antigas (sem a flag) contam como ligadas. */
    public static boolean isEnabled(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer()
                .get(enabledKey, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    /**
     * Liga/desliga a auto-fundição do item (atualiza PDC e lore) e devolve o
     * item modificado. Não muda o item original passado por referência caso o
     * servidor entregue uma cópia — sempre use o retorno.
     */
    public static ItemStack setEnabled(ItemStack item, boolean enabled) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        applyLore(meta, enabled);
        item.setItemMeta(meta);
        return item;
    }

    private static void applyLore(ItemMeta meta, boolean enabled) {
        String status = enabled
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "LIGADA"
                : ChatColor.RED + "" + ChatColor.BOLD + "DESLIGADA";
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Funde automaticamente o que você quebra,",
                ChatColor.GRAY + "como se passasse pela fornalha.",
                ChatColor.DARK_GRAY + "Eficiência e durabilidade de ferro.",
                ChatColor.DARK_GRAY + "Fortuna multiplica • Toque Suave não tem efeito.",
                "",
                ChatColor.GRAY + "Auto-fundição: " + status,
                ChatColor.DARK_GRAY + "Agache + botão direito para alternar."
        ));
    }
}
