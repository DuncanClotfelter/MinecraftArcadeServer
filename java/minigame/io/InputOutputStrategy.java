package minigame.io;

import minigame.io.output.record.GameRecord;
import minigame.player.PlayerData;
import org.bukkit.entity.Player;

public interface InputOutputStrategy {
    void saveGame(GameRecord game) throws Exception;
    void savePlayer(Player p, PlayerData pd);
    int loadPlayer(Player p, PlayerData pd);
}
