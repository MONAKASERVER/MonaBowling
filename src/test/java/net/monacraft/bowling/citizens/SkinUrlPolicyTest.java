package net.monacraft.bowling.citizens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SkinUrlPolicyTest {
    @Test
    void acceptsPublicHttpsPng() {
        assertEquals("https://raw.githubusercontent.com/MONAKASERVER/mona-resource-studio/main/"
                        + "assets/monabowling/bowling-pin.png",
                SkinUrlPolicy.requirePublicPng(
                        "https://raw.githubusercontent.com/MONAKASERVER/mona-resource-studio/main/"
                                + "assets/monabowling/bowling-pin.png"));
    }

    @Test
    void rejectsNonHttpsAndNonPngUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> SkinUrlPolicy.requirePublicPng("http://example.test/skin.png"));
        assertThrows(IllegalArgumentException.class,
                () -> SkinUrlPolicy.requirePublicPng("https://example.test/skin.jpg"));
    }
}
