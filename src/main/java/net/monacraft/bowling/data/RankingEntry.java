package net.monacraft.bowling.data;

import java.util.UUID;

public record RankingEntry(int rank, UUID playerId, String playerName, int bestScore, int gamesPlayed) {
}
