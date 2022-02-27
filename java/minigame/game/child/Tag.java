package minigame.game.child;

import minigame.Main;
import minigame.game.Minigame;
import minigame.game.MinigameSettings;
import minigame.player.PlayerManager;
import minigame.util.MinigameTeam;
import minigame.util.Misc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class Tag extends Minigame implements Listener {
    private final int MAX_TAG_SEC = 30;
    private final int GAME_LEN_SEC = 300;
    private final int MAX_WIN_TIME_DIFF = 2;
    private Instant lastTag = Instant.now();
    private boolean forcedTag = true;
    private Player it;
    private final HashMap<UUID, Duration> timeTagged = new HashMap<>();
    private int amtWinners = 1;
    private final BukkitTask timeLimit;
    private boolean outOfTime = false;

    public Tag(World world, String region, List<MinigameTeam> teams, Location exit) {
        super(MinigameSettings.TAG, world, region, teams, exit);

        tagRandom();
        startTimer();
        timeLimit = setTimeLimit();
    }

    private void tagRandom() {
        if(getPlayersRemaining() <= 1) {return;}

        Player chosen = null;
        while(chosen == null) {
            int i = (int) (Math.random() * getTeams().size());
            forcedTag = true;
            if(getTeams().get(i).getPlayers().size() == 0) {continue;}
            chosen = getTeams().get(i).getPlayers().get(0);
        }
        tag(chosen);
    }

    private void tag(Player p) {
        p.sendMessage(Misc.color("&cYou're it!"));
        if(it != null) {
            getGameRecord().set(it, "tagged", Duration.between(lastTag, Instant.now()));
            getGameRecord().nextRound();
        }
        it = p;
        lastTag = Instant.now();
        startTimer();
    }

    private void startTimer() {
        stopTimer(0);
        startTimer(MAX_TAG_SEC, 1, this::notifyPlayer, this::disqualify);
    }

    private List<MinigameTeam> disqualify(int timeRemaining) {
        if(timeRemaining == 0) {
            it.sendMessage("Oh dear, you were too slow!");
            kickPlayer(it);
            tagRandom();
            return null;
        }
        return Collections.singletonList(getTeam(it));
    }

    private String notifyPlayer(int timeRemaining) {
        if(timeRemaining == 10) {
            return "You have 10 seconds remaining to tag someone, or else you'll be disqualified!";
        }
        return null;
    }

    @EventHandler
    protected void onPlayerTag(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) {return;}
        Player victim = (Player) e.getEntity();
        Player perp = (Player) e.getDamager();
        if(!inGame(victim) || !inGame(perp) || !perp.equals(it)) {return;}
        Duration time = Duration.between(lastTag, Instant.now());
        if(!forcedTag) {
            if(timeTagged.containsKey(perp.getUniqueId())) {
                timeTagged.put(perp.getUniqueId(), timeTagged.get(perp.getUniqueId()).plus(time));
            } else {
                timeTagged.put(perp.getUniqueId(), time);
            }
        }
        e.setDamage(0);
        victim.setVelocity(perp.getLocation().getDirection().setY(0).normalize().multiply(5));
        forcedTag = false;
        tag(victim);
    }

    @Override
    protected int getTicketsAwarded() {
        return super.getTicketsAwarded() / amtWinners;
    }

    @Override
    protected void exit() {
        if(!outOfTime) {//Everyone quit before the timer was up :(
            timeLimit.cancel();
            super.exit();
            return;
        }

        for(MinigameTeam team : getTeams()) {
            for(Player p : team.getPlayers()) {
                if(!timeTagged.containsKey(p.getUniqueId())) {
                    timeTagged.put(p.getUniqueId(), Duration.ofSeconds(0));
                }
            }
        }
        //Sort our tagged player data, so we know who was tagged the least
        ArrayList<Map.Entry<UUID, Duration>> sortedList = new ArrayList<>(timeTagged.entrySet());
        sortedList.sort((o1, o2) -> o2.getValue().minus(o1.getValue()).getNano());

        //Collect our winner(s)
        ArrayList<Player> winners = new ArrayList<>(1);
        winners.add(Bukkit.getPlayer(sortedList.get(0).getKey()));
        for(int i = 1; i < sortedList.size(); i++) {
            System.out.println("Least time tagged: "+sortedList.get(0).getValue().getSeconds()+" comparing to: "+sortedList.get(i).getValue().getSeconds());
            if(sortedList.get(i).getValue().getSeconds() - sortedList.get(0).getValue().getSeconds() <= MAX_WIN_TIME_DIFF) {
                winners.add(Bukkit.getPlayer(sortedList.get(i).getKey()));
            } else {break;}
        }

        //Inform players that the game is over
        StringBuilder winnerList = new StringBuilder("Game over - ").append(winners.get(0).getDisplayName());
        for(int i = 1; i < winners.size(); i++) {
            if(i == 1 && winners.size() == 2) {
                winnerList.append(" and ").append(winners.get(i).getDisplayName());
            } else if(i == winners.size()-1) {
                winnerList.append(", and ").append(winners.get(i).getDisplayName());
            } else {
                winnerList.append(", ").append(winners.get(i).getDisplayName());
            }
        }
        amtWinners = winners.size();
        winnerList.append(" spent the least time tagged and").append(winners.size() == 1 ? " wins " : " win ").append(getTicketsAwarded()).append(" tickets!");
        messageAll(winnerList.toString());

        //Distribute tickets
        getGameRecord().setGameWinner(getTeam(winners.get(0)));
        for(int i = 1; i < winners.size(); i++) {
            PlayerManager.awardTickets(winners.get(i), getTicketsAwarded());
        }
        super.exit();
    }

    private BukkitTask setTimeLimit() {
        //Conclude the game after the amount of time specified in GAME_LEN_SEC
        return new BukkitRunnable() {
            @Override
            public void run() {
                outOfTime = true;
                exit();
            }
        }.runTaskLater(Main.getInstance(), 20 * GAME_LEN_SEC);
    }
}
