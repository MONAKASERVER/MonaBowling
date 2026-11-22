package net.monacraft.bowling.config;

import net.monacraft.bowling.model.GameMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

public record BowlingSettings(
        String language,
        Set<GameMode> enabledModes,
        int maximumConcurrentMatches,
        int maximumConcurrentHundredPinMatches,
        int maximumPlayersPerLane,
        int turnTimeoutSeconds,
        int reconnectGraceSeconds,
        int maximumChargeMillis,
        double minimumThrowSpeed,
        double maximumThrowSpeed,
        double spinPerYawDegree,
        double rackSpacing,
        double standardLaneHalfWidth,
        double standardGutterWidth,
        boolean standardSideBumpers,
        double hundredPinRackSpacing,
        double hundredPinLaneHalfWidth,
        double hundredPinGutterWidth,
        boolean hundredPinSideBumpers,
        boolean secretStrikeEnabled,
        double secretRailMinimumX,
        double secretRailMaximumX,
        double secretArmingMaximumZ,
        double secretSwitchOffset,
        double pinDisplacementThreshold,
        int pinsetterResetTicks,
        int maximumDisplayUpdatesPerTick,
        double pinVisualWidth,
        double pinVisualHeight,
        int physicsHz,
        int physicsThreads,
        int maximumCatchUpSteps,
        String databaseFile,
        int databaseBusyTimeoutMillis,
        boolean geyserEnabled,
        boolean alternativeBedrockInput,
        boolean bedrockArmorStandFallback,
        double javaYawSensitivity,
        double bedrockKeyboardSensitivity,
        double bedrockTouchSensitivity,
        double bedrockControllerSensitivity,
        double bedrockUnknownSensitivity,
        double spinDeadzoneDegrees,
        double maximumSpin,
        boolean sneakReleaseSpinEnabled,
        double sneakReleaseMinimumSpin,
        double sneakReleaseMaximumSpin,
        int inputDebounceMillis,
        boolean guideParticles,
        boolean debug
) {
    public BowlingSettings {
        enabledModes = Set.copyOf(enabledModes);
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language must not be blank");
        }
        if (enabledModes.isEmpty()) {
            throw new IllegalArgumentException("at least one game mode must be enabled");
        }
        requireRange(maximumConcurrentMatches, 1, 32, "matches.max-concurrent");
        requireRange(maximumConcurrentHundredPinMatches, 0, maximumConcurrentMatches,
                "matches.max-concurrent-hundred-pin");
        requireRange(maximumPlayersPerLane, 1, 4, "matches.max-players-per-lane");
        requireRange(turnTimeoutSeconds, 10, 300, "matches.turn-timeout-seconds");
        requireRange(reconnectGraceSeconds, 0, 600, "matches.reconnect-grace-seconds");
        requireRange(maximumChargeMillis, 500, 5000, "gameplay.maximum-charge-millis");
        requireFiniteRange(minimumThrowSpeed, 0.5, 20.0, "gameplay.minimum-throw-speed");
        requireFiniteRange(maximumThrowSpeed, minimumThrowSpeed, 30.0, "gameplay.maximum-throw-speed");
        requireFiniteRange(spinPerYawDegree, 0.0, 2.0, "gameplay.spin-per-yaw-degree");
        requireFiniteRange(rackSpacing, 0.20, 0.80, "gameplay.rack-spacing");
        requireFiniteRange(standardLaneHalfWidth, 0.6, 3.0,
                "gameplay.lane-half-width");
        requireFiniteRange(standardGutterWidth, 0.1, 1.0,
                "gameplay.gutter-width");
        if (standardLaneHalfWidth < rackSpacing * 1.5 + 0.14) {
            throw new IllegalArgumentException("gameplay.lane-half-width must contain the four-pin row");
        }
        requireFiniteRange(hundredPinRackSpacing, 0.20, 0.60,
                "hundred-pin.rack-spacing");
        requireFiniteRange(hundredPinLaneHalfWidth, 1.0, 5.0,
                "hundred-pin.lane-half-width");
        requireFiniteRange(hundredPinGutterWidth, 0.1, 1.0,
                "hundred-pin.gutter-width");
        if (hundredPinLaneHalfWidth < hundredPinRackSpacing * 6.0 + 0.08) {
            throw new IllegalArgumentException("hundred-pin.lane-half-width must contain the 13-pin row");
        }
        requireFiniteRange(secretRailMinimumX, hundredPinLaneHalfWidth, 6.0,
                "hundred-pin.secret-strike.rail-minimum-x");
        requireFiniteRange(secretRailMaximumX, secretRailMinimumX + 0.01, 6.0,
                "hundred-pin.secret-strike.rail-maximum-x");
        if (secretRailMaximumX > hundredPinLaneHalfWidth + hundredPinGutterWidth) {
            throw new IllegalArgumentException("secret-strike rail must remain inside the gutter width");
        }
        requireFiniteRange(secretArmingMaximumZ, 0.1, 5.0,
                "hundred-pin.secret-strike.arming-maximum-z");
        requireFiniteRange(secretSwitchOffset, 0.1, 3.0,
                "hundred-pin.secret-strike.switch-offset");
        requireFiniteRange(pinDisplacementThreshold, 0.10, 1.50,
                "gameplay.pin-displacement-threshold");
        requireRange(pinsetterResetTicks, 5, 200, "gameplay.pinsetter-reset-ticks");
        requireRange(maximumDisplayUpdatesPerTick, 16, 1024,
                "rendering.maximum-display-updates-per-tick");
        requireFiniteRange(pinVisualWidth, 0.10, 0.80, "rendering.pin-width");
        requireFiniteRange(pinVisualHeight, 0.25, 1.50, "rendering.pin-height");
        requireRange(physicsHz, 60, 240, "physics.fixed-hz");
        requireRange(physicsThreads, 1, 4, "physics.worker-threads");
        requireRange(maximumCatchUpSteps, 1, 16, "physics.max-catch-up-steps");
        requireRange(databaseBusyTimeoutMillis, 100, 60_000, "database.busy-timeout-millis");
        requireFiniteRange(javaYawSensitivity, 0.1, 3.0, "input.java-yaw-sensitivity");
        requireFiniteRange(bedrockKeyboardSensitivity, 0.1, 3.0,
                "geyser.keyboard-mouse-sensitivity");
        requireFiniteRange(bedrockTouchSensitivity, 0.1, 3.0, "geyser.touch-sensitivity");
        requireFiniteRange(bedrockControllerSensitivity, 0.1, 3.0,
                "geyser.controller-sensitivity");
        requireFiniteRange(bedrockUnknownSensitivity, 0.1, 3.0,
                "geyser.unknown-input-sensitivity");
        requireFiniteRange(spinDeadzoneDegrees, 0.0, 20.0, "input.spin-deadzone-degrees");
        requireFiniteRange(maximumSpin, 1.0, 50.0, "input.maximum-spin");
        requireFiniteRange(sneakReleaseMinimumSpin, 0.0, maximumSpin,
                "input.sneak-release-minimum-spin");
        requireFiniteRange(sneakReleaseMaximumSpin, sneakReleaseMinimumSpin, maximumSpin,
                "input.sneak-release-maximum-spin");
        requireRange(inputDebounceMillis, 0, 1000, "input.debounce-millis");
        if (databaseFile == null || databaseFile.isBlank()
                || databaseFile.contains("/") || databaseFile.contains("\\")) {
            throw new IllegalArgumentException("database.file must be a plain file name");
        }
    }

    public static BowlingSettings from(FileConfiguration config) {
        EnumSet<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (GameMode mode : GameMode.values()) {
            boolean enabled = mode == GameMode.WALL_DODGE
                    && !config.isSet("game-modes.wall-dodge")
                    ? config.getBoolean("game-modes.spin-control", true)
                    : config.getBoolean("game-modes." + mode.configKey(), true);
            if (enabled) {
                modes.add(mode);
            }
        }
        return new BowlingSettings(
                config.getString("language", "ja_JP"),
                modes,
                config.getInt("matches.max-concurrent", 4),
                config.getInt("matches.max-concurrent-hundred-pin", 1),
                config.getInt("matches.max-players-per-lane", 4),
                config.getInt("matches.turn-timeout-seconds", 45),
                config.getInt("matches.reconnect-grace-seconds", 90),
                config.getInt("gameplay.maximum-charge-millis", 1600),
                config.getDouble("gameplay.minimum-throw-speed", 4.0),
                config.getDouble("gameplay.maximum-throw-speed", 12.0),
                config.getDouble("gameplay.spin-per-yaw-degree", 0.16),
                config.getDouble("gameplay.rack-spacing", 0.52),
                config.getDouble("gameplay.lane-half-width", 2.70),
                config.getDouble("gameplay.gutter-width", 0.40),
                config.getBoolean("gameplay.side-bumpers", true),
                config.getDouble("hundred-pin.rack-spacing", 0.46),
                config.getDouble("hundred-pin.lane-half-width", 3.00),
                config.getDouble("hundred-pin.gutter-width", 0.40),
                config.getBoolean("hundred-pin.side-bumpers", true),
                config.getBoolean("hundred-pin.secret-strike.enabled", true),
                config.getDouble("hundred-pin.secret-strike.rail-minimum-x", 3.22),
                config.getDouble("hundred-pin.secret-strike.rail-maximum-x", 3.29),
                config.getDouble("hundred-pin.secret-strike.arming-maximum-z", 1.0),
                config.getDouble("hundred-pin.secret-strike.switch-offset", 0.65),
                config.getDouble("gameplay.pin-displacement-threshold", 0.55),
                config.getInt("gameplay.pinsetter-reset-ticks", 30),
                config.getInt("rendering.maximum-display-updates-per-tick", 256),
                config.getDouble("rendering.pin-width", 0.26),
                config.getDouble("rendering.pin-height", 0.78),
                config.getInt("physics.fixed-hz", 120),
                config.getInt("physics.worker-threads", 1),
                config.getInt("physics.max-catch-up-steps", 4),
                config.getString("database.file", "bowling.db"),
                config.getInt("database.busy-timeout-millis", 5000),
                config.getBoolean("geyser.enabled", true),
                config.getBoolean("geyser.alternative-input", true),
                config.getBoolean("geyser.armor-stand-fallback", true),
                config.getDouble("input.java-yaw-sensitivity", 1.0),
                config.getDouble("geyser.keyboard-mouse-sensitivity", 0.95),
                config.getDouble("geyser.touch-sensitivity", 0.72),
                config.getDouble("geyser.controller-sensitivity", 0.88),
                config.getDouble("geyser.unknown-input-sensitivity", 0.82),
                config.getDouble("input.spin-deadzone-degrees", 1.25),
                config.getDouble("input.maximum-spin", 18.0),
                config.getBoolean("input.sneak-release-spin-enabled", true),
                config.getDouble("input.sneak-release-minimum-spin", 6.0),
                config.getDouble("input.sneak-release-maximum-spin", 16.0),
                config.getInt("input.debounce-millis", 120),
                config.getBoolean("input.guide-particles", true),
                config.getBoolean("debug.enabled", false)
        );
    }

    public boolean sideBumpers(GameMode mode) {
        return mode == GameMode.HUNDRED_PIN ? hundredPinSideBumpers : standardSideBumpers;
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    private static void requireFiniteRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and in [" + minimum + ", " + maximum + "]");
        }
    }
}
