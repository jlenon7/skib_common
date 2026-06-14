package skibidilandia.minemagic;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Spider;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import skibidilandia.SkibModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cria e identifica os itens mágicos do plugin MineMagic:
 *
 *  - {@code Cajado de Fogo}: base de tridente. Ao segurar o botão direito o
 *    jogador faz a animação de carregar o tridente e cospe bolas de fogo em
 *    rajada enquanto segura. O tridente em si nunca é arremessado.
 *
 *  - {@code Cajado do Necromante}: base de tridente. Ao clicar com o
 *    botão direito invoca 3 zumbis com armadura e espada de ferro que lutam ao
 *    lado do invocador e nunca o atacam.
 *
 * A identidade de cada item vive no PDC (sobrevive a soltar/pegar/encantar).
 */
public final class MineMagicItems {

    public static final Material MAGE_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material HEALER_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material NECRO_STAFF_MATERIAL = Material.TRIDENT;
    public static final Material ELF_BOW_MATERIAL = Material.BOW;
    public static final Material MJOLNIR_MATERIAL = Material.MACE;
    public static final Material ASSASSIN_DAGGERS_MATERIAL = Material.NETHERITE_SWORD;
    public static final Material WARRIOR_SWORD_MATERIAL = Material.NETHERITE_SWORD;

    // Itens do sistema de nivelamento (fusão libera habilidades).
    public static final Material INFINITY_GEM_MATERIAL = Material.AMETHYST_SHARD;
    public static final Material INFINITY_FORGE_MATERIAL = Material.SMITHING_TABLE;

    /** Nomes das habilidades por arma, na ordem das teclas (1, 2, 3, ...). A habilidade
     *  de índice {@code i} (tecla {@code i+1}) só está liberada se o nível ≥ {@code i+1}. */
    private static final String[] MAGE_ABILITIES = {"Fogo", "Raio", "Congelar", "Levitar"};
    private static final String[] MAGE_DESCS = {
            "Segure o botão direito para uma rajada de bolas de fogo. Shift + botão direito: chuva de fogo na área.",
            "Botão direito faz um raio cair na área visada (segure para uma rajada). Shift + botão direito: chuva de raios.",
            "Botão direito congela o alvo visado (jogador ou mob) por alguns segundos.",
            "Lança você para o alto na hora. Recarga de 15s."};

    private static final String[] HEALER_ABILITIES = {"Cura", "Gravidade", "Limpar alvos"};
    private static final String[] HEALER_DESCS = {
            "Bata em um jogador para marcá-lo como alvo (bata de novo para remover). Segure o botão direito para curar os alvos em 30x30x30.",
            "Botão direito arremessa tudo para o alto e puxa até você. Shift + botão direito: repele tudo em 10x10x10.",
            "Remove todos os alvos de cura selecionados."};

    private static final String[] ELF_BOW_ABILITIES = {"Flechas explosivas", "Disparo em espiral", "Chuva de flechas"};
    private static final String[] ELF_BOW_DESCS = {
            "Por 10s, suas flechas causam explosão e sangramento ao acertar.",
            "Por 10s, cada disparo solta 6 flechas em espiral.",
            "Faz chover flechas sobre a área visada (aproveita as habilidades 1 e 2 ativas)."};

    private static final String[] WARRIOR_ABILITIES = {"Arremesso de espada", "Postura defensiva", "Domo de bedrock"};
    private static final String[] WARRIOR_PASSIVES = {
            "20% de chance de causar uma explosão (1 TNT) ao atacar."};
    private static final String[] WARRIOR_DESCS = {
            "Arremessa a espada, causando dano ao acertar. Ela volta à sua mão.",
            "Ganha Resistência + Lentidão por 10s.",
            "Ergue um domo de bedrock 20x20x20 prendendo os inimigos por 20s."};

    private static final String[] DAGGER_ABILITIES = {"Leque de adagas", "Clone das sombras", "Tornado de adagas", "Execução"};
    private static final String[] DAGGER_PASSIVES = {
            "20% de chance de sangramento ao acertar."};
    private static final String[] DAGGER_DESCS = {
            "Arremessa adagas na direção da mira.",
            "Joga um clone das sombras 5 blocos à frente que dura 10s e copia as habilidades 1 e 3. Aperte 2 de novo para trocar de lugar com ele.",
            "Cria um tornado de adagas ao seu redor que fere e arremessa os inimigos próximos.",
            "Teleporta para as costas do inimigo mirado e deixa um clone no lugar. Marca o alvo: a execução explode em 5s (quanto mais dano causado, maior a explosão). Aperte 4 de novo para trocar de lugar. Recarga de 20s."};

    /** Largura visível alvo (sem códigos de cor) para quebrar a lore em linhas. */
    private static final int LORE_WIDTH = 46;

    // Modos das classes (guardados no PDC do cajado).
    public static final String MAGE_FIRE = "FIRE";
    public static final String MAGE_LIGHTNING = "LIGHTNING";
    public static final String MAGE_FREEZE = "FREEZE";
    public static final String HEALER_HEAL = "HEAL";
    public static final String HEALER_GRAVITY = "GRAVITY";

