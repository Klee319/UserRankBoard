package com.codezhangborui.pixelRank.database;

import com.codezhangborui.pixelRank.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database {
    public static HashMap<String, Long> mining_rank = new HashMap<>();
    public static HashMap<String, Long> placing_rank = new HashMap<>();
    public static HashMap<String, Long> online_time_rank = new HashMap<>();
    public static HashMap<String, Long> death_rank = new HashMap<>();
    public static HashMap<String, Long> movement_rank = new HashMap<>();
    public static HashMap<String, Long> mob_kill_rank = new HashMap<>();
    private static Connection connection;
    private static String DATABASE_URL;
    private static JavaPlugin plugin;
    private static Logger logger;

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getLogger();
        DATABASE_URL = "jdbc:sqlite:" + plugin.getDataFolder() + "/" + Configuration.getString("storage.database");
    }

    public static boolean load() {
        try {
            connection = DriverManager.getConnection(DATABASE_URL);
            if (connection != null) {
                // Create three tables if not exist
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS mining_rank (player TEXT PRIMARY KEY, value INTEGER)");
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS placing_rank (player TEXT PRIMARY KEY, value INTEGER)");
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS online_time_rank (player TEXT PRIMARY KEY, value INTEGER)");
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS death_rank (player TEXT PRIMARY KEY, value INTEGER)");
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS movement_rank (player TEXT PRIMARY KEY, value INTEGER)");
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS mob_kill_rank (player TEXT PRIMARY KEY, value INTEGER)");
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS scoreboard_settings (uuid TEXT PRIMARY KEY, enabled INTEGER NOT NULL)");
                // Load data from the database
                var miningRankResultSet = connection.createStatement().executeQuery("SELECT * FROM mining_rank");
                while (miningRankResultSet.next()) {
                    mining_rank.put(miningRankResultSet.getString("player"), miningRankResultSet.getLong("value"));
                }
                var placingRankResultSet = connection.createStatement().executeQuery("SELECT * FROM placing_rank");
                while (placingRankResultSet.next()) {
                    placing_rank.put(placingRankResultSet.getString("player"), placingRankResultSet.getLong("value"));
                }
                var onlineTimeRankResultSet = connection.createStatement().executeQuery("SELECT * FROM online_time_rank");
                while (onlineTimeRankResultSet.next()) {
                    online_time_rank.put(onlineTimeRankResultSet.getString("player"), onlineTimeRankResultSet.getLong("value"));
                }
                var deathRankResultSet = connection.createStatement().executeQuery("SELECT * FROM death_rank");
                while (deathRankResultSet.next()) {
                    death_rank.put(deathRankResultSet.getString("player"), deathRankResultSet.getLong("value"));
                }
                var movementRankResultSet = connection.createStatement().executeQuery("SELECT * FROM movement_rank");
                while (movementRankResultSet.next()) {
                    movement_rank.put(movementRankResultSet.getString("player"), movementRankResultSet.getLong("value"));
                }
                var mobKillRankResultSet = connection.createStatement().executeQuery("SELECT * FROM mob_kill_rank");
                while (mobKillRankResultSet.next()) {
                    mob_kill_rank.put(mobKillRankResultSet.getString("player"), mobKillRankResultSet.getLong("value"));
                }
                // Close the connection
                connection.close();
                return true;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not connect to the SQLite database.", e);
        }
        return false;
    }

    private static void saveTable(Connection conn, String tableName, HashMap<String, Long> data) throws SQLException {
        conn.createStatement().execute("DELETE FROM " + tableName);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tableName + " (player, value) VALUES (?, ?)");
        for (var entry : data.entrySet()) {
            ps.setString(1, entry.getKey());
            ps.setLong(2, entry.getValue());
            ps.addBatch();
        }
        ps.executeBatch();
        ps.close();
    }

    /**
     * Load scoreboard enabled setting for a player from DB.
     * @return null if no saved setting exists, otherwise the saved value.
     */
    public static Boolean loadScoreboardSetting(UUID uuid) {
        try {
            Connection conn = DriverManager.getConnection(DATABASE_URL);
            PreparedStatement ps = conn.prepareStatement("SELECT enabled FROM scoreboard_settings WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            Boolean result = null;
            if (rs.next()) {
                result = rs.getInt("enabled") == 1;
            }
            rs.close();
            ps.close();
            conn.close();
            return result;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load scoreboard setting for " + uuid, e);
            return null;
        }
    }

    /**
     * Save scoreboard enabled setting for a player to DB.
     */
    public static void saveScoreboardSetting(UUID uuid, boolean enabled) {
        new Thread(() -> {
            try {
                Connection conn = DriverManager.getConnection(DATABASE_URL);
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO scoreboard_settings (uuid, enabled) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET enabled = ?");
                ps.setString(1, uuid.toString());
                ps.setInt(2, enabled ? 1 : 0);
                ps.setInt(3, enabled ? 1 : 0);
                ps.executeUpdate();
                ps.close();
                conn.close();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not save scoreboard setting for " + uuid, e);
            }
        }).start();
    }

    public static boolean save() {
        // Snapshot the data to avoid ConcurrentModificationException on the async thread
        HashMap<String, Long> miningSnapshot = new HashMap<>(mining_rank);
        HashMap<String, Long> placingSnapshot = new HashMap<>(placing_rank);
        HashMap<String, Long> onlineTimeSnapshot = new HashMap<>(online_time_rank);
        HashMap<String, Long> deathSnapshot = new HashMap<>(death_rank);
        HashMap<String, Long> movementSnapshot = new HashMap<>(movement_rank);
        HashMap<String, Long> mobKillSnapshot = new HashMap<>(mob_kill_rank);

        new Thread(() -> {
            try {
                connection = DriverManager.getConnection(DATABASE_URL);
                if (connection != null) {
                    saveTable(connection, "mining_rank", miningSnapshot);
                    saveTable(connection, "placing_rank", placingSnapshot);
                    saveTable(connection, "online_time_rank", onlineTimeSnapshot);
                    saveTable(connection, "death_rank", deathSnapshot);
                    saveTable(connection, "movement_rank", movementSnapshot);
                    saveTable(connection, "mob_kill_rank", mobKillSnapshot);
                    connection.close();
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not connect to the SQLite database!", e);
            }
        }).start();
        return true;
    }
}