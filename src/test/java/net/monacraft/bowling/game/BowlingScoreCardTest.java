package net.monacraft.bowling.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BowlingScoreCardTest {
    @Test
    void hundredPinPerfectGameScoresThreeThousand() {
        BowlingScoreCard card = new BowlingScoreCard(100);
        for (int roll = 0; roll < 12; roll++) card.recordRoll(100);
        assertTrue(card.complete());
        assertEquals(3000, card.score());
        assertEquals(10, card.strikes());
        assertEquals(10, card.frameScores().size());
    }

    @Test
    void hundredPinSpareBonusesNextRoll() {
        BowlingScoreCard card = new BowlingScoreCard(100);
        card.recordRoll(64);
        card.recordRoll(36);
        card.recordRoll(20);
        card.recordRoll(10);
        for (int frame = 2; frame < 10; frame++) { card.recordRoll(0); card.recordRoll(0); }
        assertEquals(150, card.score());
        assertEquals(1, card.spares());
    }

    @Test
    void hundredPinRollCannotExceedStandingPins() {
        BowlingScoreCard card = new BowlingScoreCard(100);
        card.recordRoll(73);
        assertEquals(27, card.standingPins());
        assertThrows(IllegalArgumentException.class, () -> card.recordRoll(28));
    }
}
