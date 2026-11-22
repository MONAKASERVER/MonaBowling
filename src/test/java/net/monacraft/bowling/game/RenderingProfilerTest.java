package net.monacraft.bowling.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderingProfilerTest {
    @Test
    void aggregatesRenderWorkIndependently() {
        RenderingProfiler profiler = new RenderingProfiler();
        profiler.record(1_000_000L, 11);
        profiler.record(3_000_000L, 7);

        RenderingProfiler.Snapshot snapshot = profiler.snapshot();
        assertEquals(2, snapshot.frames());
        assertEquals(2.0, snapshot.averageFrameMillis(), 1.0E-9);
        assertEquals(18, snapshot.displayUpdates());
    }
}
