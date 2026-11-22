package net.monacraft.bowling.physics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretRailPhysicsTest {
    private static final LaneGeometry LANE = new LaneGeometry(
            3.0, 12.0, 0.40, true, 3.22, 3.29);

    @Test
    void narrowWallTopRouteDoesNotBecomeGutterOrBounce() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        SimulationWorld world = world(config, new Vec2(3.25, 0.0), new Vec2(0.0, 8.0));
        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 120);
        assertFalse(world.ball().inGutter());
        assertTrue(LANE.isOnSecretRail(world.ball().position()));
        assertTrue(world.ball().position().z() > 6.0);
    }

    @Test
    void slabBelowRailIsAGutter() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        SimulationWorld world = world(config, new Vec2(3.10, 0.0), new Vec2(0.0, 8.0));
        new MonaBowlingPhysics().step(world);
        assertTrue(world.ball().inGutter());
    }

    @Test
    void laneSideApproachReflectsFromInnerBumper() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        SimulationWorld world = world(config, new Vec2(2.80, 0.0), new Vec2(4.0, 4.0));
        MonaBowlingPhysics physics = new MonaBowlingPhysics();
        boolean reflected = false;
        for (int step = 0; step < 30; step++) {
            MonaBowlingPhysics.StepResult result = physics.step(world);
            reflected |= result.events().stream()
                    .anyMatch(event -> event.type() == PhysicsEvent.Type.BUMPER_COLLISION);
        }
        assertTrue(reflected);
        assertFalse(world.ball().inGutter());
        assertTrue(world.ball().velocity().x() < 0.0);
    }

    @Test
    void fallingOffWallTopDropsIntoSlabGutter() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        SimulationWorld world = world(config, new Vec2(3.25, 0.0), new Vec2(-0.8, 8.0));
        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 30);
        assertTrue(world.ball().inGutter());
    }

    private static SimulationWorld world(SimulationConfig config, Vec2 position, Vec2 velocity) {
        return new SimulationWorld(config, LANE,
                new BallState(position, velocity, 0.0, config.ballRadius(), config.ballMass()),
                List.of(), List.of());
    }
}
