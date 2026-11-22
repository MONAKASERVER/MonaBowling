package net.monacraft.bowling.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameModeTest {
    @Test
    void parsesWallDodgeAndMigratesLegacyWorkingName() {
        assertEquals(GameMode.WALL_DODGE, GameMode.parse("wall-dodge").orElseThrow());
        assertEquals(GameMode.WALL_DODGE, GameMode.parse("wall_dodge").orElseThrow());
        assertEquals(GameMode.WALL_DODGE, GameMode.parse("spin-control").orElseThrow());
    }
}
