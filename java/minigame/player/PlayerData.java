package minigame.player;

import lombok.Data;
import minigame.game.MinigameSettings;
import minigame.util.GlobalSettings;

import java.time.Instant;
import java.util.HashMap;

@Data
public class PlayerData {
    private int tokens;
    private int tickets;
    private int ticketsSpent;
    private int ticketsEarned;
    private int tokensSpent;
    private int tokensEarned;
    private int messagesSent;
    private Instant gamePassFinish = Instant.now();
    private Instant joinInstant = Instant.now();

    private HashMap<String, Double> gameRatings = new HashMap<>();

    private String[] lastSignText;//Temp variable to update Scoreboard

    public void subtractTokens(int amt) {
        tokens -= amt;
        tokensSpent -= amt;
    }

    public void addTickets(int amt) {
        tickets += amt;
        ticketsEarned += amt;
    }

    public void addElo(String minigame, double amt) {
        prepareRatings(minigame);
        gameRatings.put(minigame, gameRatings.remove(minigame)+amt);
    }

    public double getElo(String minigame) {
        prepareRatings(minigame);
        return gameRatings.get(minigame);
    }

    public void setElo(String minigame, Double amt) {
        gameRatings.put(minigame, amt);
    }

    private void prepareRatings(String minigame) {
        gameRatings.computeIfAbsent(minigame, (k) -> {
            double defaultValue = GlobalSettings.getStartingElo();
            String score = MinigameSettings.valueOf(minigame).getPrimaryScore();
            if(score != null && !score.equalsIgnoreCase("elo")) {
                defaultValue = 0;
            }
            return defaultValue;
        });
    }

    public boolean isGamePassActive() {
        return gamePassFinish.isAfter(Instant.now());
    }

    public void incrementMsgCount() {
        messagesSent++;
    }
}
