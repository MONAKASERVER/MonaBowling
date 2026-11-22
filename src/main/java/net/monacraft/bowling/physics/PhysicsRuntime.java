package net.monacraft.bowling.physics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns physics worker threads and never calls Bukkit or Paper APIs.
 */
public final class PhysicsRuntime implements AutoCloseable {
    private final SimulationConfig config;
    private final int workerThreads;
    private final MonaBowlingPhysics engine = new MonaBowlingPhysics();
    private final Map<UUID, Slot> simulations = new ConcurrentHashMap<>();
    private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private ScheduledExecutorService scheduler;
    private ExecutorService workers;
    private ScheduledFuture<?> loop;
    private long lastNanos;
    private long accumulatedNanos;

    public PhysicsRuntime(SimulationConfig config, int workerThreads) {
        if (workerThreads < 1 || workerThreads > 4) {
            throw new IllegalArgumentException("workerThreads must be in [1, 4]");
        }
        this.config = config;
        this.workerThreads = workerThreads;
    }

    public synchronized UUID startSimulation(SimulationWorld world) {
        if (closed.get()) {
            throw new IllegalStateException("physics runtime is closed");
        }
        if (world.config().fixedHz() != config.fixedHz()
                || world.config().maximumCatchUpSteps() != config.maximumCatchUpSteps()) {
            throw new IllegalArgumentException("simulation clock must match runtime config");
        }
        UUID id = UUID.randomUUID();
        simulations.put(id, new Slot(world));
        ensureLoop();
        return id;
    }

    public Optional<PhysicsSnapshot> snapshot(UUID id) {
        Slot slot = simulations.get(id);
        return slot == null ? Optional.empty() : Optional.of(slot.snapshot.get());
    }

    public List<PhysicsEvent> drainEvents(UUID id) {
        Slot slot = simulations.get(id);
        if (slot == null) {
            return List.of();
        }
        List<PhysicsEvent> events = new ArrayList<>();
        PhysicsEvent event;
        while ((event = slot.events.poll()) != null) {
            events.add(event);
        }
        return List.copyOf(events);
    }

    public synchronized boolean removeSimulation(UUID id) {
        boolean removed = simulations.remove(id) != null;
        stopLoopIfIdle();
        return removed;
    }

    public int activeSimulationCount() {
        return simulations.size();
    }

    public synchronized boolean running() {
        return loop != null && !loop.isCancelled();
    }

    public Optional<Throwable> lastFailure() {
        return Optional.ofNullable(lastFailure.get());
    }

    public boolean simulationFailed(UUID id) {
        Slot slot = simulations.get(id);
        return slot != null && slot.failed.get();
    }

    public PhysicsProfiler.ProfileSnapshot profile() {
        return engine.profile();
    }

    public boolean awaitSettled(UUID id, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<PhysicsSnapshot> snapshot = snapshot(id);
            if (snapshot.isEmpty() || snapshot.get().settled()) {
                return true;
            }
            Thread.sleep(2L);
        }
        return false;
    }

    private void ensureLoop() {
        if (loop != null && !loop.isCancelled()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("MonaBowling-PhysicsClock", 0).factory());
        workers = Executors.newFixedThreadPool(workerThreads,
                Thread.ofPlatform().name("MonaBowling-PhysicsWorker-", 0).factory());
        lastNanos = System.nanoTime();
        accumulatedNanos = 0L;
        long pollingPeriod = Math.max(1L, 1_000_000_000L / config.fixedHz() / 2L);
        loop = scheduler.scheduleAtFixedRate(this::clockTick, 0L, pollingPeriod, TimeUnit.NANOSECONDS);
    }

    private void clockTick() {
        long now = System.nanoTime();
        long elapsed = Math.max(0L, Math.min(now - lastNanos, 250_000_000L));
        lastNanos = now;
        accumulatedNanos += elapsed;
        long fixedNanos = Math.round(1_000_000_000.0 / config.fixedHz());
        int steps = (int) Math.min(config.maximumCatchUpSteps(), accumulatedNanos / fixedNanos);
        if (steps <= 0) {
            return;
        }
        accumulatedNanos -= steps * fixedNanos;
        if (accumulatedNanos > fixedNanos * config.maximumCatchUpSteps()) {
            accumulatedNanos = fixedNanos * config.maximumCatchUpSteps();
        }
        for (int i = 0; i < steps; i++) {
            stepAll();
        }
    }

    private void stepAll() {
        List<Slot> current = List.copyOf(simulations.values());
        if (workerThreads == 1 || current.size() <= 1) {
            current.forEach(this::safeStep);
            return;
        }
        List<java.util.concurrent.CompletableFuture<Void>> futures = current.stream()
                .map(slot -> java.util.concurrent.CompletableFuture.runAsync(() -> safeStep(slot), workers))
                .toList();
        futures.forEach(java.util.concurrent.CompletableFuture::join);
    }

    private void safeStep(Slot slot) {
        if (slot.failed.get()) {
            return;
        }
        try {
            MonaBowlingPhysics.StepResult result = engine.step(slot.world);
            slot.snapshot.set(result.snapshot());
            slot.events.addAll(result.events());
        } catch (Throwable throwable) {
            slot.failed.set(true);
            lastFailure.compareAndSet(null, throwable);
        }
    }

    private void stopLoopIfIdle() {
        if (!simulations.isEmpty()) {
            return;
        }
        if (loop != null) {
            loop.cancel(true);
            loop = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (workers != null) {
            workers.shutdown();
            workers = null;
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        simulations.clear();
        if (loop != null) {
            loop.cancel(true);
            loop = null;
        }
        shutdown(scheduler);
        shutdown(workers);
        scheduler = null;
        workers = null;
    }

    private static void shutdown(ExecutorService service) {
        if (service == null) {
            return;
        }
        service.shutdown();
        try {
            if (!service.awaitTermination(2, TimeUnit.SECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            service.shutdownNow();
        }
    }

    private static final class Slot {
        private final SimulationWorld world;
        private final AtomicReference<PhysicsSnapshot> snapshot;
        private final ConcurrentLinkedQueue<PhysicsEvent> events = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean failed = new AtomicBoolean();

        private Slot(SimulationWorld world) {
            this.world = world;
            this.snapshot = new AtomicReference<>(world.snapshot());
        }
    }
}
