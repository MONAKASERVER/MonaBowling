package net.monacraft.bowling.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.monacraft.bowling.MonaBowlingPlugin;
import net.monacraft.bowling.data.PlayerStatistics;
import net.monacraft.bowling.data.RankingEntry;
import net.monacraft.bowling.game.BowlingGameManager;
import net.monacraft.bowling.lane.LaneManager;
import net.monacraft.bowling.model.GameMode;
import net.monacraft.bowling.model.LaneDefinition;
import net.monacraft.bowling.model.LanePoint;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BowlingCommand implements CommandExecutor, TabCompleter {
    private static final Component PREFIX = Component.text("[MonaBowling] ", NamedTextColor.AQUA);

    private final MonaBowlingPlugin plugin;
    private final LaneManager lanes;
    private final BowlingGameManager games;

    public BowlingCommand(MonaBowlingPlugin plugin, LaneManager lanes, BowlingGameManager games) {
        this.plugin = plugin;
        this.lanes = lanes;
        this.games = games;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showMenu(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> showHelp(sender);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "client" -> showClient(sender);
            case "stats" -> showStats(sender, args);
            case "top" -> showTop(sender, args);
            case "admin" -> handleAdmin(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> showHelp(sender);
        }
        return true;
    }

    private void showMenu(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text("ボウリング基盤 v" + plugin.getPluginMeta().getVersion(),
                NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/bowling join [standard|hundred-pin] [lane] - ゲームに参加",
                NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/bowmenu - レーン・人数・公開ロビーをGUIで設定",
                NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/bowling leave - ゲームから退出", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/bowling client - 入力端末の診断", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/bowling stats [mode] - 戦績", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/bowling top [mode] - ランキング", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/bowling help - 操作一覧", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("右クリック2回で構え・投球。視点移動でカーブします。", NamedTextColor.YELLOW));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(PREFIX.append(Component.text("コマンド", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/bowmenu, /bowling join, leave, client, stats [mode], top [mode], help",
                NamedTextColor.GRAY));
        if (sender.hasPermission("monabowling.admin")) {
            sender.sendMessage(Component.text(
                    "/bowling admin create|delete|setlane|setspawn|setpins|reload|debug", NamedTextColor.GOLD));
        }
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            error(sender, "このコマンドはプレイヤー専用です。");
            return;
        }
        GameMode mode = GameMode.STANDARD;
        String lane = null;
        if (args.length >= 2) {
            Optional<GameMode> parsed = GameMode.parse(args[1]);
            if (parsed.isPresent()) {
                mode = parsed.get();
                lane = args.length >= 3 ? args[2] : null;
            } else {
                lane = args[1];
            }
        }
        games.join(player, lane, mode);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "このコマンドはプレイヤー専用です。");
            return;
        }
        games.leave(player);
    }

    private void showClient(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "このコマンドはプレイヤー専用です。");
            return;
        }
        var profile = games.clientProfile(player);
        sender.sendMessage(PREFIX.append(Component.text("クライアント診断", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Edition=" + profile.edition()
                + " Input=" + profile.input() + " Platform=" + profile.platform(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Version=" + profile.clientVersion()
                + " Provider=" + profile.provider()
                + " YawScale=" + String.format(Locale.ROOT, "%.2f", games.yawSensitivity(profile)),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Renderer="
                + (profile.bedrock() && plugin.settings().bedrockArmorStandFallback()
                ? "ARMOR_STAND_ORB" : "ITEM_DISPLAY_ORB"), NamedTextColor.GRAY));
    }

    private void showStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            error(sender, "このコマンドはプレイヤー専用です。");
            return;
        }
        Optional<GameMode> parsed = parseMode(args, 1);
        if (parsed.isEmpty()) {
            error(sender, "モードは standard / hundred-pin / wall-dodge です。");
            return;
        }
        GameMode mode = parsed.get();
        plugin.supplyIo(() -> plugin.database().findStatistics(player.getUniqueId(), mode))
                .whenComplete((statistics, throwable) -> plugin.runSync(() -> {
                    if (throwable != null) {
                        plugin.reportAsyncFailure("stats query", throwable);
                        error(sender, "戦績の読み込みに失敗しました。");
                        return;
                    }
                    if (statistics.isEmpty()) {
                        sender.sendMessage(PREFIX.append(Component.text(mode.displayName()
                                + "の対戦記録はまだありません。", NamedTextColor.GRAY)));
                        return;
                    }
                    PlayerStatistics value = statistics.get();
                    sender.sendMessage(PREFIX.append(Component.text(mode.displayName() + " 戦績",
                            NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("自己ベスト: " + value.bestScore()
                            + " / 対戦: " + value.gamesPlayed() + " / 勝利: " + value.wins(), NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("ストライク: " + value.strikes()
                            + " / スペア: " + value.spares() + " / ガター: " + value.gutters(), NamedTextColor.GRAY));
                }));
    }

    private void showTop(CommandSender sender, String[] args) {
        Optional<GameMode> parsed = parseMode(args, 1);
        if (parsed.isEmpty()) {
            error(sender, "モードは standard / hundred-pin / wall-dodge です。");
            return;
        }
        GameMode mode = parsed.get();
        plugin.supplyIo(() -> plugin.database().loadRanking(mode, 10))
                .whenComplete((ranking, throwable) -> plugin.runSync(() -> {
                    if (throwable != null) {
                        plugin.reportAsyncFailure("ranking query", throwable);
                        error(sender, "ランキングの読み込みに失敗しました。");
                        return;
                    }
                    sender.sendMessage(PREFIX.append(Component.text(mode.displayName() + " TOP 10",
                            NamedTextColor.WHITE)));
                    if (ranking.isEmpty()) {
                        sender.sendMessage(Component.text("記録はまだありません。", NamedTextColor.GRAY));
                    }
                    for (RankingEntry entry : ranking) {
                        sender.sendMessage(Component.text("#" + entry.rank() + " " + entry.playerName()
                                + " — " + entry.bestScore(), NamedTextColor.GRAY));
                    }
                }));
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("monabowling.admin")) {
            error(sender, "管理者権限がありません。");
            return;
        }
        if (args.length == 0) {
            error(sender, "admin create|delete|setlane|setspawn|setpins|reload|debug");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> createLane(sender, args);
            case "delete" -> deleteLane(sender, args);
            case "setlane" -> updateLanePoint(sender, args, LaneDefinition::withOrigin, "レーン原点");
            case "setspawn" -> updateLanePoint(sender, args, LaneDefinition::withPlayerSpawn, "投球位置");
            case "setpins" -> updateLanePoint(sender, args, LaneDefinition::withPinDeck, "ピン原点");
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            default -> error(sender, "不明な管理コマンドです。");
        }
    }

    private void createLane(CommandSender sender, String[] args) {
        Player player = requirePlayerAndId(sender, args);
        if (player == null) {
            return;
        }
        String id;
        try {
            id = LaneDefinition.normalizeId(args[1]);
        } catch (IllegalArgumentException exception) {
            error(sender, exception.getMessage());
            return;
        }
        if (lanes.find(id).isPresent()) {
            error(sender, "同じIDのレーンが存在します。");
            return;
        }
        Location originLocation = player.getLocation();
        Location pinsLocation = originLocation.clone();
        Vector direction = originLocation.getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            direction.setZ(1);
        }
        pinsLocation.add(direction.normalize().multiply(18.0));
        LaneDefinition lane = new LaneDefinition(
                id,
                originLocation.getWorld().getName(),
                toPoint(originLocation),
                toPoint(originLocation),
                toPoint(pinsLocation),
                plugin.settings().enabledModes(),
                true
        );
        completeLaneWrite(sender, lanes.save(lane), "レーン " + id + " を作成しました。");
    }

    private void deleteLane(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "/bowling admin delete <ID>");
            return;
        }
        try {
            if (games.isLaneBusy(LaneDefinition.normalizeId(args[1]))) {
                error(sender, "使用中のレーンは削除できません。");
                return;
            }
            lanes.delete(args[1]).whenComplete((deleted, throwable) -> plugin.runSync(() -> {
                if (throwable != null) {
                    plugin.reportAsyncFailure("lane delete", throwable);
                    error(sender, "レーンの削除に失敗しました。");
                } else if (!deleted) {
                    error(sender, "指定したレーンは存在しません。");
                } else {
                    success(sender, "レーンを削除しました。");
                }
            }));
        } catch (IllegalArgumentException exception) {
            error(sender, exception.getMessage());
        }
    }

    private void updateLanePoint(CommandSender sender, String[] args,
                                 UnaryOperatorFactory updater, String pointName) {
        Player player = requirePlayerAndId(sender, args);
        if (player == null) {
            return;
        }
        Optional<LaneDefinition> lane = lanes.find(args[1]);
        if (lane.isEmpty()) {
            error(sender, "指定したレーンは存在しません。");
            return;
        }
        if (!lane.get().world().equals(player.getWorld().getName())) {
            error(sender, "レーンと同じワールドで設定してください。");
            return;
        }
        if (games.isLaneBusy(lane.get().id())) {
            error(sender, "使用中のレーン座標は変更できません。");
            return;
        }
        LaneDefinition updated = updater.apply(lane.get(), toPoint(player.getLocation()));
        completeLaneWrite(sender, lanes.save(updated), pointName + "を更新しました。");
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadMonaBowlingConfig();
        } catch (IllegalArgumentException exception) {
            error(sender, "設定エラー: " + exception.getMessage());
            return;
        }
        lanes.reload().whenComplete((count, throwable) -> plugin.runSync(() -> {
            if (throwable != null) {
                plugin.reportAsyncFailure("lane reload", throwable);
                error(sender, "レーンの再読み込みに失敗しました。");
            } else {
                success(sender, "設定とレーン " + count + " 件を再読み込みしました。");
            }
        }));
    }

    private void debug(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        var rendering = games.renderingProfile();
        long usedMiB = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        sender.sendMessage(PREFIX.append(Component.text("Debug", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("lanes=" + lanes.all().size()
                + " games=" + games.activeSessions()
                + " hundredPinGames=" + games.activeHundredPinSessions()
                + " db=" + plugin.database().isOpen()
                + " physicsRunning=" + plugin.physicsRuntime().running()
                + " physicsSimulations=" + plugin.physicsRuntime().activeSimulationCount()
                + " physicsSteps=" + plugin.physicsRuntime().profile().steps()
                + " renderFrames=" + rendering.frames()
                + " renderAvgMs=" + String.format(Locale.ROOT, "%.4f", rendering.averageFrameMillis())
                + " displayUpdates=" + rendering.displayUpdates()
                + " crossplay=" + plugin.crossplayService().providerStatus()
                + " floodgate=" + plugin.isPluginAvailable("floodgate")
                + " worldguard=" + plugin.isPluginAvailable("WorldGuard")
                + " papi=" + plugin.isPluginAvailable("PlaceholderAPI")
                + " heapUsedMiB=" + usedMiB, NamedTextColor.GRAY));
    }

    private void completeLaneWrite(CommandSender sender, CompletableFuture<LaneDefinition> future,
                                   String message) {
        future.whenComplete((ignored, throwable) -> plugin.runSync(() -> {
            if (throwable != null) {
                plugin.reportAsyncFailure("lane save", throwable);
                error(sender, "レーンの保存に失敗しました。");
            } else {
                success(sender, message);
            }
        }));
    }

    private Player requirePlayerAndId(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            error(sender, "この操作はゲーム内で実行してください。");
            return null;
        }
        if (args.length < 2) {
            error(sender, "レーンIDを指定してください。");
            return null;
        }
        return player;
    }

    private static Optional<GameMode> parseMode(String[] args, int index) {
        return GameMode.parse(args.length > index ? args[index] : "standard");
    }

    private static LanePoint toPoint(Location location) {
        return new LanePoint(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private static void success(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("join", "leave", "client", "stats", "top", "help"));
            if (sender.hasPermission("monabowling.admin")) {
                values.add("admin");
            }
            return filter(values, args[0]);
        }
        if ((args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("top")) && args.length == 2) {
            return filter(Arrays.stream(GameMode.values()).map(GameMode::configKey).toList(), args[1]);
        }
        if (args[0].equalsIgnoreCase("join") && args.length == 2) {
            List<String> options = new ArrayList<>(List.of("standard", "hundred-pin"));
            options.addAll(lanes.all().stream()
                    .filter(LaneDefinition::enabled)
                    .filter(lane -> lane.modes().contains(GameMode.STANDARD))
                    .filter(lane -> !games.isLaneBusy(lane.id()))
                    .map(LaneDefinition::id).toList());
            return filter(options, args[1]);
        }
        if (args[0].equalsIgnoreCase("join") && args.length == 3) {
            Optional<GameMode> mode = GameMode.parse(args[1]);
            if (mode.isPresent()) {
                return filter(lanes.all().stream()
                        .filter(LaneDefinition::enabled)
                        .filter(lane -> lane.modes().contains(mode.get()))
                        .filter(lane -> !games.isLaneBusy(lane.id()))
                        .map(LaneDefinition::id).toList(), args[2]);
            }
        }
        if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("monabowling.admin")) {
            if (args.length == 2) {
                return filter(List.of("create", "delete", "setlane", "setspawn", "setpins", "reload", "debug"),
                        args[1]);
            }
            if (args.length == 3 && Set.of("delete", "setlane", "setspawn", "setpins")
                    .contains(args[1].toLowerCase(Locale.ROOT))) {
                return filter(lanes.all().stream().map(LaneDefinition::id).toList(), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(normalized)).toList();
    }

    @FunctionalInterface
    private interface UnaryOperatorFactory {
        LaneDefinition apply(LaneDefinition lane, LanePoint point);
    }
}
