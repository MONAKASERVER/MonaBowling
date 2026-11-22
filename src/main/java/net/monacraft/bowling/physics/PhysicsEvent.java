package net.monacraft.bowling.physics;

public record PhysicsEvent(
        Type type,
        long step,
        int primaryId,
        int secondaryId,
        String obstacleId,
        double magnitude
) {
    public enum Type {
        BALL_PIN_COLLISION,
        PIN_PIN_COLLISION,
        OBSTACLE_COLLISION,
        BUMPER_COLLISION,
        GUTTER_ENTERED,
        PIN_DOWN,
        BALL_STOPPED
    }
}
