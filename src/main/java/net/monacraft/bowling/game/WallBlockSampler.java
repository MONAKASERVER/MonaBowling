package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.Obstacle;

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
}
