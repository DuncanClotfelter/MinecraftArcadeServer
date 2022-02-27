package minigame.util;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import minigame.game.MinigameSettings;
import minigame.player.PlayerManager;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.List;

@Value
public class MinigameTeam {
    String name;
    List<Player> players;
    int originalSize;
    double averageElo;
    @Setter @Getter @NonFinal private Team scoreboardTeam;

    public MinigameTeam(String name, List<Player> players, MinigameSettings settings) {
        this.name = name;
        this.players = players;

        double total = 0;
        for(Player p : players) {
            total += PlayerManager.getHighScore(p, settings.name());
        }
        originalSize = players.size();
        averageElo = total / originalSize;
    }
}