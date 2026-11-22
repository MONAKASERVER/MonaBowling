package net.monacraft.bowling.physics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsRuntimeTest {
    @Test
    void runtimeAcceptsModeSpecificPhysicsWithTheSameClock() throws Exception {
        SimulationConfig base = PhysicsTestSupport.config();
        SimulationConfig hundredPin = base.forHundredPin();
        try (PhysicsRuntime runtime = new PhysicsRuntime(base, 1)) {
            SimulationWorld world = PhysicsTestSupport.world(hundredPin,
                    new BallState(Vec2.ZERO, new Vec2(0, 0.02), 0,
                            hundredPin.ballRadius(), hundredPin.ballMass()), List.of());
            UUID simulation = runtime.startSimulation(world);
            assertTrue(runtime.awaitSettled(simulation, Duration.ofSeconds(1)));
        }
    }

    @Test
    void runtimeIsLazyPublishesSnapshotsAndStopsWhenIdle() throws Exception {
        SimulationConfig config = PhysicsTestSupport.config();
        try (PhysicsRuntime runtime = new PhysicsRuntime(config, 1)) {
            assertFalse(runtime.running());
            SimulationWorld world = PhysicsTestSupport.world(config,
                    new BallState(Vec2.ZERO, new Vec2(0, 3), 0,
                            config.ballRadius(), config.ballMass()), List.of());

            UUID simulation = runtime.startSimulation(world);
            assertTrue(runtime.running());
            Thread.sleep(80L);
            PhysicsSnapshot snapshot = runtime.snapshot(simulation).orElseThrow();
            assertTrue(snapshot.step() > 0);
            assertTrue(snapshot.ball().position().z() > 0.0);
            assertTrue(runtime.profile().steps() > 0);
            assertTrue(runtime.removeSimulation(simulation));
            assertFalse(runtime.running());
        }
    }

    @Test
    void runtimeCanWaitForASettledSimulation() throws Exception {
        SimulationConfig config = PhysicsTestSupport.config();
        try (PhysicsRuntime runtime = new PhysicsRuntime(config, 1)) {
            SimulationWorld world = PhysicsTestSupport.world(config,
                    new BallState(Vec2.ZERO, new Vec2(0, 0.02), 0,
                            config.ballRadius(), config.ballMass()), List.of());
            UUID simulation = runtime.startSimulation(world);

            assertTrue(runtime.awaitSettled(simulation, Duration.ofSeconds(1)));
            assertTrue(runtime.snapshot(simulation).orElseThrow().settled());
        }
    }
}
