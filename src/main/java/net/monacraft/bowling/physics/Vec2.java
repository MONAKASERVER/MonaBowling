package net.monacraft.bowling.physics;

public record Vec2(double x, double z) {
    public static final Vec2 ZERO = new Vec2(0.0, 0.0);

    public Vec2 {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("vector components must be finite");
        }
    }

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, z + other.z);
    }

    public Vec2 subtract(Vec2 other) {
        return new Vec2(x - other.x, z - other.z);
    }

    public Vec2 multiply(double scalar) {
        if (!Double.isFinite(scalar)) {
            throw new IllegalArgumentException("scalar must be finite");
        }
        return new Vec2(x * scalar, z * scalar);
    }

    public double dot(Vec2 other) {
        return x * other.x + z * other.z;
    }

    public double cross(Vec2 other) {
        return x * other.z - z * other.x;
    }

    public double lengthSquared() {
        return x * x + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vec2 normalizedOr(Vec2 fallback) {
        double length = length();
        return length > 1.0E-12 ? multiply(1.0 / length) : fallback;
    }

    public Vec2 perpendicularLeft() {
        return new Vec2(-z, x);
    }

    public Vec2 clampLength(double maximum) {
        double squared = lengthSquared();
        if (squared <= maximum * maximum) {
            return this;
        }
        return normalizedOr(ZERO).multiply(maximum);
    }

    public static Vec2 lerp(Vec2 previous, Vec2 current, double alpha) {
        double bounded = Math.max(0.0, Math.min(1.0, alpha));
        return previous.multiply(1.0 - bounded).add(current.multiply(bounded));
    }
}
