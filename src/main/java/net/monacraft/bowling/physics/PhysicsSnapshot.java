package net.monacraft.bowling.physics;

import java.util.List;

public record PhysicsSnapshot(
        long step,
        double simulationSeconds,
        BallSnapshot ball,
        List<PinSnapshot> pins,
        boolean settled
) {
    public PhysicsSnapshot {
        pins = List.copyOf(pins);
    }

    public PhysicsSnapshot interpolated(double alpha) {
        BallSnapshot interpolatedBall = ball.interpolated(alpha);
        List<PinSnapshot> interpolatedPins = pins.stream().map(pin -> pin.interpolated(alpha)).toList();
        return new PhysicsSnapshot(step, simulationSeconds, interpolatedBall, interpolatedPins, settled);
    }

    public record BallSnapshot(
            Vec2 previousPosition,
            Vec2 position,
            Vec2 velocity,
            double spinRadiansPerSecond,
            double rollRadians,
            boolean active,
            boolean inGutter
    ) {
        BallSnapshot interpolated(double alpha) {
            Vec2 point = Vec2.lerp(previousPosition, position, alpha);
            return new BallSnapshot(point, point, velocity, spinRadiansPerSecond, rollRadians, active, inGutter);
        }
    }

    public record PinSnapshot(
            int id,
            Vec2 previousPosition,
            Vec2 position,
            Vec2 velocity,
            Vec2 tiltDirection,
            double tiltRadians,
            boolean down,
            boolean sleeping
    ) {
        PinSnapshot interpolated(double alpha) {
            Vec2 point = Vec2.lerp(previousPosition, position, alpha);
            return new PinSnapshot(id, point, point, velocity, tiltDirection, tiltRadians, down, sleeping);
        }
    }
}
