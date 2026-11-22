package net.monacraft.bowling.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpinReleaseTest {
    @Test
    void defaultsTowardLaneCentreFromEitherSide() {
        assertTrue(SpinRelease.sneakSpin(0.5, 0.0, 0.8, 6.0, 16.0, 18.0) > 0.0);
        assertTrue(SpinRelease.sneakSpin(0.5, 0.0, -0.8, 6.0, 16.0, 18.0) < 0.0);
    }

    @Test
    void cameraDirectionOverridesAutomaticDirection() {
        assertTrue(SpinRelease.sneakSpin(0.5, -2.0, 0.8, 6.0, 16.0, 18.0) < 0.0);
        assertTrue(SpinRelease.sneakSpin(0.5, 2.0, -0.8, 6.0, 16.0, 18.0) > 0.0);
    }

    @Test
    void laterReleaseProducesMoreSpinAndRespectsCap() {
        double early = Math.abs(SpinRelease.sneakSpin(0.0, 0.0, 0.0, 6.0, 16.0, 18.0));
        double late = Math.abs(SpinRelease.sneakSpin(1.0, 0.0, 0.0, 6.0, 16.0, 18.0));
        assertEquals(6.0, early, 1.0E-9);
        assertEquals(16.0, late, 1.0E-9);
        assertTrue(late > early);
        assertEquals(12.0,
                Math.abs(SpinRelease.sneakSpin(1.0, 0.0, 0.0, 6.0, 16.0, 12.0)), 1.0E-9);
    }
}
