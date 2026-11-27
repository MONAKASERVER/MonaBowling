package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Obstacle;
import net.monacraft.bowling.physics.Vec2;

import java.util.ArrayList;
import java.util.List;

/** Converts continuous wall segments into block-centre samples without filling their gaps. */
final class WallBlockSampler {
    private WallBlockSampler() { }

    static List<Double> centres(Obstacle obstacle) {
        double span = obstacle.maximumX() - obstacle.minimumX();
        int blocks = Math.max(1, (int) Math.floor(span));
        List<Double> centres = new ArrayList<>(blocks);
        for (int index = 0; index < blocks; index++) {
            // Sampling segment centres (rather than both AABB edges) prevents
            // the left and right walls from rounding into the opening's cells.
            centres.add(obstacle.minimumX() + span * ((index + 0.5) / blocks));
        }
        return List.copyOf(centres);
    }

    /** Builds a deliberately inset hitbox around one rendered 1x1 block. */
    static Obstacle collisionBox(String id, Vec2 blockCentre, double inset,
                                 double restitution) {
        if (!Double.isFinite(inset) || inset < 0.0 || inset >= 0.5) {
            throw new IllegalArgumentException("block hitbox inset must be in [0, 0.5)");
        }
        double halfExtent = 0.5 - inset;
        return new Obstacle(id,
                blockCentre.x() - halfExtent, blockCentre.x() + halfExtent,
                blockCentre.z() - halfExtent, blockCentre.z() + halfExtent,
                restitution);
    }
}
