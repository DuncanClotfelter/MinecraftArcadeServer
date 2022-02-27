package minigame.lobby;

import lombok.Getter;
import minigame.Main;
import minigame.game.MinigameManager;
import minigame.game.MinigameSettings;
import minigame.player.PlayerManager;
import minigame.util.MinigameTeam;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MinigameLobbyGroup {
    private final List<MinigameLobby> lobbies;
    @Getter private final String minigameRegion;
    @Getter private final MinigameSettings settings;
    @Getter private final Location gameExitCoords;
    private BukkitTask timedQueue;
    private BukkitTask startGameDelay;
    @Getter private Instant queueStart;

    public MinigameLobbyGroup(int size, String minigameRegion, Location gameExitCoords, MinigameSettings settings) {
        this.lobbies = new ArrayList<>(size);
        this.minigameRegion = minigameRegion;
        this.settings = settings;
        this.gameExitCoords = gameExitCoords;
    }

    public void add(MinigameLobby lobby) {
        lobbies.add(lobby);
    }

    protected boolean startLobby(boolean delay, boolean failSilent, boolean forceStart) {
        //Game has already launched, players will have to wait. Restart the timed queue if needed.
        if(MinigameManager.isGameActive(minigameRegion)) {
            restartQueue();
            return false;
        }

        //Check that we have enough players
        int totalPlayers = 0;
        for(MinigameLobby lobby : lobbies) {
            if(!settings.isTeamRebalanced() && lobby.getPlayers().size() < settings.getMinTeamSize()) {return false;}
            totalPlayers += lobby.getPlayers().size();
        }

        if(totalPlayers < settings.getRequiredPlayers()) {
            if(!failSilent) {
                for(MinigameLobby lobby : lobbies) {
                    for(Player p : lobby.getPlayers()) {
                        p.sendMessage("Sorry, there are not enough players in the lobby to start a match at this time.");
                    }
                }
            }
            restartQueue();
            return false;
        }

        if(!forceStart && totalPlayers < settings.getMaxPlayers()) {return false;}//Don't let player events start the game prematurely

        //Start a thread to call this method again after the delay specified
        if(delay && settings.getLobbyDelaySeconds() > 0 && (startGameDelay == null || startGameDelay.isCancelled())) {
            startGameDelay = new BukkitRunnable() {
                private int counter = settings.getLobbyDelaySeconds();

                @Override
                public void run() {
                    if(counter == 0) {
                        startLobby(false, false, true);
                        cancel();
                    } else {
                        for(MinigameLobby lobby : lobbies) {
                            for(Player p : lobby.getPlayers()) {
                                p.sendMessage("Game starting in "+counter+"...");
                            }
                        }
                        counter--;
                    }
                }
            }.runTaskTimer(Main.getInstance(), 0, 20);
            return true;
        }

        //Start the game
        List<MinigameTeam> teams = new ArrayList<>(lobbies.size());
        ArrayList<Player> retainedPlayers = new ArrayList<>();
        //If we don't need to rebalance teams, drain each lobby to a team
        if(!settings.isTeamRebalanced()) {
            for (MinigameLobby lobby : lobbies) {
                //TODO utilize team names?
                //TODO unregister scoreboards?
                teams.add(new MinigameTeam(lobby.getLobbyID(), lobby.getPlayers(), settings));
                lobby.clearLobby();
            }
        } else {
            //Else ignore the lobbies and work with the list of players itself
            ArrayList<Player> allPlayers = new ArrayList<>();
            for(MinigameLobby lobby : lobbies) {
                allPlayers.addAll(lobby.getPlayers());
                lobby.clearLobby();
            }

            //Calculate the team size
            int amtTeams = settings.getMaxTeams();
            int teamSize = allPlayers.size() / amtTeams;
            while(teamSize < settings.getMinTeamSize()) {
                teamSize = allPlayers.size() / --amtTeams;
            }

            if(settings.isEqualTeamSizeForced()) {
                while(allPlayers.size() % teamSize != 0) {
                    retainedPlayers.add(allPlayers.remove(allPlayers.size()-1));
                }
            }

            //Clear our lobbies of the taken players
            for(MinigameLobby lobby : lobbies) {
                lobby.clearLobby(retainedPlayers);
            }

            //Sort our list of players dependent on the chosen method in settings
            ArrayList<Player> sortedPlayers;
            if(settings.isRankBalanced()) {
                sortedPlayers = new ArrayList<>(allPlayers.size());
                allPlayers.sort((o1, o2) -> (int) (PlayerManager.getHighScore(o1, settings.name()) - PlayerManager.getHighScore(o2, settings.name())));
                //TODO I have no idea how to do this without brute forcing. An odd number of teams and/or uneven distribution of skill
                //TODO makes this a very difficult problem to solve. This is a temporary, flawed algorithm instead.
                for(int i = 0; i < allPlayers.size(); i+=2) {
                    sortedPlayers.add(allPlayers.get(i));
                }
                int end = allPlayers.size() % 2 == 0 ? allPlayers.size()-1 : allPlayers.size()-2;
                for(int i = end; i >= 0; i-=2) {
                    sortedPlayers.add(allPlayers.get(i));
                }
            } else {
                Collections.shuffle(allPlayers);
                sortedPlayers = allPlayers;
            }

            //Create our teams with our new ordered lists
            for(int i = 0; i < amtTeams; i++) {
                List<Player> sublist = sortedPlayers.subList(teamSize*i, teamSize*(i+1));
                teams.add(new MinigameTeam(sublist.get(0).getName(), new ArrayList<>(sublist), settings));
            }
        }


        //Start the game
        MinigameManager.startGame(settings, teams, minigameRegion, gameExitCoords);

        //Restart the queue if there are still players remaining in the lobby
        if(!retainedPlayers.isEmpty()) {
            restartQueue();
        }
        return true;
    }

    /**
     * Delays startLobby() til after the the amount of time given in MinigameSettings
     */
    private void restartQueue() {
        int maxWaitTime = settings.getMaxWaitTime();
        if(maxWaitTime >= 0 && (timedQueue == null || timedQueue.isCancelled())) {
            timedQueue = new BukkitRunnable() {
                @Override
                public void run() {
                    if(startLobby(true, false, true)) {
                        cancel();
                    }
                }
            }.runTaskTimer(Main.getInstance(), 20 * maxWaitTime,maxWaitTime == 0 ? 1 : 20 * maxWaitTime);
            queueStart = Instant.now();
        }

    }

    public void notifyPlayerChange(boolean joined) {
        if(joined && playersWaiting() == 1) {
            restartQueue();
        } else if(!joined && playersWaiting() == 0) {
            if(timedQueue != null) timedQueue.cancel();
            if(startGameDelay != null) startGameDelay.cancel();
        }
    }

    private int playersWaiting() {
        int count = 0;
        for(MinigameLobby lobby : lobbies) {
            count += lobby.getPlayers().size();
        }
        return count;
    }
}
