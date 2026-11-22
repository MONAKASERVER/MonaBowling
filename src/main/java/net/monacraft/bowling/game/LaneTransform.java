package net.monacraft.bowling.game;

import net.monacraft.bowling.model.LaneDefinition;
import net.monacraft.bowling.model.LanePoint;
import net.monacraft.bowling.physics.Vec2;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.util.Vector;

public final class LaneTransform {
    private final World world;
    private final Vector origin;
    private final Vector forward;
    private final Vector right;
    private final double pinDistance;

    private LaneTransform(World world, Vector origin, Vector forward, Vector right, double pinDistance) {
        this.world = world;
        this.origin = origin;
        this.forward = forward;
        this.right = right;
        this.pinDistance = pinDistance;
    }

    public static LaneTransform from(Server server, LaneDefinition lane) {
        World world = server.getWorld(lane.world());
        if (world == null) {
            throw new IllegalArgumentException("lane world is not loaded: " + lane.world());
        }
        LanePoint spawn = lane.playerSpawn();
        LanePoint pins = lane.pinDeck();
        Vector origin = new Vector(spawn.x(), spawn.y(), spawn.z());
        Vector forward = new Vector(pins.x() - spawn.x(), 0.0, pins.z() - spawn.z());
        double distance = forward.length();
        if (distance < 3.0) {
            throw new IllegalArgumentException("pin deck must be at least 3m from player spawn");
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
        return new LaneTransform(world, origin, forward, right, distance);
    }

    public Location worldPosition(Vec2 local, double yOffset) {
        Vector point = origin.clone()
                .add(right.clone().multiply(local.x()))
                .add(forward.clone().multiply(local.z()));
        return new Location(world, point.getX(), point.getY() + yOffset, point.getZ());
    }

    public Vec2 localPosition(Location location) {
        requireWorld(location);
        Vector relative = location.toVector().subtract(origin);
        return new Vec2(relative.dot(right), relative.dot(forward));
    }

    public Vec2 localDirection(Vector direction) {
        Vector horizontal = direction.clone().setY(0.0);
        if (horizontal.lengthSquared() < 1.0E-10) {
            return new Vec2(0.0, 1.0);
        }
        horizontal.normalize();
        return new Vec2(horizontal.dot(right), horizontal.dot(forward));
    }

    public Vector worldDirection(Vec2 local) {
        return right.clone().multiply(local.x()).add(forward.clone().multiply(local.z()));
    }

    public Location playerSpawn(LaneDefinition lane) {
        LanePoint point = lane.playerSpawn();
        return new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    public World world() { return world; }
    public Vector right() { return right.clone(); }
    public Vector forward() { return forward.clone(); }
    public double pinDistance() { return pinDistance; }

    private void requireWorld(Location location) {
        if (location.getWorld() != world) {
            throw new IllegalArgumentException("location belongs to a different world");
        }
    }
}
