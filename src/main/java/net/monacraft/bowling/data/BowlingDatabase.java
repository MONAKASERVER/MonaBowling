package net.monacraft.bowling.data;

import net.monacraft.bowling.model.GameMode;
import net.monacraft.bowling.model.LaneDefinition;
import net.monacraft.bowling.model.LanePoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class BowlingDatabase implements AutoCloseable {
    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final int busyTimeoutMillis;
    private final Logger logger;
    private Connection connection;

    public BowlingDatabase(Path file, int busyTimeoutMillis, Logger logger) {
        this.file = file.toAbsolutePath().normalize();
        this.busyTimeoutMillis = busyTimeoutMillis;
        this.logger = logger;
    }

    public synchronized void open() throws SQLException, IOException {
        if (connection != null) {
            return;
        }
        Files.createDirectories(file.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = " + busyTimeoutMillis);
        }
        migrate();
    }

    public synchronized boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
        int version;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            version = result.next() ? result.getInt(1) : 0;
        }
        if (version > SCHEMA_VERSION) {
            throw new SQLException("Database schema " + version + " is newer than supported " + SCHEMA_VERSION);
        }
        if (version == 0) {
            applyVersionOne();
            logger.info("Created MonaBowling SQLite schema version 1");
        }
    }

    private void applyVersionOne() throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE players (
                      uuid TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      last_seen_epoch INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE games (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      match_uuid TEXT NOT NULL,
                      player_uuid TEXT NOT NULL REFERENCES players(uuid),
                      mode TEXT NOT NULL,
                      score INTEGER NOT NULL,
                      frame_scores TEXT NOT NULL,
                      strikes INTEGER NOT NULL,
                      spares INTEGER NOT NULL,
                      gutters INTEGER NOT NULL,
                      winner INTEGER NOT NULL,
                      completed_epoch INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE player_stats (
                      player_uuid TEXT NOT NULL REFERENCES players(uuid),
                      mode TEXT NOT NULL,
                      games_played INTEGER NOT NULL DEFAULT 0,
                      wins INTEGER NOT NULL DEFAULT 0,
                      best_score INTEGER NOT NULL DEFAULT 0,
                      total_score INTEGER NOT NULL DEFAULT 0,
                      strikes INTEGER NOT NULL DEFAULT 0,
                      spares INTEGER NOT NULL DEFAULT 0,
                      gutters INTEGER NOT NULL DEFAULT 0,
                      updated_epoch INTEGER NOT NULL,
                      PRIMARY KEY (player_uuid, mode)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE lanes (
                      id TEXT PRIMARY KEY,
                      world TEXT NOT NULL,
                      origin_x REAL NOT NULL, origin_y REAL NOT NULL, origin_z REAL NOT NULL,
                      origin_yaw REAL NOT NULL, origin_pitch REAL NOT NULL,
                      spawn_x REAL NOT NULL, spawn_y REAL NOT NULL, spawn_z REAL NOT NULL,
                      spawn_yaw REAL NOT NULL, spawn_pitch REAL NOT NULL,
                      pins_x REAL NOT NULL, pins_y REAL NOT NULL, pins_z REAL NOT NULL,
                      pins_yaw REAL NOT NULL, pins_pitch REAL NOT NULL,
                      modes TEXT NOT NULL,
                      enabled INTEGER NOT NULL,
                      updated_epoch INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX games_mode_score_idx ON games(mode, score DESC)");
            statement.executeUpdate("INSERT INTO schema_version(version) VALUES (1)");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized List<LaneDefinition> loadLanes() throws SQLException {
        requireOpen();
        List<LaneDefinition> lanes = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT * FROM lanes ORDER BY id")) {
            while (result.next()) {
                lanes.add(readLane(result));
            }
        }
        return List.copyOf(lanes);
    }

    public synchronized void saveLane(LaneDefinition lane) throws SQLException {
        requireOpen();
        String sql = """
                INSERT INTO lanes (
                  id, world,
                  origin_x, origin_y, origin_z, origin_yaw, origin_pitch,
                  spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch,
                  pins_x, pins_y, pins_z, pins_yaw, pins_pitch,
                  modes, enabled, updated_epoch
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  world=excluded.world,
                  origin_x=excluded.origin_x, origin_y=excluded.origin_y, origin_z=excluded.origin_z,
                  origin_yaw=excluded.origin_yaw, origin_pitch=excluded.origin_pitch,
                  spawn_x=excluded.spawn_x, spawn_y=excluded.spawn_y, spawn_z=excluded.spawn_z,
                  spawn_yaw=excluded.spawn_yaw, spawn_pitch=excluded.spawn_pitch,
                  pins_x=excluded.pins_x, pins_y=excluded.pins_y, pins_z=excluded.pins_z,
                  pins_yaw=excluded.pins_yaw, pins_pitch=excluded.pins_pitch,
                  modes=excluded.modes, enabled=excluded.enabled, updated_epoch=excluded.updated_epoch
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, lane.id());
            statement.setString(index++, lane.world());
            index = bindPoint(statement, index, lane.laneOrigin());
            index = bindPoint(statement, index, lane.playerSpawn());
            index = bindPoint(statement, index, lane.pinDeck());
            statement.setString(index++, encodeModes(lane.modes()));
            statement.setInt(index++, lane.enabled() ? 1 : 0);
            statement.setLong(index, Instant.now().getEpochSecond());
            statement.executeUpdate();
        }
    }

    public synchronized boolean deleteLane(String id) throws SQLException {
        requireOpen();
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM lanes WHERE id = ?")) {
            statement.setString(1, LaneDefinition.normalizeId(id));
            return statement.executeUpdate() > 0;
        }
    }

    public synchronized void saveCompletedGame(CompletedGame game) throws SQLException {
        requireOpen();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            upsertPlayer(game.playerId(), game.playerName(), game.completedAt());
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO games(match_uuid, player_uuid, mode, score, frame_scores,
                                      strikes, spares, gutters, winner, completed_epoch)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, game.matchId().toString());
                statement.setString(2, game.playerId().toString());
                statement.setString(3, game.mode().configKey());
                statement.setInt(4, game.score());
                statement.setString(5, encodeScores(game.frameScores()));
                statement.setInt(6, game.strikes());
                statement.setInt(7, game.spares());
                statement.setInt(8, game.gutters());
                statement.setInt(9, game.winner() ? 1 : 0);
                statement.setLong(10, game.completedAt().getEpochSecond());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_stats(player_uuid, mode, games_played, wins, best_score,
                                             total_score, strikes, spares, gutters, updated_epoch)
                    VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid, mode) DO UPDATE SET
                      games_played=games_played + 1,
                      wins=wins + excluded.wins,
                      best_score=MAX(best_score, excluded.best_score),
                      total_score=total_score + excluded.total_score,
                      strikes=strikes + excluded.strikes,
                      spares=spares + excluded.spares,
                      gutters=gutters + excluded.gutters,
                      updated_epoch=excluded.updated_epoch
                    """)) {
                statement.setString(1, game.playerId().toString());
                statement.setString(2, game.mode().configKey());
                statement.setInt(3, game.winner() ? 1 : 0);
                statement.setInt(4, game.score());
                statement.setInt(5, game.score());
                statement.setInt(6, game.strikes());
                statement.setInt(7, game.spares());
                statement.setInt(8, game.gutters());
                statement.setLong(9, game.completedAt().getEpochSecond());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized Optional<PlayerStatistics> findStatistics(UUID playerId, GameMode mode)
            throws SQLException {
        requireOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.name, s.* FROM player_stats s
                JOIN players p ON p.uuid = s.player_uuid
                WHERE s.player_uuid = ? AND s.mode = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, mode.configKey());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerStatistics(
                        playerId,
                        result.getString("name"),
                        mode,
                        result.getInt("games_played"),
                        result.getInt("wins"),
                        result.getInt("best_score"),
                        result.getLong("total_score"),
                        result.getInt("strikes"),
                        result.getInt("spares"),
                        result.getInt("gutters")
                ));
            }
        }
    }

    public synchronized List<RankingEntry> loadRanking(GameMode mode, int limit) throws SQLException {
        requireOpen();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<RankingEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.player_uuid, p.name, s.best_score, s.games_played
                FROM player_stats s JOIN players p ON p.uuid = s.player_uuid
                WHERE s.mode = ?
                ORDER BY s.best_score DESC, s.games_played ASC, p.name COLLATE NOCASE ASC
                LIMIT ?
                """)) {
            statement.setString(1, mode.configKey());
            statement.setInt(2, safeLimit);
            try (ResultSet result = statement.executeQuery()) {
                int rank = 1;
                while (result.next()) {
                    entries.add(new RankingEntry(
                            rank++,
                            UUID.fromString(result.getString("player_uuid")),
                            result.getString("name"),
                            result.getInt("best_score"),
                            result.getInt("games_played")
                    ));
                }
            }
        }
        return List.copyOf(entries);
    }

    private void upsertPlayer(UUID playerId, String name, Instant instant) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO players(uuid, name, last_seen_epoch) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_seen_epoch=excluded.last_seen_epoch
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, name);
            statement.setLong(3, instant.getEpochSecond());
            statement.executeUpdate();
        }
    }

    private static LaneDefinition readLane(ResultSet result) throws SQLException {
        return new LaneDefinition(
                result.getString("id"),
                result.getString("world"),
                readPoint(result, "origin"),
                readPoint(result, "spawn"),
                readPoint(result, "pins"),
                decodeModes(result.getString("modes")),
                result.getInt("enabled") != 0
        );
    }

    private static LanePoint readPoint(ResultSet result, String prefix) throws SQLException {
        return new LanePoint(
                result.getDouble(prefix + "_x"),
                result.getDouble(prefix + "_y"),
                result.getDouble(prefix + "_z"),
                result.getFloat(prefix + "_yaw"),
                result.getFloat(prefix + "_pitch")
        );
    }

    private static int bindPoint(PreparedStatement statement, int index, LanePoint point) throws SQLException {
        statement.setDouble(index++, point.x());
        statement.setDouble(index++, point.y());
        statement.setDouble(index++, point.z());
        statement.setFloat(index++, point.yaw());
        statement.setFloat(index++, point.pitch());
        return index;
    }

    private static String encodeModes(Set<GameMode> modes) {
        return modes.stream().map(GameMode::configKey).sorted().collect(Collectors.joining(","));
    }

    private static Set<GameMode> decodeModes(String value) throws SQLException {
        EnumSet<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (String token : value.split(",")) {
            GameMode mode = GameMode.parse(token.trim())
                    .orElseThrow(() -> new SQLException("Unknown game mode in lane: " + token));
            modes.add(mode);
        }
        return modes;
    }

    private static String encodeScores(List<Integer> scores) {
        return scores.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void requireOpen() throws SQLException {
        if (!isOpen()) {
            throw new SQLException("MonaBowling database is not open");
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }
}
