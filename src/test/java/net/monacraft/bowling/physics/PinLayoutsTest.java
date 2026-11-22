package net.monacraft.bowling.physics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinLayoutsTest {
    @Test
    void resortHundredUsesSymmetricFourteenRowTriangle() {
        List<Vec2> pins = PinLayouts.resortHundred(new Vec2(0, 10), 0.46);
        assertEquals(100, pins.size());
        assertEquals(new Vec2(0, 10), pins.getFirst());
        double centre = pins.stream().mapToDouble(Vec2::x).sum();
        assertEquals(0.0, centre, 1.0E-10);
        assertTrue(pins.getLast().z() > pins.getFirst().z());
        assertEquals(14, pins.stream().map(Vec2::z).distinct().count());
        double rearZ = pins.getLast().z();
        assertEquals(9, pins.stream().filter(pin -> pin.z() == rearZ).count());
    }
}
