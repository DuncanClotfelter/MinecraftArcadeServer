package minigame.util;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;

public class GlobalSettings {
    @Getter private static final String schematicsDirectory = "plugins/WorldEdit/schematics/";
    @Getter private static final String defaultLobbyMsg = null;
    @Getter private static final boolean useDatabase = true; //TODO unused
    @Getter private static final String databaseAddress = "localhost";
    @Getter private static final String databaseName = "test_database";
    @Getter private static final String databaseUsername = "root";
    @Getter private static final String databasePassword = "password";
    @Getter private static final String databaseTablePrefix = "minigame";
    @Getter private static final String serverName = "CraftyCheese";
    @Getter private static final String gamePassName = "CraftyCheese Game Pass";
    @Getter private static final String welcomeMessage = "Welcome to the "+serverName+" Arcade! Feel free to explore or teleport via the book to get right into a game!";
    @Getter private static final String websiteAddress = "www.google.com";
    @Getter private static final String firstTimeMessage = welcomeMessage;
    @Getter private static final int firstJoinTokenAward = 200;
    @Getter private static final double startingElo = 1000;
    @Getter private static final double eloConstant = 30;
    @Getter private static final String[] colorRotation = {"RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "ORANGE", "WHITE", "BLACK"};
    @Getter private static final int closeLobbyDelaySeconds = 5;
    @Getter private static final boolean hungerDisabled = true;
    @Getter private static final double ticketMultiplier = 1;
    @Getter private static final double ticketTokenRatio = 2;
    @Getter private static final double scaledEloMultiplier = 2;

    public static String getColor(int idx) { return colorRotation[idx % colorRotation.length]; }
    public static ChatColor getChatColor(int idx) { return ChatColor.valueOf(colorRotation[idx % colorRotation.length]); }
    public static World getMinigameWorld() {
        return Bukkit.getServer().getWorlds().get(0);
    }
    public static String getInsufficientTokensMsg(int amt) {return "Sorry, you need "+amt+" tokens to play this minigame.";}
}
