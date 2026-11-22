package net.monacraft.bowling.config;

import net.monacraft.bowling.physics.SimulationConfig;
import org.bukkit.configuration.file.FileConfiguration;

public final class PhysicsConfigLoader {
    private PhysicsConfigLoader() {
    }

    public static SimulationConfig from(FileConfiguration config) {
        double configuredSpinMultiplier = config.getDouble("physics.ball.spin-multiplier", 1.0);
        return new SimulationConfig(
                config.getInt("physics.fixed-hz", 120),
                config.getInt("physics.max-catch-up-steps", 4),
                config.getDouble("physics.ball.radius", 0.1085),
                config.getDouble("physics.ball.mass", 6.8),
                config.getDouble("physics.ball.lane-friction", 0.018),
                config.getDouble("physics.ball.gutter-friction", 0.055),
                config.getDouble("physics.ball.spin-curve-coefficient", 0.012) * configuredSpinMultiplier,
                config.getDouble("physics.ball.spin-decay", 0.18),
                config.getDouble("physics.ball.restitution", 0.30),
                config.getDouble("physics.ball.maximum-speed", 12.0),
                config.getDouble("physics.lane.side-bumper-restitution", 0.72),
                config.getDouble("physics.lane.side-bumper-tangential-retention", 0.96),
                config.getDouble("physics.pin.radius", 0.13),
                config.getDouble("physics.pin.fallen-sweep-radius", 0.24),
                config.getDouble("physics.pin.mass", 2.7),
                config.getDouble("physics.pin.restitution", 0.34),
                config.getDouble("physics.pin.linear-friction", 1.35),
                config.getDouble("physics.pin.angular-damping", 1.4),
                Math.toRadians(config.getDouble("physics.pin.tipping-threshold-degrees", 8.0)),
                Math.toRadians(config.getDouble("physics.pin.down-threshold-degrees", 34.0)),
                config.getDouble("physics.pin.sleep-speed", 0.04),
                config.getInt("physics.pin.sleep-steps", 24),
                config.getInt("physics.collision.maximum-substeps", 12),
                config.getBoolean("physics.collision.continuous-detection", true),
                config.getDouble("physics.collision.broadphase-cell-size", 0.5)
        );
    }
}
