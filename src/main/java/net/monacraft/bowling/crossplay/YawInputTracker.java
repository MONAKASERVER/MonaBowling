package net.monacraft.bowling.crossplay;

/** Accumulates camera yaw without producing a 360-degree spike at wraparound. */
public final class YawInputTracker {
    private float previousYaw;
    private double accumulatedDegrees;

    public void reset(float yaw) {
        previousYaw = yaw;
        accumulatedDegrees = 0.0;
    }

    public double sample(float yaw) {
        accumulatedDegrees += normalizeDegrees(yaw - previousYaw);
        previousYaw = yaw;
        return accumulatedDegrees;
    }

    public double accumulatedDegrees() {
        return accumulatedDegrees;
    }

    static double normalizeDegrees(double angle) {
        double result = angle % 360.0;
        if (result > 180.0) result -= 360.0;
        if (result < -180.0) result += 360.0;
        return result;
    }
}
