package net.monacraft.bowling.config;

import net.monacraft.bowling.model.GameMode;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BowlingSettingsTest {
    @Test
    void acceptsHubSafeDefaults() {
        BowlingSettings settings = settings("bowling.db", 120, 1);
        assertEquals(120, settings.physicsHz());
        assertEquals(1, settings.physicsThreads());
        assertEquals(4, settings.maximumPlayersPerLane());
        assertEquals(3.00, settings.hundredPinLaneHalfWidth());
        assertEquals(0.26, settings.pinVisualWidth());
        assertEquals(0.78, settings.pinVisualHeight());
        assertEquals(true, settings.standardSideBumpers());
        assertEquals(true, settings.hundredPinSideBumpers());
        assertEquals(true, settings.sideBumpers(GameMode.STANDARD));
        assertEquals(true, settings.sideBumpers(GameMode.HUNDRED_PIN));
    }

    @Test
    void rejectsDatabasePathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> settings("../other.db", 120, 1));
    }

    @Test
    void rejectsPhysicsRatesBelowSixtyHertz() {
        assertThrows(IllegalArgumentException.class, () -> settings("bowling.db", 59, 1));
    }

    @Test
    void rejectsHundredPinLaneThatCutsThroughOuterPins() {
        assertThrows(IllegalArgumentException.class,
                () -> settings("bowling.db", 120, 1, 1.55, 1.60, 1.84));
    }

    private static BowlingSettings settings(String database, int hz, int threads) {
        return settings(database, hz, threads, 3.00, 3.05, 3.35);
    }

    private static BowlingSettings settings(String database, int hz, int threads,
                                             double laneHalfWidth, double railMin, double railMax) {
        return new BowlingSettings("ja_JP", EnumSet.allOf(GameMode.class),
                4, 1, 4, 45, 90,
                1600, 4.0, 12.0, 0.16, 0.52, 2.70, 0.40, true,
                0.46, laneHalfWidth, 0.40, true, true, railMin, railMax, 1.0, 0.65,
                0.55, 30, 256, 0.26, 0.78,
                hz, threads, 4, database, 5000,
                true, true, true,
                1.0, 0.95, 0.72, 0.88, 0.82,
                1.25, 18.0, true, 6.0, 16.0, 120, true, false);
    }
}
