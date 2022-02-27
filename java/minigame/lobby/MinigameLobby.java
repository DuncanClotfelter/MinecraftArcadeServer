package minigame.lobby;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.Getter;
import minigame.Main;
import minigame.game.Minigame;
import minigame.game.MinigameManager;
import minigame.player.PlayerManager;
import minigame.util.GlobalSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MinigameLobby implements Listener {
    @Getter private final String lobbyID;
    @Getter private final MinigameLobbyGroup group;
    private final ProtectedRegion lobbyArea;
    private final String teamName;
    private final Location exitCoords;
    @Getter private ArrayList<Player> players;
    private final Scoreboard gameInfo;

    public MinigameLobby(MinigameLobbyGroup group, String lobbyID, String teamName, Location exitCoords, ProtectedRegion lobbyArea) {
        this.lobbyID = lobbyID;
        this.group = group;
        this.lobbyArea = lobbyArea;
        this.teamName = teamName;
        this.exitCoords = exitCoords;

        int maxPlayers = group.getSettings().getMaxTeamSize();
        if(maxPlayers > 0) {//If no max players, maxTeamSize is set to -1
            players = new ArrayList<>(maxPlayers);
        } else {
            players = new ArrayList<>();
        }

        ScoreboardManager sm = Bukkit.getScoreboardManager();
        this.gameInfo = sm.getNewScoreboard();
        Objective obj = gameInfo.registerNewObjective(lobbyID, "dummy", group.getSettings().getDisplayName());
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Bukkit.getServer().getPluginManager().registerEvents(this, Main.getInstance());
    }

    private void joinLobby(@NotNull Player p) {
        System.out.println(p.getName()+" joined the lobby."); //TODO remove
        MinigameManager.setLobbyID(p, lobbyID); //Update Player's current lobby

        players.add(p);
        gameInfo.getObjective(lobbyID).getScore(p.getDisplayName()).setScore((int) PlayerManager.getHighScore(p, group.getSettings().name()));
        updateScoreboard();

        if(players.size() == 1) {//Attempt to start the timer if this is the first person
            group.notifyPlayerChange(true);
        }

        if(players.size() >= group.getSettings().getMaxTeamSize()) {//Attempt to start the game if the lobby is full
            if(group.startLobby(true, true, false)) {
                return;
            }
        }

        //Welcome the Player
        String lobbyMsg = group.getSettings().getLobbyMessage();
        if(lobbyMsg != null) {
            p.sendMessage(
                    group.getSettings().getLobbyMessage()
                                        .replaceAll("%name%", group.getSettings().getDisplayName())
                                        .replaceAll("%player%", p.getDisplayName())
            );
        } else {
            lobbyMsg = GlobalSettings.getDefaultLobbyMsg();
            if(lobbyMsg != null) {
                p.sendMessage(lobbyMsg);
            }
        }

        if(group.getSettings().getMaxWaitTime() > 0) {
            long seconds = Duration.ofSeconds(group.getSettings().getMaxWaitTime()).minus(Duration.between(group.getQueueStart(), Instant.now())).getSeconds();

            p.sendMessage("This game will start automatically in "+(seconds > 60 ? seconds/60 : "<1")+" minutes, or once" +
                    " the lobby has reached "+group.getSettings().getMaxPlayers()+" players.");
            //TODO temp while this info is not on the scoreboard
        }
    }

    private void exitLobby(Player p) {
        MinigameManager.setLobbyID(p, null);
        players.remove(p);
        group.notifyPlayerChange(false);
        gameInfo.resetScores(p.getDisplayName());
        MinigameManager.closeScoreboard(p);
    }

    protected void clearLobby(List<Player> toRetain) {
        System.out.println("Clearing lobby");
        for(Player p : players) {
            if(!toRetain.contains(p)) {
                gameInfo.resetScores(p.getDisplayName());
                MinigameManager.setLobbyID(p, null);
                if(exitCoords != null) {
                    p.teleport(exitCoords);
                }
            }
        }

        if(!toRetain.isEmpty()) {
            players = new ArrayList<>(players);
            players.retainAll(toRetain);
        } else {
            players = new ArrayList<>(group.getSettings().getMaxTeamSize());
        }

        for(Player p : toRetain) {
            p.sendMessage("Sorry! You could not join the game due to a matchmaking imbalance.");
        }

        System.out.println("Lobby cleared - check for openables?: "+group.getSettings().isCheckForOpenables());
        if(group.getSettings().isCheckForOpenables()) {openLobby();}
    }

    protected void clearLobby() {
        clearLobby(new ArrayList<>(0));
    }

    /**
     * Opens all gates/doors within the lobby to allow players out. Closes them back after a specified amount of time.
     */
    protected void openLobby() {
        System.out.println("Opening lobby doors");
        //Find all Openables and add them to our collection to close later
        List<Block> opened = new ArrayList<>();
        Block b; Openable o;
        for(int x = lobbyArea.getMinimumPoint().getBlockX()-1; x <= lobbyArea.getMaximumPoint().getBlockX()+1; x++) {
            for(int y = lobbyArea.getMinimumPoint().getBlockY()-1; y <= lobbyArea.getMaximumPoint().getBlockY()+1; y++) {
                for(int z = lobbyArea.getMinimumPoint().getBlockZ()-1; z <= lobbyArea.getMaximumPoint().getBlockZ()+1; z++) {
                    b = GlobalSettings.getMinigameWorld().getBlockAt(x, y, z);
                    if(b.getBlockData() instanceof Openable) {
                        o = (Openable) b.getBlockData();
                        System.out.println("Openable found: "+o.isOpen());
                        if(!o.isOpen()) {
                            opened.add(b);
                            o.setOpen(true);
                            b.setBlockData(o);
                            b.getState().update();
                        }
                    }
                }
            }
        }

        //Close the Openables on a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                Openable o;
                for(Block b : opened) {
                    o = ((Openable)b.getBlockData());
                    o.setOpen(false);
                    b.setBlockData(o);
                    b.getState().update();
                }
            }
        }.runTaskLater(Main.getInstance(), 20 * GlobalSettings.getCloseLobbyDelaySeconds());
    }

    @EventHandler
    private void onMove(@NotNull PlayerMoveEvent e) {
        if(e.getTo() == null || sameBlock(e.getFrom(), e.getTo())) {return;}

        if(MinigameManager.inGame(e.getPlayer())) {
            if(group.getSettings().isGameInLobby()) {//For games that are played while standing in the lobby
                Minigame m = MinigameManager.getGame(e.getPlayer());
                if (group.getMinigameRegion().equals(m.getRegionID())) {
                    if (inLobbyArea(e.getFrom()) && !inLobbyArea(e.getTo())) {
                        if(group.getSettings().isGameAreaRestrictedOut()) {
                            e.setCancelled(true);
                        } else {
                            m.kickPlayer(e.getPlayer());
                        }
                    }
                }
            }
            return;
        }

        if(!checkStatus(e.getPlayer(), e.getTo(), false)) {e.setCancelled(true);}
    }

    @EventHandler
    private void onPlayerJoin(@NotNull PlayerJoinEvent e) {
        checkStatus(e.getPlayer(), e.getPlayer().getLocation(), true);
    }

    /**
     * Checks if a Player has physically entered/exited this lobby
     * @param p Player to check
     * @param loc Location Player is moving to
     */
    private boolean checkStatus(Player p, @NotNull Location loc, boolean onJoin) {
        boolean inArea = inLobbyArea(loc);
        String lastLobby = MinigameManager.getLobbyID(p);
        if(inArea && !lobbyID.equals(lastLobby)) {//Player entering this lobby
            if(onJoin && group.getGameExitCoords() != null) {
                p.teleport(group.getGameExitCoords());
                return false;
            }
            if(!PlayerManager.hasTokens(p, group.getSettings().getTokenCost())) {
                p.sendMessage(GlobalSettings.getInsufficientTokensMsg(group.getSettings().getTokenCost()));
                return false;
            }
            Minigame m = MinigameManager.getGame(p);
            if(group.getSettings().isLateJoinAllowed() && m != null) {
                if(!m.addLatePlayer(p, lobbyID)) {
                    p.sendMessage("Sorry, this game is currently full.");
                    return false;
                }
            } else {
                joinLobby(p);
            }
        } else if(lobbyID.equals(lastLobby) && !inArea) {
            exitLobby(p);
        }
        return true;
    }

    private boolean inLobbyArea(Location loc) {
        return lobbyArea.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @EventHandler
    private void onPlayerQuit(@NotNull PlayerQuitEvent e) {
        if(lobbyID.equals(MinigameManager.getLobbyID(e.getPlayer()))) {
            exitLobby(e.getPlayer());
        }
    }

    private boolean sameBlock(@NotNull Location l1, @NotNull Location l2) {
        return  l1.getBlockX() == l2.getBlockX() &&
                l1.getBlockY() == l2.getBlockY() &&
                l1.getBlockZ() == l2.getBlockZ();
    }

    protected void updateScoreboard() {
        for(Player p : players) {
            p.setScoreboard(gameInfo);
        }
    }
}
