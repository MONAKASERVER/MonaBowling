package net.monacraft.bowling.crossplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientProfileTest {
    @Test
    void mapsOfficialInputNames() {
        assertEquals(ClientProfile.InputKind.TOUCH, ClientProfile.InputKind.fromApiName("TOUCH"));
        assertEquals(ClientProfile.InputKind.CONTROLLER, ClientProfile.InputKind.fromApiName("GAMEPAD"));
        assertEquals(ClientProfile.InputKind.KEYBOARD_MOUSE,
                ClientProfile.InputKind.fromApiName("KEYBOARD_MOUSE"));
        assertEquals(ClientProfile.InputKind.UNKNOWN, ClientProfile.InputKind.fromApiName(null));
    }

    @Test
    void javaFallbackIsNotBedrock() {
        ClientProfile profile = ClientProfile.javaClient();
        assertFalse(profile.bedrock());
        assertEquals("Paper", profile.provider());
    }

    @Test
    void bedrockProfileRetainsProviderMetadata() {
        ClientProfile profile = new ClientProfile(ClientProfile.Edition.BEDROCK,
                ClientProfile.InputKind.TOUCH, "ANDROID", "1.26.70", "Floodgate");
        assertTrue(profile.bedrock());
        assertEquals("ANDROID", profile.platform());
    }
}
