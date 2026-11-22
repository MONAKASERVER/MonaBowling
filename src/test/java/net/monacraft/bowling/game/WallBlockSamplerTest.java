package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Obstacle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WallBlockSamplerTest {
    @Test
    void renderedBlockCentresStayInsideTheirWallSegment() {
        for (int frame = 1; frame <= WallDodgeCourse.STAGES; frame++) {
            List<Obstacle> walls = WallDodgeCourse.stage(frame, 2.7, 18.0, 0.34, 0.38);
            for (Obstacle wall : walls) {
                for (double centre : WallBlockSampler.centres(wall)) {
                    assertTrue(centre >= wall.minimumX() && centre <= wall.maximumX(),
                            "frame " + frame + " sampled outside wall bounds");
                }
            }
        }
    }

    @Test
    void everyProductionLaneKeepsAnEmptyWorldBlockColumn() {
        double[][] lanes = {
                {30.5099606439623, 84.6733656669955, 30.5287279503797, 102.626499431353},
                {18.4271751356123, 84.5650472372015, 18.5307708571923, 102.547371030566},
                {6.4992665346346, 84.742901226133, 6.52011129704723, 102.478850890717},
                {-5.58874354951626, 84.9150624089918, -5.4609369617091, 102.5279843686},
                {-17.5769881695539, 84.2045198484705, -17.4346446488632, 102.574843750521}
        };
        for (double[] lane : lanes) {
            double dx = lane[2] - lane[0];
            double dz = lane[3] - lane[1];
            double distance = Math.hypot(dx, dz);
            double forwardX = dx / distance;
            double rightX = -dz / distance;
            for (int frame = 1; frame <= WallDodgeCourse.STAGES; frame++) {
                List<Obstacle> walls = WallDodgeCourse.stage(frame, 2.7, distance, 0.34, 0.38);
                for (int index = 0; index < walls.size(); index++) {
                    Set<Integer> occupied = new HashSet<>();
                    Obstacle wall = walls.get(index);
                    double z = (wall.minimumZ() + wall.maximumZ()) * 0.5;
                    for (double x : WallBlockSampler.centres(wall)) {
                        occupied.add((int) Math.floor(lane[0] + rightX * x + forwardX * z));
                    }
                    Set<Integer> laneColumns = new HashSet<>();
                    for (double x = -2.3; x <= 2.3; x += 0.10) {
                        laneColumns.add((int) Math.floor(lane[0] + rightX * x + forwardX * z));
                    }
                    laneColumns.removeAll(occupied);
                    assertTrue(laneColumns.size() >= 2,
                            "production lane closed in frame " + frame + " wall " + index);
                }
            }
        }
    }
}
