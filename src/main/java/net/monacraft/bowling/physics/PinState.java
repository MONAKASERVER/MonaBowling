package net.monacraft.bowling.physics;

public final class PinState {
    private final int id;
    private final Vec2 rackPosition;
    private Vec2 previousPosition;
    private Vec2 position;
    private Vec2 velocity = Vec2.ZERO;
    private Vec2 tiltDirection = new Vec2(0.0, 1.0);
    private final double radius;
    private final double fallenSweepRadius;
    private final double mass;
    private final double inertia;
    private double tiltRadians;
    private double angularVelocity;
    private boolean down;
    private boolean sleeping;
    private int quietSteps;

    public PinState(int id, Vec2 position, double radius, double mass) {
        this(id, position, radius, 0.14, mass);
    }

    public PinState(int id, Vec2 position, double radius, double fallenSweepRadius, double mass) {
        if (id < 0) {
            throw new IllegalArgumentException("pin id must not be negative");
        }
        if (!Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(fallenSweepRadius) || fallenSweepRadius <= 0.0
                || !Double.isFinite(mass) || mass <= 0.0) {
            throw new IllegalArgumentException("pin radius and mass must be positive");
        }
        this.id = id;
        this.rackPosition = position;
        this.previousPosition = position;
        this.position = position;
        this.radius = radius;
        this.fallenSweepRadius = fallenSweepRadius;
        this.mass = mass;
        this.inertia = 0.25 * mass * 0.38 * 0.38 + 0.5 * mass * radius * radius;
    }

    public int id() { return id; }
    public Vec2 rackPosition() { return rackPosition; }
    public Vec2 previousPosition() { return previousPosition; }
    public Vec2 position() { return position; }
    public Vec2 velocity() { return velocity; }
    public Vec2 tiltDirection() { return tiltDirection; }
    public double radius() { return radius; }
    public double collisionRadius() {
        return radius + Math.sin(Math.min(Math.PI / 2.0, tiltRadians)) * fallenSweepRadius;
    }
    public double mass() { return mass; }
    public double inertia() { return inertia; }
    public double tiltRadians() { return tiltRadians; }
    public double angularVelocity() { return angularVelocity; }
    public boolean down() { return down; }
    public boolean sleeping() { return sleeping; }
    public int quietSteps() { return quietSteps; }

    Vec2 contactVelocity() {
        return velocity.add(tiltDirection.multiply(Math.abs(angularVelocity) * collisionRadius()));
    }

    void beginStep() { previousPosition = position; }
    void position(Vec2 value) { position = value; }
    void velocity(Vec2 value) { velocity = value; }
    void tiltDirection(Vec2 value) { tiltDirection = value.normalizedOr(tiltDirection); }
    void tiltRadians(double value) { tiltRadians = value; }
    void angularVelocity(double value) { angularVelocity = value; }
    void down(boolean value) { down = value; }
    void sleeping(boolean value) { sleeping = value; }
    void quietSteps(int value) { quietSteps = value; }
    void wake() { sleeping = false; quietSteps = 0; }

    PinState copy() {
        PinState copy = new PinState(id, rackPosition, radius, fallenSweepRadius, mass);
        copy.previousPosition = previousPosition;
        copy.position = position;
        copy.velocity = velocity;
        copy.tiltDirection = tiltDirection;
        copy.tiltRadians = tiltRadians;
        copy.angularVelocity = angularVelocity;
        copy.down = down;
        copy.sleeping = sleeping;
        copy.quietSteps = quietSteps;
        return copy;
    }
}
