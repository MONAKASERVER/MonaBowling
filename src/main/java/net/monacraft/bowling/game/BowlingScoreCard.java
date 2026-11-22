package net.monacraft.bowling.game;

import java.util.ArrayList;
import java.util.List;

/** Ten-frame bowling scoring parameterized by the number of pins in a full rack. */
public class BowlingScoreCard {
    private final int rackSize;
    private final List<Integer> rolls = new ArrayList<>(21);
    private int frameIndex;
    private int rollInFrame;
    private int standingPins;
    private int gutters;
    private boolean complete;

    public BowlingScoreCard(int rackSize) {
        if (rackSize < 1 || rackSize > 1000) throw new IllegalArgumentException("rackSize must be in [1, 1000]");
        this.rackSize = rackSize;
        this.standingPins = rackSize;
    }

    public void recordRoll(int pins) { recordRoll(pins, false); }

    public void recordRoll(int pins, boolean gutter) {
        if (complete) throw new IllegalStateException("game is already complete");
        if (pins < 0 || pins > standingPins) throw new IllegalArgumentException("pins must be in [0, " + standingPins + "]");
        if (gutter && pins != 0) throw new IllegalArgumentException("a gutter roll cannot knock down pins");
        if (gutter) gutters++;
        rolls.add(pins);
        if (frameIndex < 9) recordNormalFrame(pins); else recordTenthFrame(pins);
    }

    private void recordNormalFrame(int pins) {
        if (rollInFrame == 0 && pins == rackSize) {
            frameIndex++;
            standingPins = rackSize;
            return;
        }
        standingPins -= pins;
        if (rollInFrame == 0) rollInFrame = 1;
        else {
            frameIndex++;
            rollInFrame = 0;
            standingPins = rackSize;
        }
    }

    private void recordTenthFrame(int pins) {
        int start = tenthFrameStartIndex();
        int first = rolls.get(start);
        if (rollInFrame == 0) {
            rollInFrame = 1;
            standingPins = first == rackSize ? rackSize : rackSize - first;
            return;
        }
        if (rollInFrame == 1) {
            int second = pins;
            boolean bonusAllowed = first == rackSize || first + second == rackSize;
            if (!bonusAllowed) {
                complete = true;
                standingPins = 0;
                return;
            }
            rollInFrame = 2;
            standingPins = first == rackSize ? (second == rackSize ? rackSize : rackSize - second) : rackSize;
            return;
        }
        complete = true;
        standingPins = 0;
    }

    public int score() {
        List<Integer> scores = frameScores();
        return scores.isEmpty() ? 0 : scores.getLast();
    }

    /** Returns cumulative scores only for frames whose bonuses are already known. */
    public List<Integer> frameScores() {
        List<Integer> cumulative = new ArrayList<>(10);
        int rollIndex = 0;
        int total = 0;
        for (int frame = 0; frame < 10 && rollIndex < rolls.size(); frame++) {
            int first = rolls.get(rollIndex);
            if (frame < 9 && first == rackSize) {
                if (rollIndex + 2 >= rolls.size()) break;
                total += rackSize + rolls.get(rollIndex + 1) + rolls.get(rollIndex + 2);
                cumulative.add(total);
                rollIndex++;
            } else if (frame < 9) {
                if (rollIndex + 1 >= rolls.size()) break;
                int second = rolls.get(rollIndex + 1);
                if (first + second == rackSize) {
                    if (rollIndex + 2 >= rolls.size()) break;
                    total += rackSize + rolls.get(rollIndex + 2);
                } else total += first + second;
                cumulative.add(total);
                rollIndex += 2;
            } else {
                if (!complete) break;
                int frameTotal = 0;
                while (rollIndex < rolls.size()) frameTotal += rolls.get(rollIndex++);
                total += frameTotal;
                cumulative.add(total);
            }
        }
        return List.copyOf(cumulative);
    }

    public List<Integer> rolls() { return List.copyOf(rolls); }
    public int rackSize() { return rackSize; }
    public int currentFrameNumber() { return Math.min(frameIndex + 1, 10); }
    public int rollInFrame() { return rollInFrame; }
    public int standingPins() { return standingPins; }
    public boolean complete() { return complete; }
    public boolean needsFullRack() { return standingPins == rackSize; }

    public int strikes() {
        int count = 0;
        int index = 0;
        for (int frame = 0; frame < 10 && index < rolls.size(); frame++) {
            if (rolls.get(index) == rackSize) count++;
            if (frame < 9) index += rolls.get(index) == rackSize ? 1 : 2; else break;
        }
        return count;
    }

    public int spares() {
        int count = 0;
        int index = 0;
        for (int frame = 0; frame < 10 && index < rolls.size(); frame++) {
            int first = rolls.get(index);
            if (frame < 9) {
                if (first != rackSize && index + 1 < rolls.size() && first + rolls.get(index + 1) == rackSize) count++;
                index += first == rackSize ? 1 : 2;
            } else {
                if (first != rackSize && index + 1 < rolls.size() && first + rolls.get(index + 1) == rackSize) count++;
                break;
            }
        }
        return count;
    }

    public int gutters() { return gutters; }

    private int tenthFrameStartIndex() {
        int index = 0;
        for (int frame = 0; frame < 9; frame++) index += rolls.get(index) == rackSize ? 1 : 2;
        return index;
    }
}
