package net.monacraft.bowling.physics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardRackBalanceTest {
    @Test
    void maximumPowerHasARealStrikeWindow() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        long best = 0;
        int strikeLines = 0;
        for (double offset = -0.30; offset <= 0.3001; offset += 0.05) {
            long down = downPins(config, offset, 12.0);
            best = Math.max(best, down);
            if (down == 10) strikeLines++;
        }
        assertEquals(10, best, "maximum power must have at least one strike pocket");
        int strikeWindow = strikeLines;
        assertTrue(strikeWindow >= 2, () -> "strike window was too narrow: " + strikeWindow);
    }

    @Test
    void ordinaryStraightThrowDoesNotClearTheRackAutomatically() {
        SimulationConfig config = SimulationConfig.hubDefaults();
        long down = downPins(config, 0.0, 6.0);
        assertTrue(down >= 2 && down < 10, () -> "ordinary centre throw down pins=" + down);
    }

    private static long downPins(SimulationConfig config, double offset, double speed) {
        List<Vec2> rack = PinLayouts.standardTen(new Vec2(0.0, 2.0), 0.52);
        SimulationWorld world = new SimulationWorld(config,
                new LaneGeometry(2.70, 6.0, 0.40, true),
                new BallState(new Vec2(offset, 0.0), new Vec2(0.0, speed), 0.0,
                        config.ballRadius(), config.ballMass()),
                SimulationWorld.createPins(rack, config), List.of());
        PhysicsTestSupport.runSteps(new MonaBowlingPhysics(), world, 1800);
        return PhysicsTestSupport.downCount(world);
    }
}
