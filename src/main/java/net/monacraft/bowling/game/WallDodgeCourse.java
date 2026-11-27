package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Obstacle;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Ten progressively harder wall layouts modelled from Wii Sports Resort's
 * wall-dodge/spin-control course. Coordinates are generated from the actual
 * configured lane dimensions, so the same course works on every Hub lane.
 */
public final class WallDodgeCourse {
    public static final int STAGES = 10;
    private static final double[] MOVEMENT_COLUMNS = {
            -0.50, -0.25, 0.0, 0.25, 0.50, 0.25, 0.0, -0.25
    };

    private WallDodgeCourse() { }

    public static List<Obstacle> stage(int frameNumber, double halfWidth, double pinDistance,
                                       double thickness, double restitution) {
        return stage(frameNumber, halfWidth, pinDistance, thickness, restitution, 0L);
    }

    public static List<Obstacle> stage(int frameNumber, double halfWidth, double pinDistance,
                                       double thickness, double restitution, long courseSeed) {
        return stage(frameNumber, halfWidth, pinDistance, thickness, restitution,
                courseSeed, 0L);
    }

    public static List<Obstacle> stage(int frameNumber, double halfWidth, double pinDistance,
                                       double thickness, double restitution, long courseSeed,
                                       long motionStep) {
        if (frameNumber < 1 || frameNumber > STAGES) {
            throw new IllegalArgumentException("frameNumber must be in [1, 10]");
        }
        if (!Double.isFinite(halfWidth) || halfWidth < 1.0
                || !Double.isFinite(pinDistance) || pinDistance < 6.0) {
            throw new IllegalArgumentException("lane is too small for wall dodge");
        }
        // Each match gets a stable seed, while each frame derives a different
        // arrangement from it. The outer lanes remain clear so randomisation
        // cannot create an impossible course.
        int count = switch (frameNumber) {
            case 1 -> 1;
            case 2 -> 2;
            case 3, 4 -> 3;
            case 5, 6 -> 4;
            case 7, 8 -> 5;
            case 9, 10 -> 6;
            default -> throw new AssertionError();
        };
        SplittableRandom random = new SplittableRandom(
                courseSeed ^ (0x9E3779B97F4A7C15L * frameNumber));
        double[] columns = {-0.50, -0.31, -0.12, 0.12, 0.31, 0.50};
        for (int index = columns.length - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            double value = columns[index];
            columns[index] = columns[swap];
            columns[swap] = value;
        }

        List<Wall> walls = new ArrayList<>(count);
        int movingCount = movingWallCount(frameNumber);
        for (int index = 0; index < count; index++) {
            double zRatio;
            if (count == 1) {
                zRatio = random.nextDouble(0.35, 0.66);
            } else {
                double spacing = 0.58 / (count - 1);
                zRatio = 0.22 + spacing * index + random.nextDouble(-0.025, 0.026);
            }
            double width = random.nextDouble(0.90, 1.26);
            boolean moving = index < movingCount;
            double centreRatio = columns[index];
            if (moving) {
                int phaseOffset = random.nextInt(MOVEMENT_COLUMNS.length);
                int phase = (int) Math.floorMod(motionStep + phaseOffset,
                        (long) MOVEMENT_COLUMNS.length);
                centreRatio = MOVEMENT_COLUMNS[phase];
            }
            walls.add(new Wall(centreRatio, width, zRatio, moving));
        }

        ArrayList<Obstacle> result = new ArrayList<>(walls.size());
        for (int index = 0; index < walls.size(); index++) {
            Wall wall = walls.get(index);
            double z = pinDistance * wall.zRatio;
            double centre = wall.centreRatio * halfWidth;
            double minimumX = centre - wall.width / 2.0;
            double maximumX = centre + wall.width / 2.0;
            double minZ = z - thickness / 2.0;
            double maxZ = z + thickness / 2.0;
            result.add(new Obstacle("wall-" + frameNumber + '-'
                    + (wall.moving ? "moving-" : "static-") + index,
                    minimumX, maximumX, minZ, maxZ, restitution));
        }
        return List.copyOf(result);
    }

    public static int movingWallCount(int frameNumber) {
        if (frameNumber < 1 || frameNumber > STAGES) {
            throw new IllegalArgumentException("frameNumber must be in [1, 10]");
        }
        if (frameNumber < 4) return 0;
        return frameNumber < 7 ? 1 : 2;
    }

    public static boolean hasMovingWalls(int frameNumber) {
        return movingWallCount(frameNumber) > 0;
    }

    private record Wall(double centreRatio, double width, double zRatio, boolean moving) { }
}
