package minigame.io;

import minigame.Main;
import minigame.game.MinigameSettings;
import minigame.io.output.record.*;
import minigame.player.PlayerData;
import minigame.util.GlobalSettings;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class SQLInputOutputStrategy implements InputOutputStrategy {
    private final Connection con;
    private final HashMap<String, Set<String>> knownColumns = new HashMap<>();

    public SQLInputOutputStrategy() throws SQLException {
        //Establish a connection
        con = DriverManager.getConnection(
                "jdbc:mysql://" + GlobalSettings.getDatabaseAddress() +
                "/" + GlobalSettings.getDatabaseName() +
                "?user=" + GlobalSettings.getDatabaseUsername() +
                "&password=" + GlobalSettings.getDatabasePassword() +
                "&allowMultiQueries=true");

        //Index current tables & columns for better fluidity
        try(PreparedStatement stm = con.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE TABLE_SCHEMA = '"+GlobalSettings.getDatabaseName()+"' "+
                            "AND TABLE_NAME LIKE '"+GlobalSettings.getDatabaseTablePrefix()+"%'")) {

            ResultSet rs = stm.executeQuery();
            while(rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName.startsWith(GlobalSettings.getDatabaseTablePrefix())) {
                    knownColumns.put(tableName, new HashSet<>());
                }
            }
        }

        for(String tableName : knownColumns.keySet()) {
            try(PreparedStatement stm = con.prepareStatement(
                "  SELECT COLUMN_NAME " +
                    "  FROM INFORMATION_SCHEMA.COLUMNS " +
                    "  WHERE TABLE_SCHEMA = '"+GlobalSettings.getDatabaseName()+"'"+
                        " AND TABLE_NAME = '"+tableName+"'")) {

                ResultSet rs = stm.executeQuery();
                while(rs.next()) {
                    knownColumns.get(tableName).add(rs.getString("COLUMN_NAME"));
                }
            }
        }

        //Create basic tables if they don't exist
        try(PreparedStatement stm = con.prepareStatement(
                "CREATE TABLE IF NOT EXISTS minigame_game " +
                        "(game_id INT AUTO_INCREMENT PRIMARY KEY, game_type VARCHAR(36) NOT NULL, " +
                        "game_winning_team VARCHAR(36), game_end TIMESTAMP DEFAULT NOW())")) {
            stm.executeUpdate();
        }

        try(PreparedStatement stm = con.prepareStatement(
                "CREATE TABLE IF NOT EXISTS minigame_player " +
                        "(player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16) NOT NULL, " +
                        "player_tokens INT, player_tickets INT, player_pass_expiration TIMESTAMP DEFAULT NOW())")) {
            stm.executeUpdate();
        }

        try(PreparedStatement stm = con.prepareStatement(
                "CREATE TABLE IF NOT EXISTS minigame_player_session " +
                        "(session_player_uuid VARCHAR(36), session_duration BIGINT, session_tokens_spent INT, " +
                        "session_tickets_spent INT, session_tokens_earned INT, session_tickets_earned INT, " +
                        "session_messages_sent INT, " +
                        "FOREIGN KEY (session_player_uuid) REFERENCES minigame_player (player_uuid) ON DELETE CASCADE)")) {
            stm.executeUpdate();
        }

        try(PreparedStatement stm = con.prepareStatement(
                "CREATE TABLE IF NOT EXISTS minigame_player_map " +
                        "(player_uuid VARCHAR(36), game_id INT, team_name VARCHAR(36) NOT NULL," +
                        "player_elo_change DOUBLE PRECISION, " +
                        "FOREIGN KEY (player_uuid) REFERENCES minigame_player (player_uuid) ON DELETE CASCADE, " +
                        "FOREIGN KEY (game_id) REFERENCES minigame_game (game_id) ON DELETE CASCADE)")) {
            stm.executeUpdate();
        }
    }

    private void prepareTable(List<Record> records, String gameType, String tableSuffix) throws SQLException {
        HashMap<String, Object> newColumns = new HashMap<>();
        String tableName = getTableName(gameType, tableSuffix);
        Set<String> oldTable = knownColumns.get(tableName);

        String columnName;
        for (Record record : records) {
            for (Map.Entry<String, Object> entry : record.getData().entrySet()) {
                columnName = getColumnName(tableSuffix, entry.getKey());
                if (oldTable == null || !oldTable.contains(columnName)) {
                    newColumns.put(columnName, entry.getValue());
                }
            }
        }

        if(newColumns.isEmpty()) {return;} //No new columns to add

        //Add the columns we're about to create into our "known columns" collection
        HashSet<String> columns = new HashSet<>(newColumns.keySet());
        knownColumns.put(tableName, columns);

        if(oldTable == null) {
            StringBuilder columnsSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append('(');
            if(tableSuffix.equals("round")) {
                    columnsSQL.append(
                        "round_id  INT AUTO_INCREMENT PRIMARY KEY, round_game_id INT, " +
                        "FOREIGN KEY (round_game_id) REFERENCES minigame_game (game_id) ON DELETE CASCADE,"
                    );
                    columns.add("round_id"); columns.add("round_game_id");
            } else if(tableSuffix.equals("round_player")) {
                columnsSQL.append(
                        "round_player_id  INT AUTO_INCREMENT PRIMARY KEY, round_player_round_id INT, " +
                        "FOREIGN KEY (round_player_round_id) REFERENCES minigame_"+gameType+"_round (round_id) ON DELETE CASCADE,"
                );
            }


            for(Map.Entry<String, Object> c : newColumns.entrySet()) {
                columnsSQL.append(c.getKey()).append(" ").append(getDataType(c.getValue())).append(",");
            }

            columnsSQL.setLength(columnsSQL.length()-1);
            columnsSQL.append(")");

            try (PreparedStatement stm = con.prepareStatement(columnsSQL.toString())) {
                stm.executeUpdate();
            }

        } else {
            StringBuilder sql = new StringBuilder("ALTER TABLE "+tableName+" ");
            for(Map.Entry<String, Object> column : newColumns.entrySet()) {
                sql.append("ADD COLUMN ").append(column.getKey()).append(" ").append(getDataType(column.getValue())).append(",");
            }
            sql.setLength(sql.length()-1);

            try (PreparedStatement stm = con.prepareStatement(sql.toString())) {
                stm.executeUpdate();
            }
            knownColumns.get(tableName).addAll(newColumns.keySet());
        }
    }

    /**
     * Saves all datapoints that were logged during a game. Should be called only once per game.
     * @param game The GameRecord that holds the save data
     * @throws SQLException :(
     */
    public void saveGame(@NotNull GameRecord game) throws SQLException {
        //Collect the records and prepare the tables (create tables & new columns)
        List<Record> roundRecords = game.getRoundRecords();
        List<Record> playerRecords = new ArrayList<>();
        Set<UUID> knownPlayers = new HashSet<UUID>();
        int[] roundRecordCount = {roundRecords.size()};
        int[] playerRecordCount = new int[roundRecords.size()];
        int idx = 0;
        for(Record round : roundRecords) {
            playerRecordCount[idx++] = ((RoundRecord) round).getPlayerRecords().size();
            for(Map.Entry<UUID, RoundPlayerRecord> playerEntry : ((RoundRecord) round).getPlayerRecords().entrySet()) {
                knownPlayers.add(playerEntry.getKey());
                if(!playerEntry.getValue().getData().isEmpty()) {
                    playerEntry.getValue().getData().put("player_uuid", playerEntry.getKey().toString());
                    playerRecords.add(playerEntry.getValue());
                }
            }
        }
        prepareTable(game.getRoundRecords(), game.getGameType(), "round");
        prepareTable(playerRecords, game.getGameType(), "round_player");

        //Log the game itself
        try(PreparedStatement stm = con.prepareStatement(
                "INSERT INTO minigame_game (game_type, game_winning_team) VALUES (?, ?)")) {
            stm.setString(1, game.getGameType());
            stm.setString(2, game.getWinningTeam());
            stm.executeUpdate();
        }

        //Return the game's unique ID to associate the rest of the data
        int gameID;
        try(PreparedStatement stm = con.prepareStatement("SELECT LAST_INSERT_ID()")) {
            ResultSet rs = stm.executeQuery();
            rs.next();
            gameID = rs.getInt(1);
        }

        //Map the Players to this new game entry
        StringBuilder sql = new StringBuilder("INSERT INTO minigame_player_map (player_uuid, game_id, team_name, player_elo_change) VALUES ");
        for(Map.Entry<UUID, PlayerRecord> p : game.getPlayerRecords().entrySet()) {
            sql.append("(").append("'").append(p.getKey().toString()).append("'").append(", ")
                            .append(gameID).append(", ")
                            .append("'").append(p.getValue().getStartingTeam()).append("'").append(",")
                            .append(p.getValue().getEloChange()).append("),");
        }
        sql.setLength(sql.length()-1);

        try (PreparedStatement stm = con.prepareStatement(sql.toString())) {
            stm.executeUpdate();
        }

        int[] roundIDs = fillVariableTable("round", game, "game_id", new int[] {gameID}, roundRecords, roundRecordCount);
        fillVariableTable("round_player", game, "round_id", roundIDs, playerRecords, playerRecordCount);
    }

    private int[] fillVariableTable(String tableSuffix, GameRecord game, String IDname, int[] IDs, List<Record> records, int[] recordCount) throws SQLException {
        if(records.isEmpty() || IDs == null) {return null;}
        String tableName = getTableName(game.getGameType(), tableSuffix);

        //Get column names
        String IDcolumn = tableSuffix + "_id";
        String fkColumn = tableSuffix + "_" + IDname;
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (").append(fkColumn).append(",");

        Set<String> columns = new HashSet<>(knownColumns.get(tableName));
        columns.remove(IDcolumn);
        columns.remove(fkColumn);
        sql.append(String.join(",", columns)).append(") VALUES (");

        //Add tokens for PreparedStatement to replace
        for(int i = 0; i < columns.size()+1; i++) {
            sql.append("?,");
        }
        sql.setLength(sql.length()-1);
        sql.append(")");

        int[] returnIDs = new int[records.size()];
        try(PreparedStatement stm = con.prepareStatement(sql.toString(), PreparedStatement.RETURN_GENERATED_KEYS)) {
            int idxCounter = 0;
            int counter = 0;
            int idx;
            for(Record record : records) {
                idx = 1;
                if(record.getData().isEmpty()) {continue;}
                while(counter == recordCount[idxCounter]) {
                    counter = 0;
                    idxCounter++;
                }

                stm.setInt(idx++, IDs[idxCounter]);
                for(String column : columns) {
                    stm.setObject(idx++, record.getData().get(getFieldName(tableSuffix, column)));
                }
                stm.addBatch();
                counter++;
            }

            stm.executeBatch();
            int i = 0;
            try(ResultSet rs = stm.getGeneratedKeys()) {
                while (rs.next()) {
                    returnIDs[i++] = rs.getInt(1);
                }
            }
        }
        return returnIDs;
    }

    private String getFieldName(String suffix, String column) {
        return column.substring(suffix.length()+1);
    }

    private String getColumnName(@NotNull String suffix, @NotNull String field) {
        return suffix + "_" + field;
    }

    private String getTableName(@NotNull String minigame, @Nullable String suffix) {
        String toReturn = GlobalSettings.getDatabaseTablePrefix() + "_" +
                minigame.replaceAll(" ", "_").replaceAll(",", "").toLowerCase();
        if(suffix != null) {toReturn += "_" + suffix;}
        return toReturn;
    }

    private String getDataType(Object data) {
        if(data instanceof Boolean) {
            return "BOOLEAN";
        } else if(data instanceof Number) {
            if(data instanceof Byte) {
                return "TINYINT";
            } else if(data instanceof Short) {
                return "SMALLINT";
            } else if(data instanceof Integer) {
                return "INT";
            } else if(data instanceof Long) {
                return "BIGINT";
            } else {
                return "DOUBLE PRECISION";
            }
        }
        return "VARCHAR(36)";
    }

    public void savePlayer(Player p, PlayerData pd) {
        String query = "UNKNOWN QUERY";
        try(PreparedStatement stm = con.prepareStatement("INSERT INTO minigame_player " +
                "(player_uuid, player_name, player_tokens, player_tickets, player_pass_expiration) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "player_name=VALUES(player_name), player_tokens=VALUES(player_tokens), " +
                "player_tickets=VALUES(player_tickets), player_pass_expiration=VALUES(player_pass_expiration)")) {
            stm.setString(1, p.getUniqueId().toString());
            stm.setString(2, p.getName());
            stm.setInt(3, pd.getTokens());
            stm.setInt(4, pd.getTickets());
            Instant expiry = pd.getGamePassFinish() == null ? Instant.now() : pd.getGamePassFinish();
            stm.setTimestamp(5, new Timestamp(expiry.toEpochMilli()));
            query = stm.toString();
            stm.executeUpdate();
        } catch(Exception e) {
            notifyError(e, "CRITICAL: Failed to save player: "+query+" -> ");
        }

        try(PreparedStatement stm = con.prepareStatement("INSERT INTO minigame_player_session " +
                "(session_player_uuid, session_duration, session_tokens_spent, session_tickets_earned, " +
                "session_tickets_spent, session_tokens_earned, session_messages_sent) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            stm.setString(1, p.getUniqueId().toString());
            stm.setLong(2, Duration.between(pd.getJoinInstant(), Instant.now()).getSeconds());
            stm.setInt(3, pd.getTokensSpent());
            stm.setInt(4, pd.getTicketsEarned());
            stm.setInt(5, pd.getTicketsSpent());
            stm.setInt(6, pd.getTokensEarned());
            stm.setInt(7, pd.getMessagesSent());
            stm.executeUpdate();
        } catch(Exception e) {
            notifyError(e, "CRITICAL: Failed to save player session: "+query+" -> ");
        }
    }

    public int loadPlayer(Player p, PlayerData pd) {
        try(PreparedStatement stm = con.prepareStatement("SELECT *," +
                "(SELECT COUNT(session_player_uuid) FROM minigame_player_session WHERE session_player_uuid = player_uuid) AS player_logins" +
                " FROM minigame_player WHERE player_UUID = ?")) {
            stm.setString(1, p.getUniqueId().toString());
            ResultSet rs = stm.executeQuery();
            if(rs.next()) {
                pd.setTickets(rs.getInt("player_tickets"));
                pd.setTokens(rs.getInt("player_tokens"));
                pd.setGamePassFinish(rs.getTimestamp("player_pass_expiration").toInstant());

                try(PreparedStatement elo = con.prepareStatement(
                    "SELECT game_type, " +
                            "SUM((SELECT COUNT(*)" +
                            "    FROM minigame_player_map" +
                            "    WHERE minigame_player_map.game_id = minigame_game.game_id" +
                            "    AND minigame_player_map.player_uuid = ?" +
                            "    AND game_winning_team = team_name)) AS game_wins, " +
                            "SUM((SELECT SUM(player_elo_change) " +
                            "    FROM minigame_player_map " +
                            "    WHERE minigame_player_map.game_id = minigame_game.game_id " +
                            "    AND minigame_player_map.player_uuid = ?)) AS elo_change " +
                            "FROM minigame_game " +
                            "WHERE TRUE " +
                            "GROUP BY game_type ")) {
                    elo.setString(1, p.getUniqueId().toString());
                    elo.setString(2, p.getUniqueId().toString());
                    ResultSet eloSets = elo.executeQuery();
                    String gameType; MinigameSettings settings;
                    while(eloSets.next()) {
                        gameType = eloSets.getString("game_type");
                        settings = MinigameSettings.valueOf(gameType);
                        if(settings.getPrimaryScore() == null || settings.getPrimaryScore().equalsIgnoreCase("elo")) {
                            System.out.println(p.getName() + "'s elo in " + eloSets.getString("game_type") + " is " + eloSets.getDouble("elo_change"));
                            pd.addElo(gameType, eloSets.getDouble("elo_change"));
                        } else if(settings.getPrimaryScore().equalsIgnoreCase("wins")) {
                            pd.addElo(gameType, eloSets.getInt("game_wins"));
                        }
                    }
                }

                StringBuilder highScoreSQL = new StringBuilder();
                String name, primaryScore;
                for(MinigameSettings game : MinigameSettings.values()) {
                    primaryScore = game.getPrimaryScore();
                    name = game.name().toLowerCase();
                    if(primaryScore == null || primaryScore.equalsIgnoreCase("elo") || primaryScore.equalsIgnoreCase("wins") ||
                            !knownColumns.containsKey("minigame_"+name+"_round") || !knownColumns.containsKey("minigame_"+name+"_round_player")) {continue;}
                    String collFunc = game.isPrimaryScoreAggregate() ? "SUM" : "MAX";
                    highScoreSQL.append(
                        "SELECT '").append(game.name()).append("'").append(" AS game_type, ").append(collFunc).append(
                                "((SELECT SUM(round_player_").append(primaryScore).append(") FROM minigame_").append(name).append("_round " +
                        "    LEFT JOIN minigame_").append(name).append("_round_player " +
                        "       ON minigame_").append(name).append("_round_player.round_player_round_id = minigame_").append(name).append("_round.round_id  " +
                        "    WHERE minigame_").append(name).append("_round.round_game_id = minigame_game.game_id " +
                        "       AND `minigame_").append(name).append("_round_player`.round_player_player_uuid = '").append(p.getUniqueId()).append("')) AS high_score " +
                        "FROM minigame_game;"
                    );
                }

                try(PreparedStatement hiScore = con.prepareStatement(highScoreSQL.toString())) {
                    ResultSet scores = hiScore.executeQuery();
                    while(scores.next()) {
                        System.out.println(p.getName()+"'s highscore in "+scores.getString("game_type")+" is "+scores.getDouble("high_score"));
                        pd.addElo(scores.getString("game_type"), scores.getDouble("high_score"));
                    }
                }
                return rs.getInt("player_logins");
            }
            try(PreparedStatement insert = con.prepareStatement("INSERT INTO minigame_player (player_uuid, player_name) VALUES (?, ?)")) {
                insert.setString(1, p.getUniqueId().toString());
                insert.setString(2, p.getName());
                insert.executeUpdate();
                return 0;
            }
        } catch(Exception e) {
            notifyError(e, "CRITICAL: Failed to load player: ");
            return -1;
        }
    }

    private void notifyError(Exception e, String errorText) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        Main.getInstance().getLogger().severe(errorText+sw);
    }
}