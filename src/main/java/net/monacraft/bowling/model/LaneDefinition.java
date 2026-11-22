package net.monacraft.bowling.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record LaneDefinition(
        String id,
        String world,
        LanePoint laneOrigin,
        LanePoint playerSpawn,
        LanePoint pinDeck,
        Set<GameMode> modes,
        boolean enabled
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    public LaneDefinition {
        id = normalizeId(id);
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
        laneOrigin = Objects.requireNonNull(laneOrigin, "laneOrigin");
        playerSpawn = Objects.requireNonNull(playerSpawn, "playerSpawn");
        pinDeck = Objects.requireNonNull(pinDeck, "pinDeck");
        modes = Set.copyOf(Objects.requireNonNull(modes, "modes"));
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("at least one game mode is required");
        }
    }

    public static String normalizeId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("lane id is required");
        }
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("lane id must match " + ID_PATTERN.pattern());
        }
        return normalized;
    }

    public LaneDefinition withOrigin(LanePoint point) {
        return new LaneDefinition(id, world, point, playerSpawn, pinDeck, modes, enabled);
    }

    public LaneDefinition withPlayerSpawn(LanePoint point) {
        return new LaneDefinition(id, world, laneOrigin, point, pinDeck, modes, enabled);
    }

    public LaneDefinition withPinDeck(LanePoint point) {
        return new LaneDefinition(id, world, laneOrigin, playerSpawn, point, modes, enabled);
    }
}
