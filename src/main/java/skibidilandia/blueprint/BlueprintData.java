package skibidilandia.blueprint;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa uma construção capturada: dimensões, uma paleta de tipos de bloco
 * e a lista de blocos não-vazios (índice na paleta + posição relativa ao canto
 * mínimo). É serializável para uma única String, que é guardada no PDC do item
 * de blueprint — assim o blueprint é autossuficiente (sobrevive a soltar/pegar).
 */
public class BlueprintData {

    /** Teto de blocos por blueprint, para não estourar o NBT do item nem travar a colagem. */
    public static final int MAX_BLOCKS = 60000;

    private static final String HEADER = "SKIBBP1";

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<String> palette;
    /** Cada entrada: {paletteIndex, relX, relY, relZ}. */
    private final List<int[]> blocks;

    private BlueprintData(int sizeX, int sizeY, int sizeZ, List<String> palette, List<int[]> blocks) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.blocks = blocks;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    /**
     * Lê todos os blocos não-vazios do cuboide entre os dois cantos (inclusive).
     * Retorna null se passar do teto de {@link #MAX_BLOCKS} blocos.
     */
    public static BlueprintData capture(World world, int minX, int minY, int minZ,
                                        int maxX, int maxY, int maxZ) {
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;

        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>();
        List<int[]> blocks = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    String data = block.getBlockData().getAsString();
                    Integer pi = paletteIndex.get(data);
                    if (pi == null) {
                        pi = palette.size();
                        palette.add(data);
                        paletteIndex.put(data, pi);
                    }
                    blocks.add(new int[]{pi, x - minX, y - minY, z - minZ});
                    if (blocks.size() > MAX_BLOCKS) {
                        return null;
                    }
                }
            }
        }
        return new BlueprintData(sx, sy, sz, palette, blocks);
    }

    /** Empacota tudo numa String (4 linhas: header / dimensões / paleta / blocos). */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append(sizeX).append(',').append(sizeY).append(',').append(sizeZ).append('\n');
        // entradas da paleta nunca contêm ';' (estão no formato namespace[estado=...])
        sb.append(String.join(";", palette)).append('\n');
        StringBuilder body = new StringBuilder();
        for (int[] e : blocks) {
            if (body.length() > 0) {
                body.append(';');
            }
            body.append(e[0]).append(',').append(e[1]).append(',').append(e[2]).append(',').append(e[3]);
        }
        sb.append(body);
        return sb.toString();
    }

    /** Reconstrói a partir da String do PDC. Retorna null se o formato for inválido. */
    public static BlueprintData deserialize(String s) {
        if (s == null) {
            return null;
        }
        String[] lines = s.split("\n", 4);
        if (lines.length < 4 || !lines[0].equals(HEADER)) {
            return null;
        }
        try {
            String[] dims = lines[1].split(",");
            int sx = Integer.parseInt(dims[0]);
            int sy = Integer.parseInt(dims[1]);
            int sz = Integer.parseInt(dims[2]);

            List<String> palette = lines[2].isEmpty()
                    ? new ArrayList<>()
                    : new ArrayList<>(Arrays.asList(lines[2].split(";")));

            List<int[]> blocks = new ArrayList<>();
            if (!lines[3].isEmpty()) {
                for (String part : lines[3].split(";")) {
                    String[] f = part.split(",");
                    blocks.add(new int[]{
                            Integer.parseInt(f[0]),
                            Integer.parseInt(f[1]),
                            Integer.parseInt(f[2]),
                            Integer.parseInt(f[3])
                    });
                }
            }
            return new BlueprintData(sx, sy, sz, palette, blocks);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Cola a construção com o canto mínimo em {@code base}. Coloca apenas os
     * blocos guardados (vazios são ignorados, então a colagem é "aditiva" e
     * não cava buracos no terreno em volta). Retorna quantos blocos colocou.
     */
    public int paste(Location base) {
        World world = base.getWorld();
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        BlockData[] cache = new BlockData[palette.size()];
        int placed = 0;
        for (int[] e : blocks) {
            int y = by + e[2];
            if (y < minHeight || y >= maxHeight) {
                continue;
            }
            BlockData bd = cache[e[0]];
            if (bd == null) {
                try {
                    bd = Bukkit.createBlockData(palette.get(e[0]));
                } catch (IllegalArgumentException invalid) {
                    continue; // tipo de bloco não existe mais nesta versão: pula
                }
                cache[e[0]] = bd;
            }
            world.getBlockAt(bx + e[1], y, bz + e[3]).setBlockData(bd, false);
            placed++;
        }
        return placed;
    }
}
