package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Vec2;

final class ApproachBounds {
    private ApproachBounds() {
    }

    /** Local z=0 is the /bowling admin setspawn foul line. */
    static Vec2 clampToFoulLine(Vec2 position) {
        if (position.z() <= 0.0) {
            return position;
        }
        return new Vec2(position.x(), 0.0);
    }
}
