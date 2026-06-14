package skibidilandia;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

/**
 * Liga um item ao seu modelo customizado do resource pack via o componente
 * {@code minecraft:item_model}. Cada id corresponde a {@code assets/skib/items/<id>.json}
 * no pack (namespace "skib"). Usar isto em vez de CustomModelData mantém o pack
 * limpo: apenas itens que chamam {@link #apply} são retexturizados, nenhum item
 * vanilla é afetado.
 */
public final class SkibModel {

    public static final String NAMESPACE = "skib";

    private SkibModel() {
    }

    /** Aplica o modelo {@code skib:<id>} ao item (no-op se o item for nulo). */
    public static void apply(ItemStack item, String id) {
        if (item == null) {
            return;
        }
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(NAMESPACE, id));
    }
}
