package net.monacraft.bowling.physics;

import java.util.ArrayList;
import java.util.List;

public final class SimulationWorld {
    private final SimulationConfig config;
    private final LaneGeometry lane;
    private final BallState ball;
    private final List<PinState> pins;
    private final List<Obstacle> obstacles;
    private long step;
    private boolean settled;

    public SimulationWorld(SimulationConfig config, LaneGeometry lane, BallState ball,
                           List<PinState> pins, List<Obstacle> obstacles) {
        this.config = config;
        this.lane = lane;
        this.ball = ball;
        this.pins = new ArrayList<>(pins.size());
        for (PinState pin : pins) {
            this.pins.add(pin.copy());
        }
        this.obstacles = List.copyOf(obstacles);
    }

    public SimulationConfig config() { return config; }
    public LaneGeometry lane() { return lane; }
    public BallState ball() { return ball; }
    public List<PinState> pins() { return pins; }
    public List<Obstacle> obstacles() { return obstacles; }
    public long step() { return step; }
    public boolean settled() { return settled; }
    void incrementStep() { step++; }
    void settled(boolean value) { settled = value; }

    public PhysicsSnapshot snapshot() {
        PhysicsSnapshot.BallSnapshot ballSnapshot = new PhysicsSnapshot.BallSnapshot(
                ball.previousPosition(), ball.position(), ball.velocity(), ball.spinRadiansPerSecond(),
                ball.rollRadians(), ball.active(), ball.inGutter());
        List<PhysicsSnapshot.PinSnapshot> pinSnapshots = pins.stream()
                .map(pin -> new PhysicsSnapshot.PinSnapshot(
                        pin.id(), pin.previousPosition(), pin.position(), pin.velocity(), pin.tiltDirection(),
                        pin.tiltRadians(), pin.down(), pin.sleeping()))
                .toList();
        return new PhysicsSnapshot(step, step * config.fixedDeltaSeconds(), ballSnapshot, pinSnapshots, settled);
    }

    public static List<PinState> createPins(List<Vec2> positions, SimulationConfig config) {
        List<PinState> pins = new ArrayList<>(positions.size());
        for (int id = 0; id < positions.size(); id++) {
            pins.add(new PinState(id, positions.get(id), config.pinRadius(),
                    config.pinFallenSweepRadius(), config.pinMass()));
        }
        return List.copyOf(pins);
    }
}
