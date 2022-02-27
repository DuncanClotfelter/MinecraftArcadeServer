package minigame.game.child;

import minigame.game.Minigame;
import minigame.game.MinigameSettings;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class Graffiti extends Minigame implements Listener {
    public Graffiti(World world, String region, List<MinigameTeam> teams, Location exit) {
        super(MinigameSettings.GRAFFITI, world, region, teams, exit);
    }

    @Override
    protected void joinGame(Player p) {
        super.joinGame(p);
        setInventory(p);
    }

    private void setInventory(Player p) {
        p.getInventory().setItem(1, new ItemStack(Material.BOW, 1));
        p.getInventory().setItem(3, new ItemStack(Material.SNOWBALL, 1));
        p.getInventory().setItem(2, new ItemStack(Material.EGG, 1));
        p.getInventory().setItem(0, new ItemStack(Material.TRIDENT, 1));
        p.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
    }

    @EventHandler
    protected void onProjectileHit(ProjectileHitEvent e) {
        if(!(e.getEntity().getShooter() instanceof Player)) {return;}
        Player p = (Player) e.getEntity().getShooter();
        if(!inGame(p)) {return;}

        String brushType = e.getEntity().getName();
        e.getEntity().remove();

        Block hit = e.getHitBlock();
        if(hit == null || !validBlock(hit)) {return;}

        Material color = p.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
        if(!color.toString().endsWith("_WOOL")) {return;}

        int brushSize = getBrushSize(brushType);

        int blocksChanged = 0;
        Block b;
        for(int x = -brushSize / 2; x <= brushSize / 2; x++) {//TODO allow rotated game area
            for(int y = -brushSize / 2; y <= brushSize / 2; y++) {
                b = hit.getWorld().getBlockAt(hit.getX() + x, hit.getY() + y, hit.getZ());
                if(!validBlock(b) || b.getType() == color) {continue;}
                blocksChanged++;
                b.setType(color);
                b.getState().update();
            }
        }

        getGameRecord().increment(p, "blocks_painted", blocksChanged);

        setInventory(p);
    }

    private boolean validBlock(Block b) {
        return getGameArea().contains(b.getX(), b.getY(), b.getZ()) && b.getType().toString().endsWith("_WOOL");
    }

    private int getBrushSize(String brushType) {
        switch(brushType) {
            case "Trident":
                return 5;
            case "Arrow":
                return 7;
            case "Thrown Egg":
                return 3;
            case "Snowball":
                return 1;
            default:
                return 0;
        }
    }
}
