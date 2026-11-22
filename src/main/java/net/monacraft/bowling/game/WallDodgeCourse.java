package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Obstacle;

import java.util.List;

/**
 * Ten progressively harder wall layouts modelled from Wii Sports Resort's
 * wall-dodge/spin-control course. Coordinates are generated from the actual
 * configured lane dimensions, so the same course works on every Hub lane.
 */
public final class WallDodgeCourse {
    public static final int STAGES = 10;

    private WallDodgeCourse() { }

    public static List<Obstacle> stage(int frameNumber, double halfWidth, double pinDistance,
                                       double thickness, double restitution) {
        if (frameNumber < 1 || frameNumber > STAGES) {
            throw new IllegalArgumentException("frameNumber must be in [1, 10]");
        }
        if (!Double.isFinite(halfWidth) || halfWidth < 1.0
                || !Double.isFinite(pinDistance) || pinDistance < 6.0) {
            throw new IllegalArgumentException("lane is too small for wall dodge");
        }
        // The supplied video uses isolated wall pieces, not a full-width gate
        // at every depth. Keep every piece away from the outer 0.75m corridor;
        // a player can always choose a safe straight route while tighter centre
        // lines still reward position and spin control.
        List<Wall> walls = switch (frameNumber) {
            case 1 -> List.of(w(0.00, 1.00, 0.48));
            case 2 -> List.of(w(-0.32, 1.00, 0.38), w(0.32, 1.00, 0.68));
            case 3 -> List.of(w(-0.35, 1.10, 0.32), w(0.00, 0.90, 0.56),
                    w(0.35, 1.10, 0.76));
            case 4 -> List.of(w(0.00, 1.35, 0.30), w(-0.38, 1.00, 0.53),
                    w(0.38, 1.00, 0.75));
            case 5 -> List.of(w(-0.40, 1.00, 0.27), w(0.10, 1.20, 0.44),
                    w(0.40, 1.00, 0.62), w(-0.10, 1.20, 0.79));
            case 6 -> List.of(w(0.38, 1.05, 0.25), w(-0.20, 1.10, 0.42),
                    w(-0.38, 1.05, 0.60), w(0.20, 1.10, 0.78));
            case 7 -> List.of(w(0.00, 1.30, 0.23), w(-0.40, 1.00, 0.37),
                    w(0.25, 1.00, 0.51), w(0.40, 1.00, 0.66),
                    w(-0.20, 1.10, 0.80));
            case 8 -> List.of(w(-0.42, 1.00, 0.22), w(0.15, 1.15, 0.36),
                    w(0.42, 1.00, 0.50), w(-0.15, 1.15, 0.64),
                    w(0.00, 1.25, 0.79));
            case 9 -> List.of(w(0.42, 1.00, 0.21), w(-0.20, 1.10, 0.33),
                    w(-0.42, 1.00, 0.46), w(0.20, 1.10, 0.59),
                    w(0.42, 1.00, 0.72), w(-0.15, 1.15, 0.83));
            case 10 -> List.of(w(0.00, 1.35, 0.20), w(-0.42, 1.00, 0.32),
                    w(0.25, 1.05, 0.44), w(0.42, 1.00, 0.56),
                    w(-0.25, 1.05, 0.68), w(0.00, 1.35, 0.81));
            default -> throw new AssertionError();
        };

        java.util.ArrayList<Obstacle> result = new java.util.ArrayList<>(walls.size());
        for (int index = 0; index < walls.size(); index++) {
            Wall wall = walls.get(index);
            double z = pinDistance * wall.zRatio;
            double centre = wall.centreRatio * halfWidth;
            double minimumX = centre - wall.width / 2.0;
            double maximumX = centre + wall.width / 2.0;
            double minZ = z - thickness / 2.0;
            double maxZ = z + thickness / 2.0;
            result.add(new Obstacle("wall-" + frameNumber + '-' + index,
                    minimumX, maximumX, minZ, maxZ, restitution));
        }
        return List.copyOf(result);
    }

    private static Wall w(double centreRatio, double width, double zRatio) {
        return new Wall(centreRatio, width, zRatio);
    }

    private record Wall(double centreRatio, double width, double zRatio) { }
}