    // Ids de modelo do resource pack (componente item_model, namespace "skib").
    private static String mageModelId(String mode) {
        if (MAGE_LIGHTNING.equals(mode)) {
            return "cajado_mago_raio";
        }
        if (MAGE_FREEZE.equals(mode)) {
            return "cajado_mago_congelar";
        }
        return "cajado_mago_fogo";
    }

    private static String healerModelId(String mode) {
        return HEALER_GRAVITY.equals(mode) ? "cajado_curandeiro_gravidade" : "cajado_curandeiro_cura";
    }

    /** Em testes, o cajado entregue pelo admin já vem com almas de tudo. */
    private static final boolean SEED_TEST_SOULS = true;
    private static final int SEED_TEST_AMOUNT = 100;

    /** Os 16 tipos de alma coletáveis/invocáveis, na ordem em que aparecem no menu.
     *  (O Ender Dragon foi removido: não é controlável montado sem brigar com o anticheat.) */
    public static final List<EntityType> COLLECTIBLE_TYPES = Collections.unmodifiableList(Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HORSE, EntityType.BLAZE,
            EntityType.GHAST, EntityType.SPIDER, EntityType.GIANT,
            EntityType.RAVAGER, EntityType.VEX, EntityType.BREEZE, EntityType.ELDER_GUARDIAN,
            EntityType.ENDERMAN, EntityType.PHANTOM, EntityType.WARDEN, EntityType.WITCH,
            EntityType.WITHER_SKELETON));

    /** Teto de servos vivos por tipo (tipos ausentes usam {@link #DEFAULT_CAP}). */
    private static final int DEFAULT_CAP = 50;
    private static final Map<EntityType, Integer> CAPS = new HashMap<>();
    static {
        CAPS.put(EntityType.HORSE, 1);
        CAPS.put(EntityType.GIANT, 1);
        CAPS.put(EntityType.RAVAGER, 1);
        CAPS.put(EntityType.ELDER_GUARDIAN, 1);
        CAPS.put(EntityType.WARDEN, 1);
        CAPS.put(EntityType.BLAZE, 3);
        CAPS.put(EntityType.GHAST, 3);
        CAPS.put(EntityType.WITCH, 3);
        CAPS.put(EntityType.WITHER_SKELETON, 3);
    }

    private static NamespacedKey mageStaffKey;
    private static NamespacedKey mageModeKey;
    private static NamespacedKey healerStaffKey;
    private static NamespacedKey healerModeKey;
    private static NamespacedKey healTargetsKey;
    private static NamespacedKey healTargetNamesKey;
    private static NamespacedKey necroStaffKey;
    private static NamespacedKey necroSoulsKey;
    private static NamespacedKey necroSelTypeKey;
    private static NamespacedKey necroSelQtyKey;
    private static NamespacedKey elfBowKey;
    private static NamespacedKey mjolnirKey;
    private static NamespacedKey warriorSwordKey;
    private static NamespacedKey assassinDaggersKey;
    /** Nível de habilidades liberadas (1 = só a primeira). Compartilhado por todas as armas upgradeáveis. */
    private static NamespacedKey abilityLevelKey;
    private static NamespacedKey infinityGemKey;
    private static NamespacedKey infinityForgeKey;

    private MineMagicItems() {
    }

    public static void init(JavaPlugin plugin) {
        mageStaffKey = new NamespacedKey(plugin, "mage_staff");
        mageModeKey = new NamespacedKey(plugin, "mage_mode");
        healerStaffKey = new NamespacedKey(plugin, "healer_staff");
        healerModeKey = new NamespacedKey(plugin, "healer_mode");
        healTargetsKey = new NamespacedKey(plugin, "heal_targets");
        healTargetNamesKey = new NamespacedKey(plugin, "heal_target_names");
        necroStaffKey = new NamespacedKey(plugin, "necro_staff");
        necroSoulsKey = new NamespacedKey(plugin, "necro_souls");
        necroSelTypeKey = new NamespacedKey(plugin, "necro_sel_type");
        necroSelQtyKey = new NamespacedKey(plugin, "necro_sel_qty");
        elfBowKey = new NamespacedKey(plugin, "elf_bow");
        mjolnirKey = new NamespacedKey(plugin, "mjolnir");
        warriorSwordKey = new NamespacedKey(plugin, "warrior_sword");
        assassinDaggersKey = new NamespacedKey(plugin, "assassin_daggers");
        abilityLevelKey = new NamespacedKey(plugin, "ability_level");
        infinityGemKey = new NamespacedKey(plugin, "infinity_gem");
        infinityForgeKey = new NamespacedKey(plugin, "infinity_forge");
    }

    // =========================================================================
    //  Cajado do Mago (classe: alterna entre Fogo e Raio)
    // =========================================================================

    /** Constrói o Cajado do Mago no modo Fogo (base de tridente, indestrutível). */
    public static ItemStack createMageStaff() {
        ItemStack item = new ItemStack(MAGE_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(mageStaffKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(abilityLevelKey, PersistentDataType.INTEGER, 1);
        applyMageMode(meta, MAGE_FIRE);
        item.setItemMeta(meta);
        SkibModel.apply(item, mageModelId(MAGE_FIRE));
        return item;
    }

    /** True se o item for o Cajado do Mago. */
    public static boolean isMageStaff(ItemStack item) {
        if (item == null || item.getType() != MAGE_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(mageStaffKey, PersistentDataType.BYTE);
    }

    /** Modo atual do Cajado do Mago ({@link #MAGE_FIRE} por padrão). */
    public static String getMageMode(ItemStack item) {
        if (!isMageStaff(item)) {
            return MAGE_FIRE;
        }
        String m = item.getItemMeta().getPersistentDataContainer().get(mageModeKey, PersistentDataType.STRING);
        if (MAGE_LIGHTNING.equals(m) || MAGE_FREEZE.equals(m)) {
            return m;
        }
        return MAGE_FIRE;
    }

    /** Troca o modo do Cajado do Mago e atualiza modelo, nome e lore. */
    public static void setMageMode(ItemStack item, String mode) {
        if (!isMageStaff(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        applyMageMode(meta, mode);
        item.setItemMeta(meta);
        SkibModel.apply(item, mageModelId(getMageMode(item)));
    }

    /** Modo do Cajado do Mago lido direto do meta ({@link #MAGE_FIRE} por padrão). */
    private static String getMageMode(ItemMeta meta) {
        String m = meta.getPersistentDataContainer().get(mageModeKey, PersistentDataType.STRING);
        if (MAGE_LIGHTNING.equals(m) || MAGE_FREEZE.equals(m)) {
            return m;
        }
        return MAGE_FIRE;
    }

    /** Aplica nome/modelo/lore correspondentes ao modo do Cajado do Mago. */
    private static void applyMageMode(ItemMeta meta, String mode) {
        String resolved;
        if (MAGE_LIGHTNING.equals(mode)) {
            resolved = MAGE_LIGHTNING;
        } else if (MAGE_FREEZE.equals(mode)) {
            resolved = MAGE_FREEZE;
        } else {
            resolved = MAGE_FIRE;
        }
        meta.getPersistentDataContainer().set(mageModeKey, PersistentDataType.STRING, resolved);
        meta.setDisplayName(ChatColor.BLUE + "" + ChatColor.BOLD + "Cajado do Mago");
        int activeIndex = MAGE_LIGHTNING.equals(resolved) ? 1 : MAGE_FREEZE.equals(resolved) ? 2 : 0;
        meta.setLore(abilityLore(meta, null, MAGE_ABILITIES, MAGE_DESCS, activeIndex));
    }

    // =========================================================================
    //  Cajado do Necromante
    // =========================================================================

    /**
     * Constrói o Cajado do Necromante (base de tridente, indestrutível).
     *
     * Estilo Solo Leveling: o cajado coleta as almas dos mobs mortos pelo dono e
     * permite invocar exércitos dessas almas, gastando-as. As contagens de almas e
     * a seleção atual vivem no PDC do próprio item (acompanham o item).
     */
    public static ItemStack createNecromancerStaff() {
        ItemStack item = new ItemStack(NECRO_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Cajado do Necromante");
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(necroStaffKey, PersistentDataType.BYTE, (byte) 1);

        Map<EntityType, Integer> souls = SEED_TEST_SOULS ? buildTestSouls() : new LinkedHashMap<>();
        writeSouls(meta, souls);
        rebuildLore(meta, souls, getSelType(meta), getSelQty(meta));
        item.setItemMeta(meta);
        SkibModel.apply(item, "cajado_necromante");
        return item;
    }

    /** True se o item for o Cajado do Necromante. */
    public static boolean isNecromancerStaff(ItemStack item) {
        if (item == null || item.getType() != NECRO_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(necroStaffKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ almas

    /**
     * Tipo de alma canônico para um mob, ou {@code null} se não for coletável.
     * Variantes são normalizadas: husk/drowned/zombie villager → ZOMBIE,
     * stray/bogged → SKELETON, cave spider → SPIDER. O wither skeleton é seu
     * próprio tipo, por isso é checado antes do esqueleto comum.
     */
    public static EntityType soulTypeOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof WitherSkeleton) {
            return EntityType.WITHER_SKELETON;
        }
        if (entity instanceof AbstractSkeleton) { // skeleton, stray, bogged
            return EntityType.SKELETON;
        }
        if (entity instanceof Zombie) { // husk, drowned, zombie villager, zombified piglin (Giant NÃO é Zombie)
            return EntityType.ZOMBIE;
        }
        if (entity instanceof Spider) { // spider, cave spider
            return EntityType.SPIDER;
        }
        EntityType type = entity.getType();
        return COLLECTIBLE_TYPES.contains(type) ? type : null;
    }

    /** True se o mob morto rende uma alma coletável. */
    public static boolean isCollectible(Entity entity) {
        return soulTypeOf(entity) != null;
    }

    /** True se {@code type} é um dos tipos de alma canônicos. */
    public static boolean isCollectibleType(EntityType type) {
        return COLLECTIBLE_TYPES.contains(type);
    }

    /** Máximo de servos vivos desse tipo por dono. */
    public static int cap(EntityType type) {
        Integer c = CAPS.get(type);
        return c == null ? DEFAULT_CAP : c;
    }

    /** Persistentes (cavalo): sem recarga e não expiram; somem ao invocar outro tipo
     *  ou ao apertar F duas vezes. */
    public static boolean isPersistent(EntityType type) {
        return type == EntityType.HORSE;
    }

    /** Lê as almas guardadas no cajado, na ordem de inserção. */
    public static Map<EntityType, Integer> getSouls(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new LinkedHashMap<>();
        }
        return readSouls(item.getItemMeta());
    }

    /** Soma uma alma do tipo morto e reescreve o item (contagem + lore). */
    public static void addSoul(ItemStack item, EntityType type) {
        addSouls(item, type, 1);
    }

    /** Soma {@code amount} almas do tipo e reescreve o item (contagem + lore). */
    public static void addSouls(ItemStack item, EntityType type, int amount) {
        if (item == null || !item.hasItemMeta() || amount <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        Map<EntityType, Integer> souls = readSouls(meta);
        souls.merge(type, amount, Integer::sum);
        writeSouls(meta, souls);
        rebuildLore(meta, souls, getSelType(meta), getSelQty(meta));
        item.setItemMeta(meta);
    }

    /** Zera todas as almas do cajado e limpa a seleção. */
    public static void clearSouls(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(necroSelTypeKey);
        meta.getPersistentDataContainer().remove(necroSelQtyKey);
        Map<EntityType, Integer> empty = new LinkedHashMap<>();
        writeSouls(meta, empty);
        rebuildLore(meta, empty, null, 1);
        item.setItemMeta(meta);
    }

    /** Consome {@code amount} almas do tipo; remove a entrada se zerar. */
    public static void consumeSouls(ItemStack item, EntityType type, int amount) {
        if (item == null || !item.hasItemMeta() || amount <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        Map<EntityType, Integer> souls = readSouls(meta);
        Integer have = souls.get(type);
        if (have == null) {
            return;
        }
        int left = have - amount;
        if (left > 0) {
            souls.put(type, left);
        } else {
            souls.remove(type);
        }
        writeSouls(meta, souls);
        rebuildLore(meta, souls, getSelType(meta), getSelQty(meta));
        item.setItemMeta(meta);
    }

    // -------------------------------------------------------------- seleção

    /** Tipo atualmente selecionado no cajado, ou null. */
    public static EntityType getSelType(ItemStack item) {
        return item != null && item.hasItemMeta() ? getSelType(item.getItemMeta()) : null;
    }

    /** Quantidade selecionada (>=1). */
    public static int getSelQty(ItemStack item) {
        return item != null && item.hasItemMeta() ? getSelQty(item.getItemMeta()) : 1;
    }

    /** Grava a seleção (tipo + quantidade) e atualiza a lore. */
    public static void setSelection(ItemStack item, EntityType type, int qty) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(necroSelTypeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(necroSelQtyKey, PersistentDataType.INTEGER, Math.max(1, qty));
        rebuildLore(meta, readSouls(meta), type, Math.max(1, qty));
        item.setItemMeta(meta);
    }

    // ---------------------------------------------------------- internos PDC

    private static EntityType getSelType(ItemMeta meta) {
        String raw = meta.getPersistentDataContainer().get(necroSelTypeKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return EntityType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int getSelQty(ItemMeta meta) {
        Integer q = meta.getPersistentDataContainer().get(necroSelQtyKey, PersistentDataType.INTEGER);
        return q == null ? 1 : Math.max(1, q);
    }

    private static Map<EntityType, Integer> readSouls(ItemMeta meta) {
        Map<EntityType, Integer> souls = new LinkedHashMap<>();
        String raw = meta.getPersistentDataContainer().get(necroSoulsKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return souls;
        }
        for (String entry : raw.split(";")) {
            int sep = entry.lastIndexOf(':');
            if (sep <= 0) {
                continue;
            }
            try {
                EntityType type = EntityType.valueOf(entry.substring(0, sep));
                int count = Integer.parseInt(entry.substring(sep + 1));
                if (count > 0) {
                    souls.put(type, count);
                }
            } catch (IllegalArgumentException ignored) {
                // entrada corrompida: ignora
            }
        }
        return souls;
    }

    private static void writeSouls(ItemMeta meta, Map<EntityType, Integer> souls) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<EntityType, Integer> e : souls.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey().name()).append(':').append(e.getValue());
        }
        meta.getPersistentDataContainer().set(necroSoulsKey, PersistentDataType.STRING, sb.toString());
    }

    /** Reconstrói a lore: controles, seleção atual e lista de almas coletadas. */
    private static void rebuildLore(ItemMeta meta, Map<EntityType, Integer> souls,
                                    EntityType selType, int selQty) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Colete almas dos mobs que matar e");
        lore.add(ChatColor.GRAY + "invoque exércitos delas.");
        lore.add("");
        lore.add(ChatColor.GOLD + "Controles:");
        lore.add(ChatColor.GRAY + " • " + ChatColor.YELLOW + "Shift duplo" + ChatColor.GRAY + ": abrir menu");
        lore.add(ChatColor.GRAY + " • " + ChatColor.YELLOW + "Scroll" + ChatColor.GRAY + ": trocar alma / quantidade");
        lore.add(ChatColor.GRAY + " • " + ChatColor.YELLOW + "Botão direito" + ChatColor.GRAY + ": confirmar / invocar");
        lore.add(ChatColor.GRAY + " • " + ChatColor.YELLOW + "F duplo" + ChatColor.GRAY + ": recolher servos (devolve almas)");
        lore.add("");
        if (selType != null) {
            lore.add(ChatColor.DARK_PURPLE + "Seleção atual: " + ChatColor.LIGHT_PURPLE
                    + prettyName(selType) + " " + ChatColor.GRAY + "x" + selQty);
        } else {
            lore.add(ChatColor.DARK_PURPLE + "Seleção atual: " + ChatColor.GRAY + "(nenhuma)");
        }
        lore.add("");
        lore.add(ChatColor.LIGHT_PURPLE + "Almas coletadas:");
        if (souls.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + " (nenhuma — mate mobs para coletar)");
        } else {
            // ordena alfabeticamente (por nome amigável) para a lista ficar estável
            for (Map.Entry<String, Integer> e : new TreeMap<>(toNameMap(souls)).entrySet()) {
                lore.add(ChatColor.GRAY + " • " + ChatColor.WHITE + e.getKey()
                        + ChatColor.GRAY + ": " + ChatColor.AQUA + e.getValue());
            }
        }
        meta.setLore(lore);
    }

    private static Map<String, Integer> toNameMap(Map<EntityType, Integer> souls) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<EntityType, Integer> e : souls.entrySet()) {
            out.put(prettyName(e.getKey()), e.getValue());
        }
        return out;
    }

    /** "ENDER_DRAGON" -> "Ender Dragon". */
    public static String prettyName(EntityType type) {
        String[] parts = type.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /** Mapa de teste: 100 almas de cada um dos 17 tipos coletáveis. */
    private static Map<EntityType, Integer> buildTestSouls() {
        Map<EntityType, Integer> souls = new LinkedHashMap<>();
        for (EntityType type : COLLECTIBLE_TYPES) {
            souls.put(type, SEED_TEST_AMOUNT);
        }
        return souls;
    }

    // =========================================================================
    //  Cajado do Curandeiro (classe: alterna entre Cura e Gravidade)
    // =========================================================================

    /** Constrói o Cajado do Curandeiro no modo Cura (base de tridente, indestrutível). */
    public static ItemStack createHealerStaff() {
        ItemStack item = new ItemStack(HEALER_STAFF_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(healerStaffKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(abilityLevelKey, PersistentDataType.INTEGER, 1);
        applyHealerMode(meta, HEALER_HEAL);
        item.setItemMeta(meta);
        SkibModel.apply(item, healerModelId(HEALER_HEAL));
        return item;
    }

    /** True se o item for o Cajado do Curandeiro. */
    public static boolean isHealerStaff(ItemStack item) {
        if (item == null || item.getType() != HEALER_STAFF_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(healerStaffKey, PersistentDataType.BYTE);
    }

    /** Modo atual do Cajado do Curandeiro ({@link #HEALER_HEAL} por padrão). */
    public static String getHealerMode(ItemStack item) {
        if (!isHealerStaff(item)) {
            return HEALER_HEAL;
        }
        return getHealerMode(item.getItemMeta());
    }

    /** Troca o modo do Cajado do Curandeiro e atualiza modelo, nome e lore. */
    public static void setHealerMode(ItemStack item, String mode) {
        if (!isHealerStaff(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        applyHealerMode(meta, mode);
        item.setItemMeta(meta);
        SkibModel.apply(item, healerModelId(getHealerMode(item)));
    }

    /** UUIDs dos jogadores atualmente selecionados como alvo de cura. */
    public static List<UUID> getHealTargets(ItemStack item) {
        List<UUID> out = new ArrayList<>();
        if (!isHealerStaff(item)) {
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
     * Grava no item a lista ordenada de alvos de cura (UUIDs + nomes) e reescreve
     * a lore. {@code targetIds} e {@code targetNames} devem ter o mesmo tamanho e
     * ordem. Os nomes ficam guardados no PDC para que a lore possa ser refeita ao
     * trocar de modo sem precisar resolver os UUIDs de novo.
     */
    public static void setHealTargets(ItemStack item, List<UUID> targetIds, List<String> targetNames) {
        if (!isHealerStaff(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < targetIds.size(); i++) {
            if (i > 0) {
                ids.append(';');
            }
            ids.append(targetIds.get(i).toString());
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < targetNames.size(); i++) {
            if (i > 0) {
                names.append(';');
            }
            names.append(targetNames.get(i));
        }
        meta.getPersistentDataContainer().set(healTargetsKey, PersistentDataType.STRING, ids.toString());
        meta.getPersistentDataContainer().set(healTargetNamesKey, PersistentDataType.STRING, names.toString());
        applyHealerMode(meta, getHealerMode(meta));
        item.setItemMeta(meta);
    }

    private static String getHealerMode(ItemMeta meta) {
        String m = meta.getPersistentDataContainer().get(healerModeKey, PersistentDataType.STRING);
        return HEALER_GRAVITY.equals(m) ? HEALER_GRAVITY : HEALER_HEAL;
    }

    /** Nomes dos alvos de cura guardados no PDC (para reconstruir a lore). */
    private static List<String> readTargetNames(ItemMeta meta) {
        List<String> out = new ArrayList<>();
        String raw = meta.getPersistentDataContainer().get(healTargetNamesKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(";")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    /** Aplica nome/modelo/lore correspondentes ao modo do Cajado do Curandeiro. */
    private static void applyHealerMode(ItemMeta meta, String mode) {
        boolean gravity = HEALER_GRAVITY.equals(mode);
        meta.getPersistentDataContainer().set(healerModeKey, PersistentDataType.STRING,
                gravity ? HEALER_GRAVITY : HEALER_HEAL);
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Cajado do Curandeiro");

        List<String> lore = abilityLore(meta, null, HEALER_ABILITIES, HEALER_DESCS, gravity ? 1 : 0);

        List<String> targetNames = readTargetNames(meta);
        lore.add("");
        if (targetNames.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "Nenhum alvo selecionado.");
        } else {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Alvos" + ChatColor.GREEN + " ("
                    + targetNames.size() + "):");
            for (String name : targetNames) {
                lore.add(ChatColor.GRAY + " • " + ChatColor.WHITE + name);
            }
        }
        meta.setLore(lore);
    }

    // =========================================================================
    //  Arco do Elfo
    // =========================================================================

    /** Constrói o Arco do Elfo (base de arco, indestrutível). */
    public static ItemStack createElfBow() {
        ItemStack item = new ItemStack(ELF_BOW_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(elfBowKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(abilityLevelKey, PersistentDataType.INTEGER, 1);
        applyElfBowLore(meta);
        item.setItemMeta(meta);
        SkibModel.apply(item, "arco_elfo");
        return item;
    }

    /** (Re)constrói nome e lore do Arco do Elfo conforme o nível de habilidades. */
    private static void applyElfBowLore(ItemMeta meta) {
        meta.setDisplayName(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Arco do Elfo");
        meta.setLore(abilityLore(meta, null, ELF_BOW_ABILITIES, ELF_BOW_DESCS, -1));
    }

    /** True se o item for o Arco do Elfo. */
    public static boolean isElfBow(ItemStack item) {
        if (item == null || item.getType() != ELF_BOW_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(elfBowKey, PersistentDataType.BYTE);
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
        SkibModel.apply(item, "mjolnir");
        return item;
    }

    /** True se o item for o Mjolnir. */
    public static boolean isMjolnir(ItemStack item) {
        if (item == null || item.getType() != MJOLNIR_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(mjolnirKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Espada do Guerreiro
    // =========================================================================

    /** Constrói a Espada do Guerreiro (base de espada de netherita, indestrutível). */
    public static ItemStack createWarriorSword() {
        ItemStack item = new ItemStack(WARRIOR_SWORD_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(warriorSwordKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(abilityLevelKey, PersistentDataType.INTEGER, 1);
        applyWarriorSwordLore(meta);
        item.setItemMeta(meta);
        SkibModel.apply(item, "espada_guerreiro");
        return item;
    }

    /** (Re)constrói nome e lore da Espada do Guerreiro conforme o nível de habilidades. */
    private static void applyWarriorSwordLore(ItemMeta meta) {
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Espada do Guerreiro");
        meta.setLore(abilityLore(meta, WARRIOR_PASSIVES, WARRIOR_ABILITIES, WARRIOR_DESCS, -1));
    }

    /** True se o item for a Espada do Guerreiro. */
    public static boolean isWarriorSword(ItemStack item) {
        if (item == null || item.getType() != WARRIOR_SWORD_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(warriorSwordKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Adagas do Assassino
    // =========================================================================

    /** Constrói as Adagas do Assassino (base de espada de netherita, indestrutíveis). */
    public static ItemStack createAssassinDaggers() {
        ItemStack item = new ItemStack(ASSASSIN_DAGGERS_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(assassinDaggersKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(abilityLevelKey, PersistentDataType.INTEGER, 1);
        applyDaggersLore(meta);
        item.setItemMeta(meta);
        SkibModel.apply(item, "adaga_assassino");
        return item;
    }

    /** (Re)constrói nome e lore das Adagas do Assassino conforme o nível de habilidades. */
    private static void applyDaggersLore(ItemMeta meta) {
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Adagas do Assassino");
        meta.setLore(abilityLore(meta, DAGGER_PASSIVES, DAGGER_ABILITIES, DAGGER_DESCS, -1));
    }

    /** True se o item for as Adagas do Assassino. */
    public static boolean isAssassinDaggers(ItemStack item) {
        if (item == null || item.getType() != ASSASSIN_DAGGERS_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(assassinDaggersKey, PersistentDataType.BYTE);
    }

    // =========================================================================
    //  Nivelamento de habilidades (Gema + Forja do Infinito)
    // =========================================================================

    /** Lista de habilidades da arma (na ordem das teclas), ou null se não for upgradeável. */
    private static String[] abilityNames(ItemStack item) {
        if (isMageStaff(item)) {
            return MAGE_ABILITIES;
        }
        if (isHealerStaff(item)) {
            return HEALER_ABILITIES;
        }
        if (isElfBow(item)) {
            return ELF_BOW_ABILITIES;
        }
        if (isWarriorSword(item)) {
            return WARRIOR_ABILITIES;
        }
        if (isAssassinDaggers(item)) {
            return DAGGER_ABILITIES;
        }
        return null;
    }

    /** True se o item participa do sistema de nivelamento. */
    public static boolean isUpgradeable(ItemStack item) {
        return abilityNames(item) != null;
    }

    /** Total de habilidades da arma (0 se não for upgradeável). */
    public static int getMaxAbilityLevel(ItemStack item) {
        String[] names = abilityNames(item);
        return names == null ? 0 : names.length;
    }

    /** Nível atual de habilidades liberadas (≥1). Armas antigas sem o dado contam como nível 1. */
    public static int getAbilityLevel(ItemStack item) {
        if (!isUpgradeable(item) || !item.hasItemMeta()) {
            return 1;
        }
        return clampLevel(levelFromMeta(item.getItemMeta()), getMaxAbilityLevel(item));
    }

    /** True se a habilidade da tecla {@code slotIndex+1} (0-based) está liberada. */
    public static boolean isAbilityUnlocked(ItemStack item, int slotIndex) {
        return slotIndex < getAbilityLevel(item);
    }

    /** Nome da habilidade da tecla {@code slotIndex+1}, ou "?" se fora do alcance. */
    public static String abilityName(ItemStack item, int slotIndex) {
        String[] names = abilityNames(item);
        if (names == null || slotIndex < 0 || slotIndex >= names.length) {
            return "?";
        }
        return names[slotIndex];
    }

    /** Define o nível (entre 1 e o máximo) e reconstrói a lore. */
    public static void setAbilityLevel(ItemStack item, int level) {
        if (!isUpgradeable(item) || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(abilityLevelKey, PersistentDataType.INTEGER,
                clampLevel(level, getMaxAbilityLevel(item)));
        applyLoreFor(item, meta);
        item.setItemMeta(meta);
    }

    /**
     * Libera a próxima habilidade da arma. Retorna o novo nível, ou -1 se já estava
     * no máximo (ou se o item não for upgradeável).
     */
    public static int unlockNextAbility(ItemStack item) {
        if (!isUpgradeable(item)) {
            return -1;
        }
        int lvl = getAbilityLevel(item);
        if (lvl >= getMaxAbilityLevel(item)) {
            return -1;
        }
        setAbilityLevel(item, lvl + 1);
        return lvl + 1;
    }

    private static int levelFromMeta(ItemMeta meta) {
        Integer lvl = meta.getPersistentDataContainer().get(abilityLevelKey, PersistentDataType.INTEGER);
        return lvl == null ? 1 : lvl;
    }

    private static int clampLevel(int level, int max) {
        if (max <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(max, level));
    }

    /** Reconstrói a lore da arma respeitando seu tipo, modo e nível atuais. */
    private static void applyLoreFor(ItemStack item, ItemMeta meta) {
        if (isMageStaff(item)) {
            applyMageMode(meta, getMageMode(meta));
        } else if (isHealerStaff(item)) {
            applyHealerMode(meta, getHealerMode(meta));
        } else if (isElfBow(item)) {
            applyElfBowLore(meta);
        } else if (isWarriorSword(item)) {
            applyWarriorSwordLore(meta);
        } else if (isAssassinDaggers(item)) {
            applyDaggersLore(meta);
        }
    }

    /**
     * Monta a lore unificada da arma: bloco de passivas (sempre ✅), bloco de
     * habilidades ativas com ✅/❌ conforme o nível e a descrição embutida, e — só
     * enquanto houver o que liberar — a dica de fusão. {@code activeIndex} marca a
     * habilidade-modo ativa dos cajados (use -1 quando não houver modo).
     */
    private static List<String> abilityLore(ItemMeta meta, String[] passives,
                                            String[] names, String[] descs, int activeIndex) {
        int level = clampLevel(levelFromMeta(meta), names.length);
        List<String> lore = new ArrayList<>();
        if (passives != null && passives.length > 0) {
            lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Habilidades Passiva:");
            for (String p : passives) {
                appendEntry(lore, ChatColor.GREEN + "✅", ChatColor.GRAY.toString(), p);
            }
            lore.add("");
        }
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Habilidades Ativas:");
        for (int i = 0; i < names.length; i++) {
            boolean unlocked = i < level;
            String nameColor = (unlocked ? ChatColor.WHITE : ChatColor.DARK_GRAY).toString();
            String descColor = (unlocked ? ChatColor.GRAY : ChatColor.DARK_GRAY).toString();
            String active = (unlocked && i == activeIndex) ? (ChatColor.YELLOW + " (ativo)") : "";
            String head = (unlocked ? ChatColor.GREEN + "✅" : ChatColor.RED + "❌")
                    + " " + nameColor + "Tecla " + (i + 1) + " - " + names[i] + active + nameColor + ":";
            appendEntry(lore, head, descColor, descs[i]);
        }
        if (level < names.length) {
            lore.add("");
            appendEntry(lore, "", ChatColor.YELLOW.toString(),
                    "Para liberar novas habilidades, funda o item com uma Gema do Infinito na Forja do Infinito.");
        }
        return lore;
    }

    /**
     * Acrescenta uma entrada à lore quebrando a descrição em linhas de ~{@link #LORE_WIDTH}
     * caracteres visíveis. A 1ª linha começa com {@code head} (já colorido); as
     * continuações são indentadas. {@code head} vazio = parágrafo simples.
     */
    private static void appendEntry(List<String> lore, String head, String descColor, String description) {
        final String indent = "  ";
        boolean hasHead = !head.isEmpty();
        StringBuilder line = new StringBuilder(hasHead ? head + " " + descColor : descColor);
        int visible = hasHead ? visibleLen(head) + 1 : 0;
        boolean any = false;
        for (String word : description.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            int wlen = word.length();
            if (any && visible + 1 + wlen > LORE_WIDTH) {
                lore.add(line.toString());
                line = new StringBuilder(descColor + indent + word);
                visible = indent.length() + wlen;
            } else if (any) {
                line.append(' ').append(word);
                visible += 1 + wlen;
            } else {
                line.append(word);
                visible += wlen;
            }
            any = true;
        }
        lore.add(line.toString());
    }

    /** Comprimento visível (descontando os códigos de cor §x). */
    private static int visibleLen(String s) {
        return ChatColor.stripColor(s).length();
    }

    // =========================================================================
    //  Gema do Infinito e Forja do Infinito
    // =========================================================================

    /** Constrói uma pilha de Gemas do Infinito (consumível da fusão). */
    public static ItemStack createInfinityGem(int amount) {
        ItemStack item = new ItemStack(INFINITY_GEM_MATERIAL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Gema do Infinito");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Funda esta gema na " + ChatColor.AQUA + "Forja do Infinito",
                ChatColor.GRAY + "junto de uma arma mágica para liberar",
                ChatColor.GRAY + "a " + ChatColor.WHITE + "próxima habilidade" + ChatColor.GRAY + " dela.",
                "",
                ChatColor.DARK_GRAY + "Consumida a cada fusão."));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(infinityGemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        SkibModel.apply(item, "gema_infinito");
        return item;
    }

    /** True se o item for uma Gema do Infinito. */
    public static boolean isInfinityGem(ItemStack item) {
        if (item == null || item.getType() != INFINITY_GEM_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(infinityGemKey, PersistentDataType.BYTE);
    }

    /** Constrói a Forja do Infinito (estação reutilizável; clique direito abre o menu de fusão). */
    public static ItemStack createInfinityForge() {
        ItemStack item = new ItemStack(INFINITY_FORGE_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Forja do Infinito");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Posicione no " + ChatColor.WHITE + "chão" + ChatColor.GRAY + " e clique com o",
                ChatColor.RED + "botão direito" + ChatColor.GRAY + " no bloco para abrir a forja.",
                ChatColor.GRAY + "Coloque uma " + ChatColor.WHITE + "arma mágica" + ChatColor.GRAY + " e uma",
                ChatColor.LIGHT_PURPLE + "Gema do Infinito" + ChatColor.GRAY + ", então funda para",
                ChatColor.GRAY + "liberar a próxima habilidade da arma.",
                "",
                ChatColor.DARK_GRAY + "Estação compartilhada — qualquer um usa; só a gema some."));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(infinityForgeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        SkibModel.apply(item, "forja_infinito");
        return item;
    }

    /** True se o item for a Forja do Infinito. */
    public static boolean isInfinityForge(ItemStack item) {
        if (item == null || item.getType() != INFINITY_FORGE_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(infinityForgeKey, PersistentDataType.BYTE);
    }
}
