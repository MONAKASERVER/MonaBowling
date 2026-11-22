package net.monacraft.bowling.physics;

import java.util.ArrayList;
import java.util.List;

public final class PinLayouts {
    private PinLayouts() {
    }

    public static List<Vec2> standardTen(Vec2 headPin, double spacing) {
        List<Vec2> positions = new ArrayList<>(10);
        int id = 0;
        for (int row = 0; row < 4; row++) {
            double z = headPin.z() + row * spacing * 0.87;
            double firstX = headPin.x() - row * spacing * 0.5;
            for (int column = 0; column <= row; column++) {
                positions.add(new Vec2(firstX + column * spacing, z));
                id++;
            }
        }
        return List.copyOf(positions);
    }

    /**
     * Production 100-pin rack: the 91-pin 13-row triangle described by Nintendo's
     * Power Throws documentation, plus the centred nine-pin rear row visible in
     * the Resort 100-pin rack. Standard equilateral spacing is preserved.
     */
    public static List<Vec2> resortHundred(Vec2 headPin, double spacing) {
        List<Vec2> positions = new ArrayList<>(100);
        for (int row = 0; row < 14; row++) {
            int count = row < 13 ? row + 1 : 9;
            double z = headPin.z() + row * spacing * Math.sqrt(3.0) / 2.0;
            double firstX = headPin.x() - (count - 1) * spacing * 0.5;
            for (int column = 0; column < count; column++) {
                positions.add(new Vec2(firstX + column * spacing, z));
            }
        }
        return List.copyOf(positions);
    }

    /** A compact deterministic load-test rack retained for physics regression tests. */
    public static List<Vec2> hundredPinStressRack(Vec2 headPin, double spacing) {
        List<Vec2> positions = new ArrayList<>(100);
        int remaining = 100;
        int row = 0;
        while (remaining > 0) {
            int count = Math.min(row + 1, remaining);
            double z = headPin.z() + row * spacing * 0.87;
            double firstX = headPin.x() - (count - 1) * spacing * 0.5;
            for (int column = 0; column < count; column++) {
                positions.add(new Vec2(firstX + column * spacing, z));
            }
            remaining -= count;
            row++;
        }
        return List.copyOf(positions);
    }
}
