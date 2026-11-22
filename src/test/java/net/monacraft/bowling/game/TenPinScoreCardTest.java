package net.monacraft.bowling.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenPinScoreCardTest {
    @Test
    void perfectGameScoresThreeHundred() {
        TenPinScoreCard card = new TenPinScoreCard();
        for (int i = 0; i < 12; i++) card.recordRoll(10);
        assertTrue(card.complete());
        assertEquals(300, card.score());
        assertEquals(10, card.strikes());
        assertEquals(10, card.frameScores().size());
    }

    @Test
    void allSparesWithFiveScoresOneHundredFifty() {
        TenPinScoreCard card = new TenPinScoreCard();
        for (int i = 0; i < 10; i++) { card.recordRoll(5); card.recordRoll(5); }
        card.recordRoll(5);
        assertEquals(150, card.score());
        assertEquals(10, card.spares());
    }

    @Test
    void openGameAndGuttersAreCounted() {
        TenPinScoreCard card = new TenPinScoreCard();
        for (int frame = 0; frame < 10; frame++) { card.recordRoll(9); card.recordRoll(0, true); }
        assertEquals(90, card.score());
        assertEquals(10, card.gutters());
        assertTrue(card.complete());
    }

    @Test
    void strikeAndSpareBonusesUseFollowingRolls() {
        TenPinScoreCard card = new TenPinScoreCard();
        card.recordRoll(10);
        card.recordRoll(7); card.recordRoll(3);
        card.recordRoll(4); card.recordRoll(2);
        for (int frame = 3; frame < 10; frame++) { card.recordRoll(0); card.recordRoll(0); }
        assertEquals(40, card.score());
        assertEquals(List.of(20, 34, 40, 40, 40, 40, 40, 40, 40, 40), card.frameScores());
    }

    @Test
    void tenthFrameLimitsPinsAfterNonStrikeBonusRoll() {
        TenPinScoreCard card = new TenPinScoreCard();
        for (int frame = 0; frame < 9; frame++) { card.recordRoll(0); card.recordRoll(0); }
        card.recordRoll(10);
        card.recordRoll(7);
        assertEquals(3, card.standingPins());
        assertThrows(IllegalArgumentException.class, () -> card.recordRoll(4));
        card.recordRoll(3);
        assertEquals(20, card.score());
    }

    @Test
    void incompleteBonusIsNotScoredEarly() {
        TenPinScoreCard card = new TenPinScoreCard();
        card.recordRoll(10);
        assertEquals(0, card.score());
        assertFalse(card.complete());
    }

    @Test
    void zeroPinMissIsNotAutomaticallyAGutter() {
        TenPinScoreCard card = new TenPinScoreCard();
        card.recordRoll(0, false);
        card.recordRoll(0, true);
        assertEquals(1, card.gutters());
    }
}
