package net.monacraft.bowling.data;

import net.monacraft.bowling.model.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CompletedGame(
        UUID playerId,
        String playerName,
        UUID matchId,
        GameMode mode,
        int score,
        List<Integer> frameScores,
        int strikes,
        int spares,
        int gutters,
        boolean winner,
        Instant completedAt
) {
    public CompletedGame {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mode, "mode");
        frameScores = List.copyOf(frameScores);
        Objects.requireNonNull(completedAt, "completedAt");
        if (score < 0 || strikes < 0 || spares < 0 || gutters < 0) {
            throw new IllegalArgumentException("game counters must not be negative");
        }
    }
}
