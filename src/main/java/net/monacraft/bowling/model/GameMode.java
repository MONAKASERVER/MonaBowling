package net.monacraft.bowling.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum GameMode {
    STANDARD("standard", "スタンダード"),
    HUNDRED_PIN("hundred-pin", "100ピン"),
    WALL_DODGE("wall-dodge", "かべよけ");

    private final String configKey;
    private final String displayName;

    GameMode(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<GameMode> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', '-');
        // Phase 5 exposed the original working title in lane/DB data even though
        // that mode was not playable. Preserve those lanes while migrating the
        // slot to the completed Japanese "wall dodge" mode.
        if (normalized.equals("spin-control") || normalized.equals("spin")) {
            return Optional.of(WALL_DODGE);
        }
        return Arrays.stream(values())
                .filter(mode -> mode.configKey.equals(normalized) || mode.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
