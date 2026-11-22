package net.monacraft.bowling.physics;

public record LaneGeometry(double halfWidth, double length, double gutterWidth,
                           boolean sideBumpersEnabled,
                           double secretRailMinimumX, double secretRailMaximumX) {
    public LaneGeometry(double halfWidth, double length, double gutterWidth) {
        this(halfWidth, length, gutterWidth, false, 0.0, 0.0);
    }

    public LaneGeometry(double halfWidth, double length, double gutterWidth,
                        boolean sideBumpersEnabled) {
        this(halfWidth, length, gutterWidth, sideBumpersEnabled, 0.0, 0.0);
    }

    public LaneGeometry {
        if (!Double.isFinite(halfWidth) || halfWidth <= 0.0
                || !Double.isFinite(length) || length <= 0.0
                || !Double.isFinite(gutterWidth) || gutterWidth < 0.0
                || !Double.isFinite(secretRailMinimumX)
                || !Double.isFinite(secretRailMaximumX)) {
            throw new IllegalArgumentException("lane dimensions must be finite and valid");
        }
        boolean noRail = secretRailMinimumX == 0.0 && secretRailMaximumX == 0.0;
        if (!noRail && (secretRailMinimumX < halfWidth
                || secretRailMaximumX <= secretRailMinimumX
                || secretRailMaximumX > halfWidth + gutterWidth)) {
            throw new IllegalArgumentException("secret rail must be a narrow band inside the gutter");
        }
    }

    public static LaneGeometry regulationLike() {
        return new LaneGeometry(0.525, 19.2, 0.24, false);
    }

    public double outerWallCentreLimit(double radius) {
        return Math.max(0.05, halfWidth + gutterWidth - radius);
    }

    public double innerBumperCentreLimit(double radius) {
        return Math.max(0.05, halfWidth - radius);
    }

    public boolean hasSecretRail() {
        return secretRailMaximumX > secretRailMinimumX;
    }

    public boolean isOnSecretRail(Vec2 position) {
        double absoluteX = Math.abs(position.x());
        return hasSecretRail() && absoluteX >= secretRailMinimumX
                && absoluteX <= secretRailMaximumX;
    }

    public boolean isInGutter(Vec2 position, double radius) {
        return Math.abs(position.x()) + radius > halfWidth;
    }

    public boolean isOutOfBounds(Vec2 position, double radius) {
        return Math.abs(position.x()) - radius > halfWidth + gutterWidth
                || position.z() > length + 2.0
                || position.z() < -2.0;
    }
}
