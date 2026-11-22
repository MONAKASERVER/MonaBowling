package net.monacraft.bowling.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MonaBowlingPhysics {
    private static final double GRAVITY = 9.80665;
    private static final double BALL_STOP_SPEED = 0.035;
    private static final double POSITION_EPSILON = 1.0E-5;
    private static final double BALL_PIN_ANGULAR_TRANSFER = 0.025;
    private static final double PIN_PIN_ANGULAR_TRANSFER = 0.022;
    private static final double PIN_BOUNDARY_RESTITUTION = 0.08;
    private static final double PIN_DECK_TRAVEL = 1.50;

    private final PhysicsProfiler profiler = new PhysicsProfiler();

    public StepResult step(SimulationWorld world) {
        if (world.settled()) {
            return new StepResult(world.snapshot(), List.of());
        }
        long started = System.nanoTime();
        List<PhysicsEvent> events = new ArrayList<>();
        MutableCounters counters = new MutableCounters();
        SimulationConfig config = world.config();
        BallState ball = world.ball();

        ball.beginStep();
        world.pins().forEach(PinState::beginStep);

        int subSteps = calculateSubSteps(world);
        double delta = config.fixedDeltaSeconds() / subSteps;
        for (int subStep = 0; subStep < subSteps; subStep++) {
            Vec2 ballStart = ball.position();
            List<Vec2> pinStarts = world.pins().stream().map(PinState::position).toList();

            integrateBall(ball, world.lane(), config, delta, world.step(), events);
            confineBall(ball, world.lane(), world.step(), events);
            integratePins(world.pins(), world.lane(), config, delta, world.step(), events);
            resolveObstacles(ball, ballStart, world.obstacles(), delta, world.step(), events, counters);

            if (ball.active() && !ball.inGutter()) {
                for (int i = 0; i < world.pins().size(); i++) {
                    PinState pin = world.pins().get(i);
                    // Once a pin is down its centre of mass is below the ball's
                    // centre. Treating it as another upright cylinder causes the
                    // ball to hit the same fallen pin repeatedly and stop dead.
                    // Pin-to-pin contacts remain active for visible chain reactions.
                    if (pin.down() || pin.tiltRadians() >= config.pinDownThreshold() * 0.65) {
                        continue;
                    }
                    counters.checks++;
                    double impulse = resolveBallPin(ball, ballStart, pin, pinStarts.get(i), delta, config);
                    if (impulse > 0.0) {
                        counters.resolved++;
                        events.add(new PhysicsEvent(PhysicsEvent.Type.BALL_PIN_COLLISION, world.step(),
                                pin.id(), -1, null, impulse));
                    }
                }
            }
            resolvePinPairs(world.pins(), pinStarts, delta, config, world.step(), events, counters);
            confinePins(world.pins(), world.lane());
        }

        updateSleeping(world.pins(), config);
        if (ball.active() && (world.lane().isOutOfBounds(ball.position(), ball.radius())
                || ball.velocity().length() < BALL_STOP_SPEED)) {
            ball.active(false);
            ball.velocity(Vec2.ZERO);
            events.add(new PhysicsEvent(PhysicsEvent.Type.BALL_STOPPED, world.step(), -1, -1,
                    null, 0.0));
        }

        boolean pinsAtRest = world.pins().stream().allMatch(pin -> pin.sleeping()
                || (pin.velocity().length() < config.pinSleepSpeed()
                && Math.abs(pin.angularVelocity()) < 0.05));
        world.settled(!ball.active() && pinsAtRest);
        world.incrementStep();

        long elapsed = System.nanoTime() - started;
        profiler.record(elapsed, counters.checks, counters.resolved, subSteps);
        return new StepResult(world.snapshot(), List.copyOf(events));
    }

    public PhysicsProfiler.ProfileSnapshot profile() {
        return profiler.snapshot();
    }

    private static int calculateSubSteps(SimulationWorld world) {
        SimulationConfig config = world.config();
        if (!config.continuousCollisionDetection()) {
            return 1;
        }
        double maximumSpeed = world.ball().active() ? world.ball().velocity().length() : 0.0;
        for (PinState pin : world.pins()) {
            maximumSpeed = Math.max(maximumSpeed, pin.velocity().length());
        }
        double minimumRadius = Math.min(config.ballRadius(), config.pinRadius());
        double displacement = maximumSpeed * config.fixedDeltaSeconds();
        int required = (int) Math.ceil(displacement / Math.max(minimumRadius * 0.45, 1.0E-6));
        return Math.max(1, Math.min(config.maximumSubSteps(), required));
    }

    private static void integrateBall(BallState ball, LaneGeometry lane, SimulationConfig config,
                                      double delta, long step, List<PhysicsEvent> events) {
        if (!ball.active()) {
            ball.acceleration(Vec2.ZERO);
            return;
        }
        Vec2 velocity = ball.velocity();
        double speed = velocity.length();
        Vec2 acceleration = Vec2.ZERO;
        if (!ball.inGutter() && speed > 1.0E-8) {
            acceleration = velocity.normalizedOr(Vec2.ZERO).perpendicularLeft()
                    .multiply(ball.spinRadiansPerSecond() * speed * config.spinCurveCoefficient());
        }
        velocity = velocity.add(acceleration.multiply(delta)).clampLength(config.maximumBallSpeed());
        double friction = ball.inGutter() ? config.gutterFriction() : config.ballFriction();
        velocity = decelerate(velocity, friction * GRAVITY * delta);
        Vec2 movement = velocity.multiply(delta);
        ball.position(ball.position().add(movement));
        ball.velocity(velocity);
        ball.acceleration(acceleration);
        ball.addRollRadians(movement.length() / ball.radius());
        ball.spinRadiansPerSecond(ball.spinRadiansPerSecond()
                * Math.exp(-config.spinDecay() * delta));

        if (lane.sideBumpersEnabled() && lane.hasSecretRail()) {
            double bumperLimit = lane.innerBumperCentreLimit(ball.radius());
            boolean crossedFromLane = Math.abs(ball.previousPosition().x()) <= bumperLimit
                    && Math.abs(ball.position().x()) > bumperLimit;
            if (!ball.inGutter() && lane.isOnSecretRail(ball.position())) {
                // The ball was deliberately placed on the narrow wall-top route.
                // Leaving either side drops it to the slab gutter permanently.
            } else if (!ball.inGutter() && crossedFromLane) {
                resolveSideBumper(ball, lane, config, step, events, bumperLimit);
            } else if (!ball.inGutter() && lane.isInGutter(ball.position(), ball.radius())) {
                ball.inGutter(true);
                events.add(new PhysicsEvent(PhysicsEvent.Type.GUTTER_ENTERED, step, -1, -1,
                        "slab-gutter", Math.abs(ball.position().x())));
            }
        } else if (lane.sideBumpersEnabled()) {
            resolveSideBumper(ball, lane, config, step, events,
                    lane.outerWallCentreLimit(ball.radius()));
        } else if (!ball.inGutter() && lane.isInGutter(ball.position(), ball.radius())) {
            ball.inGutter(true);
            events.add(new PhysicsEvent(PhysicsEvent.Type.GUTTER_ENTERED, step, -1, -1,
                    null, Math.abs(ball.position().x())));
        }
    }

    private static void resolveSideBumper(BallState ball, LaneGeometry lane,
                                          SimulationConfig config, long step,
                                          List<PhysicsEvent> events, double limit) {
        Vec2 position = ball.position();
        Vec2 velocity = ball.velocity();
        boolean right = position.x() > limit;
        boolean left = position.x() < -limit;
        if (!right && !left) {
            return;
        }

        double incoming = Math.abs(velocity.x());
        double reflectedX = right
                ? -Math.abs(velocity.x()) * config.sideBumperRestitution()
                : Math.abs(velocity.x()) * config.sideBumperRestitution();
        ball.position(new Vec2(right ? limit - POSITION_EPSILON : -limit + POSITION_EPSILON,
                position.z()));
        ball.velocity(new Vec2(reflectedX,
                velocity.z() * config.sideBumperTangentialRetention()));
        ball.spinRadiansPerSecond(ball.spinRadiansPerSecond() * 0.65);
        ball.inGutter(false);
        events.add(new PhysicsEvent(PhysicsEvent.Type.BUMPER_COLLISION, step, -1, -1,
                right ? "right-bumper" : "left-bumper", incoming * ball.mass()));
    }

    private static void integratePins(List<PinState> pins, LaneGeometry lane,
                                      SimulationConfig config, double delta,
                                      long step, List<PhysicsEvent> events) {
        for (PinState pin : pins) {
            if (pin.sleeping()) {
                continue;
            }
            Vec2 velocity = decelerate(pin.velocity(), config.pinLinearFriction() * delta);
            pin.position(pin.position().add(velocity.multiply(delta)));
            pin.velocity(velocity);

            double angular = pin.angularVelocity();
            double tilt = pin.tiltRadians();
            if (!pin.down()) {
                if (tilt >= config.pinTippingThreshold()) {
                    angular += 14.0 * Math.sin(tilt) * delta;
                } else {
                    angular -= 18.0 * tilt * delta;
                }
            }
            angular *= Math.exp(-config.pinAngularDamping() * delta);
            tilt = Math.max(0.0, Math.min(Math.PI / 2.0, tilt + angular * delta));
            pin.angularVelocity(tilt >= Math.PI / 2.0 - 1.0E-4 ? 0.0 : angular);
            pin.tiltRadians(tilt);
            if (!pin.down() && tilt >= config.pinDownThreshold()) {
                pin.down(true);
                events.add(new PhysicsEvent(PhysicsEvent.Type.PIN_DOWN, step, pin.id(), -1,
                        null, tilt));
            }
        }
        confinePins(pins, lane);
    }

    private static void confineBall(BallState ball, LaneGeometry lane, long step,
                                    List<PhysicsEvent> events) {
        if (!ball.active()) {
            return;
        }
        double xLimit = Math.max(0.05, lane.halfWidth() + lane.gutterWidth() - ball.radius());
        double zMinimum = -1.0;
        double zMaximum = lane.length() + 1.5;
        Vec2 position = ball.position();
        if (Math.abs(position.x()) <= xLimit
                && position.z() >= zMinimum && position.z() <= zMaximum) {
            return;
        }
        ball.position(new Vec2(
                Math.max(-xLimit, Math.min(xLimit, position.x())),
                Math.max(zMinimum, Math.min(zMaximum, position.z()))));
        ball.velocity(Vec2.ZERO);
        ball.active(false);
        events.add(new PhysicsEvent(PhysicsEvent.Type.BALL_STOPPED, step, -1, -1,
                null, 0.0));
    }

    private static void confinePins(List<PinState> pins, LaneGeometry lane) {
        double outerHalfWidth = lane.halfWidth() + lane.gutterWidth();
        for (PinState pin : pins) {
            double radius = Math.min(pin.collisionRadius(), outerHalfWidth * 0.45);
            double xLimit = Math.max(0.05, outerHalfWidth - radius);
            double zMinimum = Math.max(-1.0, pin.rackPosition().z() - PIN_DECK_TRAVEL);
            double zMaximum = Math.min(lane.length() + 1.0,
                    pin.rackPosition().z() + PIN_DECK_TRAVEL);
            Vec2 position = pin.position();
            Vec2 velocity = pin.velocity();
            double x = position.x();
            double z = position.z();
            boolean hit = false;

            if (x > xLimit) {
                x = xLimit;
                velocity = new Vec2(-Math.abs(velocity.x()) * PIN_BOUNDARY_RESTITUTION,
                        velocity.z());
                hit = true;
            } else if (x < -xLimit) {
                x = -xLimit;
                velocity = new Vec2(Math.abs(velocity.x()) * PIN_BOUNDARY_RESTITUTION,
                        velocity.z());
                hit = true;
            }
            if (z > zMaximum) {
                z = zMaximum;
                velocity = new Vec2(velocity.x(),
                        -Math.abs(velocity.z()) * PIN_BOUNDARY_RESTITUTION);
                hit = true;
            } else if (z < zMinimum) {
                z = zMinimum;
                velocity = new Vec2(velocity.x(),
                        Math.abs(velocity.z()) * PIN_BOUNDARY_RESTITUTION);
                hit = true;
            }
            if (hit) {
                pin.position(new Vec2(x, z));
                pin.velocity(velocity);
                pin.angularVelocity(pin.angularVelocity() * 0.55);
            }
        }
    }

    private static void resolveObstacles(BallState ball, Vec2 start, List<Obstacle> obstacles,
                                         double delta, long step, List<PhysicsEvent> events,
                                         MutableCounters counters) {
        if (!ball.active()) {
            return;
        }
        for (Obstacle obstacle : obstacles) {
            counters.checks++;
            SweepHit hit = sweepExpandedBox(start, ball.position(), ball.radius(), obstacle);
            if (hit == null) {
                continue;
            }
            double normalVelocity = ball.velocity().dot(hit.normal());
            if (normalVelocity >= 0.0) {
                continue;
            }
            Vec2 reflected = ball.velocity().subtract(hit.normal()
                    .multiply((1.0 + obstacle.restitution()) * normalVelocity));
            double remaining = delta * (1.0 - hit.time());
            ball.position(hit.point().add(hit.normal().multiply(POSITION_EPSILON))
                    .add(reflected.multiply(remaining)));
            ball.velocity(reflected);
            counters.resolved++;
            events.add(new PhysicsEvent(PhysicsEvent.Type.OBSTACLE_COLLISION, step, -1, -1,
                    obstacle.id(), Math.abs(normalVelocity) * ball.mass()));
        }
    }

    private static double resolveBallPin(BallState ball, Vec2 ballStart, PinState pin, Vec2 pinStart,
                                         double delta, SimulationConfig config) {
        CollisionContact contact = circleContact(ballStart, ball.position(), ball.radius(),
                pinStart, pin.position(), pin.collisionRadius(), config.continuousCollisionDetection());
        if (contact == null) {
            return 0.0;
        }
        Vec2 relativeVelocity = pin.velocity().subtract(ball.velocity());
        double alongNormal = relativeVelocity.dot(contact.normal());
        if (alongNormal >= 0.0 && contact.penetration() <= 0.0) {
            return 0.0;
        }
        double restitution = Math.min(config.ballRestitution(), config.pinRestitution());
        double impulse = Math.max(0.0, -(1.0 + restitution) * alongNormal
                / (1.0 / ball.mass() + 1.0 / pin.mass()));
        Vec2 impulseVector = contact.normal().multiply(impulse);
        ball.velocity(ball.velocity().add(impulseVector.multiply(-1.0 / ball.mass())));
        pin.velocity(pin.velocity().add(impulseVector.multiply(1.0 / pin.mass())));
        pin.tiltDirection(contact.normal());
        pin.angularVelocity(pin.angularVelocity()
                + impulse * BALL_PIN_ANGULAR_TRANSFER / pin.inertia());
        pin.wake();
        correctPositions(ball, pin, contact);
        advanceAfterImpact(ball, ballStart, pin, pinStart, contact.time(), delta);
        return impulse;
    }

    private static void resolvePinPairs(List<PinState> pins, List<Vec2> starts, double delta,
                                        SimulationConfig config, long step, List<PhysicsEvent> events,
                                        MutableCounters counters) {
        double cellSize = Math.max(config.broadphaseCellSize(),
                (config.pinRadius() + config.pinFallenSweepRadius()) * 2.05);
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < pins.size(); i++) {
            PinState pin = pins.get(i);
            int cellX = floorCell(pin.position().x(), cellSize);
            int cellZ = floorCell(pin.position().z(), cellSize);
            grid.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(i);
        }

        Set<Long> visited = new HashSet<>();
        for (int i = 0; i < pins.size(); i++) {
            PinState first = pins.get(i);
            int cellX = floorCell(first.position().x(), cellSize);
            int cellZ = floorCell(first.position().z(), cellSize);
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    List<Integer> candidates = grid.get(cellKey(cellX + offsetX, cellZ + offsetZ));
                    if (candidates == null) {
                        continue;
                    }
                    for (int j : candidates) {
                        if (j <= i) {
                            continue;
                        }
                        long pairKey = ((long) i << 32) | (j & 0xffffffffL);
                        if (!visited.add(pairKey)) {
                            continue;
                        }
                        PinState second = pins.get(j);
                        if (first.sleeping() && second.sleeping()) {
                            continue;
                        }
                        counters.checks++;
                        double impulse = resolvePinPin(first, starts.get(i), second, starts.get(j),
                                delta, config);
                        if (impulse > 0.0) {
                            counters.resolved++;
                            events.add(new PhysicsEvent(PhysicsEvent.Type.PIN_PIN_COLLISION, step,
                                    first.id(), second.id(), null, impulse));
                        }
                    }
                }
            }
        }
    }

    private static double resolvePinPin(PinState first, Vec2 firstStart, PinState second, Vec2 secondStart,
                                        double delta, SimulationConfig config) {
        CollisionContact contact = circleContact(firstStart, first.position(), first.collisionRadius(),
                secondStart, second.position(), second.collisionRadius(), config.continuousCollisionDetection());
        if (contact == null) {
            return 0.0;
        }
        Vec2 relativeVelocity = second.contactVelocity().subtract(first.contactVelocity());
        double alongNormal = relativeVelocity.dot(contact.normal());
        if (alongNormal >= 0.0 && contact.penetration() <= 0.0) {
            return 0.0;
        }
        double impulse = Math.max(0.0, -(1.0 + config.pinRestitution()) * alongNormal
                / (1.0 / first.mass() + 1.0 / second.mass()));
        Vec2 impulseVector = contact.normal().multiply(impulse);
        first.velocity(first.velocity().add(impulseVector.multiply(-1.0 / first.mass())));
        second.velocity(second.velocity().add(impulseVector.multiply(1.0 / second.mass())));
        double angularKick = impulse * PIN_PIN_ANGULAR_TRANSFER;
        first.tiltDirection(contact.normal().multiply(-1.0));
        second.tiltDirection(contact.normal());
        first.angularVelocity(first.angularVelocity() + angularKick / first.inertia());
        second.angularVelocity(second.angularVelocity() + angularKick / second.inertia());
        first.wake();
        second.wake();
        correctPositions(first, second, contact);
        advanceAfterImpact(first, firstStart, second, secondStart, contact.time(), delta);
        return impulse;
    }

    private static CollisionContact circleContact(Vec2 firstStart, Vec2 firstEnd, double firstRadius,
                                                   Vec2 secondStart, Vec2 secondEnd, double secondRadius,
                                                   boolean continuousDetection) {
        double radius = firstRadius + secondRadius;
        Vec2 endDelta = secondEnd.subtract(firstEnd);
        double endDistance = endDelta.length();
        if (endDistance <= radius) {
            Vec2 normal = endDelta.normalizedOr(new Vec2(0.0, 1.0));
            return new CollisionContact(normal, 1.0, radius - endDistance);
        }
        if (!continuousDetection) {
            return null;
        }

        Vec2 relativeStart = firstStart.subtract(secondStart);
        Vec2 relativeMove = firstEnd.subtract(firstStart).subtract(secondEnd.subtract(secondStart));
        double a = relativeMove.lengthSquared();
        if (a < 1.0E-14) {
            return null;
        }
        double b = 2.0 * relativeStart.dot(relativeMove);
        double c = relativeStart.lengthSquared() - radius * radius;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
            return null;
        }
        double root = Math.sqrt(discriminant);
        double time = (-b - root) / (2.0 * a);
        if (time < 0.0 || time > 1.0) {
            return null;
        }
        Vec2 firstAtImpact = Vec2.lerp(firstStart, firstEnd, time);
        Vec2 secondAtImpact = Vec2.lerp(secondStart, secondEnd, time);
        Vec2 normal = secondAtImpact.subtract(firstAtImpact)
                .normalizedOr(relativeMove.multiply(-1.0).normalizedOr(new Vec2(0.0, 1.0)));
        return new CollisionContact(normal, time, 0.0);
    }

    private static void correctPositions(BallState ball, PinState pin, CollisionContact contact) {
        if (contact.penetration() <= 0.0) {
            return;
        }
        double totalInverseMass = 1.0 / ball.mass() + 1.0 / pin.mass();
        Vec2 correction = contact.normal().multiply((contact.penetration() + POSITION_EPSILON)
                / totalInverseMass);
        ball.position(ball.position().add(correction.multiply(-1.0 / ball.mass())));
        pin.position(pin.position().add(correction.multiply(1.0 / pin.mass())));
    }

    private static void correctPositions(PinState first, PinState second, CollisionContact contact) {
        if (contact.penetration() <= 0.0) {
            return;
        }
        double totalInverseMass = 1.0 / first.mass() + 1.0 / second.mass();
        Vec2 correction = contact.normal().multiply((contact.penetration() + POSITION_EPSILON)
                / totalInverseMass);
        first.position(first.position().add(correction.multiply(-1.0 / first.mass())));
        second.position(second.position().add(correction.multiply(1.0 / second.mass())));
    }

    private static void advanceAfterImpact(BallState ball, Vec2 ballStart,
                                           PinState pin, Vec2 pinStart,
                                           double time, double delta) {
        if (time >= 1.0) {
            return;
        }
        Vec2 ballAtImpact = Vec2.lerp(ballStart, ball.position(), time);
        Vec2 pinAtImpact = Vec2.lerp(pinStart, pin.position(), time);
        double remaining = delta * (1.0 - time);
        ball.position(ballAtImpact.add(ball.velocity().multiply(remaining)));
        pin.position(pinAtImpact.add(pin.velocity().multiply(remaining)));
    }

    private static void advanceAfterImpact(PinState first, Vec2 firstStart,
                                           PinState second, Vec2 secondStart,
                                           double time, double delta) {
        if (time >= 1.0) {
            return;
        }
        Vec2 firstAtImpact = Vec2.lerp(firstStart, first.position(), time);
        Vec2 secondAtImpact = Vec2.lerp(secondStart, second.position(), time);
        double remaining = delta * (1.0 - time);
        first.position(firstAtImpact.add(first.velocity().multiply(remaining)));
        second.position(secondAtImpact.add(second.velocity().multiply(remaining)));
    }

    private static SweepHit sweepExpandedBox(Vec2 start, Vec2 end, double radius, Obstacle obstacle) {
        double minX = obstacle.minimumX() - radius;
        double maxX = obstacle.maximumX() + radius;
        double minZ = obstacle.minimumZ() - radius;
        double maxZ = obstacle.maximumZ() + radius;
        Vec2 movement = end.subtract(start);

        if (start.x() > minX && start.x() < maxX && start.z() > minZ && start.z() < maxZ) {
            double left = start.x() - minX;
            double right = maxX - start.x();
            double back = start.z() - minZ;
            double front = maxZ - start.z();
            double minimum = Math.min(Math.min(left, right), Math.min(back, front));
            Vec2 normal = minimum == left ? new Vec2(-1, 0)
                    : minimum == right ? new Vec2(1, 0)
                    : minimum == back ? new Vec2(0, -1) : new Vec2(0, 1);
            return new SweepHit(0.0, start, normal);
        }

        double entryX;
        double exitX;
        if (Math.abs(movement.x()) < 1.0E-12) {
            if (start.x() < minX || start.x() > maxX) return null;
            entryX = Double.NEGATIVE_INFINITY;
            exitX = Double.POSITIVE_INFINITY;
        } else {
            double tx1 = (minX - start.x()) / movement.x();
            double tx2 = (maxX - start.x()) / movement.x();
            entryX = Math.min(tx1, tx2);
            exitX = Math.max(tx1, tx2);
        }
        double entryZ;
        double exitZ;
        if (Math.abs(movement.z()) < 1.0E-12) {
            if (start.z() < minZ || start.z() > maxZ) return null;
            entryZ = Double.NEGATIVE_INFINITY;
            exitZ = Double.POSITIVE_INFINITY;
        } else {
            double tz1 = (minZ - start.z()) / movement.z();
            double tz2 = (maxZ - start.z()) / movement.z();
            entryZ = Math.min(tz1, tz2);
            exitZ = Math.max(tz1, tz2);
        }
        double entry = Math.max(entryX, entryZ);
        double exit = Math.min(exitX, exitZ);
        if (entry > exit || exit < 0.0 || entry < 0.0 || entry > 1.0) {
            return null;
        }
        Vec2 normal;
        if (entryX > entryZ) {
            normal = movement.x() > 0.0 ? new Vec2(-1, 0) : new Vec2(1, 0);
        } else {
            normal = movement.z() > 0.0 ? new Vec2(0, -1) : new Vec2(0, 1);
        }
        return new SweepHit(entry, Vec2.lerp(start, end, entry), normal);
    }

    private static void updateSleeping(List<PinState> pins, SimulationConfig config) {
        for (PinState pin : pins) {
            if (pin.velocity().length() < config.pinSleepSpeed()
                    && Math.abs(pin.angularVelocity()) < 0.05) {
                pin.quietSteps(pin.quietSteps() + 1);
                if (pin.quietSteps() >= config.pinSleepSteps()) {
                    pin.velocity(Vec2.ZERO);
                    pin.angularVelocity(0.0);
                    pin.sleeping(true);
                }
            } else {
                pin.quietSteps(0);
                pin.sleeping(false);
            }
        }
    }

    private static Vec2 decelerate(Vec2 velocity, double amount) {
        double speed = velocity.length();
        if (speed <= amount || speed < 1.0E-12) {
            return Vec2.ZERO;
        }
        return velocity.multiply((speed - amount) / speed);
    }

    private static int floorCell(double value, double cellSize) {
        return (int) Math.floor(value / cellSize);
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public record StepResult(PhysicsSnapshot snapshot, List<PhysicsEvent> events) {
        public StepResult {
            events = List.copyOf(events);
        }
    }

    private record CollisionContact(Vec2 normal, double time, double penetration) {
    }

    private record SweepHit(double time, Vec2 point, Vec2 normal) {
    }

    private static final class MutableCounters {
        long checks;
        long resolved;
    }
}
