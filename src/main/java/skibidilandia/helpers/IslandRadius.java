package skibidilandia.helpers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

public class IslandRadius {
    public final Random random = new Random();
    public final Integer minX;
    public final Integer minZ;
    public final Integer maxX;
    public final Integer maxZ;

    public IslandRadius(Integer minX, Integer minZ, Integer maxX, Integer maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public Location getRandomLocation(World world) {
        Integer realMinX = Math.min(minX, maxX);
        Integer realMaxX = Math.max(minX, maxX);
        Integer realMinZ = Math.min(minZ, maxZ);
        Integer realMaxZ = Math.max(minZ, maxZ);

        for (Integer i = 0; i < 50; i++) {
            Integer x = random.nextInt(realMaxX - realMinX + 1) + realMinX;
            Integer z = random.nextInt(realMaxZ - realMinZ + 1) + realMinZ;
            Integer y = world.getHighestBlockYAt(x, z);

            Location location = new Location(world, x + 0.5, y, z + 0.5);

            if (isSafeLocation(location)) {
                return location;
            }
        }

        return null;
    }

    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        Location below = location.clone().subtract(0, 1, 0);
        Material blockType = world.getBlockAt(below).getType();

        return blockType != Material.AIR && blockType != Material.VOID_AIR;
    }
}
