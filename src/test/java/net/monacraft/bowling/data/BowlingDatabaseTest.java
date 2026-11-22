package net.monacraft.bowling.data;

import net.monacraft.bowling.model.GameMode;
import net.monacraft.bowling.model.LaneDefinition;
import net.monacraft.bowling.model.LanePoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BowlingDatabaseTest {
    @TempDir
    Path temporaryDirectory;

    private BowlingDatabase database;

    @BeforeEach
    void openDatabase() throws Exception {
        database = new BowlingDatabase(temporaryDirectory.resolve("test.db"), 1000,
                Logger.getAnonymousLogger());
        database.open();
    }

    @AfterEach
    void closeDatabase() throws Exception {
        database.close();
    }

    @Test
    void migratesAndPersistsLaneDefinitions() throws Exception {
        LanePoint origin = new LanePoint(10, 65, -3, 180, 0);
        LaneDefinition lane = new LaneDefinition("alpha", "hub", origin, origin,
                new LanePoint(10, 65, -21, 180, 0), EnumSet.allOf(GameMode.class), true);

        database.saveLane(lane);

        assertEquals(List.of(lane), database.loadLanes());
        assertTrue(database.deleteLane("alpha"));
        assertFalse(database.deleteLane("alpha"));
        assertTrue(database.loadLanes().isEmpty());
    }

    @Test
    void aggregatesCompletedGamesAndRanking() throws Exception {
        UUID player = UUID.randomUUID();
        database.saveCompletedGame(new CompletedGame(
                player, "Bowler", UUID.randomUUID(), GameMode.STANDARD, 180,
                List.of(18, 36, 54, 72, 90, 108, 126, 144, 162, 180),
                4, 3, 1, true, Instant.parse("2026-11-21T00:00:00Z")
        ));
        database.saveCompletedGame(new CompletedGame(
                player, "Bowler", UUID.randomUUID(), GameMode.STANDARD, 220,
                List.of(20, 40, 60, 80, 100, 120, 140, 160, 190, 220),
                7, 2, 0, false, Instant.parse("2026-11-21T00:01:00Z")
        ));

        PlayerStatistics statistics = database.findStatistics(player, GameMode.STANDARD).orElseThrow();
        assertEquals(2, statistics.gamesPlayed());
        assertEquals(1, statistics.wins());
        assertEquals(220, statistics.bestScore());
        assertEquals(400, statistics.totalScore());
        assertEquals(11, statistics.strikes());

        List<RankingEntry> ranking = database.loadRanking(GameMode.STANDARD, 10);
        assertEquals(1, ranking.size());
        assertEquals("Bowler", ranking.getFirst().playerName());
        assertEquals(220, ranking.getFirst().bestScore());
    }
}
