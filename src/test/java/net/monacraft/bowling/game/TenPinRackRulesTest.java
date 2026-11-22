package net.monacraft.bowling.game;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenPinRackRulesTest {
    @Test
    void detectsSevenTenSplit() {
        assertTrue(TenPinRackRules.isSplit(Set.of(6, 9)));
    }

    @Test
    void headPinStandingIsNeverSplit() {
        assertFalse(TenPinRackRules.isSplit(Set.of(0, 6, 9)));
    }

    @Test
    void standingConnectorKeepsRackTogether() {
        assertFalse(TenPinRackRules.isSplit(Set.of(3, 4, 7)));
    }
}
