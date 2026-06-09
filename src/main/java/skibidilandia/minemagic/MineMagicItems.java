package skibidilandia.minemagic;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Cria e identifica os itens mágicos do plugin MineMagic:
 *
 *  - {@code Cajado de Fogo}: base de tridente. Ao segurar o botão direito o
 *    jogador faz a animação de carregar o tridente e cospe bolas de fogo em
 *    rajada enquanto segura. O tridente em si nunca é arremessado.
 *
 *  - {@code Cajado do Necromante}: base de bastão de blaze. Ao clicar com o
 *    botão direito invoca 3 zumbis com armadura e espada de ferro que lutam ao
 *    lado do invocador e nunca o atacam.
 *
 * A identidade de cada item vive no PDC (sobrevive a soltar/pegar/encantar).
 */
public final class MineMagicItems {

    public static final Material FIRE_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material NECRO_STAFF_MATERIAL = Material.BLAZE_ROD;
    public static final Material LIGHTNING_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material DARK_ELF_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material GRAVITY_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material HEAL_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material ELF_BOW_MATERIAL = Material.BOW;
    public static final Material DARK_ELF_BOW_MATERIAL = Material.BOW;
    public static final Material MJOLNIR_MATERIAL = Material.MACE;

    private static NamespacedKey fireStaffKey;
    private static NamespacedKey necroStaffKey;
    private static NamespacedKey lightningStaffKey;
    private static NamespacedKey darkElfStaffKey;
    private static NamespacedKey gravityStaffKey;
    private static NamespacedKey healStaffKey;
    private static NamespacedKey healTargetsKey;
    private static NamespacedKey elfBowKey;
    private static NamespacedKey darkElfBowKey;
    private static NamespacedKey mjolnirKey;

    private MineMagicItems() {
    }

    public static void init(JavaPlugin plugin) {
        fireStaffKey = new NamespacedKey(plugin, "fire_staff");
        necroStaffKey = new NamespacedKey(plugin, "necro_staff");
        lightningStaffKey = new NamespacedKey(plugin, "lightning_staff");
        darkElfStaffKey = new NamespacedKey(plugin, "dark_elf_staff");
        gravityStaffKey = new NamespacedKey(plugin, "gravity_staff");
        healStaffKey = new NamespacedKey(plugin, "heal_staff");
        healTargetsKey = new NamespacedKey(plugin, "heal_targets");
        elfBowKey = new NamespacedKey(plugin, "elf_bow");
        darkElfBowKey = new NamespacedKey(plugin, "dark_elf_bow");
        mjolnirKey = new NamespacedKey(plugin, "mjolnir");
    }

    // =========================================================================
    //  Cajado de Fogo
    // =========================================================================

