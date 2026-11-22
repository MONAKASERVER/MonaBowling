package net.monacraft.bowling.physics;

import java.util.concurrent.atomic.LongAdder;

public final class PhysicsProfiler {
    private final LongAdder steps = new LongAdder();
    private final LongAdder totalStepNanos = new LongAdder();
    private final LongAdder collisionChecks = new LongAdder();
    private final LongAdder resolvedCollisions = new LongAdder();
    private final LongAdder subSteps = new LongAdder();

    void record(long elapsedNanos, long checks, long resolved, int subStepCount) {
        steps.increment();
        totalStepNanos.add(elapsedNanos);
        collisionChecks.add(checks);
        resolvedCollisions.add(resolved);
        subSteps.add(subStepCount);
    }

    public ProfileSnapshot snapshot() {
        long stepCount = steps.sum();
        long nanos = totalStepNanos.sum();
        return new ProfileSnapshot(
                stepCount,
                nanos,
                stepCount == 0 ? 0.0 : nanos / 1_000_000.0 / stepCount,
                collisionChecks.sum(),
                resolvedCollisions.sum(),
                subSteps.sum()
        );
    }

    public record ProfileSnapshot(
            long steps,
            long totalStepNanos,
            double averageStepMillis,
            long collisionChecks,
            long resolvedCollisions,
            long subSteps
    ) {
    }
}
