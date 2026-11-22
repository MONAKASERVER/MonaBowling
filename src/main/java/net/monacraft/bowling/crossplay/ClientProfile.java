package net.monacraft.bowling.crossplay;

import java.util.Locale;

public record ClientProfile(
        Edition edition,
        InputKind input,
        String platform,
        String clientVersion,
        String provider
) {
    public ClientProfile {
        platform = normalize(platform, "UNKNOWN");
        clientVersion = normalize(clientVersion, "unknown");
        provider = normalize(provider, "Paper");
    }

    public static ClientProfile javaClient() {
        return new ClientProfile(Edition.JAVA, InputKind.KEYBOARD_MOUSE,
                "JAVA", "unknown", "Paper");
    }

    public boolean bedrock() {
        return edition == Edition.BEDROCK;
    }

    public enum Edition { JAVA, BEDROCK }

    public enum InputKind {
        KEYBOARD_MOUSE, TOUCH, CONTROLLER, MOTION, UNKNOWN;

        public static InputKind fromApiName(String value) {
            String normalized = normalize(value, "UNKNOWN").toUpperCase(Locale.ROOT);
            if (normalized.contains("TOUCH")) return TOUCH;
            if (normalized.contains("GAMEPAD") || normalized.contains("CONTROLLER")) return CONTROLLER;
            if (normalized.contains("MOTION")) return MOTION;
            if (normalized.contains("KEYBOARD") || normalized.contains("MOUSE")) return KEYBOARD_MOUSE;
            return UNKNOWN;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
