package net.monacraft.bowling.citizens;

import java.net.URI;

public final class SkinUrlPolicy {
    private SkinUrlPolicy() {
    }

    public static String requirePublicPng(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("citizens.skin-url が未設定です");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("citizens.skin-url が正しいURLではありません", exception);
        }
        String path = uri.getPath();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || path == null
                || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("citizens.skin-url は公開HTTPS PNG URLにしてください");
        }
        return uri.toASCIIString();
    }
}
