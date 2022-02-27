package minigame.io.output.record;

import lombok.Getter;
import minigame.io.Record;

import java.util.UUID;

@Getter
public final class RoundPlayerRecord extends Record {
    @Getter private String name;
    @Getter private UUID uuid;
    @Getter private String teamName;

    protected RoundPlayerRecord(String name, UUID uuid, String teamName) {
        this.name = name;
        this.uuid = uuid;
        this.teamName = teamName;
    }
}
