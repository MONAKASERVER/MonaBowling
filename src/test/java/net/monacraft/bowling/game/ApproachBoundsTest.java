package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApproachBoundsTest {
    @Test
    void movementBehindFoulLineIsUnchanged() {
        Vec2 position = new Vec2(1.25, -2.0);
        assertEquals(position, ApproachBounds.clampToFoulLine(position));
    }

    @Test
    void movementAcrossConfiguredSpawnLineIsClamped() {
        assertEquals(new Vec2(-1.4, 0.0),
                ApproachBounds.clampToFoulLine(new Vec2(-1.4, 0.8)));
    }
}
