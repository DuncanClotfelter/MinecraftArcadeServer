package minigame.game;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.Getter;
import lombok.var;
import minigame.Main;
import minigame.lobby.MinigameLobby;
import minigame.lobby.MinigameLobbyGroup;
import minigame.player.PlayerManager;
import minigame.util.GlobalSettings;
import minigame.util.MinigameTeam;
import minigame.util.Misc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class MinigameManager {
    //List of every created minigame region
    @Getter private final static List<String> allMinigames = new ArrayList<String>();

    //Maps the Player (by UUID) to the ID of the Lobby they are currently in. Defaults to -1 for no lobby.
    private final static HashMap<UUID, String> currentLobby = new HashMap<>();

    //Set of the Minigame Region ID (unique) of the actively running Minigame instance
    private final static HashSet<String> activeGames = new HashSet<>();

    //Set of Player UUIDs currently in an active minigame
    private final static HashMap<UUID, Minigame> playersInGame = new HashMap<>();

    /*
        Minigame Flags
        "minigame" - "checkers" for all game areas and lobbies of this minigame type
        "team-lobby" - "blue" sets the name of this 'team' in the minigame. "ffa" sets each team name to the Player's name
        "minigame-uuid" - matches a group of lobbies with (1) minigame area. Must be unique to each set.
     */
    private static StringFlag minigameFlag;
    private static StringFlag lobbyFlag;
    private static StringFlag minigameIDflag;
    private static StringFlag teleportFlag;
    private static RegionManager regionManager;

    static void start() {
        MinigameSettings.loadConfig();
        registerFlags();
        findRegions();
    }

    //Add WorldGuard flags for every Minigame set in MinigameSettings
    public static void registerFlags() {
        minigameFlag = registerStringFlag("minigame-name", "Minigame");
        lobbyFlag = registerStringFlag("team-lobby", "");
        minigameIDflag = registerStringFlag("minigame-uuid", "");
        teleportFlag = registerStringFlag("exit-coords", "");
    }

    private static StringFlag registerStringFlag(String flagName, String def) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StringFlag flag = new StringFlag(flagName, def);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            // some other plugin registered a flag by the same name already
            Flag<?> existing = registry.get(flagName);
            if (existing instanceof StringFlag) {
                return (StringFlag) existing;
            } else {
                fatalError("WorldGuard flags are configured incorrectly. Already exists use of "+flagName+
                        " outside this plugin. Please reinstall WorldGuard or deregister that flag for this plugin's use.");
                return null;
            }
        }
    }

    private static void fatalError(String error) {
        Main.getInstance().getLogger().severe(error);
        Bukkit.getPluginManager().disablePlugin(Main.getInstance());
    }

    /** Finds all regions with our minigame flags set
     * Matches lobby(ies) to minigame area via minigame-uuid
     * If no minigame-uuid is provided, this will match all lobbies to a singular minigame,
     * or throw an exception if several minigames are found
     */
    public static void findRegions() {
        regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(
                GlobalSettings.getMinigameWorld()
        ));
        if(regionManager == null) {
            fatalError("Failed to find RegionManager for this world. Make sure the world is set in this plugin's config correctly.");
            return;
        }
        Map<String, ProtectedRegion> allRegions = regionManager.getRegions();
        Map<String, ArrayList<ProtectedRegion>> matches = new HashMap<>();
        for(ProtectedRegion region : allRegions.values()) {
            String minigame = region.getFlag(minigameFlag);

            //Match all regions with one of our custom flags together by value
            if(minigame != null) {
                boolean foundMatches = matches.containsKey(minigame);
                var siblings = foundMatches? matches.get(minigame) : new ArrayList<ProtectedRegion>();
                siblings.add(region);
                if(!foundMatches) {matches.put(minigame, siblings);}
            }
        }

        for(Map.Entry<String, ArrayList<ProtectedRegion>> matchList : matches.entrySet()) {
            //Check if given minigame exists
            MinigameSettings ms;
            try {
                ms = MinigameSettings.valueOf(matchList.getKey().toUpperCase());
            } catch(IllegalArgumentException e) {
                Bukkit.getLogger().severe("Region(s) with minigame/lobby flag "+matchList.getKey()+" do not match any configured minigame!");
                continue;
            }

            //Sort regions by their minigame UUID flag to allow for several instances of the same minigame
            var gameUUIDs = new HashMap<String, String>(); //Map<pairUUID, regionUUID>
            var sortedLobbies = new HashMap<String, ArrayList<ProtectedRegion>>();//Map<pairUUID, Lobbies>
            for(ProtectedRegion region : matchList.getValue()) {
                String pairUUID = region.getFlag(minigameIDflag);
                pairUUID = (pairUUID == null)? "unset" : pairUUID;
                if(region.getFlag(lobbyFlag) == null) {
                    //Minigame Region - only one allowed per pairing
                    if(gameUUIDs.get(pairUUID) != null) {
                        Bukkit.getLogger().warning("Minigame-uuid flag set incorrectly. UUIDs must be unique, and" +
                                "there is a maximum of 1 unset UUID game/lobbies parings per Minigame.");
                    } else {
                        gameUUIDs.put(pairUUID, region.getId());
                        allMinigames.add(region.getId());
                    }
                } else {
                    //Lobby Region - multiple allowed
                    sortedLobbies.computeIfAbsent(pairUUID, k -> new ArrayList<>());
                    sortedLobbies.get(pairUUID).add(region);
                }
            }

            //Instantiate our Lobby objects, then add them a new group
            for(Map.Entry<String, String> game : gameUUIDs.entrySet()) {
                ArrayList<ProtectedRegion> linkedLobbies = sortedLobbies.get(game.getKey());

                if(linkedLobbies == null) {
                    Bukkit.getLogger().warning("One of your minigames does not have any lobbies linked to it! " +
                            "Make sure BOTH the minigame and the lobbies have the \"minigame-name\" WorldGuard flag.");
                } else {
                    MinigameLobbyGroup lobbyGroup = new MinigameLobbyGroup(linkedLobbies.size(), game.getValue(), parseExitCoords(getRegion(game.getValue())), ms);

                    for(ProtectedRegion region : linkedLobbies) {
                        MinigameLobby lobby = new MinigameLobby(lobbyGroup, region.getId(), region.getFlag(lobbyFlag), parseExitCoords(region), region);
                        lobbyGroup.add(lobby);
                    }
                }
            }

            //Inform user if they have unmatched lobbies. Flawed, as this check will not work if they also have unmatched games.
            if(gameUUIDs.keySet().size() < sortedLobbies.keySet().size()) {
                Bukkit.getLogger().warning("One of your lobby groups does not have a minigame matched to it! " +
                        "Make sure BOTH the minigame and the lobbies have the \"minigame-name\" WorldGuard flag.");
            }
        }
    }

    public static Location getViewerLocation(String regionID) {
        return Misc.strToLoc(GlobalSettings.getMinigameWorld(), regionManager.getRegion(regionID).getFlag(teleportFlag));
    }

    public static String getMinigameName(String regionID) {
        return Misc.sanitize(getRegion(regionID).getId());
    }

    public static MinigameSettings getMinigameSettings(String regionID) {
        return MinigameSettings.valueOf(getRegion(regionID).getFlag(minigameFlag).toUpperCase());
    }

    private static Location parseExitCoords(ProtectedRegion region) {
        String coords = region.getFlag(teleportFlag);
        if(coords == null) {return null;}
        try {
            return Misc.strToLoc(GlobalSettings.getMinigameWorld(), coords);
        } catch(Exception e) {
            Bukkit.getLogger().warning("The "+teleportFlag.getName()+" flag is misconfigured on region "+region.getId() + " (" +
                    coords+"). Players will NOT be teleported until this is fixed! Correct syntax: x=?,y=?,z=?,(OPTIONAL:)pitch=?,yaw=?");
            return null;
        }
    }

    //Returns the ProtectedRegion with ID regionID
    public static ProtectedRegion getRegion(String regionID) {
        return regionManager.getRegion(regionID);
    }


    //Returns the Lobby ID that the player is currently in, or null if no lobby.
    public static String getLobbyID(@NotNull Player p) {
        return currentLobby.get(p.getUniqueId());
    }

    //Sets the Lobby this Player is in. For MinigameLobby use only.
    public static void setLobbyID(Player p, String lobbyID) {
        if(lobbyID == null) {
            currentLobby.remove(p.getUniqueId());
            return;
        }

        currentLobby.put(p.getUniqueId(), lobbyID);
    }
    //Returns true if there is an ongoing minigame at the region given by String regionID
    public static boolean isGameActive(String regionID) {
        return activeGames.contains(regionID);
    }

    public static void kickPlayer(Player p) { playersInGame.remove(p.getUniqueId());}

    public static Minigame getGame(Player p) {return playersInGame.get(p.getUniqueId());}

    public static boolean inGame(Player p) {return playersInGame.get(p.getUniqueId()) != null;}

    public static boolean inLobby(Player p) {return getLobbyID(p) != null;}

    public static void startGame(MinigameSettings settings, List<MinigameTeam> teams, String regionID, Location gameExitCoords) {
        System.out.println("Starting game!");
        Minigame m = (Minigame)settings.getConstructor().apply(GlobalSettings.getMinigameWorld(), regionID, teams, gameExitCoords);
        activeGames.add(regionID);
        for(MinigameTeam team : teams) {
            for(Player p : team.getPlayers()) {
                playersInGame.put(p.getUniqueId(), m);
            }
        }
    }

    public static void endGame(Minigame m) {
        activeGames.remove(m.getRegionID());
        for(MinigameTeam team : m.getTeams()) {
            for(Player p : team.getPlayers()) {
                playersInGame.remove(p.getUniqueId());
            }
        }
    }

    public static void killAllGames() {
        for(Minigame m : playersInGame.values()) {
            m.exit();
        }
    }

    public static void closeScoreboard(Player p) {
        PlayerManager.updateScoreboard(p);
    }
}
