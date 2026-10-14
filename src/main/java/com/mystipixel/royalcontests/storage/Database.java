package com.mystipixel.royalcontests.storage;

import com.mystipixel.royalcontests.storage.Records.Leader;
import com.mystipixel.royalcontests.storage.Records.PendingReward;
import com.mystipixel.royalcontests.storage.Records.PlayerStats;
import com.mystipixel.royalcontests.storage.Records.Result;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite or MySQL, through one connection pool and one worker thread, never on the server thread.
 *
 * <p>One worker because SQLite allows one writer anyway, and a single queue means a claim can never
 * overtake the write that created its reward. {@code player_stats} and {@code pending_rewards} match
 * JacobsContest's tables, so an existing database carries over.
 */
public final class Database {

    private final Plugin plugin;
    private final Logger logger;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RoyalContests-Database");
        thread.setDaemon(true);
        return thread;
    });
    private HikariDataSource pool;
    private boolean mysql;

    public Database(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Connect and create the tables. Runs on the calling thread: the plugin cannot work without it. */
    public void open(ConfigurationSection config) throws SQLException {
        String type = config == null ? "sqlite" : config.getString("type", "sqlite");
        this.mysql = "mysql".equalsIgnoreCase(type) || "mariadb".equalsIgnoreCase(type);
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("RoyalContests");
        if (mysql) {
            ConfigurationSection m = config.getConfigurationSection("mysql");
            String host = m == null ? "localhost" : m.getString("host", "localhost");
            int port = m == null ? 3306 : m.getInt("port", 3306);
            String database = m == null ? "royalcontests" : m.getString("database", "royalcontests");
            String properties = m == null ? "" : m.getString("properties", "useSSL=false&allowPublicKeyRetrieval=true");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + (properties == null || properties.isBlank() ? "" : "?" + properties));
            hikari.setUsername(m == null ? "root" : m.getString("username", "root"));
            hikari.setPassword(m == null ? "" : m.getString("password", ""));
            hikari.setMaximumPoolSize(Math.max(2, m == null ? 4 : m.getInt("pool-size", 4)));
        } else {
            File file = new File(plugin.getDataFolder(), config == null ? "data.db" : config.getString("sqlite-file", "data.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
            // driver properties reach every connection; FULL so a claimed reward stays claimed after a power loss
            Properties sqlite = new Properties();
            sqlite.setProperty("journal_mode", "WAL");
            sqlite.setProperty("synchronous", "FULL");
            sqlite.setProperty("busy_timeout", "5000");
            hikari.setDataSourceProperties(sqlite);
        }
        hikari.setConnectionTimeout(10_000);
        this.pool = new HikariDataSource(hikari);
        createTables();
        logger.info("Storage: " + (mysql ? "MySQL" : "SQLite") + ".");
    }

    private void createTables() throws SQLException {
        String autoId = mysql ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String text = mysql ? "VARCHAR(128)" : "TEXT";
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_stats ("
                    + "uuid VARCHAR(36) NOT NULL, crop VARCHAR(64) NOT NULL, "
                    + "personal_best BIGINT DEFAULT 0, total_harvested BIGINT DEFAULT 0, "
                    + "contests_participated INT DEFAULT 0, best_bracket VARCHAR(32) DEFAULT 'NONE', "
                    + "PRIMARY KEY (uuid, crop))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS pending_rewards ("
                    + "id " + autoId + ", uuid VARCHAR(36) NOT NULL, player_name VARCHAR(64) NOT NULL, "
                    + "event_name " + text + " NOT NULL, crop VARCHAR(64) NOT NULL, score BIGINT DEFAULT 0, "
                    + "personal_best BIGINT DEFAULT 0, bracket VARCHAR(32) NOT NULL, "
                    + "bracket_display " + text + " NOT NULL, position INT DEFAULT 0, "
                    + "total_participants INT DEFAULT 0, commands TEXT NOT NULL, timestamp BIGINT NOT NULL, "
                    + "claimed INT DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS contest_results ("
                    + "id " + autoId + ", contest_id VARCHAR(96) NOT NULL, event VARCHAR(64) NOT NULL, "
                    + "crop VARCHAR(64) NOT NULL, uuid VARCHAR(36) NOT NULL, player_name VARCHAR(64) NOT NULL, "
                    + "score BIGINT NOT NULL, position INT NOT NULL, participants INT NOT NULL, "
                    + "bracket VARCHAR(32), finished_at BIGINT NOT NULL)");
            // leaderboards sort player_stats by crop and best, and look up each leader's latest name by uuid
            if (!mysql) {
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rewards_uuid ON pending_rewards(uuid, claimed)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_results_uuid_time ON contest_results(uuid, finished_at)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_crop_best ON player_stats(crop, personal_best)");
                s.executeUpdate("DROP INDEX IF EXISTS idx_results_uuid");
            } else {
                createIndexQuietly(s, "CREATE INDEX idx_rewards_uuid ON pending_rewards(uuid, claimed)");
                createIndexQuietly(s, "CREATE INDEX idx_results_uuid_time ON contest_results(uuid, finished_at)");
                createIndexQuietly(s, "CREATE INDEX idx_stats_crop_best ON player_stats(crop, personal_best)");
                createIndexQuietly(s, "DROP INDEX idx_results_uuid ON contest_results");
            }
        }
    }

    // MySQL has no CREATE INDEX IF NOT EXISTS; an index that exists already is fine
    private static void createIndexQuietly(Statement s, String sql) {
        try {
            s.executeUpdate(sql);
        } catch (SQLException exists) {
        }
    }

    /** Finish queued work, then close. Waits up to ten seconds so a shutdown does not lose results. */
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Database work was still queued at shutdown and has been abandoned.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (pool != null) {
            pool.close();
        }
    }

    private interface Query<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> CompletableFuture<T> submit(String what, Query<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = pool.getConnection()) {
                return query.run(connection);
            } catch (SQLException failed) {
                logger.log(Level.SEVERE, "Database: " + what + " failed", failed);
                throw new IllegalStateException(failed);
            }
        }, worker);
    }

    /** What {@link #record} did: whether it was a personal best, and the reward's id if one was saved. */
    public record Recorded(boolean newBest, int rewardId) {
    }

    /** A placing to record, with its reward or null. */
    public record Placing(Result result, PendingReward reward) {
    }

    /**
     * Record a finished contest's placings, fold each into the player's stats and save the rewards, as
     * one queued task and one transaction: during shutdown a follow-up task would arrive after
     * {@link #close} and the rewards would be lost. Results come back in the same order.
     *
     * @param bracketRank orders bracket ids, lower being better, to keep each player's best
     */
    public CompletableFuture<List<Recorded>> record(List<Placing> placings, ToIntFunction<String> bracketRank) {
        return submit("recording contest results", c -> {
            List<Recorded> out = new ArrayList<>(placings.size());
            c.setAutoCommit(false);
            try {
                for (Placing placing : placings) {
                    out.add(record(c, placing.result(), placing.reward(), bracketRank));
                }
                c.commit();
            } catch (SQLException | RuntimeException failed) {
                c.rollback();
                throw failed;
            } finally {
                c.setAutoCommit(true);
            }
            return out;
        });
    }

    private Recorded record(Connection c, Result result, PendingReward reward, ToIntFunction<String> bracketRank)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO contest_results (contest_id, event, "
                + "crop, uuid, player_name, score, position, participants, bracket, finished_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, result.contestId());
            ps.setString(2, result.event());
            ps.setString(3, result.crop());
            ps.setString(4, result.player().toString());
            ps.setString(5, result.playerName());
            ps.setLong(6, result.score());
            ps.setInt(7, result.position());
            ps.setInt(8, result.total());
            ps.setString(9, result.bracket());
            ps.setLong(10, result.finishedAt());
            ps.executeUpdate();
        }
        PlayerStats old = readStats(c, result.player(), result.crop());
        boolean newBest = result.score() > old.personalBest();
        String best = old.bestBracket();
        if (result.bracket() != null
                && bracketRank.applyAsInt(result.bracket()) < bracketRank.applyAsInt(best)) {
            best = result.bracket();
        }
        try (PreparedStatement ps = c.prepareStatement(mysql
                ? "INSERT INTO player_stats (uuid, crop, personal_best, total_harvested, contests_participated, "
                + "best_bracket) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE personal_best = VALUES(personal_best), "
                + "total_harvested = VALUES(total_harvested), contests_participated = VALUES(contests_participated), "
                + "best_bracket = VALUES(best_bracket)"
                : "INSERT INTO player_stats (uuid, crop, personal_best, total_harvested, contests_participated, "
                + "best_bracket) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(uuid, crop) DO UPDATE SET "
                + "personal_best = excluded.personal_best, total_harvested = excluded.total_harvested, "
                + "contests_participated = excluded.contests_participated, best_bracket = excluded.best_bracket")) {
            ps.setString(1, result.player().toString());
            ps.setString(2, result.crop());
            ps.setLong(3, Math.max(old.personalBest(), result.score()));
            ps.setLong(4, old.totalHarvested() + result.score());
            ps.setInt(5, old.contests() + 1);
            ps.setString(6, best == null ? "NONE" : best);
            ps.executeUpdate();
        }
        int rewardId = reward == null ? -1 : insertReward(c, reward);
        return new Recorded(newBest, rewardId);
    }

    private static PlayerStats readStats(Connection c, UUID player, String crop) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM player_stats WHERE uuid = ? AND crop = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, crop);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return stats(rs, player);
                }
            }
        }
        return new PlayerStats(player, crop, 0, 0, 0, "NONE");
    }

    private static PlayerStats stats(ResultSet rs, UUID player) throws SQLException {
        return new PlayerStats(player, rs.getString("crop"), rs.getLong("personal_best"),
                rs.getLong("total_harvested"), rs.getInt("contests_participated"), rs.getString("best_bracket"));
    }

    public CompletableFuture<List<PlayerStats>> stats(UUID player) {
        return submit("loading stats", c -> {
            List<PlayerStats> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM player_stats WHERE uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(stats(rs, player));
                    }
                }
            }
            return out;
        });
    }

    /** A crop's all-time best scores, one per player. */
    public CompletableFuture<List<Leader>> leaderboard(String crop, int limit) {
        return submit("loading a leaderboard", c -> {
            List<Leader> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT s.uuid, s.personal_best, "
                    + "(SELECT r.player_name FROM contest_results r WHERE r.uuid = s.uuid ORDER BY r.finished_at DESC LIMIT 1) AS name "
                    + "FROM player_stats s WHERE s.crop = ? AND s.personal_best > 0 "
                    + "ORDER BY s.personal_best DESC LIMIT ?")) {
                ps.setString(1, crop);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        out.add(new Leader(UUID.fromString(rs.getString("uuid")), name == null ? "?" : name,
                                rs.getLong("personal_best")));
                    }
                }
            }
            return out;
        });
    }

    private static int insertReward(Connection c, PendingReward reward) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO pending_rewards (uuid, player_name, "
                + "event_name, crop, score, personal_best, bracket, bracket_display, position, "
                + "total_participants, commands, timestamp, claimed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reward.player().toString());
            ps.setString(2, reward.playerName());
            ps.setString(3, reward.eventName());
            ps.setString(4, reward.crop());
            ps.setLong(5, reward.score());
            ps.setLong(6, reward.personalBest());
            ps.setString(7, reward.bracket());
            ps.setString(8, reward.bracketDisplay());
            ps.setInt(9, reward.position());
            ps.setInt(10, reward.total());
            ps.setString(11, String.join("||", reward.actions()));
            ps.setLong(12, reward.timestamp());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    public CompletableFuture<List<PendingReward>> pending(UUID player) {
        return submit("loading rewards", c -> {
            List<PendingReward> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM pending_rewards WHERE uuid = ? AND claimed = 0 ORDER BY timestamp DESC")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String raw = rs.getString("commands");
                        List<String> actions = raw == null || raw.isEmpty()
                                ? List.of() : Arrays.asList(raw.split("\\|\\|"));
                        out.add(new PendingReward(rs.getInt("id"), player, rs.getString("player_name"),
                                rs.getString("event_name"), rs.getString("crop"), rs.getLong("score"),
                                rs.getLong("personal_best"), rs.getString("bracket"),
                                rs.getString("bracket_display"), rs.getInt("position"),
                                rs.getInt("total_participants"), actions, rs.getLong("timestamp")));
                    }
                }
            }
            return out;
        });
    }

    public CompletableFuture<Integer> pendingCount(UUID player) {
        return submit("counting rewards", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM pending_rewards WHERE uuid = ? AND claimed = 0")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    /**
     * Mark a reward claimed, if it is still unclaimed and belongs to this player. Returns true only for
     * the one call that flipped it; this atomic update is the whole defence against double-claiming.
     */
    public CompletableFuture<Boolean> claim(int id, UUID player) {
        return submit("claiming a reward", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE pending_rewards SET claimed = 1 WHERE id = ? AND uuid = ? AND claimed = 0")) {
                ps.setInt(1, id);
                ps.setString(2, player.toString());
                return ps.executeUpdate() == 1;
            }
        });
    }
}
