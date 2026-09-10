package au.com.addstar.velocityunscramble.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * All access is async. Nothing here may be called from an event or command
 * thread without going through the returned future - PlayerChatEvent gates chat
 * forwarding for the whole network, so blocking there would add latency to
 * everyone's chat.
 */
public class Database implements AutoCloseable {
    private static final int POOL_SIZE = 4;

    private final HikariDataSource source;
    private final ExecutorService executor;
    private final Logger logger;

    public Database(String url, String username, String password, Logger logger) {
        this.logger = logger;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        // Set explicitly: DriverManager auto-discovery is unreliable inside
        // Velocity's isolated plugin classloaders.
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(POOL_SIZE);
        config.setPoolName("VelocityUnscramble");
        this.source = new HikariDataSource(config);

        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "VelocityUnscramble-DB-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(POOL_SIZE, factory);
    }

    /** Creates the tables if they are missing. */
    public CompletableFuture<Void> initSchema() {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = source.getConnection()) {
                try (PreparedStatement s = c.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS players (
                          playerid VARCHAR(40) NOT NULL PRIMARY KEY,
                          totalpoints INT NOT NULL DEFAULT 0,
                          points INT NOT NULL DEFAULT 0,
                          wins INT NOT NULL DEFAULT 0
                        )""")) {
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS wins (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          playerid VARCHAR(40) NOT NULL,
                          phrase VARCHAR(255) NOT NULL,
                          duration DOUBLE NOT NULL,
                          difficulty INT NOT NULL,
                          points INT NOT NULL,
                          INDEX idx_wins_player (playerid)
                        )""")) {
                    s.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Could not initialise schema", e);
            }
        }, executor);
    }

    /** Never completes with null; completes exceptionally on SQL failure. */
    public CompletableFuture<PlayerRecord> getRecord(UUID id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT totalpoints, points, wins FROM players WHERE playerid = ?";
            try (Connection c = source.getConnection();
                 PreparedStatement s = c.prepareStatement(sql)) {
                s.setString(1, id.toString());
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerRecord(id, rs.getInt(1), rs.getInt(2), rs.getInt(3));
                    }
                    return PlayerRecord.empty(id);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Could not read record for " + id, e);
            }
        }, executor);
    }

    /**
     * Records a win and increments the player's totals in one transaction, then
     * returns the updated record. Both writes commit together, and the totals
     * are incremented in SQL so concurrent updates cannot be lost.
     */
    public CompletableFuture<PlayerRecord> recordWin(UUID id, String phrase, int difficulty,
                                                     int points, double duration) {
        return CompletableFuture.supplyAsync(() -> {
            String upsert = """
                    INSERT INTO players (playerid, totalpoints, points, wins)
                    VALUES (?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                      totalpoints = totalpoints + VALUES(totalpoints),
                      points = points + VALUES(points),
                      wins = wins + 1""";
            String insertWin = """
                    INSERT INTO wins (playerid, phrase, duration, difficulty, points)
                    VALUES (?, ?, ?, ?, ?)""";
            String select = "SELECT totalpoints, points, wins FROM players WHERE playerid = ?";

            try (Connection c = source.getConnection()) {
                boolean previousAutoCommit = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement s = c.prepareStatement(upsert)) {
                        s.setString(1, id.toString());
                        s.setInt(2, points);
                        s.setInt(3, points);
                        s.executeUpdate();
                    }
                    try (PreparedStatement s = c.prepareStatement(insertWin)) {
                        s.setString(1, id.toString());
                        s.setString(2, phrase);
                        s.setDouble(3, duration);
                        s.setInt(4, difficulty);
                        s.setInt(5, points);
                        s.executeUpdate();
                    }
                    PlayerRecord updated;
                    try (PreparedStatement s = c.prepareStatement(select)) {
                        s.setString(1, id.toString());
                        try (ResultSet rs = s.executeQuery()) {
                            updated = rs.next()
                                    ? new PlayerRecord(id, rs.getInt(1), rs.getInt(2), rs.getInt(3))
                                    : PlayerRecord.empty(id);
                        }
                    }
                    c.commit();
                    return updated;
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(previousAutoCommit);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Could not record win for " + id, e);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        source.close();
        logger.info("Database connections closed.");
    }
}
