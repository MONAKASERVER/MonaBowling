package net.monacraft.bowling.physics;

public record Obstacle(String id, double minimumX, double maximumX, double minimumZ, double maximumZ,
                       double restitution) {
    public Obstacle {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("obstacle id is required");
        }
        if (!Double.isFinite(minimumX) || !Double.isFinite(maximumX)
                || !Double.isFinite(minimumZ) || !Double.isFinite(maximumZ)
                || minimumX >= maximumX || minimumZ >= maximumZ) {
            throw new IllegalArgumentException("invalid obstacle bounds");
        }
        if (!Double.isFinite(restitution) || restitution < 0.0 || restitution > 1.0) {
            throw new IllegalArgumentException("obstacle restitution must be in [0, 1]");
        }
    }

    public Vec2 nearestPoint(Vec2 point) {
        return new Vec2(
                Math.max(minimumX, Math.min(maximumX, point.x())),
                Math.max(minimumZ, Math.min(maximumZ, point.z()))
        );
    }
}
