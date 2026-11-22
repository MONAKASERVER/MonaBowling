package net.monacraft.bowling.game;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Rack topology helpers. Pin ids follow {@code PinLayouts.standardTen}. */
public final class TenPinRackRules {
    private static final int[][] SUPPORT_EDGES = {
            {0, 1}, {0, 2},
            {1, 3}, {1, 4}, {2, 4}, {2, 5},
            {3, 6}, {3, 7}, {4, 7}, {4, 8}, {5, 8}, {5, 9}
    };

    private TenPinRackRules() {
    }

    /**
     * A split requires the head pin to be down and the remaining rack to have
     * at least two groups with no standing pin connecting them toward the head.
     */
    public static boolean isSplit(Set<Integer> standingPins) {
        Set<Integer> standing = Set.copyOf(standingPins);
        if (standing.contains(0) || standing.size() < 2) return false;
        Set<Integer> visited = new HashSet<>();
        int components = 0;
        for (int pin : standing) {
            if (!visited.add(pin)) continue;
            components++;
            if (components >= 2) return true;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(pin);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                for (int[] edge : SUPPORT_EDGES) {
                    int neighbour = edge[0] == current ? edge[1] : edge[1] == current ? edge[0] : -1;
                    if (neighbour >= 0 && standing.contains(neighbour) && visited.add(neighbour)) {
                        queue.addLast(neighbour);
                    }
                }
            }
        }
        return false;
    }
}
