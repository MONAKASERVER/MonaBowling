package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.PhysicsSnapshot;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class RollJudgement {
    private RollJudgement() {
    }

    /**
     * A gutter roll always scores zero, even if a pin was disturbed before the ball
     * crossed into the gutter. A pin that merely slid while remaining upright also
     * remains standing; this keeps scoring aligned with what players can see.
     */
    static Set<Integer> knockedPins(PhysicsSnapshot snapshot,
                                    Map<Integer, Integer> physicsToRack,
                                    Set<Integer> standing) {
        if (snapshot.ball().inGutter()) {
            return Set.of();
        }
        Set<Integer> knocked = new LinkedHashSet<>();
        for (PhysicsSnapshot.PinSnapshot pin : snapshot.pins()) {
            Integer rackId = physicsToRack.get(pin.id());
            if (rackId != null && pin.down() && standing.contains(rackId)) {
                knocked.add(rackId);
            }
        }
        return Set.copyOf(knocked);
    }
}
