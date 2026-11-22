package net.monacraft.bowling.game;

import java.util.List;

final class TurnRotation {
    private TurnRotation() {
    }

    static int nextIncomplete(int current, List<Boolean> completed) {
        if (completed.isEmpty() || current < 0 || current >= completed.size()) return -1;
        for (int offset = 1; offset <= completed.size(); offset++) {
            int candidate = (current + offset) % completed.size();
            if (!completed.get(candidate)) return candidate;
        }
        return -1;
    }
}
