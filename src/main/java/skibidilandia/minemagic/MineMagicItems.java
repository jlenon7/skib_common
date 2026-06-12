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

    // Modos das classes (guardados no PDC do cajado).
    public static final String MAGE_FIRE = "FIRE";
    public static final String MAGE_LIGHTNING = "LIGHTNING";
    public static final String MAGE_FREEZE = "FREEZE";
    public static final String HEALER_HEAL = "HEAL";
    public static final String HEALER_GRAVITY = "GRAVITY";

    // custom_model_data por modo (um resource pack pode dar textura própria a cada um).
    private static final int CMD_MAGE_FIRE = 5101;
    private static final int CMD_MAGE_LIGHTNING = 5102;
    private static final int CMD_MAGE_FREEZE = 5105;
    private static final int CMD_HEALER_HEAL = 5103;
    private static final int CMD_HEALER_GRAVITY = 5104;
    private static final int CMD_ASSASSIN_DAGGERS = 5200;

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
        applyMageMode(meta, MAGE_FIRE);
        item.setItemMeta(meta);
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
    }

    /** Aplica nome/modelo/lore correspondentes ao modo do Cajado do Mago. */
    private static void applyMageMode(ItemMeta meta, String mode) {
        String resolved;
        int cmd;
        if (MAGE_LIGHTNING.equals(mode)) {
            resolved = MAGE_LIGHTNING;
            cmd = CMD_MAGE_LIGHTNING;
        } else if (MAGE_FREEZE.equals(mode)) {
            resolved = MAGE_FREEZE;
            cmd = CMD_MAGE_FREEZE;
        } else {
            resolved = MAGE_FIRE;
            cmd = CMD_MAGE_FIRE;
        }
        meta.getPersistentDataContainer().set(mageModeKey, PersistentDataType.STRING, resolved);
        meta.setCustomModelData(cmd);
        meta.setDisplayName(ChatColor.BLUE + "" + ChatColor.BOLD + "Cajado do Mago");

        List<String> lore = new ArrayList<>();
        switch (resolved) {
            case MAGE_LIGHTNING:
                lore.add(ChatColor.GRAY + "Modo atual: " + ChatColor.AQUA + "" + ChatColor.BOLD + "Raio");
                lore.add("");
                lore.add(ChatColor.GRAY + "Botão direito: um " + ChatColor.AQUA + "raio" + ChatColor.GRAY
                        + " cai do céu sobre");
                lore.add(ChatColor.GRAY + "a área visada. Segure para uma " + ChatColor.AQUA + "rajada de raios"
                        + ChatColor.GRAY + ".");
                lore.add(ChatColor.GRAY + "" + ChatColor.RED + "Shift" + ChatColor.GRAY + " + botão direito: "
                        + ChatColor.AQUA + "chuva de raios" + ChatColor.GRAY + " sobre a área.");
                break;
            case MAGE_FREEZE:
                lore.add(ChatColor.GRAY + "Modo atual: " + ChatColor.WHITE + "" + ChatColor.BOLD + "Congelar");
                lore.add("");
                lore.add(ChatColor.GRAY + "Botão direito: " + ChatColor.WHITE + "congela" + ChatColor.GRAY
                        + " o alvo visado");
                lore.add(ChatColor.GRAY + "(jogador ou mob), prendendo-o por alguns segundos.");
                break;
            default:
                lore.add(ChatColor.GRAY + "Modo atual: " + ChatColor.GOLD + "" + ChatColor.BOLD + "Fogo");
                lore.add("");
                lore.add(ChatColor.GRAY + "Segure o " + ChatColor.RED + "botão direito" + ChatColor.GRAY
                        + ": rajada de " + ChatColor.GOLD + "bolas de fogo" + ChatColor.GRAY + ".");
                lore.add(ChatColor.GRAY + "" + ChatColor.RED + "Shift" + ChatColor.GRAY + " + botão direito: "
                        + ChatColor.GOLD + "chuva de fogo" + ChatColor.GRAY + " sobre a área.");
                break;
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Tecla 1" + ChatColor.GRAY + ": Fogo  ·  " + ChatColor.YELLOW + "Tecla 2"
                + ChatColor.GRAY + ": Raio  ·  " + ChatColor.YELLOW + "Tecla 3" + ChatColor.GRAY + ": Congelar");
        lore.add(ChatColor.YELLOW + "Tecla 4" + ChatColor.GRAY + ": Levitar na hora "
                + ChatColor.DARK_GRAY + "(recarga de 15s)");
        lore.add(ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                + " como um tridente.");
        meta.setLore(lore);
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
        applyHealerMode(meta, HEALER_HEAL);
        item.setItemMeta(meta);
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
        meta.setCustomModelData(gravity ? CMD_HEALER_GRAVITY : CMD_HEALER_HEAL);
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Cajado do Curandeiro");

        List<String> lore = new ArrayList<>();
        if (gravity) {
            lore.add(ChatColor.GRAY + "Modo atual: " + ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Gravidade");
            lore.add("");
            lore.add(ChatColor.GRAY + "Botão direito: arremessa para o alto e " + ChatColor.AQUA + "puxa"
                    + ChatColor.GRAY + " tudo até você.");
            lore.add(ChatColor.GRAY + "" + ChatColor.RED + "Shift" + ChatColor.GRAY + " + botão direito: "
                    + ChatColor.RED + "repele" + ChatColor.GRAY + " tudo (10x10x10).");
        } else {
            lore.add(ChatColor.GRAY + "Modo atual: " + ChatColor.GREEN + "" + ChatColor.BOLD + "Cura");
            lore.add("");
            lore.add(ChatColor.GRAY + "Bata em um " + ChatColor.GREEN + "jogador" + ChatColor.GRAY
                    + " para selecioná-lo");
            lore.add(ChatColor.GRAY + "como alvo (bata de novo para remover).");
            lore.add(ChatColor.GRAY + "Segure o " + ChatColor.RED + "botão direito" + ChatColor.GRAY + ": "
                    + ChatColor.GREEN + "cura" + ChatColor.GRAY + " os alvos em 30x30x30.");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Tecla 1" + ChatColor.GRAY + ": Cura  ·  " + ChatColor.YELLOW + "Tecla 2"
                + ChatColor.GRAY + ": Gravidade " + ChatColor.DARK_GRAY + "(sem recarga)");
        lore.add(ChatColor.YELLOW + "Tecla 3" + ChatColor.GRAY + ": limpar alvos.");
        lore.add(ChatColor.GRAY + "O cajado " + ChatColor.WHITE + "nunca é arremessado" + ChatColor.GRAY
                + " como um tridente.");

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
        meta.setDisplayName(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Arco do Elfo");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Habilidades " + ChatColor.WHITE + "(aperte a tecla para usar)" + ChatColor.GRAY + ":",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 1" + ChatColor.GRAY + ": por " + ChatColor.WHITE
                        + "10s" + ChatColor.GRAY + ", suas flechas causam",
                ChatColor.GRAY + "   " + ChatColor.RED + "explosão" + ChatColor.GRAY + " e " + ChatColor.DARK_RED
                        + "sangramento" + ChatColor.GRAY + " ao acertar.",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 2" + ChatColor.GRAY + ": por " + ChatColor.WHITE
                        + "10s" + ChatColor.GRAY + ", cada disparo solta",
                ChatColor.GRAY + "   " + ChatColor.GREEN + "6 flechas em espiral" + ChatColor.GRAY + ".",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 3" + ChatColor.GRAY + ": " + ChatColor.DARK_GREEN
                        + "chuva de flechas" + ChatColor.GRAY + " sobre a área.",
                "",
                ChatColor.GRAY + "As habilidades " + ChatColor.YELLOW + "1" + ChatColor.GRAY + " e " + ChatColor.YELLOW
                        + "2" + ChatColor.GRAY + " se combinam, e a",
                ChatColor.GRAY + "chuva " + ChatColor.YELLOW + "(3)" + ChatColor.GRAY + " aproveita ambas.",
                ChatColor.DARK_GRAY + "Dica: mantenha o arco num slot 5-9 para",
                ChatColor.DARK_GRAY + "deixar as teclas 1-3 livres para as habilidades.",
                ChatColor.GRAY + "Indestrutível."
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(elfBowKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
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
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Espada do Guerreiro");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Passiva: " + ChatColor.RED + "20%" + ChatColor.GRAY + " de chance de causar",
                ChatColor.GRAY + "uma " + ChatColor.RED + "explosão (1 TNT)" + ChatColor.GRAY + " ao atacar.",
                "",
                ChatColor.GOLD + "Habilidades:",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 1" + ChatColor.GRAY + ": arremessa a espada e",
                ChatColor.GRAY + "   causa dano ao acertar " + ChatColor.WHITE + "(volta à mão)" + ChatColor.GRAY + ".",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 2" + ChatColor.GRAY + ": " + ChatColor.AQUA
                        + "Resistência" + ChatColor.GRAY + " + " + ChatColor.BLUE + "Lentidão" + ChatColor.GRAY + " (10s).",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 3" + ChatColor.GRAY + ": " + ChatColor.DARK_GRAY
                        + "domo de bedrock" + ChatColor.GRAY + " 20x20x20",
                ChatColor.GRAY + "   prendendo os inimigos por 20s.",
                "",
                ChatColor.GRAY + "Indestrutível."));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(warriorSwordKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
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
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Adagas do Assassino");
        meta.setCustomModelData(CMD_ASSASSIN_DAGGERS);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Habilidades " + ChatColor.WHITE + "(aperte a tecla para usar)" + ChatColor.GRAY + ":",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 1" + ChatColor.GRAY + ": arremessa "
                        + ChatColor.WHITE + "adagas" + ChatColor.GRAY + " na direção da mira.",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 2" + ChatColor.GRAY + ": joga um "
                        + ChatColor.DARK_PURPLE + "clone das sombras" + ChatColor.GRAY + " 5 blocos à frente.",
                ChatColor.GRAY + "   O clone dura 10s e copia as habilidades 1 e 3.",
                ChatColor.GRAY + "   Aperte 2 de novo (em 10s) para " + ChatColor.WHITE + "trocar de lugar" + ChatColor.GRAY + ".",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 3" + ChatColor.GRAY + ": "
                        + ChatColor.AQUA + "tornado de adagas" + ChatColor.GRAY + " ao redor.",
                ChatColor.GRAY + " • " + ChatColor.YELLOW + "Tecla 4" + ChatColor.GRAY + " " + ChatColor.LIGHT_PURPLE
                        + "(especial)" + ChatColor.GRAY + ": teleporta para as " + ChatColor.WHITE + "costas",
                ChatColor.GRAY + "   de um inimigo mirado e deixa um clone no lugar.",
                ChatColor.GRAY + "   Marca o alvo: " + ChatColor.DARK_RED + "execução" + ChatColor.GRAY
                        + " explode em 5s (mais",
                ChatColor.GRAY + "   dano causado = explosão maior).",
                ChatColor.GRAY + "   Aperte 4 de novo (em 10s) para trocar de lugar.",
                ChatColor.DARK_GRAY + "   Recarga de 20s após o especial.",
                "",
                ChatColor.DARK_GRAY + "Dica: mantenha as adagas num slot 5-9 para",
                ChatColor.DARK_GRAY + "deixar as teclas 1-4 livres para as habilidades.",
                ChatColor.GRAY + "Indestrutíveis."));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(assassinDaggersKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True se o item for as Adagas do Assassino. */
    public static boolean isAssassinDaggers(ItemStack item) {
        if (item == null || item.getType() != ASSASSIN_DAGGERS_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(assassinDaggersKey, PersistentDataType.BYTE);
    }
}
