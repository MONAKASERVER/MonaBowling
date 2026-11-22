package net.monacraft.bowling.game;

import net.monacraft.bowling.physics.PhysicsSnapshot;
import net.monacraft.bowling.physics.Obstacle;
import net.monacraft.bowling.physics.Vec2;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BowlingRenderer implements AutoCloseable {
    private static final BlockData PIN_BLOCK = Material.BONE_BLOCK.createBlockData();
    private static final BlockData SWITCH_BLOCK = Material.REDSTONE_BLOCK.createBlockData();
    // A GUI-transformed Heart of the Sea is a round blue sprite on both vanilla clients.
    // It avoids the previous black-concrete cube without requiring a resource pack.
    private static final ItemStack BALL_ITEM = new ItemStack(Material.HEART_OF_THE_SEA);

    private final LaneTransform transform;
    private final List<Vec2> rack;
    private final RenderingProfiler profiler;
    private final boolean armorStandFallback;
    private final int maximumUpdatesPerTick;
    private final float pinWidth;
    private final float pinHeight;
    private final Material wallMaterial;
    private final Map<Integer, Entity> pins = new HashMap<>();
    private final Map<Integer, VisualState> lastPinStates = new HashMap<>();
    private final List<Entity> secretSwitches = new java.util.ArrayList<>();
    private final Map<Block, BlockData> obstacleBlocks = new HashMap<>();
    private Entity ball;

    BowlingRenderer(LaneTransform transform, List<Vec2> rack, RenderingProfiler profiler,
                    boolean armorStandFallback, int maximumUpdatesPerTick,
                    double pinWidth, double pinHeight, Material wallMaterial) {
        this.transform = transform;
        this.rack = rack;
        this.profiler = profiler;
        this.armorStandFallback = armorStandFallback;
        this.maximumUpdatesPerTick = maximumUpdatesPerTick;
        this.pinWidth = (float) pinWidth;
        this.pinHeight = (float) pinHeight;
        this.wallMaterial = wallMaterial;
    }

    void resetRack(Set<Integer> standing) {
        for (Integer rackId : List.copyOf(pins.keySet())) {
            if (!standing.contains(rackId)) {
                pins.remove(rackId).remove();
                lastPinStates.remove(rackId);
            }
        }
        for (int rackId : standing) {
            Vec2 position = rack.get(rackId);
            Entity visual = pins.get(rackId);
            if (visual == null || !visual.isValid()) {
                visual = armorStandFallback
                        ? spawnBedrockItemVisual(bedrockItemLocation(position, false), Material.BONE_BLOCK)
                        : spawnDisplay(transform.worldPosition(position, 0.0), PIN_BLOCK);
                pins.put(rackId, visual);
            } else {
                visual.teleport(armorStandFallback
                        ? bedrockItemLocation(position, false) : transform.worldPosition(position, 0.0));
            }
            updatePinVisual(visual, 0.0, new Vec2(0, 1));
            lastPinStates.put(rackId, new VisualState(position, 0.0));
        }
    }

    void spawnBall(Vec2 position) {
        removeBall();
        ball = armorStandFallback
                ? spawnBedrockItemVisual(bedrockItemLocation(position, true), Material.HEART_OF_THE_SEA)
                : spawnBallDisplay(transform.worldPosition(position, 0.11));
        updateBallVisual(ball, 0.0);
    }

    void showSecretSwitch(double railX, double switchZ) {
        secretSwitches.forEach(Entity::remove);
        secretSwitches.clear();
        for (double side : new double[]{-1.0, 1.0}) {
            Vec2 position = new Vec2(side * railX, switchZ);
            Entity visual = armorStandFallback
                    ? spawnBedrockItemVisual(bedrockItemLocation(position, false).add(0.0, 0.08, 0.0),
                    Material.REDSTONE_BLOCK)
                    : spawnSwitchDisplay(transform.worldPosition(position, 0.08));
            secretSwitches.add(visual);
        }
    }

    /**
     * Uses real, temporary blocks rather than display entities. This deliberately
     * makes wall visibility independent of Geyser's display-entity translation.
     * Only air is replaced and every touched block is restored on stage change
     * or session cleanup.
     */
    void showObstacles(List<Obstacle> obstacles) {
        clearObstacles();
        for (Obstacle obstacle : obstacles) {
            double centreZ = (obstacle.minimumZ() + obstacle.maximumZ()) * 0.5;
            for (double x : WallBlockSampler.centres(obstacle)) {
                Block block = transform.worldPosition(new Vec2(x, centreZ), 0.12).getBlock();
                if (!block.getType().isAir() || obstacleBlocks.containsKey(block)) continue;
                obstacleBlocks.put(block, block.getBlockData().clone());
                block.setType(wallMaterial, false);
            }
        }
    }

    void update(PhysicsSnapshot snapshot, Map<Integer, Integer> physicsToRack) {
        long started = System.nanoTime();
        int updates = 0;
        PhysicsSnapshot interpolated = snapshot.interpolated(0.5);
        if (ball != null && ball.isValid() && updates < maximumUpdatesPerTick) {
            ball.teleport(armorStandFallback
                    ? bedrockItemLocation(interpolated.ball().position(), true)
                    : transform.worldPosition(interpolated.ball().position(), 0.11));
            updateBallVisual(ball, interpolated.ball().rollRadians());
            updates++;
        }
        for (PhysicsSnapshot.PinSnapshot pin : interpolated.pins()) {
            Integer rackId = physicsToRack.get(pin.id());
            Entity visual = rackId == null ? null : pins.get(rackId);
            if (visual == null || !visual.isValid()) continue;
            VisualState previous = lastPinStates.get(rackId);
            if (previous != null && pin.position().subtract(previous.position()).lengthSquared() < 1.0E-7
                    && Math.abs(pin.tiltRadians() - previous.tilt()) < 1.0E-4) continue;
            if (updates >= maximumUpdatesPerTick) break;
            visual.teleport(armorStandFallback
                    ? bedrockItemLocation(pin.position(), false)
                    : transform.worldPosition(pin.position(), 0.0));
            updatePinVisual(visual, pin.tiltRadians(), pin.tiltDirection());
            lastPinStates.put(rackId, new VisualState(pin.position(), pin.tiltRadians()));
            updates++;
        }
        profiler.record(System.nanoTime() - started, updates);
    }

    void beginPinsetter() {
        for (Entity visual : pins.values()) {
            if (!visual.isValid()) continue;
            if (visual instanceof BlockDisplay display) display.setInterpolationDuration(10);
            visual.teleport(visual.getLocation().add(0.0, 1.2, 0.0));
        }
    }

    void playSecretStrikeCollapse(Set<Integer> standing) {
        World world = transform.world();
        int particleBudget = 0;
        for (int rackId : standing) {
            Entity visual = pins.get(rackId);
            if (visual == null || !visual.isValid()) continue;
            Vec2 position = rack.get(rackId);
            Vec2 direction = new Vec2(position.x(), 0.35).normalizedOr(new Vec2(0, 1));
            Vec2 scattered = position.add(direction.multiply(0.18 + (rackId % 5) * 0.025));
            visual.teleport(armorStandFallback
                    ? bedrockItemLocation(scattered, false).add(0.0, 0.12 + (rackId % 3) * 0.04, 0.0)
                    : transform.worldPosition(scattered, 0.12 + (rackId % 3) * 0.04));
            updatePinVisual(visual, Math.PI / 2.0, direction);
            lastPinStates.put(rackId, new VisualState(scattered, Math.PI / 2.0));
            if (particleBudget++ % 4 == 0) {
                world.spawnParticle(Particle.END_ROD, transform.worldPosition(position, 0.25),
                        2, 0.08, 0.08, 0.08, 0.015);
            }
        }
        if (!rack.isEmpty()) {
            Location centre = transform.worldPosition(rack.get(rack.size() / 2), 0.3);
            world.spawnParticle(Particle.FLASH, centre, 1);
            world.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.35f);
            world.playSound(centre, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.7f);
        }
    }

    void removeBall() {
        if (ball != null) {
            ball.remove();
            ball = null;
        }
    }

    boolean usingArmorStandFallback() {
        return armorStandFallback;
    }

    private BlockDisplay spawnDisplay(Location location, BlockData data) {
        World world = location.getWorld();
        return world.spawn(location, BlockDisplay.class, display -> {
            display.setBlock(data);
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(2);
            configureEntity(display);
        });
    }

    private BlockDisplay spawnSwitchDisplay(Location location) {
        World world = location.getWorld();
        return world.spawn(location, BlockDisplay.class, display -> {
            display.setBlock(SWITCH_BLOCK);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(new Transformation(
                    new Vector3f(-0.09f, 0.0f, -0.09f), new Quaternionf(),
                    new Vector3f(0.18f, 0.12f, 0.18f), new Quaternionf()));
            configureEntity(display);
        });
    }

    private Item spawnBedrockItemVisual(Location location, Material material) {
        return location.getWorld().dropItem(location, new ItemStack(material), item -> {
            item.setGravity(false);
            item.setVelocity(new Vector());
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setUnlimitedLifetime(true);
            configureEntity(item);
        });
    }

    private ItemDisplay spawnBallDisplay(Location location) {
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(BALL_ITEM.clone());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            display.setBillboard(Display.Billboard.CENTER);
            display.setInterpolationDuration(2);
            display.setTeleportDuration(2);
            configureEntity(display);
        });
    }

    private static void configureEntity(Entity entity) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.addScoreboardTag("monabowling_visual");
        entity.addScoreboardTag("monabowling_" + (entity instanceof Item ? "bedrock" : "java"));
    }

    private void updatePinVisual(Entity visual, double tilt, Vec2 localDirection) {
        if (visual instanceof BlockDisplay display) {
            display.setTransformation(pinTransformation(tilt, localDirection));
        } else if (visual instanceof Item item) {
            Material material = tilt >= Math.toRadians(24.0) ? Material.BONE : Material.BONE_BLOCK;
            if (item.getItemStack().getType() != material) {
                item.setItemStack(new ItemStack(material));
            }
        }
    }

    private void updateBallVisual(Entity visual, double roll) {
        if (visual instanceof ItemDisplay display) {
            display.setTransformation(ballTransformation(roll));
        } else if (visual instanceof Item item) {
            item.setVelocity(new Vector());
        }
    }

    private Location bedrockItemLocation(Vec2 position, boolean ballVisual) {
        return transform.worldPosition(position, ballVisual ? 0.18 : 0.22);
    }

    private Transformation pinTransformation(double tilt, Vec2 localDirection) {
        Vector direction = transform.worldDirection(localDirection).normalize();
        Vector axis = new Vector(direction.getZ(), 0.0, -direction.getX()).normalize();
        Quaternionf rotation = new Quaternionf().rotateAxis((float) tilt,
                (float) axis.getX(), (float) axis.getY(), (float) axis.getZ());
        float floorLift = (float) (pinWidth * 0.5 * Math.abs(Math.sin(tilt)));
        return new Transformation(
                new Vector3f(-pinWidth / 2.0f, floorLift, -pinWidth / 2.0f),
                rotation,
                new Vector3f(pinWidth, pinHeight, pinWidth),
                new Quaternionf()
        );
    }

    private Transformation ballTransformation(double roll) {
        Quaternionf rotation = new Quaternionf().rotateZ((float) roll);
        return new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                rotation,
                new Vector3f(0.34f, 0.34f, 0.34f),
                new Quaternionf()
        );
    }

    private void removePins() {
        pins.values().forEach(Entity::remove);
        pins.clear();
        lastPinStates.clear();
    }

    private void clearObstacles() {
        for (Map.Entry<Block, BlockData> entry : obstacleBlocks.entrySet()) {
            Block block = entry.getKey();
            if (block.getType() == wallMaterial) {
                block.setBlockData(entry.getValue(), false);
            }
        }
        obstacleBlocks.clear();
    }

    @Override
    public void close() {
        removeBall();
        removePins();
        clearObstacles();
        secretSwitches.forEach(Entity::remove);
        secretSwitches.clear();
    }

    private record VisualState(Vec2 position, double tilt) { }
}
