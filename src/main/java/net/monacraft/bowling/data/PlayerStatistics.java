package net.monacraft.bowling.data;

import net.monacraft.bowling.model.GameMode;

import java.util.UUID;

public record PlayerStatistics(
        UUID playerId,
        String playerName,
        GameMode mode,
        int gamesPlayed,
        int wins,
        int bestScore,
        long totalScore,
        int strikes,
        int spares,
        int gutters
) {
}
