package net.monacraft.bowling.game;

final class SpinRelease {
    private static final double DIRECTION_EPSILON = 1.0E-9;

    private SpinRelease() {
    }

    static double sneakSpin(double charge, double yawSpin, double startX,
                            double minimum, double maximum, double cap) {
        double clampedCharge = Math.max(0.0, Math.min(1.0, charge));
        double eased = Math.sin(clampedCharge * Math.PI / 2.0);
        double strength = Math.min(cap, minimum + (maximum - minimum) * eased);
        double direction;
        if (Math.abs(yawSpin) > DIRECTION_EPSILON) {
            direction = Math.signum(yawSpin);
        } else {
            // Positive spin curves left. From either side, default toward the lane centre.
            direction = startX >= 0.0 ? 1.0 : -1.0;
        }
        return direction * strength;
    }
}
