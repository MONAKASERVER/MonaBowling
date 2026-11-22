package net.monacraft.bowling.physics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class HundredPinBalanceTest {
    @Test
    void ordinaryPowerThrowsDoNotBecomeAutomaticStrikes() {
        SimulationConfig config = SimulationConfig.hubDefaults().forHundredPin();
        List<Vec2> rack = PinLayouts.resortHundred(new Vec2(0, 18.0), 0.46);
        long largestChain = 0;
        for (double speed : new double[]{7.0, 9.0, 11.0, 12.0}) {
            for (double offset : new double[]{-0.35, -0.15, 0.0, 0.15, 0.35}) {
                BallState ball = new BallState(new Vec2(offset, 0.0), new Vec2(0.0, speed), 0.0,
                        config.ballRadius(), config.ballMass());
                SimulationWorld world = new SimulationWorld(config,
                        new LaneGeometry(3.0, 26.0, 0.4), ball,
                        SimulationWorld.createPins(rack, config), List.of());
                MonaBowlingPhysics engine = new MonaBowlingPhysics();
                PhysicsTestSupport.runSteps(engine, world, 7200);
                long down = PhysicsTestSupport.downCount(world);
                System.out.printf("hundred balance speed=%.1f offset=%.2f down=%d%n",
                        speed, offset, down);
                largestChain = Math.max(largestChain, down);
                assertTrue(down < 95,
                        () -> "ordinary shot became near-automatic strike: speed=" + speed
                                + ", offset=" + offset + ", down=" + down);
            }
        }
        assertTrue(largestChain >= 40,
                "100-pin chain reaction became too weak: maximum=" + largestChain);
    }
}
