package minigame.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Ease of Access class for Subclasses of Minigame.
 * This will correctly calculate the correct real Location after scaling
 * of the original model.
 */
public class AdjustedLocation {
    private final World world;
    private final int x;
    private final int y;
    private final int z;

    //For manually created coordinates
    public AdjustedLocation(World world, @NotNull Location baseLoc, @NotNull int[] scale, int x, int y, int z) {
        this.world = world;
        this.x = baseLoc.getBlockX()+(scale[0]*x);
        this.y = baseLoc.getBlockY()+(scale[1]*y);
        this.z = baseLoc.getBlockZ()+(scale[2]*z);
    }

    //For event-generated (true) coordinates
    public AdjustedLocation(@NotNull Block b) {
        this.world = b.getWorld();
        this.x = b.getX();
        this.y = b.getY();
        this.z = b.getZ();
    }

    public AdjustedLocation(@NotNull Player p) {
        this.world = p.getWorld();
        Location loc = p.getLocation();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
    }

    public AdjustedLocation(@NotNull Location loc) {
        this.world = loc.getWorld();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
    }

    public Location add(int x2, int y2, int z2) {
        return new Location(world, x+x2, y+y2, z+z2);
    }

    public Location add(int[] i) {
        return add(i[0], i[1], i[2]);
    }
}
