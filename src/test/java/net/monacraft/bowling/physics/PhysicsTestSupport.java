package net.monacraft.bowling.physics;

import java.util.List;

final class PhysicsTestSupport {
    private PhysicsTestSupport() {
    }

    static SimulationConfig config() {
        return new SimulationConfig(
                120, 4,
                0.1085, 6.8, 0.004, 0.04,
                0.010, 0.12, 0.45, 60.0, 0.72, 0.96,
                0.061, 0.14, 1.55, 0.38,
                0.9, 1.4,
                Math.toRadians(6.0), Math.toRadians(28.0),
                0.02, 24, 16, true, 0.32
        );
    }

    static SimulationWorld world(SimulationConfig config, BallState ball, List<Vec2> pinPositions) {
        return new SimulationWorld(config, new LaneGeometry(4.0, 25.0, 0.3), ball,
                SimulationWorld.createPins(pinPositions, config), List.of());
    }

    static void runSteps(MonaBowlingPhysics engine, SimulationWorld world, int maximumSteps) {
        for (int i = 0; i < maximumSteps && !world.settled(); i++) {
            engine.step(world);
        }
    }

    static long downCount(SimulationWorld world) {
        return world.pins().stream().filter(PinState::down).count();
    }
}
