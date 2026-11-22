package net.monacraft.bowling.lane;

import net.monacraft.bowling.data.BowlingDatabase;
import net.monacraft.bowling.model.LaneDefinition;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class LaneManager {
    private final BowlingDatabase database;
    private final Executor ioExecutor;
    private final ConcurrentHashMap<String, LaneDefinition> lanes = new ConcurrentHashMap<>();

    public LaneManager(BowlingDatabase database, Executor ioExecutor, Collection<LaneDefinition> initialLanes) {
        this.database = database;
        this.ioExecutor = ioExecutor;
        for (LaneDefinition lane : initialLanes) {
            lanes.put(lane.id(), lane);
        }
    }

    public Optional<LaneDefinition> find(String id) {
        try {
            return Optional.ofNullable(lanes.get(LaneDefinition.normalizeId(id)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public List<LaneDefinition> all() {
        return lanes.values().stream()
                .sorted(Comparator.comparing(LaneDefinition::id))
                .toList();
    }

    public CompletableFuture<LaneDefinition> save(LaneDefinition lane) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                database.saveLane(lane);
                lanes.put(lane.id(), lane);
                return lane;
            } catch (SQLException exception) {
                throw new LaneStorageException("Could not save lane " + lane.id(), exception);
            }
        }, ioExecutor);
    }

    public CompletableFuture<Boolean> delete(String id) {
        String normalized = LaneDefinition.normalizeId(id);
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean deleted = database.deleteLane(normalized);
                if (deleted) {
                    lanes.remove(normalized);
                }
                return deleted;
            } catch (SQLException exception) {
                throw new LaneStorageException("Could not delete lane " + normalized, exception);
            }
        }, ioExecutor);
    }

    public CompletableFuture<Integer> reload() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<LaneDefinition> loaded = database.loadLanes();
                lanes.clear();
                loaded.forEach(lane -> lanes.put(lane.id(), lane));
                return loaded.size();
            } catch (SQLException exception) {
                throw new LaneStorageException("Could not reload lanes", exception);
            }
        }, ioExecutor);
    }

    public static final class LaneStorageException extends RuntimeException {
        public LaneStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
