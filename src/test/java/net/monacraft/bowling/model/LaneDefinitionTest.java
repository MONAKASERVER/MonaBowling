package net.monacraft.bowling.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LaneDefinitionTest {
    private static final LanePoint ORIGIN = new LanePoint(1, 64, 2, 90, 0);

    @Test
    void normalizesSafeLaneIdentifiers() {
        LaneDefinition lane = new LaneDefinition("  LANE_01 ", "world", ORIGIN, ORIGIN, ORIGIN,
                EnumSet.of(GameMode.STANDARD), true);

        assertEquals("lane_01", lane.id());
    }

    @Test
    void rejectsUnsafeLaneIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new LaneDefinition(
                "../lane", "world", ORIGIN, ORIGIN, ORIGIN, EnumSet.of(GameMode.STANDARD), true));
    }

    @Test
    void pointUpdatesCreateANewImmutableDefinition() {
        LaneDefinition original = new LaneDefinition("one", "world", ORIGIN, ORIGIN, ORIGIN,
                EnumSet.allOf(GameMode.class), true);
        LanePoint replacement = new LanePoint(4, 70, 8, 0, 0);

        LaneDefinition updated = original.withPinDeck(replacement);

        assertNotSame(original, updated);
        assertEquals(ORIGIN, original.pinDeck());
        assertEquals(replacement, updated.pinDeck());
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new LanePoint(Double.NaN, 0, 0, 0, 0));
    }
}
