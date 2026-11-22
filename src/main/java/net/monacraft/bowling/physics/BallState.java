package net.monacraft.bowling.physics;

public final class BallState {
    private Vec2 previousPosition;
    private Vec2 position;
    private Vec2 velocity;
    private Vec2 acceleration = Vec2.ZERO;
    private final double radius;
    private final double mass;
    private double spinRadiansPerSecond;
    private double rollRadians;
    private boolean active = true;
    private boolean inGutter;

    public BallState(Vec2 position, Vec2 velocity, double spinRadiansPerSecond,
                     double radius, double mass) {
        this.previousPosition = position;
        this.position = position;
        this.velocity = velocity;
        if (!Double.isFinite(spinRadiansPerSecond)) {
            throw new IllegalArgumentException("spin must be finite");
        }
        if (!Double.isFinite(radius) || radius <= 0.0 || !Double.isFinite(mass) || mass <= 0.0) {
            throw new IllegalArgumentException("ball radius and mass must be positive");
        }
        this.spinRadiansPerSecond = spinRadiansPerSecond;
        this.radius = radius;
        this.mass = mass;
    }

    public Vec2 previousPosition() { return previousPosition; }
    public Vec2 position() { return position; }
    public Vec2 velocity() { return velocity; }
    public Vec2 acceleration() { return acceleration; }
    public double radius() { return radius; }
    public double mass() { return mass; }
    public double spinRadiansPerSecond() { return spinRadiansPerSecond; }
    public double rollRadians() { return rollRadians; }
    public boolean active() { return active; }
    public boolean inGutter() { return inGutter; }

    void beginStep() { previousPosition = position; }
    void position(Vec2 value) { position = value; }
    void velocity(Vec2 value) { velocity = value; }
    void acceleration(Vec2 value) { acceleration = value; }
    void spinRadiansPerSecond(double value) { spinRadiansPerSecond = value; }
    void addRollRadians(double value) { rollRadians += value; }
    void active(boolean value) { active = value; }
    void inGutter(boolean value) { inGutter = value; }

    BallState copy() {
        BallState copy = new BallState(position, velocity, spinRadiansPerSecond, radius, mass);
        copy.previousPosition = previousPosition;
        copy.acceleration = acceleration;
        copy.rollRadians = rollRadians;
        copy.active = active;
        copy.inGutter = inGutter;
        return copy;
    }
}
