package skibidilandia.mcmmo;

import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.player.UserManager;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Ponte com o mcMMO. <b>Todo</b> o acoplamento ao mcMMO mora aqui — o resto do
 * plugin só chama estes métodos com tipos do Bukkit, então o mcMMO continua sendo
 * uma dependência <i>soft</i>: se não estiver instalado, os métodos viram no-op e
 * as habilidades dos itens seguem funcionando normalmente.
 *
 * Por que precisamos disto? O mcMMO só dá XP quando enxerga o jogador como autor
 * de um {@code BlockBreakEvent} real (mineração/lenhador/escavação) ou de um
 * {@code EntityDamageByEntityEvent} de ataque com a arma certa na mão (combate).
 * Vários itens customizados fogem disso: quebram blocos via
 * {@code setType(AIR)}/{@code breakNaturally} (sem evento), causam dano por
 * explosão, ou atacam com a arma fora da mão (arremesso). Para esses casos
 * creditamos o XP manualmente pela própria API do mcMMO.
 */
public final class McmmoXp {

    /** Cache da presença do mcMMO (null = ainda não checado). */
    private static Boolean present;

    private McmmoXp() {
    }

    private static boolean available() {
        if (present == null) {
            present = Bukkit.getPluginManager().getPlugin("mcMMO") != null;
        }
        return present;
    }

    /**
     * Credita o XP que o mcMMO daria por quebrar este bloco (mineração, lenhador,
     * escavação ou herbalismo — ele decide pelo material e pela quantia da sua
     * config). Use quando a quebra real não dispara {@code BlockBreakEvent}.
     *
     * @param before estado do bloco capturado <b>antes</b> de quebrá-lo.
     */
    public static void blockBreak(Player player, BlockState before) {
        if (!available() || player == null || before == null) {
            return;
        }
        try {
            McMMOPlayer mmo = UserManager.getPlayer(player);
            if (mmo != null) {
                ExperienceAPI.addXpFromBlock(before, mmo);
            }
        } catch (Throwable ignored) {
            // mcMMO ausente/incompatível: nunca derruba a habilidade do item.
        }
    }

    /**
     * Credita XP fixo de uma skill (ex.: bedrock, que não tem valor de mineração
     * na tabela do mcMMO). {@code skill} é o nome da {@link PrimarySkillType}.
     */
    public static void flatXp(Player player, String skill, int amount) {
        if (!available() || player == null || amount <= 0) {
            return;
        }
        try {
            ExperienceAPI.addXP(player, skill, amount, "PVE");
        } catch (Throwable ignored) {
        }
    }

    /**
     * Credita XP de combate ao atingir um ser vivo, do jeito do mcMMO (escala pelo
     * mob e pelos multiplicadores da skill). Use quando o dano não é um ataque
     * normal com a arma na mão — dano de explosão ou arma arremessada.
     *
     * @param skill nome da {@link PrimarySkillType} (ex.: "ARCHERY", "SWORDS", "MACES").
     * @param damage dano efetivamente causado (base do XP).
     */
    public static void combat(Player player, LivingEntity victim, String skill, double damage) {
        if (!available() || player == null || victim == null || damage <= 0.0) {
            return;
        }
        try {
            McMMOPlayer mmo = UserManager.getPlayer(player);
            if (mmo != null) {
                ExperienceAPI.addCombatXP(mmo, victim, PrimarySkillType.valueOf(skill), damage);
            }
        } catch (Throwable ignored) {
        }
    }
}
