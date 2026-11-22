package net.monacraft.bowling.crossplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YawInputTrackerTest {
    @Test
    void accumulatesMultipleSamples() {
        YawInputTracker tracker = new YawInputTracker();
        tracker.reset(10.0f);
        tracker.sample(14.0f);
        assertEquals(9.0, tracker.sample(19.0f), 1.0E-9);
    }

    @Test
    void crossesPositiveWraparoundWithoutSpike() {
        YawInputTracker tracker = new YawInputTracker();
        tracker.reset(179.0f);
        assertEquals(2.0, tracker.sample(-179.0f), 1.0E-9);
    }

    @Test
    void crossesNegativeWraparoundWithoutSpike() {
        YawInputTracker tracker = new YawInputTracker();
        tracker.reset(-179.0f);
        assertEquals(-2.0, tracker.sample(179.0f), 1.0E-9);
    }
}
