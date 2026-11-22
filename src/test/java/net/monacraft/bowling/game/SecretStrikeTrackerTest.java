package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretStrikeTrackerTest {
    @Test
    void uninterruptedGuardTripTriggersAtRearSwitch() {
        SecretStrikeTracker tracker = new SecretStrikeTracker(1.60, 1.84, 1.0, 20.0);
        for (int z = 0; z < 20; z++) assertFalse(tracker.sample(new Vec2(-1.72, z), true));
        assertTrue(tracker.sample(new Vec2(-1.72, 20.0), true));
        assertTrue(tracker.triggered());
    }

    @Test
    void lateGutterEntryCannotArmSecretStrike() {
        SecretStrikeTracker tracker = new SecretStrikeTracker(1.60, 1.84, 1.0, 20.0);
        assertFalse(tracker.sample(new Vec2(0.2, 0.0), true));
        assertFalse(tracker.sample(new Vec2(1.72, 20.0), true));
        assertTrue(tracker.invalid());
    }

    @Test
    void leavingGuardCorridorInvalidatesRoll() {
        SecretStrikeTracker tracker = new SecretStrikeTracker(1.60, 1.84, 1.0, 20.0);
        assertFalse(tracker.sample(new Vec2(1.72, 0.0), true));
        assertFalse(tracker.sample(new Vec2(1.50, 8.0), true));
        assertFalse(tracker.sample(new Vec2(1.72, 20.0), true));
        assertTrue(tracker.invalid());
    }
}
