package net.monacraft.bowling.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public record WallDodgeSettings(
        boolean sideBumpers,
        double wallThickness,
        double restitution,
        double hitboxInset,
        Material wallMaterial
) {
    public WallDodgeSettings {
        if (!Double.isFinite(wallThickness) || wallThickness < 0.10 || wallThickness > 1.0) {
            throw new IllegalArgumentException("wall-dodge.wall-thickness must be in [0.10, 1.0]");
        }
        if (!Double.isFinite(restitution) || restitution < 0.0 || restitution > 1.0) {
            throw new IllegalArgumentException("wall-dodge.restitution must be in [0, 1]");
        }
        if (!Double.isFinite(hitboxInset) || hitboxInset < 0.0 || hitboxInset > 0.35) {
            throw new IllegalArgumentException("wall-dodge.hitbox-inset must be in [0, 0.35]");
        }
        if (wallMaterial == null || !wallMaterial.isBlock() || wallMaterial.isAir()) {
            throw new IllegalArgumentException("wall-dodge.wall-material must be a visible block material");
        }
    }

    public static WallDodgeSettings from(FileConfiguration config) {
        String materialName = config.getString(
                "wall-dodge.wall-material", "POLISHED_BLACKSTONE_WALL");
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        return new WallDodgeSettings(
                config.getBoolean("wall-dodge.side-bumpers", false),
                config.getDouble("wall-dodge.wall-thickness", 0.34),
                config.getDouble("wall-dodge.restitution", 0.38),
                config.getDouble("wall-dodge.hitbox-inset", 0.24),
                material
        );
    }
}
