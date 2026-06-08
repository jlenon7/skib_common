package skibidilandia.furnacetools;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tabela de fundição: material de entrada -> resultado da fornalha.
 *
 * Em vez de fixar centenas de combinações na mão, lemos as próprias receitas de
 * fornalha do servidor ({@link FurnaceRecipe}) no boot. Isso garante que TODAS
 * as combinações possíveis da versão atual do jogo (e de qualquer datapack)
 * fiquem cobertas automaticamente — minério bruto -> lingote, areia -> vidro,
 * tronco -> carvão vegetal, argila -> tijolo, e assim por diante.
 *
 * Usamos só {@link FurnaceRecipe} (a fornalha comum). Alto-forno e defumador
 * são subtipos diferentes de receita de cozimento e dariam os mesmos
 * resultados, então não precisamos deles aqui.
 */
public final class FurnaceSmelting {

    private final Map<Material, ItemStack> results = new EnumMap<>(Material.class);

    /** Varre as receitas do servidor e monta a tabela. Retorna quantas entradas mapeou. */
    public int build(Server server) {
        results.clear();
        Iterator<Recipe> it = server.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (!(recipe instanceof FurnaceRecipe)) {
                continue;
            }
            FurnaceRecipe furnace = (FurnaceRecipe) recipe;
            ItemStack result = furnace.getResult();
            if (result == null || result.getType() == Material.AIR) {
                continue;
            }
            for (Material input : inputsOf(furnace)) {
                // putIfAbsent: se duas receitas mapeiam a mesma entrada, a primeira vence.
                results.putIfAbsent(input, result.clone());
            }
        }
        return results.size();
    }

    /** Resultado da fundição de um material, ou null se não houver receita de fornalha. */
    public ItemStack smelt(Material input) {
        ItemStack result = results.get(input);
        return result == null ? null : result.clone();
    }

    /** Extrai todos os materiais de entrada de uma receita (lida com MaterialChoice). */
    private static Iterable<Material> inputsOf(FurnaceRecipe furnace) {
        RecipeChoice choice = furnace.getInputChoice();
        if (choice instanceof RecipeChoice.MaterialChoice) {
            return ((RecipeChoice.MaterialChoice) choice).getChoices();
        }
        // Fallback para receitas com um único ingrediente.
        return java.util.Collections.singletonList(furnace.getInput().getType());
    }
}
