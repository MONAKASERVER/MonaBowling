package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Obstacle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallDodgeCourseTest {
    @Test
    void exposesTenIncreasingStagesInsideThePlayableLane() {
        int previousCount = 0;
        for (int frame = 1; frame <= WallDodgeCourse.STAGES; frame++) {
            List<Obstacle> obstacles = WallDodgeCourse.stage(frame, 2.7, 18.0, 0.34, 0.38);
            assertFalse(obstacles.isEmpty());
            assertTrue(obstacles.size() >= previousCount,
                    "stage " + frame + " unexpectedly became easier");
            previousCount = obstacles.size();
            for (Obstacle obstacle : obstacles) {
                assertTrue(obstacle.minimumX() >= -2.7);
                assertTrue(obstacle.maximumX() <= 2.7);
                assertTrue(obstacle.minimumZ() > 2.5);
                assertTrue(obstacle.maximumZ() < 17.0);
                assertEquals(0.38, obstacle.restitution());
            }
        }
    }

    @Test
    void leavesARealBallWidthOpeningAtEveryGate() {
        for (int frame = 1; frame <= WallDodgeCourse.STAGES; frame++) {
            List<Obstacle> obstacles = WallDodgeCourse.stage(frame, 2.7, 18.0, 0.34, 0.38);
            for (int index = 0; index < obstacles.size(); index += 2) {
                Obstacle left = obstacles.get(index);
                Obstacle right = obstacles.get(index + 1);
                assertTrue(right.minimumX() - left.maximumX() > 0.70,
                        "stage " + frame + " gate is too narrow");
            }
        }
    }

    @Test
    void rejectsFramesOutsideTenFrameGame() {
        assertThrows(IllegalArgumentException.class,
                () -> WallDodgeCourse.stage(0, 2.7, 18.0, 0.34, 0.38));
        assertThrows(IllegalArgumentException.class,
                () -> WallDodgeCourse.stage(11, 2.7, 18.0, 0.34, 0.38));
    }
}
