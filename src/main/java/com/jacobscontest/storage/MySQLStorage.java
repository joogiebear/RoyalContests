package com.jacobscontest.storage;

import com.jacobscontest.JacobsContest;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MySQL-based storage implementation using HikariCP connection pooling.
 * Suitable for multi-server setups sharing a single database.
 */
public class MySQLStorage implements StorageProvider {

    private final JacobsContest plugin;
    private HikariDataSource dataSource;

    public MySQLStorage(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "jacobscontest");
            String username = plugin.getConfig().getString("storage.mysql.username", "root");
            String password = plugin.getConfig().getString("storage.mysql.password", "");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000);       // 5 minutes
            config.setConnectionTimeout(10000);  // 10 seconds
            config.setMaxLifetime(1800000);      // 30 minutes
            config.setPoolName("JacobsContest-Pool");

            dataSource = new HikariDataSource(config);

            // Create tables
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid VARCHAR(36) NOT NULL,
                        crop VARCHAR(64) NOT NULL,
                        personal_best INT DEFAULT 0,
                        total_harvested BIGINT DEFAULT 0,
                        contests_participated INT DEFAULT 0,
                        best_bracket VARCHAR(32) DEFAULT 'NONE',
                        PRIMARY KEY (uuid, crop)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pending_rewards (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL,
                        event_name VARCHAR(128) NOT NULL,
                        crop VARCHAR(64) NOT NULL,
                        score INT DEFAULT 0,
                        personal_best INT DEFAULT 0,
                        bracket VARCHAR(32) NOT NULL,
                        bracket_display VARCHAR(128) NOT NULL,
                        position INT DEFAULT 0,
                        total_participants INT DEFAULT 0,
                        commands TEXT NOT NULL,
                        timestamp BIGINT NOT NULL,
                        claimed TINYINT DEFAULT 0,
                        INDEX idx_rewards_uuid (uuid, claimed)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            }

            plugin.getLogger().info("MySQL storage initialized (pool: " + host + ":" + port + "/" + database + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL storage!", e);
            plugin.getLogger().severe("Falling back to SQLite is not automatic — check your MySQL config!");
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public PlayerStats getPlayerStats(UUID uuid, String crop) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
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
            plugin.getLogger().log(Level.WARNING, "Error loading player stats from MySQL", e);
        }
        return new PlayerStats(uuid, crop);
    }

    @Override
    public List<PlayerStats> getAllPlayerStats(UUID uuid) {
        List<PlayerStats> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
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
            plugin.getLogger().log(Level.WARNING, "Error loading all player stats from MySQL", e);
        }
        return list;
    }

    @Override
    public void savePlayerStats(PlayerStats stats) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO player_stats (uuid, crop, personal_best, total_harvested, contests_participated, best_bracket)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    personal_best = VALUES(personal_best),
                    total_harvested = VALUES(total_harvested),
                    contests_participated = VALUES(contests_participated),
                    best_bracket = VALUES(best_bracket)
            """)) {
            ps.setString(1, stats.getPlayerId().toString());
            ps.setString(2, stats.getCrop());
            ps.setInt(3, stats.getPersonalBest());
            ps.setLong(4, stats.getTotalHarvested());
            ps.setInt(5, stats.getContestsParticipated());
            ps.setString(6, stats.getBestBracket());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error saving player stats to MySQL", e);
        }
    }

    @Override
    public void savePendingReward(PendingReward reward) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("""
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
            plugin.getLogger().log(Level.WARNING, "Error saving pending reward to MySQL", e);
        }
    }

    @Override
    public List<PendingReward> getPendingRewards(UUID uuid) {
        List<PendingReward> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
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
            plugin.getLogger().log(Level.WARNING, "Error loading pending rewards from MySQL", e);
        }
        return list;
    }

    @Override
    public void markRewardClaimed(int rewardId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE pending_rewards SET claimed = 1 WHERE id = ?")) {
            ps.setInt(1, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error marking reward claimed in MySQL", e);
        }
    }

    @Override
    public void deleteReward(int rewardId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM pending_rewards WHERE id = ?")) {
            ps.setInt(1, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error deleting reward from MySQL", e);
        }
    }
}
