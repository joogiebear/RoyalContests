package com.jacobscontest.storage;

import com.jacobscontest.JacobsContest;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite-based local storage implementation.
 */
public class SQLiteStorage implements StorageProvider {

    private final JacobsContest plugin;
    private Connection connection;

    public SQLiteStorage(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            // Create tables
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid TEXT NOT NULL,
                        crop TEXT NOT NULL,
                        personal_best INTEGER DEFAULT 0,
                        total_harvested INTEGER DEFAULT 0,
                        contests_participated INTEGER DEFAULT 0,
                        best_bracket TEXT DEFAULT 'NONE',
                        PRIMARY KEY (uuid, crop)
                    )
                """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_rewards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        event_name TEXT NOT NULL,
                        crop TEXT NOT NULL,
                        score INTEGER DEFAULT 0,
                        personal_best INTEGER DEFAULT 0,
                        bracket TEXT NOT NULL,
                        bracket_display TEXT NOT NULL,
                        position INTEGER DEFAULT 0,
                        total_participants INTEGER DEFAULT 0,
                        commands TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        claimed INTEGER DEFAULT 0
                    )
                """);

                stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_rewards_uuid
                    ON pending_rewards(uuid, claimed)
                """);
            }

            plugin.getLogger().info("SQLite storage initialized: " + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite storage!", e);
        }
    }

    @Override
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection", e);
        }
    }

    @Override
    public PlayerStats getPlayerStats(UUID uuid, String crop) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_stats WHERE uuid = ? AND crop = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, crop);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerStats(
                        uuid, crop,
                        rs.getInt("personal_best"),
                        rs.getLong("total_harvested"),
                        rs.getInt("contests_participated"),
                        rs.getString("best_bracket")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading player stats", e);
        }
        return new PlayerStats(uuid, crop);
    }

    @Override
    public List<PlayerStats> getAllPlayerStats(UUID uuid) {
        List<PlayerStats> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new PlayerStats(
                        uuid,
                        rs.getString("crop"),
                        rs.getInt("personal_best"),
                        rs.getLong("total_harvested"),
                        rs.getInt("contests_participated"),
                        rs.getString("best_bracket")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading all player stats", e);
        }
        return list;
    }

    @Override
    public void savePlayerStats(PlayerStats stats) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_stats (uuid, crop, personal_best, total_harvested, contests_participated, best_bracket)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, crop) DO UPDATE SET
                    personal_best = excluded.personal_best,
                    total_harvested = excluded.total_harvested,
                    contests_participated = excluded.contests_participated,
                    best_bracket = excluded.best_bracket
            """)) {
            ps.setString(1, stats.getPlayerId().toString());
            ps.setString(2, stats.getCrop());
            ps.setInt(3, stats.getPersonalBest());
            ps.setLong(4, stats.getTotalHarvested());
            ps.setInt(5, stats.getContestsParticipated());
            ps.setString(6, stats.getBestBracket());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error saving player stats", e);
        }
    }

    @Override
    public void savePendingReward(PendingReward reward) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO pending_rewards
                (uuid, player_name, event_name, crop, score, personal_best, bracket, bracket_display,
                 position, total_participants, commands, timestamp, claimed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reward.getPlayerId().toString());
            ps.setString(2, reward.getPlayerName());
            ps.setString(3, reward.getEventName());
            ps.setString(4, reward.getCrop());
            ps.setInt(5, reward.getScore());
            ps.setInt(6, reward.getPersonalBest());
            ps.setString(7, reward.getBracket());
            ps.setString(8, reward.getBracketDisplay());
            ps.setInt(9, reward.getPosition());
            ps.setInt(10, reward.getTotalParticipants());
            ps.setString(11, String.join("||", reward.getCommands()));
            ps.setLong(12, reward.getTimestamp());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                reward.setId(keys.getInt(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error saving pending reward", e);
        }
    }

    @Override
    public List<PendingReward> getPendingRewards(UUID uuid) {
        List<PendingReward> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM pending_rewards WHERE uuid = ? AND claimed = 0 ORDER BY timestamp DESC")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                List<String> commands = Arrays.asList(rs.getString("commands").split("\\|\\|"));
                list.add(new PendingReward(
                        rs.getInt("id"),
                        uuid,
                        rs.getString("player_name"),
                        rs.getString("event_name"),
                        rs.getString("crop"),
                        rs.getInt("score"),
                        rs.getInt("personal_best"),
                        rs.getString("bracket"),
                        rs.getString("bracket_display"),
                        rs.getInt("position"),
                        rs.getInt("total_participants"),
                        commands,
                        rs.getLong("timestamp"),
                        rs.getBoolean("claimed")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading pending rewards", e);
        }
        return list;
    }

    @Override
    public void markRewardClaimed(int rewardId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pending_rewards SET claimed = 1 WHERE id = ?")) {
            ps.setInt(1, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error marking reward claimed", e);
        }
    }

    @Override
    public void deleteReward(int rewardId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM pending_rewards WHERE id = ?")) {
            ps.setInt(1, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error deleting reward", e);
        }
    }
}
