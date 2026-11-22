package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Obstacle;

import java.util.ArrayList;
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
        double edge = halfWidth - 0.08;
        double wideGap = Math.max(0.92, halfWidth * 0.38);
        double narrowGap = Math.max(0.76, halfWidth * 0.31);
        List<Gate> gates = switch (frameNumber) {
            case 1 -> List.of(g(0.46, 0.46, wideGap));
            case 2 -> List.of(g(0.34, -0.46, wideGap), g(0.65, 0.46, wideGap));
            case 3 -> List.of(g(0.31, 0.48, wideGap), g(0.56, 0.00, wideGap),
                    g(0.76, -0.48, wideGap));
            case 4 -> List.of(g(0.28, -0.52, wideGap), g(0.50, 0.52, wideGap),
                    g(0.72, -0.52, narrowGap));
            case 5 -> List.of(g(0.26, 0.52, wideGap), g(0.43, -0.52, wideGap),
                    g(0.61, 0.00, narrowGap), g(0.78, 0.52, narrowGap));
            case 6 -> List.of(g(0.24, -0.55, wideGap), g(0.40, 0.55, wideGap),
                    g(0.56, -0.55, narrowGap), g(0.72, 0.55, narrowGap));
            case 7 -> List.of(g(0.23, 0.00, wideGap), g(0.38, -0.56, narrowGap),
                    g(0.53, 0.56, narrowGap), g(0.68, 0.00, narrowGap),
                    g(0.81, -0.56, narrowGap));
            case 8 -> List.of(g(0.22, 0.56, narrowGap), g(0.36, -0.56, narrowGap),
                    g(0.50, 0.20, narrowGap), g(0.64, -0.20, narrowGap),
                    g(0.78, 0.56, narrowGap));
            case 9 -> List.of(g(0.21, -0.58, narrowGap), g(0.34, 0.58, narrowGap),
                    g(0.47, -0.20, narrowGap), g(0.60, 0.20, narrowGap),
                    g(0.73, -0.58, narrowGap), g(0.84, 0.58, narrowGap));
            case 10 -> List.of(g(0.20, 0.58, narrowGap), g(0.32, -0.58, narrowGap),
                    g(0.44, 0.22, narrowGap), g(0.56, -0.22, narrowGap),
                    g(0.68, 0.58, narrowGap), g(0.80, -0.58, narrowGap));
            default -> throw new AssertionError();
        };

        List<Obstacle> result = new ArrayList<>();
        int index = 0;
        for (Gate gate : gates) {
            double z = pinDistance * gate.zRatio;
            double centre = gate.gapCentreRatio * halfWidth;
            double gapMin = Math.max(-edge, centre - gate.gapWidth / 2.0);
            double gapMax = Math.min(edge, centre + gate.gapWidth / 2.0);
            double minZ = z - thickness / 2.0;
            double maxZ = z + thickness / 2.0;
            if (gapMin > -edge + 0.08) {
                result.add(new Obstacle("wall-" + frameNumber + '-' + index++ + "L",
                        -edge, gapMin, minZ, maxZ, restitution));
            }
            if (gapMax < edge - 0.08) {
                result.add(new Obstacle("wall-" + frameNumber + '-' + index++ + "R",
                        gapMax, edge, minZ, maxZ, restitution));
            }
        }
        return List.copyOf(result);
    }

    private static Gate g(double zRatio, double gapCentreRatio, double gapWidth) {
        return new Gate(zRatio, gapCentreRatio, gapWidth);
    }

    private record Gate(double zRatio, double gapCentreRatio, double gapWidth) { }
}
