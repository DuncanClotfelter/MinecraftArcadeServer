package minigame.game.child;

import minigame.game.Minigame;
import minigame.game.MinigameSettings;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class Spleef extends Minigame implements Listener {
    public Spleef(World world, String region, List<MinigameTeam> teams, Location exit) {
        super(MinigameSettings.SPLEEF, world, region, teams, exit);

        for(MinigameTeam team : getTeams()) {
            for(Player p : team.getPlayers()) {
                p.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SHOVEL, 1));
                p.teleport(new Location(
                        getWorld(),
                        getBaseLoc().getBlockX() + (int)(Math.random() * getXLength()),
                        getBaseLoc().getBlockY() + 1,
                        getBaseLoc().getBlockZ() + (int)(Math.random() * getZLength())
                ));
            }
        }
    }

    @Override
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        super.onPlayerMove(e);
        if(!e.isCancelled() && inGame(e.getPlayer()) && e.getTo() != null && !inGameArea(e.getTo())) {
            getGameRecord().set(e.getPlayer(), "survive_duration", Duration.between(getGameStart(), Instant.now()));
            kickPlayer(e.getPlayer());
        }
    }

    @Override
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        super.onBlockBreak(e);
        if(!e.isCancelled() && e.getBlock().getType() != Material.SNOW_BLOCK && inGame(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
