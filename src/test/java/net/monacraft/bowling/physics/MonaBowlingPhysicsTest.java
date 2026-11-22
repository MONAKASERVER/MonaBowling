package net.monacraft.bowling.physics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonaBowlingPhysicsTest {
    @Test
    void ballTravelsStraightWithoutSpin() {
        SimulationConfig config = PhysicsTestSupport.config();
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 7), 0, config.ballRadius(), config.ballMass()),
                List.of());

        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 120);

        assertEquals(0.0, world.ball().position().x(), 1.0E-10);
        assertTrue(world.ball().position().z() > 6.5);
    }

    @Test
    void spinProducesAContinuousCurve() {
        SimulationConfig config = PhysicsTestSupport.config();
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 7), 14, config.ballRadius(), config.ballMass()),
                List.of());

        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 120);

        assertTrue(world.ball().position().x() < -0.25,
                () -> "expected left curve, x=" + world.ball().position().x());
        assertTrue(world.ball().position().z() > 6.0);
    }

    @Test
    void ballCollisionTransfersMomentumAndTipsPin() {
        SimulationConfig config = PhysicsTestSupport.config();
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 9), 0, config.ballRadius(), config.ballMass()),
                List.of(new Vec2(0, 2.0)));
        MonaBowlingPhysics engine = new MonaBowlingPhysics();

        PhysicsTestSupport.runSteps(engine, world, 300);

        PinState pin = world.pins().getFirst();
        assertTrue(pin.position().z() > 2.1);
        assertTrue(pin.down(), () -> "tilt=" + pin.tiltRadians());
        assertTrue(engine.profile().resolvedCollisions() > 0);
    }

    @Test
    void ballRetainsForwardMomentumAfterHeadPinImpact() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 8), 0,
                        config.ballRadius(), config.ballMass()),
                List.of(new Vec2(0, 2.0)));
        MonaBowlingPhysics engine = new MonaBowlingPhysics();
        double speedAfterImpact = Double.NaN;

        for (int step = 0; step < 180 && Double.isNaN(speedAfterImpact); step++) {
            MonaBowlingPhysics.StepResult result = engine.step(world);
            if (result.events().stream()
                    .anyMatch(event -> event.type() == PhysicsEvent.Type.BALL_PIN_COLLISION)) {
                speedAfterImpact = world.ball().velocity().z();
            }
        }

        assertTrue(Double.isFinite(speedAfterImpact), "the ball never reached the head pin");
        assertTrue(speedAfterImpact > 4.5,
                () -> "ball lost too much forward momentum: " + world.ball().velocity());
        int additionalImpacts = 0;
        for (int step = 0; step < 60 && !world.settled(); step++) {
            MonaBowlingPhysics.StepResult result = engine.step(world);
            additionalImpacts += (int) result.events().stream()
                    .filter(event -> event.type() == PhysicsEvent.Type.BALL_PIN_COLLISION)
                    .count();
        }
        int completedAdditionalImpacts = additionalImpacts;
        assertTrue(world.ball().active(), () -> "the ball stopped immediately after one pin impact: position="
                + world.ball().position() + ", velocity=" + world.ball().velocity()
                + ", repeatedImpacts=" + completedAdditionalImpacts
                + ", pinDown=" + world.pins().getFirst().down()
                + ", pinTilt=" + world.pins().getFirst().tiltRadians());
        assertTrue(world.ball().position().z() > 4.0,
                () -> "ball did not follow through: " + world.ball().position());
        assertTrue(world.ball().velocity().z() > 4.0,
                () -> "ball follow-through speed is too low: " + world.ball().velocity());
    }

    @Test
    void fallingPinTransfersEnergyToNeighbour() {
        SimulationConfig config = PhysicsTestSupport.config();
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 10), 0, config.ballRadius(), config.ballMass()),
                List.of(new Vec2(0, 2.0), new Vec2(0, 2.14), new Vec2(0, 2.28)));

        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 420);

        assertTrue(PhysicsTestSupport.downCount(world) >= 2,
                () -> "down pins=" + PhysicsTestSupport.downCount(world));
        assertTrue(world.pins().get(2).position().z() > 2.28);
    }

    @Test
    void compactTenPinRackCanBeClearedByPhysicalChain() {
        SimulationConfig config = PhysicsTestSupport.config();
        List<Vec2> rack = PinLayouts.standardTen(new Vec2(0, 2.0), 0.128);
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 13), 0, config.ballRadius(), config.ballMass()), rack);

        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 600);

        assertEquals(10, PhysicsTestSupport.downCount(world),
                () -> "down pins=" + PhysicsTestSupport.downCount(world));
    }

    @Test
    void regulationSpacingRackProducesARealPinChain() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        List<Vec2> rack = PinLayouts.standardTen(new Vec2(0, 2.0), 0.52);
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(new Vec2(0.035, 0), new Vec2(0, 13), 0,
                        config.ballRadius(), config.ballMass()), rack);

        MonaBowlingPhysics engine = new MonaBowlingPhysics();
        int steps = 0;
        while (steps < 5400 && !world.settled()) {
            engine.step(world);
            steps++;
        }

        assertTrue(PhysicsTestSupport.downCount(world) >= 3,
                () -> "down pins=" + PhysicsTestSupport.downCount(world));
        int completedSteps = steps;
        assertTrue(world.settled(), () -> "the production-spacing roll did not settle after "
                + completedSteps + " steps; ballActive=" + world.ball().active()
                + ", movingPins=" + world.pins().stream().filter(pin -> !pin.sleeping()).count());
        assertTrue(steps < 1200, () -> "roll took too long to settle: " + completedSteps + " steps");
        assertFinite(world);
    }

    @Test
    void moderateThrowDoesNotAutomaticallyClearStandardRack() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        List<Vec2> rack = PinLayouts.standardTen(new Vec2(0, 2.0), 0.52);
        SimulationWorld world = new SimulationWorld(config, new LaneGeometry(2.70, 6.0, 0.40),
                new BallState(new Vec2(0.12, 0), new Vec2(0, 8.0), 0,
                        config.ballRadius(), config.ballMass()),
                SimulationWorld.createPins(rack, config), List.of());

        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 2400);

        long down = PhysicsTestSupport.downCount(world);
        assertTrue(down > 0 && down < 10, () -> "moderate throw down pins=" + down);
        assertTrue(world.settled());
        assertFinite(world);
    }

    @Test
    void pinCannotEscapeVirtualLaneBoundaryAtExtremeSpeed() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        PinState pin = new PinState(0, new Vec2(3.0, 3.0), config.pinRadius(), config.pinMass());
        pin.velocity(new Vec2(40.0, 0.0));
        BallState stoppedBall = new BallState(Vec2.ZERO, Vec2.ZERO, 0,
                config.ballRadius(), config.ballMass());
        stoppedBall.active(false);
        LaneGeometry lane = new LaneGeometry(3.0, 8.0, 0.40);
        SimulationWorld world = new SimulationWorld(config, lane, stoppedBall, List.of(pin), List.of());

        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 600);

        double outerLimit = lane.halfWidth() + lane.gutterWidth();
        assertTrue(Math.abs(world.pins().getFirst().position().x()) < outerLimit);
        assertTrue(Math.abs(world.pins().getFirst().position().z()
                - world.pins().getFirst().rackPosition().z()) <= 1.50 + 1.0E-9);
        assertFinite(world);
    }

    @Test
    void hundredPinRackProducesLargeChainWithoutNonFiniteState() {
        SimulationConfig config = PhysicsTestSupport.config();
        List<Vec2> rack = PinLayouts.hundredPinStressRack(new Vec2(0, 2.0), 0.124);
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 14), 0, config.ballRadius(), config.ballMass()), rack);
        MonaBowlingPhysics engine = new MonaBowlingPhysics();

        PhysicsTestSupport.runSteps(engine, world, 840);

        assertTrue(PhysicsTestSupport.downCount(world) >= 35,
                () -> "down pins=" + PhysicsTestSupport.downCount(world));
        assertFinite(world);
        PhysicsProfiler.ProfileSnapshot profile = engine.profile();
        assertTrue(profile.steps() > 0);
        assertTrue(Double.isFinite(profile.averageStepMillis()));
        System.out.printf("100-pin profile: steps=%d averageMs=%.4f checks=%d resolved=%d substeps=%d%n",
                profile.steps(), profile.averageStepMillis(), profile.collisionChecks(),
                profile.resolvedCollisions(), profile.subSteps());
    }

    @Test
    void productionHundredPinRackProducesPhysicalChainAndSettles() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        List<Vec2> rack = PinLayouts.resortHundred(new Vec2(0, 2.0), 0.46);
        BallState ball = new BallState(new Vec2(0.045, 0), new Vec2(0, 14), 0,
                config.ballRadius(), config.ballMass());
        SimulationWorld world = new SimulationWorld(config, new LaneGeometry(3.00, 9.5, 0.40),
                ball, SimulationWorld.createPins(rack, config), List.of());
        MonaBowlingPhysics engine = new MonaBowlingPhysics();

        int steps = 0;
        // A dense rack keeps transferring very small impulses long after the
        // visible cascade. Allow up to 45 simulated seconds, while still
        // requiring the production configuration to reach its sleep state.
        while (steps < 5400 && !world.settled()) {
            engine.step(world);
            steps++;
        }

        int completedSteps = steps;
        assertTrue(world.settled(), () -> "100-pin production roll did not settle in " + completedSteps
                + " steps; ballActive=" + world.ball().active()
                + ", movingPins=" + world.pins().stream().filter(pin -> !pin.sleeping()).count());
        assertTrue(PhysicsTestSupport.downCount(world) >= 3,
                () -> "down pins=" + PhysicsTestSupport.downCount(world));
        assertTrue(engine.profile().resolvedCollisions() >= 3,
                () -> "resolved=" + engine.profile().resolvedCollisions());
        assertFinite(world);
    }

    @Test
    void gutterIsDetectedAndStopsPinCollisions() {
        SimulationConfig config = PhysicsTestSupport.config();
        SimulationWorld world = new SimulationWorld(config, LaneGeometry.regulationLike(),
                new BallState(new Vec2(0.48, 0), new Vec2(0, 8), 0,
                        config.ballRadius(), config.ballMass()),
                SimulationWorld.createPins(List.of(new Vec2(0.48, 2)), config), List.of());

        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 360);

        assertTrue(world.ball().inGutter());
        assertFalse(world.pins().getFirst().down());
    }

    @Test
    void standardSideBumperReflectsBallWithoutEnteringGutter() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        LaneGeometry lane = new LaneGeometry(1.0, 12.0, 0.25, true);
        BallState ball = new BallState(new Vec2(0.95, 0), new Vec2(4.0, 4.0), 0,
                config.ballRadius(), config.ballMass());
        SimulationWorld world = new SimulationWorld(config, lane, ball, List.of(), List.of());
        MonaBowlingPhysics engine = new MonaBowlingPhysics();
        boolean collided = false;

        for (int step = 0; step < 60 && !collided; step++) {
            MonaBowlingPhysics.StepResult result = engine.step(world);
            collided = result.events().stream()
                    .anyMatch(event -> event.type() == PhysicsEvent.Type.BUMPER_COLLISION);
        }

        assertTrue(collided);
        assertTrue(world.ball().velocity().x() < 0.0);
        assertTrue(Math.abs(world.ball().velocity().x()) < 4.0);
        assertFalse(world.ball().inGutter());
        assertTrue(world.ball().position().x()
                <= lane.outerWallCentreLimit(config.ballRadius()));
    }

    @Test
    void thinObstacleReflectsFastBallWithoutTunnelling() {
        SimulationConfig config = PhysicsTestSupport.config();
        BallState ball = new BallState(Vec2.ZERO, new Vec2(0, 50), 0,
                config.ballRadius(), config.ballMass());
        SimulationWorld world = new SimulationWorld(config, new LaneGeometry(4, 25, 0.3), ball,
                List.of(), List.of(new Obstacle("wall", -1, 1, 0.4, 0.42, 0.8)));
        MonaBowlingPhysics engine = new MonaBowlingPhysics();

        for (int i = 0; i < 4; i++) {
            engine.step(world);
        }

        assertTrue(world.ball().velocity().z() < 0.0,
                () -> "velocity=" + world.ball().velocity());
        assertTrue(world.ball().position().z() < 0.4);
    }

    @Test
    void highSpeedBallCannotTunnelThroughPin() {
        SimulationConfig config = PhysicsTestSupport.config();
        SimulationWorld world = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0, 50), 0, config.ballRadius(), config.ballMass()),
                List.of(new Vec2(0, 0.6)));
        MonaBowlingPhysics engine = new MonaBowlingPhysics();

        for (int i = 0; i < 3; i++) {
            engine.step(world);
        }

        assertTrue(engine.profile().resolvedCollisions() > 0);
        assertTrue(world.pins().getFirst().velocity().length() > 0.0
                || world.pins().getFirst().tiltRadians() > 0.0);
    }

    @Test
    void identicalInitialStateIsDeterministic() {
        SimulationConfig config = PhysicsTestSupport.config();
        List<Vec2> rack = PinLayouts.standardTen(new Vec2(0.03, 2.0), 0.13);
        SimulationWorld first = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0.1, 11), 3, config.ballRadius(), config.ballMass()), rack);
        SimulationWorld second = PhysicsTestSupport.world(config,
                new BallState(Vec2.ZERO, new Vec2(0.1, 11), 3, config.ballRadius(), config.ballMass()), rack);

        MonaBowlingPhysics firstEngine = new MonaBowlingPhysics();
        MonaBowlingPhysics secondEngine = new MonaBowlingPhysics();
        for (int i = 0; i < 500; i++) {
            firstEngine.step(first);
            secondEngine.step(second);
        }

        assertEquals(first.snapshot(), second.snapshot());
    }

    private static void assertFinite(SimulationWorld world) {
        List<Double> values = new ArrayList<>();
        values.add(world.ball().position().x());
        values.add(world.ball().position().z());
        values.add(world.ball().velocity().x());
        values.add(world.ball().velocity().z());
        for (PinState pin : world.pins()) {
            values.add(pin.position().x());
            values.add(pin.position().z());
            values.add(pin.velocity().x());
            values.add(pin.velocity().z());
            values.add(pin.tiltRadians());
            values.add(pin.angularVelocity());
        }
        assertTrue(values.stream().allMatch(Double::isFinite));
    }
}
