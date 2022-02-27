package minigame.io.output.record;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import minigame.Main;
import minigame.game.Minigame;
import minigame.io.Record;
import minigame.util.MinigameTeam;
import minigame.io.InputOutputManager;
import minigame.player.PlayerManager;
import minigame.util.GlobalSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;

public class GameRecord {
    private final String NOT_APPLICABLE = "n/a";
    @Getter private final String gameType;
    @Getter private final List<Record> roundRecords = new ArrayList<>();
    @Getter private final HashMap<UUID, PlayerRecord> playerRecords = new HashMap<>();
    private final List<MinigameTeam> teams;//MinigameTeams are mutable
    private RoundRecord currentRound;
    @Getter private String winningTeam = NOT_APPLICABLE;
    private boolean hasSaved = false;
    private final String primaryScore;
    private final boolean aggregatePrimaryScore;
    @Setter private List<MinigameTeam> teamRanks;

    public GameRecord(@NotNull Minigame m, @NotNull List<MinigameTeam> teams) {
        this.gameType = m.getName();
        this.primaryScore = m.getSettings().getPrimaryScore();
        this.aggregatePrimaryScore = m.getSettings().isPrimaryScoreAggregate();
        this.teams = teams;
        this.currentRound = new RoundRecord();
        for(MinigameTeam team : teams) {
            for(Player p : team.getPlayers()) {
                this.playerRecords.put(p.getUniqueId(), new PlayerRecord(team.getName()));
            }
        }
    }

    //Save and close this GameRecord
    public void save() {
        try {
            if(hasSaved) {throw new IllegalStateException();}
            currentRound.endRound();
            roundRecords.add(currentRound);

            if(primaryScore != null && !primaryScore.equals("elo")) {//Update player stats in-game (high score)
                for(Record round : roundRecords) {
                    for(Map.Entry<UUID, RoundPlayerRecord> record : ((RoundRecord)round).getPlayerRecords().entrySet()) {
                        Object wrapper = record.getValue().getData().get(primaryScore);
                        UUID p = record.getKey();
                        if(wrapper == null) {continue;}
                        double score = ((Number) wrapper).doubleValue();
                        if(!aggregatePrimaryScore && PlayerManager.getHighScore(p, gameType) < score) {
                            PlayerManager.setHighScore(p, gameType, score);
                        } else if(aggregatePrimaryScore) {
                            PlayerManager.changeHighScore(p, gameType, score);
                        }
                    }
                }
            } else if(winningTeam != null) {//Update player stats in-game (traditional elo
                if(teamRanks == null) {//Normal elo calculation - 1 winner, the rest are losers
                    setElo(calculateElo(teams, winningTeam), null, 1);
                } else {//Ranked elo calculations - top half wins, bottom half loses
                    for(int i = 0; i < teams.size()/2; i++) {
                        setElo(calculateElo(teamRanks.subList(i, teamRanks.size()), teamRanks.get(i).getName()),
                                teamRanks.get(i).getName(), (double)teams.size()/2/(i+1));
                    }
                    int loserIdx = teams.size() / 2 + (teams.size() % 2 == 0 ? 0 : 1);//Middle guy wins/loses nothing
                    for(int i = loserIdx; i < teams.size(); i++) {
                        setElo(calculateElo(teamRanks.subList(0, i+1), teamRanks.get(i).getName()),
                                teamRanks.get(i).getName(), (double)-teams.size()/2/(teams.size()-i));
                    }
                }
            }

            InputOutputManager.getOutput().saveGame(this);
            hasSaved = true;
        } catch(Exception e) {
            notifyError(e, "failed to save");
        }
    }

    public void setGameWinner(MinigameTeam winner) {
        try {
            if(!winningTeam.equals(NOT_APPLICABLE)) {throw new IllegalStateException();}
            winningTeam = winner.getName();
        } catch(IllegalStateException e) {
            notifyError(e, "game winner set twice");
        }
    }

