package skibidilandia.minemagic;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu vertical em boss bars empilhadas, compartilhado pelos cajados com menu
 * (Mago, Curandeiro, Necromante). Cada linha do menu vira uma boss bar; elas
 * aparecem uma sobre a outra no topo da tela, atualizam no lugar (sem poluir o
 * chat) e cabem muito mais opções de forma legível do que a action bar de uma
 * linha só.
 *
 * <p>As barras são reaproveitadas entre os ticks (só os títulos mudam), evitando
 * piscar. {@link #buildLines} monta as linhas com uma janela rolante de até
 * {@link #MAX_VISIBLE} opções centrada na seleção, com indicadores ▲/▼ quando há
 * itens fora da janela — assim o menu continua usável mesmo com muitas opções.
 */
public final class MenuBars {

    /** Máximo de opções visíveis por vez (fora a linha de título). */
    public static final int MAX_VISIBLE = 7;

    private final JavaPlugin plugin;
    private final BossBar.Color color;
    private final Map<UUID, List<BossBar>> shown = new HashMap<>();

    public MenuBars(JavaPlugin plugin, BossBar.Color color) {
        this.plugin = plugin;
        this.color = color;
    }

    /** Atualiza (reaproveitando as barras) o conjunto de linhas exibidas ao jogador. */
    public void render(Player player, List<Component> lines) {
        List<BossBar> bars = shown.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        while (bars.size() < lines.size()) {
            BossBar bar = BossBar.bossBar(Component.empty(), 1.0f, color, BossBar.Overlay.PROGRESS);
            bars.add(bar);
            player.showBossBar(bar);
        }
        while (bars.size() > lines.size()) {
            player.hideBossBar(bars.remove(bars.size() - 1));
        }
        for (int i = 0; i < lines.size(); i++) {
            bars.get(i).name(lines.get(i));
        }
    }

    public void clear(Player player) {
        List<BossBar> bars = shown.remove(player.getUniqueId());
        if (bars != null) {
            for (BossBar bar : bars) {
                player.hideBossBar(bar);
            }
        }
    }

    /** Limpa por UUID (resolve o jogador online; se offline, as barras já sumiram). */
    public void clear(UUID id) {
        if (!shown.containsKey(id)) {
            return;
        }
        Player player = plugin.getServer().getPlayer(id);
        if (player != null) {
            clear(player);
        } else {
            shown.remove(id);
        }
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(shown.keySet())) {
            clear(id);
        }
    }

    /**
     * Monta as linhas de um menu vertical: uma linha de título com a dica de
     * controle e, abaixo, até {@link #MAX_VISIBLE} opções numa janela rolante
     * centrada em {@code selected}, com indicadores ▲/▼ para o que ficou de fora.
     */
    public static List<Component> buildLines(String title, NamedTextColor titleColor,
                                             List<String> labels, int selected) {
        return buildLines(title, titleColor, labels, selected, MAX_VISIBLE);
    }

    /**
     * Igual ao {@link #buildLines(String, NamedTextColor, List, int)}, mas com a
     * janela rolante limitada a {@code maxVisible} opções. Menus com muitas opções
     * (ex.: as 16 almas do Necromante) precisam de uma janela pequena: cada linha
     * vira uma boss bar e, empilhadas demais, elas transbordam a tela e a opção
     * selecionada deixa de aparecer. Mantenha a soma (título + ▲/▼ + janela) baixa.
     */
    public static List<Component> buildLines(String title, NamedTextColor titleColor,
                                             List<String> labels, int selected, int maxVisible) {
        int window = Math.max(1, maxVisible);
        List<Component> out = new ArrayList<>();
        out.add(Component.text(title, titleColor)
                .append(Component.text("  —  scroll · botão direito", NamedTextColor.DARK_GRAY)));

        int n = labels.size();
        int start = 0;
        if (n > window) {
            start = Math.max(0, Math.min(selected - window / 2, n - window));
        }
        int end = Math.min(n, start + window);

        if (start > 0) {
            out.add(Component.text("   ▲ mais " + start, NamedTextColor.DARK_GRAY));
        }
        for (int i = start; i < end; i++) {
            if (i == selected) {
                out.add(Component.text("▶ ", NamedTextColor.YELLOW)
                        .append(Component.text(labels.get(i), NamedTextColor.WHITE)));
            } else {
                out.add(Component.text("   " + labels.get(i), NamedTextColor.GRAY));
            }
        }
        if (end < n) {
            out.add(Component.text("   ▼ mais " + (n - end), NamedTextColor.DARK_GRAY));
        }
        return out;
    }
}
