package minigame.game.child;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import minigame.Main;
import minigame.game.Minigame;
import minigame.game.MinigameSettings;
import minigame.player.PlayerManager;
import minigame.util.GlobalSettings;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class Bridges extends Minigame implements Listener {
    private final Location[] spawnPoints;
    private Region endZone;
    private int deadPlayerCount = 0;
    private int winningPlayerCount = 0;
    private int ticketsRemaining;
    private final ArrayList<Player> winners;
    private final ArrayList<Player> losers;
    private boolean gameEnded = false;

    public Bridges(World world, String region, List<MinigameTeam> teams, Location exit) {
        super(MinigameSettings.BRIDGES, world, region, teams, exit);
        this.winners = new ArrayList<>(getTeams().size());
        this.losers = new ArrayList<>(getTeams().size());

        spawnPoints = new Location[getTeams().size()];
        calculateSpawns();

        ticketsRemaining = getTeams().size() * getSettings().getTokenCost() * 2;

        for(int i = 0; i < getTeams().size(); i++) {
            getTeams().get(i).getScoreboardTeam().setColor(GlobalSettings.getChatColor(i));
            for(Player p : getTeams().get(i).getPlayers()) {
                p.getInventory().setItem(0, new ItemStack(Material.SNOWBALL, 64));
                for(int j = 1; j < 9; j++) {
                    p.getInventory().setItem(j, new ItemStack(Material.valueOf(GlobalSettings.getColor(i) + "_WOOL"), 64));
                }
                p.teleport(spawnPoints[i]);
            }
        }
    }

    @Override @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        if(e.getTo() == null) {return;}
        super.onPlayerMove(e);
        MinigameTeam team = getTeam(e.getPlayer());
        if(team == null) {return;}
        if(winners.contains(e.getPlayer())) {return;}
        if(endZone.contains(getVector(e.getPlayer().getLocation()))) {
            winners.add(e.getPlayer());
            if(winningPlayerCount++ == 0) {
                getGameRecord().setGameWinner(team);
                if(!checkGameOver()) {
                    messageAll(e.getPlayer().getDisplayName()+" has won first place! Game will end automatically in 10 seconds.");
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if(!gameEnded) {exit();}
                        }
                    }.runTaskLater(Main.getInstance(), 20 * 10);
                }
            } else {
                checkGameOver();
            }
        }
    }

    @Override @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        super.onPlayerDeath(e);
        checkGameOver();
    }

    private boolean checkGameOver() {
        if(winningPlayerCount + deadPlayerCount == getTeams().size()) {
            exit();
            return true;
        }
        return false;
    }

    @Override
    protected void exit() {
        if(gameEnded) {return;}
        gameEnded = true;
        for(int i = winners.size()-1; i > 0; i--) {//Exclude the #1 player
            awardLoserTickets(winners.get(i));
        }

        List<MinigameTeam> teamRanks = new ArrayList<>(winners.size() + losers.size());
        for(int i = losers.size()-1; i >= 0; i--) {
            winners.add(losers.get(i));
        }
        for(Player p : winners) {
            teamRanks.add(getTeam(p));
        }
        getGameRecord().setTeamRanks(teamRanks);
        super.exit();
    }

    @Override
    protected void awardLoserTickets(Player p) {
        if(!gameEnded && winners.contains(p)) {return;}//Prevent players from winning + dying, getting counted twice
        if(deadPlayerCount >= getTeams().size() / 2) {
            int tickets = (int) (1 / Math.pow(2, getTeams().size() - deadPlayerCount) * ticketsRemaining);
            PlayerManager.awardTickets(p, tickets);
            ticketsRemaining -= tickets;
        }
        deadPlayerCount++;
    }

    @Override
    protected int getTicketsAwarded() {
        return ticketsRemaining;
    }

    private void calculateSpawns() {
        BlockVector3 min = getGameArea().getMinimumPoint();
        BlockVector3 max = getGameArea().getMaximumPoint();
        BlockVector3 gameSize = max.subtract(min).add(1, 1, 1);
        boolean xDir = gameSize.getBlockX() > gameSize.getBlockZ();

        for(int i = 0; i < spawnPoints.length; i++) {
            spawnPoints[i] = new Location(getWorld(),
                    min.getBlockX() + ((double)gameSize.getBlockX() / spawnPoints.length * (xDir? 0 : i) +
                            ((double)gameSize.getBlockX() / spawnPoints.length / 2 * (xDir ? 0 : 1))) + (xDir ? 1 : 0),
                    min.getY()+2,
                    min.getBlockZ() + ((double)gameSize.getBlockZ() / spawnPoints.length * (xDir? i : 0) +
                            ((double)gameSize.getBlockZ() / spawnPoints.length / 2 * (xDir ? 1 : 0))) + (xDir ? 0 : 1)
            );
        }

        endZone = new CuboidRegion(
                BlockVector3.at(xDir? max.getBlockX() : min.getBlockX(), min.getBlockY(), xDir ? min.getBlockZ() : max.getBlockZ()),
                BlockVector3.at(max.getBlockX(), max.getBlockY(), max.getBlockZ())
        );
    }
}