package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.PhysicsSnapshot;
import net.monacraft.bowling.physics.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollJudgementTest {
    @Test
    void gutterRollNeverCountsDisturbedPins() {
        PhysicsSnapshot snapshot = snapshot(true, List.of(
                pin(0, new Vec2(0.8, 4.0), true),
                pin(1, new Vec2(-0.8, 4.0), false)));

        Set<Integer> knocked = RollJudgement.knockedPins(
                snapshot, Map.of(0, 4, 1, 7), Set.of(4, 7));

        assertTrue(knocked.isEmpty());
    }

    @Test
    void displacedButUprightPinRemainsStanding() {
        PhysicsSnapshot snapshot = snapshot(false, List.of(
                pin(0, new Vec2(1.2, 5.0), false),
                pin(1, new Vec2(0.0, 5.0), true)));

        Set<Integer> knocked = RollJudgement.knockedPins(
                snapshot, Map.of(0, 2, 1, 3), Set.of(2, 3));

        assertEquals(Set.of(3), knocked);
    }

    private static PhysicsSnapshot snapshot(boolean gutter,
                                            List<PhysicsSnapshot.PinSnapshot> pins) {
        PhysicsSnapshot.BallSnapshot ball = new PhysicsSnapshot.BallSnapshot(
                Vec2.ZERO, Vec2.ZERO, Vec2.ZERO, 0.0, 0.0, false, gutter);
        return new PhysicsSnapshot(1L, 1.0, ball, pins, true);
    }

    private static PhysicsSnapshot.PinSnapshot pin(int id, Vec2 position, boolean down) {
        return new PhysicsSnapshot.PinSnapshot(id, position, position, Vec2.ZERO,
                new Vec2(0.0, 1.0), down ? Math.toRadians(60.0) : 0.0,
                down, true);
    }
}
