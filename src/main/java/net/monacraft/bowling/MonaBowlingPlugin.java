package net.monacraft.bowling;

import net.monacraft.bowling.command.BowlingCommand;
import net.monacraft.bowling.citizens.BowlingNpcService;
import net.monacraft.bowling.config.BowlingSettings;
import net.monacraft.bowling.config.PhysicsConfigLoader;
import net.monacraft.bowling.crossplay.CrossplayService;
import net.monacraft.bowling.data.BowlingDatabase;
import net.monacraft.bowling.game.BowlingGameManager;
import net.monacraft.bowling.lane.LaneManager;
import net.monacraft.bowling.menu.BowlingMenu;
import net.monacraft.bowling.model.LaneDefinition;
import net.monacraft.bowling.physics.PhysicsRuntime;
import net.monacraft.bowling.physics.SimulationConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class MonaBowlingPlugin extends JavaPlugin {
    private volatile BowlingSettings settings;
    private BowlingDatabase database;
    private LaneManager laneManager;
    private ExecutorService ioExecutor;
    private SimulationConfig physicsConfig;
    private PhysicsRuntime physicsRuntime;
    private BowlingGameManager gameManager;
    private CrossplayService crossplayService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = BowlingSettings.from(getConfig());
            physicsConfig = PhysicsConfigLoader.from(getConfig());
            physicsRuntime = new PhysicsRuntime(physicsConfig, settings.physicsThreads());
            ioExecutor = Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().name("MonaBowling-IO", 0).factory());

            Path databasePath = getDataFolder().toPath().resolve(settings.databaseFile());
            database = new BowlingDatabase(databasePath, settings.databaseBusyTimeoutMillis(), getLogger());
            database.open();
            List<LaneDefinition> initialLanes = database.loadLanes();
            laneManager = new LaneManager(database, ioExecutor, initialLanes);
            crossplayService = new CrossplayService(getServer().getPluginManager(), getLogger());
            gameManager = new BowlingGameManager(this, laneManager, crossplayService);
            getServer().getPluginManager().registerEvents(gameManager, this);
            BowlingMenu bowlingMenu = new BowlingMenu(this, gameManager);
            getServer().getPluginManager().registerEvents(bowlingMenu, this);

            BowlingCommand bowlingCommand = new BowlingCommand(this, laneManager, gameManager);
            PluginCommand command = Objects.requireNonNull(getCommand("bowling"),
                    "bowling command missing from plugin.yml");
            command.setExecutor(bowlingCommand);
            command.setTabCompleter(bowlingCommand);
            PluginCommand menuCommand = Objects.requireNonNull(getCommand("bowmenu"),
                    "bowmenu command missing from plugin.yml");
            menuCommand.setExecutor((sender, ignored, ignoredLabel, ignoredArgs) -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    bowlingMenu.open(player);
                } else {
                    sender.sendMessage("This command is player-only.");
                }
                return true;
            });

            PluginCommand npcCommand = Objects.requireNonNull(getCommand("setnpc"),
                    "setnpc command missing from plugin.yml");
            if (isPluginAvailable("Citizens")) {
                BowlingNpcService npcService = new BowlingNpcService(this, bowlingMenu);
                npcCommand.setExecutor(npcService);
                getServer().getPluginManager().registerEvents(npcService, this);
            } else {
                npcCommand.setExecutor((sender, ignored, ignoredLabel, ignoredArgs) -> {
                    sender.sendMessage("Citizensが有効ではないためNPCを作成できません。");
                    return true;
                });
            }

            getLogger().info("MonaBowling Phase 5 enabled with " + initialLanes.size() + " lane(s)");
            getLogger().info("Cross-play providers: " + crossplayService.providerStatus());
            getLogger().info("Optional hooks: WorldGuard=" + isPluginAvailable("WorldGuard")
                    + ", PlaceholderAPI=" + isPluginAvailable("PlaceholderAPI")
                    + ", Floodgate=" + isPluginAvailable("floodgate")
                    + ", Citizens=" + isPluginAvailable("Citizens"));
            getLogger().info("MonaBowlingPhysics is ready at " + physicsConfig.fixedHz()
                    + " Hz; runtime remains dormant until a simulation starts");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "MonaBowling could not start safely", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.close();
        }
        if (physicsRuntime != null) {
            physicsRuntime.close();
        }
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                ioExecutor.shutdownNow();
            }
        }
        if (database != null) {
            try {
                database.close();
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Could not close MonaBowling database", exception);
            }
        }
    }

    public void reloadMonaBowlingConfig() {
        reloadConfig();
        BowlingSettings reloaded = BowlingSettings.from(getConfig());
        SimulationConfig reloadedPhysics = PhysicsConfigLoader.from(getConfig());
        if (!reloaded.databaseFile().equals(settings.databaseFile())
                || reloaded.databaseBusyTimeoutMillis() != settings.databaseBusyTimeoutMillis()) {
            throw new IllegalArgumentException("database.file と busy-timeout の変更にはHub再起動が必要です");
        }
        if (gameManager != null && gameManager.activeSessions() > 0) {
            throw new IllegalArgumentException("設定の再読込は進行中のゲーム終了後に行ってください");
        }
        if (physicsRuntime.activeSimulationCount() > 0) {
            throw new IllegalArgumentException("物理設定の再読込は進行中のシミュレーション終了後に行ってください");
        }
        physicsRuntime.close();
        physicsConfig = reloadedPhysics;
        physicsRuntime = new PhysicsRuntime(physicsConfig, reloaded.physicsThreads());
        settings = reloaded;
    }

    public BowlingSettings settings() {
        return settings;
    }

    public BowlingDatabase database() {
        return database;
    }

    public PhysicsRuntime physicsRuntime() {
        return physicsRuntime;
    }

    public SimulationConfig physicsConfig() {
        return physicsConfig;
    }

    public BowlingGameManager gameManager() {
        return gameManager;
    }

    public CrossplayService crossplayService() {
        return crossplayService;
    }

    public boolean isPluginAvailable(String name) {
        return getServer().getPluginManager().isPluginEnabled(name);
    }

    public <T> CompletableFuture<T> supplyIo(CheckedSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, ioExecutor);
    }

    public void runSync(Runnable action) {
        if (isEnabled()) {
            getServer().getScheduler().runTask(this, action);
        }
    }

    public void reportAsyncFailure(String operation, Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        getLogger().log(Level.SEVERE, "Asynchronous " + operation + " failed", cause);
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