    private double[] calculateElo(List<MinigameTeam> winnerLosers, String teamName) {
        double loseTotal = 0;
        double winTotal = 0;
        int losePop = 0;
        int winPop = 0;

        for(MinigameTeam team : winnerLosers) {
            if(!team.getName().equals(teamName)) {
                loseTotal += team.getAverageElo() * team.getOriginalSize();
                losePop += team.getOriginalSize();
            } else {
                winTotal += team.getAverageElo() * team.getOriginalSize();
                winPop += team.getOriginalSize();
            }
        }

        if(winPop == 0 || losePop == 0) {return null;}//Single-player Game or no set winner/loser

        double avgWinnerElo = winTotal / winPop;
        double avgLoserElo = loseTotal / losePop;

        double winProbability =  (1.0 / (1.0 + Math.pow(10, ((avgLoserElo - avgWinnerElo) / 400))));
        double change = GlobalSettings.getEloConstant() * winProbability;

        double[] eloChange = new double[winnerLosers.size()];
        int idx = 0;
        for(Map.Entry<UUID, PlayerRecord> p : playerRecords.entrySet()) {
            eloChange[idx++] = p.getValue().getCurrentTeam().equals(winningTeam)? Math.abs(change) : -Math.abs(change);
        }
        return eloChange;
    }

    private void setElo(double[] eloChange, @Nullable String onlySet, double multiplier) {
        if(eloChange == null) {return;}
        int idx = 0;
        for(Map.Entry<UUID, PlayerRecord> p : playerRecords.entrySet()) {
            //If one team is specified to be the only one set (and this is it), change it.
            //Or if no team is specified in such a way, change them all. If neither, skip.
            if(onlySet == null || p.getValue().getCurrentTeam().equals(winningTeam)) {
                p.getValue().setEloChange(eloChange[idx] * multiplier);
            } else {idx++; continue;}

            try {
                PlayerManager.changeHighScore(
                        Objects.requireNonNull(Bukkit.getPlayer(p.getKey())), gameType, eloChange[idx]
                );
            } catch(NullPointerException ignored) {}
            idx++;
        }
    }

    //Log the winner of the Game OR Round
    public void saveRoundWin(@NonNull MinigameTeam winner) {
        currentRound.teamWon(winner.getName());
        nextRound();
    }
    /*
        Overwrite events (only the last (or only) value matters)
     */
    //Timed Player Event
    public void set(Player p, String event, Duration duration) {
        getRoundPlayerRecord(p).overwrite(event, duration);
    }

    //Player event
    public void set(Player p, String event, String result) {
        getRoundPlayerRecord(p).overwrite(event, result);
    }

    //Player event
    public void set(Player p, String event, Number result) {
        getRoundPlayerRecord(p).overwrite(event, result);
    }

    //General Minigame Event
    public void set(String event, String result) {
        currentRound.overwrite(event, result);
    }

    //Boolean boii
    public void set(String event, boolean result) {
        currentRound.overwrite(event, result);
    }

    /*
        Count events (the number of occurrences matters)
     */
    //Player-on-Player Event
    public void add(Player p, String event, Player otherPlayer) {
        getRoundPlayerRecord(p).add(event, otherPlayer.getUniqueId().toString());
    }

    //Timed Player Event
    public void increment(Player p, String event, Duration toAdd) {
        getRoundPlayerRecord(p).add(event, toAdd);
    }

    //Timed General Minigame Event
    public void increment(String event, Duration toAdd) {
        currentRound.add(event, toAdd);
    }

    //Player event
    public void increment(Player p, String event, Number toAdd) {
        getRoundPlayerRecord(p).add(event, toAdd);
    }

    //General Minigame Event
    public void increment(String event, Number toAdd) {
        currentRound.add(event, toAdd);
    }

    public void increment(String event, String toAdd) {
        currentRound.add(event, toAdd);
    }

    public void decrement(String event, Duration toSubtract) {
        currentRound.add(event, toSubtract);
    }

    private RoundPlayerRecord getRoundPlayerRecord(@NotNull Player p) {
        currentRound.getPlayerRecords().computeIfAbsent(p.getUniqueId(), (k) -> new RoundPlayerRecord(p.getName(), k, getTeam(p)));
        return currentRound.getPlayerRecords().get(p.getUniqueId());
    }

    public void nextRound() {
        currentRound.endRound();
        roundRecords.add(currentRound);
        currentRound = new RoundRecord();
    }

    public void changeTeam(Player p, String teamName) {
        getRoundPlayerRecord(p).overwrite("team_swap", teamName);
        playerRecords.get(p.getUniqueId()).setCurrentTeam(teamName);
    }

    private String getTeam(Player p) {
        for(MinigameTeam team : teams) {
            if(team.getPlayers().contains(p)) {
                return team.getName();
            }
        }
        return NOT_APPLICABLE;
    }

    public int getRoundIndex() {
        return roundRecords.size();
    }

    public void notifyError(@NotNull Exception e, String errorText) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        Main.getInstance().getLogger().severe("Improper use of GameRecord logging in Minigame "+gameType+" ("+errorText+": "+sw);
    }
}
