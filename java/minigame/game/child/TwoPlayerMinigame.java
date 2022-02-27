package minigame.game.child;

import lombok.Getter;
import minigame.game.Minigame;
import minigame.game.MinigameSettings;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public abstract class TwoPlayerMinigame extends Minigame {
    @Getter private boolean homeTurn = true;
    private Instant turnStart;
    private final Duration[] timeRemaining = {timeLimit(), timeLimit()};

    protected Duration timeLimit() {return Duration.ofMinutes(10);}

    public TwoPlayerMinigame(MinigameSettings gameType, World world, String region, List<MinigameTeam> teams, Location exit) {
        super(gameType, world, region, teams, exit);

        turnStart = Instant.now();
        startTimer((int)timeRemaining[homeTurn? 0 : 1].getSeconds(), 1, this::notifyPlayer, this::getTeam);
    }

    protected void startNextTurn() {
        if(isGameOver()) {
            endGame();
            return;
        }

        timeRemaining[homeTurn? 0 : 1] = timeRemaining[homeTurn? 0 : 1].minus(Duration.between(turnStart, Instant.now()));
        homeTurn = !homeTurn;
        turnStart = Instant.now();
        stopTimer(0);
        getGameRecord().nextRound();
        startTimer((int)timeRemaining[homeTurn? 0 : 1].getSeconds(), 1, this::notifyPlayer, this::getTeam);
    }

    private String notifyPlayer(int timeRemaining) {
        //updateScoreboard
        if(!(timeRemaining % 60 == 0) && timeRemaining != 0) {return null;}
        if(timeRemaining != 0) {
            return "Your time bank has " + (timeRemaining / 60) + " minutes remaining.";
        } else {
            homeTurn = !homeTurn;
            endGame();
            return "You ran out of time!";
        }
    }

    private List<MinigameTeam> getTeam(int timeRemaining) {
        return homeTurn ? Arrays.asList(getTeams().get(0)) : Arrays.asList(getTeams().get(1));
    }

    protected abstract boolean isGameOver();

    protected void endGame() {
        getPlayer().sendMessage("Congratulations! You have won!");
        getEnemy().sendMessage("Better luck next time!");
        getGameRecord().setGameWinner(currentTeam());
        exit();
    }

    protected Player getPlayer() {
        return homeTurn ? getTeams().get(0).getPlayers().get(0) : getTeams().get(1).getPlayers().get(0);
    }

    protected Player getEnemy() {
        return homeTurn ? getTeams().get(1).getPlayers().get(0) : getTeams().get(0).getPlayers().get(0);
    }

    private MinigameTeam currentTeam() {
        return homeTurn ? getTeams().get(0) : getTeams().get(1);
    }
}
