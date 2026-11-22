package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Vec2;

/**
 * Validates one uninterrupted trip over either gutter guard into the rear switch.
 * Entering a gutter late, changing sides, or falling away from the narrow guard
 * corridor permanently invalidates that roll.
 */
public final class SecretStrikeTracker {
    private final double minimumRailX;
    private final double maximumRailX;
    private final double armingMaximumZ;
    private final double switchZ;
    private int side;
    private boolean invalid;
    private boolean triggered;

    public SecretStrikeTracker(double minimumRailX, double maximumRailX,
                               double armingMaximumZ, double switchZ) {
        if (!Double.isFinite(minimumRailX) || !Double.isFinite(maximumRailX)
                || minimumRailX <= 0.0 || maximumRailX <= minimumRailX
                || !Double.isFinite(armingMaximumZ) || armingMaximumZ < 0.0
                || !Double.isFinite(switchZ) || switchZ <= armingMaximumZ) {
            throw new IllegalArgumentException("invalid secret-strike corridor");
        }
        this.minimumRailX = minimumRailX;
        this.maximumRailX = maximumRailX;
        this.armingMaximumZ = armingMaximumZ;
        this.switchZ = switchZ;
    }

    public boolean sample(Vec2 position, boolean active) {
        if (invalid || triggered || !active) return triggered;
        double absoluteX = Math.abs(position.x());
        boolean onRail = absoluteX >= minimumRailX && absoluteX <= maximumRailX;
        if (side == 0) {
            if (position.z() > armingMaximumZ || !onRail) {
                invalid = true;
                return false;
            }
            side = position.x() < 0.0 ? -1 : 1;
        } else if (!onRail || Integer.signum(side) != (position.x() < 0.0 ? -1 : 1)) {
            invalid = true;
            return false;
        }
        if (position.z() >= switchZ) triggered = true;
        return triggered;
    }

    public boolean armed() { return side != 0 && !invalid; }
    public boolean invalid() { return invalid; }
    public boolean triggered() { return triggered; }
}
