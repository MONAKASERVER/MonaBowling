package net.monacraft.bowling.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnRotationTest {
    @Test
    void rotatesAcrossFourActivePlayers() {
        List<Boolean> active = List.of(false, false, false, false);
        assertEquals(1, TurnRotation.nextIncomplete(0, active));
        assertEquals(2, TurnRotation.nextIncomplete(1, active));
        assertEquals(3, TurnRotation.nextIncomplete(2, active));
        assertEquals(0, TurnRotation.nextIncomplete(3, active));
    }

    @Test
    void skipsPlayersWhoseGamesAreComplete() {
        assertEquals(3, TurnRotation.nextIncomplete(0, List.of(false, true, true, false)));
        assertEquals(0, TurnRotation.nextIncomplete(3, List.of(false, true, true, false)));
    }

    @Test
    void reportsNoNextTurnWhenEveryoneIsComplete() {
        assertEquals(-1, TurnRotation.nextIncomplete(2, List.of(true, true, true, true)));
    }
}
