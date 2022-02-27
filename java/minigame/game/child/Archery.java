package minigame.game.child;

import com.sk89q.worldedit.math.transform.AffineTransform;
import minigame.util.MinigameTeam;
import minigame.game.Minigame;
import minigame.game.MinigameSettings;
import minigame.player.PlayerManager;
import minigame.util.GlobalSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class Archery extends Minigame {
    private int totalPoints = 0;
    private int currentTarget = 0;
    private Direction targetDir;
    private final int TARGET_DURATION = 5;
    //private Location targetLocation;

    public Archery(World world, String region, List<MinigameTeam> teams, Location exit) {
        super(MinigameSettings.ARCHERY, world, region, teams, exit);
        getGameRecord().setGameWinner(getTeam());
        getPlayer().getInventory().setItem(0, new ItemStack(Material.BOW, 1));
        getPlayer().getInventory().setItem(1, new ItemStack(Material.ARROW, 1));
        startTimer(TARGET_DURATION, 1, this::notifyPlayer, this::moveTarget);
    }

    private List<MinigameTeam> moveTarget(int timeRemaining) {
        if(timeRemaining != 0 && currentTarget != 0) {return getTeams();}
        if(currentTarget++ != 0) {
            undo();
        }
        getGameRecord().increment(getPlayer(), "points", 0);
        getGameRecord().nextRound();
        targetDir = Direction.random();
        resetTransform();
        setTransform(targetDir.applyTransform(getTransform()));
        Location toPaste = targetDir.getSpawnArea(this).add(getLocation(getGameArea().getMinimumPoint()));
        addModel("target", adj(toPaste));
        getPlayer().getInventory().setItem(1, new ItemStack(Material.ARROW, 1));
        if(currentTarget == 20) {
            exit();
        }
        return currentTarget == 20 ? null : getTeams();
    }

    private String notifyPlayer(int timeRemaining) {
        if(timeRemaining == TARGET_DURATION && currentTarget == 1) {
            return "Your first target is toward the "+targetDir+"!";
        } else if(timeRemaining == 0) {
            return "The target has moved to the "+targetDir+"! You have "+TARGET_DURATION+" seconds to shoot it!";
        } else if(timeRemaining < 4) {
            return timeRemaining+"...";
        }
        return null;
    }

    @EventHandler
    protected void onProjectileHit(ProjectileHitEvent e) {
        if(!(e.getEntity() instanceof Arrow)) {return;}
        if(!(e.getEntity().getShooter() instanceof Player)) {return;}
        Arrow a = (Arrow) e.getEntity();
        Player p = (Player) e.getEntity().getShooter();
        if(p == null) {return;}
        if(!inGame(p)) {return;}

        int points = 0;
        if(e.getHitBlock() != null) {
            points = getPoints(e.getHitBlock().getType());
        }

        if(points > 0) {
            p.sendMessage("Nice shot! You gained "+points+" points!");
        }

        totalPoints += points;
        getGameRecord().increment(p, "points", points);
    }

    @Override
    protected void exit() {
        undo();
        super.exit();
    }

    @Override
    protected int getTicketsAwarded() {
        return totalPoints / 4;
    }

    @Override
    protected void awardLoserTickets(Player p) {
        PlayerManager.awardTickets(p, getTicketsAwarded());
    }

    private int getPoints(Material hit) {
        switch(hit) {
            case SEA_LANTERN:
                return 10;
            case RED_WOOL:
                return 5;
            case BLUE_WOOL:
                return 2;
            default:
                return 0;
        }
    }

    private MinigameTeam getTeam() {return getTeams().get(0);}
    private Player getPlayer() {
        return getTeams().get(0).getPlayers().get(0);
    }

    private enum Direction {
        NORTH, EAST, SOUTH, WEST, UP;

        private static final Random RANDOM = new Random();
        public static Direction random() {return values()[RANDOM.nextInt(values().length)];}

        public AffineTransform applyTransform(AffineTransform old) {
            switch(this) {
                case EAST:
                case WEST:
                    return old.rotateY(90);

                case UP:
                    return old.rotateX(90);

                default:
                    return old;
            }
        }

        public Location getSpawnArea(Minigame m) {
            switch(this) {
                case NORTH:
                    return tailoredRandom(m, 0.6, 0.2, 0.6, 0.2, 0.2, 0);

                case SOUTH:
                    return tailoredRandom(m, 0.6, 0.2, 0.6, 0.2, 0.2, 0.8);

                case WEST:
                    return tailoredRandom(m, 0.2, 0, 0.6, 0.2, 0.6, 0.2);

                case EAST:
                    return tailoredRandom(m, 0.2, 0.8, 0.6, 0.2, 0.6, 0.2);

                case UP:
                    return tailoredRandom(m, 0.8, 0.1, 0.2, 0.8, 0.8, 0.1);
            }
            return null;
        }

        private Location tailoredRandom(Minigame m, double varyX, double offX, double varyY, double offY, double varyZ, double offZ) {
            return new Location(GlobalSettings.getMinigameWorld(),
                    RANDOM.nextDouble() * m.getXLength() * varyX + m.getXLength() * offX,
                    RANDOM.nextDouble() * m.getYLength() * varyY + m.getYLength() * offY,
                    RANDOM.nextDouble() * m.getZLength() * varyZ + m.getZLength() * offZ
            );
        }
    }
}
