package minigame.game;

import io.vavr.Function4;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import minigame.game.child.*;
import minigame.util.GlobalSettings;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

@Getter
@AllArgsConstructor
public enum MinigameSettings {
    CHECKERS("Checkers", Checkers::new, 10, -1, 2, 2, 1, 1, -1, 3,
            "checkerboard", new String[]{"checkers_home", "checkers_away", "checkers_select", "checkers_homeking", "checkers_awayking", "checkers_selectking"},
            new boolean[] {true, false, true}, false, true,
            true, false, true,
            "Welcome to Checkers! The match will start automatically when both teams have a player.",
            "Your Checkers match has started - good luck!",
            null, false, false, false, false,
            false, false, false, false, false, false
    ),
    ARCHERY("Archery", Archery::new, 5, 50/*custom*/, 1, 1, 1, 1, -1, 3,
            null, new String[]{"target"},
            new boolean[] {false, false, false}, false, true,
            true, false, true, null,
            "20 arrows - 20 shots! Good luck!",
            "points", false, false, false, false,
            false, false, false, false, false, false
    ),
    BRIDGES("Bridge Battle", Bridges::new, 20, 80/*custom*/, 2, 8, 1, 1, 120, 3,
            "lava", new String[0],
            new boolean[] {false, false, false}, true, true,
            true, false, false,
            null,
            "Quick! Start building across!",
            null, false, true, true, true,
            false, true, false, false, false, false
    ),
    TAG("Tag", Tag::new, 10, -1/*custom*/, 2, 100, 1, 1, 15, 3,
            null, new String[0],
            new boolean[] {false, false, false}, false, false,
            true, false, false,
            null,
            null,
            "wins", false, false, false, true,
            false, true, false, true, false, false
    ),
    CONNECT4("Connect4", Connect4::new, 10, 40, 2, 2, 1, 1, -1, 3,
            "connect4board", new String[] {"connect4_home", "connect4_away"},
            new boolean[] {true, true, true}, false, true,
            true, false, true,
            null,
            null,
            null, false, false, false, false,
            false, true, false, false, false, false
    ),
    SPLEEF("Spleef", Spleef::new, 10, -1, 2, 20, 1, 1, 15, 3,
            "snow", new String[0],
            new boolean[] {true, false, true}, true, false,
            true, false, false,
            null,
            null,
            null, false, false, false, true,
            false, true, false, false, false, false
    ),
    GRAFFITI("Graffiti Wall", Graffiti::new, 0, 0, 1, 20, 1, 1, 0, 0,
            null, null,
            new boolean[] {false, false, false}, false, false,
            true, false, true,
            null,
            "Stand on a color to choose a brush color, swap projectiles to choose your brush size. Have fun!",
            "blocks_painted", false, false, false, true,
            false, true, false, false, false, true
    );

    private final String displayName;//required
    private final Function4<World, String, List<MinigameTeam>, Location, Minigame> constructor;
    private final int tokenCost;
    private final int ticketReward;
    private final int minTeams;
    private final int maxTeams;
    private final int minTeamSize;
    private final int maxTeamSize; //-1 for Unlimited Players (will start on timer)
    private final int maxWaitTime; //-1 for infinite wait time (no auto start)
    private final int lobbyDelaySeconds;
    @Getter(AccessLevel.NONE) private final String mainSchem;
    @Getter(AccessLevel.NONE) private final String[] moreSchem;
    private final boolean[] scaleDirs; //x/y/z, whether the schem should scale in this direction
    private final boolean gameAreaRestrictedIn;
    private final boolean gameAreaRestrictedOut;
    private final boolean friendlyInvisibleVisible;
    private final boolean friendlyFireAllowed;
    private final boolean gameInLobby;
    private final String lobbyMessage;//%name% %time% %remaining% %player% //TODO %time% & remaining players needed
    private final String minigameMessage;
    //private final Location viewerLocation;//must not be null if restrictGameArea is true
    private final String primaryScore;//Must be numeric
    private final boolean primaryScoreAggregate;
    private final boolean deadPlayerKicked;
    private final boolean deathAllowed;
    private final boolean teamRebalanced;
    private final boolean rankBalanced;
    private final boolean equalTeamSizeForced;
    private final boolean multiDeathsAllowed;
    private final boolean checkForOpenables;
    private final boolean dropOnDeath;
    private final boolean lateJoinAllowed;

    public String getSchemName(int idx) {
        if(idx == -1) {return mainSchem;}
        return moreSchem[idx];
    }

    public static String getDir(String schem) {
        return GlobalSettings.getSchematicsDirectory()+schem+".schem";
    }

    public boolean hasMainSchem() {
        return mainSchem != null;
    }

    public String getSchemDir(int idx) {
        String dir = GlobalSettings.getSchematicsDirectory();
        String fileExt = ".schem";
        if(idx == -1) {return dir+mainSchem+fileExt;}
        return dir+moreSchem[idx]+fileExt;
    }

    public int getSchemLength() {
        return moreSchem == null? 0 : moreSchem.length;
    }


    public int getRequiredPlayers() {
        return minTeams * minTeamSize;
    }

    public int getMaxPlayers() {
        return maxTeams * maxTeamSize;
    }



    public static void loadConfig() {

    }
}
