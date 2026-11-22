package net.monacraft.bowling.game;

import java.util.concurrent.atomic.LongAdder;

/** Main-thread display profiling, kept separate from the physics worker profile. */
public final class RenderingProfiler {
    private final LongAdder frames = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final LongAdder displayUpdates = new LongAdder();

    void record(long elapsedNanos, int updates) {
        frames.increment();
        totalNanos.add(Math.max(0L, elapsedNanos));
        displayUpdates.add(Math.max(0, updates));
    }

    public Snapshot snapshot() {
        long frameCount = frames.sum();
        long nanos = totalNanos.sum();
        return new Snapshot(frameCount, nanos,
                frameCount == 0 ? 0.0 : nanos / 1_000_000.0 / frameCount,
                displayUpdates.sum());
    }

    public record Snapshot(long frames, long totalNanos, double averageFrameMillis,
                           long displayUpdates) {
    }
}