    /** Constrói o Cajado de Fogo (base de tridente, indestrutível). */
    public static ItemStack createFireStaff() {
        ItemStack item = new ItemStack(FIRE_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Cajado de Fogo");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Segure o " + ChatColor.RED + "botão direito" + ChatColor.GRAY
                        + " para lançar uma",
                ChatColor.GRAY + "rajada de " + ChatColor.GOLD + "bolas de fogo" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "" + ChatColor.RED + "Shift" + ChatColor.GRAY + " + botão direito: uma "
                        + ChatColor.GOLD + "chuva de fogo" + ChatColor.GRAY,
                ChatColor.GRAY + "desaba sobre a área visada.",
                ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                        + " como um tridente."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(fireStaffKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado de Fogo. */
    public static boolean isFireStaff(ItemStack item) {
        if (item == null || item.getType() != FIRE_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(fireStaffKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Cajado do Necromante
    // =========================================================================

    /** Constrói o Cajado do Necromante (base de bastão de blaze). */
    public static ItemStack createNecromancerStaff() {
        ItemStack item = new ItemStack(NECRO_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Cajado do Necromante");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Botão direito: invoca " + ChatColor.GREEN + "3 zumbis" + ChatColor.GRAY,
                ChatColor.GRAY + "com armadura e espada de " + ChatColor.WHITE + "ferro" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Eles lutam ao seu lado e " + ChatColor.WHITE + "não atacam você" + ChatColor.GRAY + "."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(necroStaffKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado do Necromante. */
    public static boolean isNecromancerStaff(ItemStack item) {
        if (item == null || item.getType() != NECRO_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(necroStaffKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Cajado do Raio
    // =========================================================================

    /** Constrói o Cajado do Raio (base de tridente, indestrutível). */
    public static ItemStack createLightningStaff() {
        ItemStack item = new ItemStack(LIGHTNING_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Cajado do Raio");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Botão direito: um " + ChatColor.AQUA + "raio" + ChatColor.GRAY
                        + " cai do céu",
                ChatColor.GRAY + "sobre a área visada.",
                ChatColor.GRAY + "Segure para lançar uma " + ChatColor.AQUA + "rajada de raios" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                        + " como um tridente."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(lightningStaffKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado do Raio. */
    public static boolean isLightningStaff(ItemStack item) {
        if (item == null || item.getType() != LIGHTNING_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(lightningStaffKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Cajado do Elfo Negro
    // =========================================================================

    /** Constrói o Cajado do Elfo Negro (base de tridente, indestrutível). */
    public static ItemStack createDarkElfStaff() {
        ItemStack item = new ItemStack(DARK_ELF_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Cajado do Elfo Negro");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Botão direito: uma " + ChatColor.DARK_PURPLE + "espiral de flechas" + ChatColor.GRAY,
                ChatColor.GRAY + "chove do céu sobre a área visada.",
                ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                        + " como um tridente."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(darkElfStaffKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado do Elfo Negro. */
    public static boolean isDarkElfStaff(ItemStack item) {
        if (item == null || item.getType() != DARK_ELF_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(darkElfStaffKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Cajado da Gravidade
    // =========================================================================

    /** Constrói o Cajado da Gravidade (base de tridente, indestrutível). */
    public static ItemStack createGravityStaff() {
        ItemStack item = new ItemStack(GRAVITY_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Cajado da Gravidade");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Botão direito: arremessa para o alto e " + ChatColor.AQUA + "puxa" + ChatColor.GRAY,
                ChatColor.GRAY + "mobs e jogadores até você.",
                ChatColor.GRAY + "Shift + botão direito: arremessa para o alto e " + ChatColor.RED + "repele" + ChatColor.GRAY,
                ChatColor.GRAY + "tudo para longe de você.",
                ChatColor.GRAY + "Alcance: " + ChatColor.WHITE + "10x10x10 blocos" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                        + " como um tridente."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(gravityStaffKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado da Gravidade. */
    public static boolean isGravityStaff(ItemStack item) {
        if (item == null || item.getType() != GRAVITY_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(gravityStaffKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Cajado de Cura
    // =========================================================================

    /** Constrói o Cajado de Cura (base de tridente, indestrutível). */
    public static ItemStack createHealStaff() {
        ItemStack item = new ItemStack(HEAL_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Cajado de Cura");
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(healStaffKey, PersistentDataType.BYTE, (byte) 1);
        applyHealLore(meta, Collections.emptyList());
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Cajado de Cura. */
    public static boolean isHealStaff(ItemStack item) {
        if (item == null || item.getType() != HEAL_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(healStaffKey, PersistentDataType.BYTE);
    }

    /** UUIDs dos jogadores atualmente selecionados como alvo deste Cajado de Cura. */
    public static List<UUID> getHealTargets(ItemStack item) {
        List<UUID> out = new ArrayList<>();
        if (!isHealStaff(item)) {
            return out;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(healTargetsKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(";")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                out.add(UUID.fromString(part));
            } catch (IllegalArgumentException ignored) {
                // entrada corrompida: ignora
            }
        }
        return out;
    }

    /**
     * Grava no item a lista ordenada de alvos (UUIDs) e reescreve a lore para
     * mostrar os nomes correspondentes. {@code targetIds} e {@code targetNames}
     * devem ter o mesmo tamanho e ordem.
     */
    public static void setHealTargets(ItemStack item, List<UUID> targetIds, List<String> targetNames) {
        if (!isHealStaff(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < targetIds.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(targetIds.get(i).toString());
        }
        meta.getPersistentDataContainer().set(healTargetsKey, PersistentDataType.STRING, sb.toString());
        applyHealLore(meta, targetNames);
        item.setItemMeta(meta);
    }

    /** Monta a lore do Cajado de Cura: instruções fixas + lista de alvos. */
    private static void applyHealLore(ItemMeta meta, List<String> targetNames) {
        List<String> lore = new ArrayList<>(Arrays.asList(
                ChatColor.GRAY + "Bata em um " + ChatColor.GREEN + "jogador" + ChatColor.GRAY + " para selecioná-lo",
                ChatColor.GRAY + "como alvo (bata de novo para remover).",
                ChatColor.GRAY + "Segure o " + ChatColor.RED + "botão direito" + ChatColor.GRAY + " para " + ChatColor.GREEN + "curar" + ChatColor.GRAY,
                ChatColor.GRAY + "os alvos dentro de " + ChatColor.WHITE + "30x30x30 blocos" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Agache " + ChatColor.RED + "3x rápido" + ChatColor.GRAY + " para limpar os alvos.",
                ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                        + " como um tridente."
        ));
        lore.add("");
        if (targetNames.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "Nenhum alvo selecionado.");
        } else {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Alvos" + ChatColor.GREEN + " (" + targetNames.size() + "):");
            for (String name : targetNames) {
                lore.add(ChatColor.GRAY + " • " + ChatColor.WHITE + name);
            }
        }
        meta.setLore(lore);
    }

    // =========================================================================
    //  Arco Élfico
    // =========================================================================

    /** Constrói o Arco Élfico (base de arco, indestrutível). */
    public static ItemStack createElfBow() {
        ItemStack item = new ItemStack(ELF_BOW_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Arco Élfico");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Atira " + ChatColor.GREEN + "3 flechas" + ChatColor.GRAY + " de uma só vez:",
                ChatColor.GRAY + "uma no centro e uma para cada " + ChatColor.WHITE + "lado" + ChatColor.GRAY + ",",
                ChatColor.GRAY + "espalhadas em leque como no " + ChatColor.GOLD + "Archero" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Carrega " + ChatColor.AQUA + "2x mais rápido" + ChatColor.GRAY + " que um arco normal."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(elfBowKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Arco Élfico. */
    public static boolean isElfBow(ItemStack item) {
        if (item == null || item.getType() != ELF_BOW_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(elfBowKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Arco do Elfo Negro
    // =========================================================================

    /** Constrói o Arco do Elfo Negro (base de arco, indestrutível). */
    public static ItemStack createDarkElfBow() {
        ItemStack item = new ItemStack(DARK_ELF_BOW_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Arco do Elfo Negro");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Segure o " + ChatColor.RED + "botão direito" + ChatColor.GRAY + ": ao ficar",
                ChatColor.GRAY + "" + ChatColor.WHITE + "no talo" + ChatColor.GRAY + ", começa a metralhar " + ChatColor.DARK_PURPLE + "3 flechas",
                ChatColor.GRAY + "em leque, sempre a " + ChatColor.AQUA + "potência máxima" + ChatColor.GRAY + ".",
                ChatColor.DARK_GRAY + "(precisa de ao menos 1 flecha no inventário)"
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(darkElfBowKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Arco do Elfo Negro. */
    public static boolean isDarkElfBow(ItemStack item) {
        if (item == null || item.getType() != DARK_ELF_BOW_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(darkElfBowKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Mjolnir
    // =========================================================================

    /** Constrói o Mjolnir (base de martelo/maça, indestrutível). */
    public static ItemStack createMjolnir() {
        ItemStack item = new ItemStack(MJOLNIR_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Mjolnir");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Botão direito: " + ChatColor.YELLOW + "arremessa o martelo" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Ele volta à sua mão após bater em um " + ChatColor.YELLOW + "alvo" + ChatColor.GRAY,
                ChatColor.GRAY + "ou em uma " + ChatColor.YELLOW + "estrutura" + ChatColor.GRAY + ".",
                ChatColor.GRAY + "Um " + ChatColor.AQUA + "raio" + ChatColor.GRAY + " cai do céu no ponto de impacto."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(mjolnirKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for o Mjolnir. */
    public static boolean isMjolnir(ItemStack item) {
        if (item == null || item.getType() != MJOLNIR_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(mjolnirKey, PersistentDataType.BYTE);
    }
}
