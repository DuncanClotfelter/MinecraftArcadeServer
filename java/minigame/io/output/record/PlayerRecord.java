package minigame.io.output.record;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PlayerRecord {
    private final String startingTeam;
    @Setter private String currentTeam;
    @Setter private double eloChange = 0;

    public PlayerRecord(String startingTeam) {
        this.startingTeam = startingTeam;
        this.currentTeam = startingTeam;
    }
}
