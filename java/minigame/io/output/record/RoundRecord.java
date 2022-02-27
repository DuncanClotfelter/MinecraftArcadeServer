package minigame.io.output.record;

import lombok.Getter;
import minigame.io.Record;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class RoundRecord extends Record {
    @Getter private final HashMap<UUID, RoundPlayerRecord> playerRecords = new HashMap<>();
    private final Instant roundStart; //Used to calculate round length
    @Getter private boolean modified; //TODO reutilize

    protected RoundRecord() {
        roundStart = Instant.now();
    }

    protected void endRound() {
        overwrite("length", Duration.between(roundStart, Instant.now()));
    }

    protected void addPlayers(List<Player> players, String teamName) {
        for(Player p : players) {
            this.playerRecords.put(p.getUniqueId(), new RoundPlayerRecord(p.getName(), p.getUniqueId(), teamName));
        }
    }

    protected void teamWon(String teamName) {
        getData().put("winning_team", teamName);
    }
}
