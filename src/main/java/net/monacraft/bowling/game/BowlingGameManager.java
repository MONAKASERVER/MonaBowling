package net.monacraft.bowling.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.bossbar.BossBar;
import net.monacraft.bowling.MonaBowlingPlugin;
import net.monacraft.bowling.data.CompletedGame;
import net.monacraft.bowling.crossplay.ClientProfile;
import net.monacraft.bowling.crossplay.CrossplayService;
import net.monacraft.bowling.crossplay.YawInputTracker;
import net.monacraft.bowling.lane.LaneManager;
import net.monacraft.bowling.model.GameMode;
import net.monacraft.bowling.model.LaneDefinition;
import net.monacraft.bowling.physics.BallState;
import net.monacraft.bowling.physics.LaneGeometry;
import net.monacraft.bowling.physics.Obstacle;
import net.monacraft.bowling.physics.PhysicsEvent;
import net.monacraft.bowling.physics.PhysicsSnapshot;
import net.monacraft.bowling.physics.PinLayouts;
import net.monacraft.bowling.physics.PinState;
import net.monacraft.bowling.physics.SimulationConfig;
import net.monacraft.bowling.physics.SimulationWorld;
import net.monacraft.bowling.physics.Vec2;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BowlingGameManager implements Listener, AutoCloseable {
    private static final Component PREFIX = Component.text("[MonaBowling] ", NamedTextColor.AQUA);
    private final MonaBowlingPlugin plugin;
    private final LaneManager laneManager;
    private final CrossplayService crossplay;
    private final NamespacedKey ballKey;
    private final RenderingProfiler renderingProfiler = new RenderingProfiler();
    private final Map<UUID, Session> byPlayer = new HashMap<>();
    private final Map<String, Session> byLane = new HashMap<>();
    private BukkitTask ticker;
    private long tick;

    public BowlingGameManager(MonaBowlingPlugin plugin, LaneManager laneManager,
                              CrossplayService crossplay) {
        this.plugin = plugin;
        this.laneManager = laneManager;
        this.crossplay = crossplay;
        this.ballKey = new NamespacedKey(plugin, "bowling_ball");
    }

    public void join(Player player, String requestedLane) {
        join(player, requestedLane, GameMode.STANDARD);
    }

    public void join(Player player, String requestedLane, GameMode mode) {
        createMatch(player, requestedLane, mode, 1);
    }

    public void createMatch(Player player, String requestedLane, GameMode mode, int maximumPlayers) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            message(player, NamedTextColor.RED, "すでにゲームへ参加しています。");
            return;
        }
        if (maximumPlayers < 1 || maximumPlayers > plugin.settings().maximumPlayersPerLane()) {
            message(player, NamedTextColor.RED, "人数は1〜"
                    + plugin.settings().maximumPlayersPerLane() + "人で指定してください。");
            return;
        }
        if (byLane.size() >= plugin.settings().maximumConcurrentMatches()) {
            message(player, NamedTextColor.RED, "現在は全レーンが使用中です。");
            return;
        }
        if (!plugin.settings().enabledModes().contains(mode)) {
            message(player, NamedTextColor.RED, mode.displayName() + "モードは現在無効です。");
            return;
        }
        if (mode == GameMode.HUNDRED_PIN && activeHundredPinSessions()
                >= plugin.settings().maximumConcurrentHundredPinMatches()) {
            message(player, NamedTextColor.RED, "100ピンモードは現在使用中です。");
            return;
        }
        Optional<LaneDefinition> selected = selectLane(requestedLane, mode);
        if (selected.isEmpty()) {
            message(player, NamedTextColor.RED, requestedLane == null
                    ? "使用可能な" + mode.displayName() + "レーンがありません。"
                    : "指定したレーンは存在しないか使用中です。");
            return;
        }
        LaneDefinition lane = selected.get();
        Participant participant = createParticipant(player, mode);
        if (participant == null) {
            message(player, NamedTextColor.RED, "投球用ボールを受け取る空きスロットが必要です。");
            return;
        }
        BowlingRenderer renderer = null;
        Session session = null;
        try {
            ClientProfile profile = crossplay.profile(player.getUniqueId());
            LaneTransform transform = LaneTransform.from(plugin.getServer(), lane);
            List<Vec2> rack = mode == GameMode.HUNDRED_PIN
                    ? PinLayouts.resortHundred(new Vec2(0.0, transform.pinDistance()),
                    plugin.settings().hundredPinRackSpacing())
                    : PinLayouts.standardTen(new Vec2(0.0, transform.pinDistance()),
                    plugin.settings().rackSpacing());
            boolean fallback = plugin.settings().bedrockArmorStandFallback() && profile.bedrock();
            renderer = new BowlingRenderer(transform, rack, renderingProfiler, fallback,
                    plugin.settings().maximumDisplayUpdatesPerTick(),
                    plugin.settings().pinVisualWidth(), plugin.settings().pinVisualHeight(),
                    plugin.wallDodgeSettings().wallMaterial());
            Set<Integer> standing = fullRack(rack.size());
            renderer.resetRack(standing);
            showSecretSwitch(renderer, rack, mode);
            showWallDodgeStage(renderer, transform, mode, 1);

            session = new Session(UUID.randomUUID(), lane, transform, rack, renderer,
                    mode, maximumPlayers, participant);
            byPlayer.put(player.getUniqueId(), session);
            byLane.put(lane.id(), session);
            ensureTicker();
            if (maximumPlayers == 1) {
                startMatch(session);
            } else {
                message(player, NamedTextColor.GREEN, "レーン " + lane.id() + " に "
                        + maximumPlayers + "人用ロビーを作成しました。");
                updateLobbyBossBar(session);
            }
        } catch (RuntimeException exception) {
            if (session != null && byLane.get(session.lane.id()) == session) {
                cleanup(session, true);
            } else if (renderer != null) {
                renderer.close();
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not create bowling session", exception);
            message(player, NamedTextColor.RED, "レーンの開始に失敗しました。管理者へ連絡してください。");
        }
    }

    public void joinLobby(Player player, String laneId) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            message(player, NamedTextColor.RED, "すでに別のゲームへ参加しています。");
            return;
        }
        Session session;
        try {
            session = byLane.get(LaneDefinition.normalizeId(laneId));
        } catch (IllegalArgumentException exception) {
            message(player, NamedTextColor.RED, "レーンIDが正しくありません。");
            return;
        }
        if (session == null || session.started || session.participants.size() >= session.maximumPlayers) {
            message(player, NamedTextColor.RED, "参加できるロビーがありません。");
            return;
        }
        Participant participant = createParticipant(player, session.mode);
        if (participant == null) {
            message(player, NamedTextColor.RED, "インベントリに空きスロットが必要です。");
            return;
        }
        session.participants.add(participant);
        byPlayer.put(player.getUniqueId(), session);
        for (Participant member : session.participants) {
            Player online = plugin.getServer().getPlayer(member.playerId);
            if (online != null) {
                online.sendActionBar(Component.text(player.getName() + " が参加しました（"
                        + session.participants.size() + "/" + session.maximumPlayers + "）",
                        NamedTextColor.GREEN));
            }
        }
        updateLobbyBossBar(session);
        if (session.participants.size() >= session.maximumPlayers) {
            startMatch(session);
        }
    }

    public void startLobby(Player player) {
        Session session = byPlayer.get(player.getUniqueId());
        if (session == null || session.started) {
            message(player, NamedTextColor.RED, "開始待ちのロビーに参加していません。");
            return;
        }
        if (!session.participants.getFirst().playerId.equals(player.getUniqueId())) {
            message(player, NamedTextColor.RED, "ロビー作成者のみ開始できます。");
            return;
        }
        startMatch(session);
    }

    public List<LobbyView> openLobbies() {
        return byLane.values().stream().filter(session -> !session.started)
                .map(session -> new LobbyView(session.id, session.lane.id(), session.mode,
                        session.participants.size(), session.maximumPlayers,
                        session.participants.getFirst().playerName))
                .toList();
    }

    public List<LaneDefinition> availableLanes(GameMode mode) {
        return laneManager.all().stream().filter(LaneDefinition::enabled)
                .filter(lane -> lane.modes().contains(mode))
                .filter(lane -> !byLane.containsKey(lane.id())).toList();
    }

    public boolean isInMatch(UUID playerId) {
        return byPlayer.containsKey(playerId);
    }

    public boolean isWaitingLobby(UUID playerId) {
        Session session = byPlayer.get(playerId);
        return session != null && !session.started;
    }

    public boolean isLobbyOwner(UUID playerId) {
        Session session = byPlayer.get(playerId);
        return session != null && !session.started && !session.participants.isEmpty()
                && session.participants.getFirst().playerId.equals(playerId);
    }

    private Participant createParticipant(Player player, GameMode mode) {
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) {
            return null;
        }
        int rackSize = mode == GameMode.HUNDRED_PIN ? 100 : 10;
        return new Participant(player, slot, crossplay.profile(player.getUniqueId()), rackSize);
    }

    private void startMatch(Session session) {
        if (session.started || session.participants.isEmpty()) {
            return;
        }
        try {
            ensureCompatibleRenderer(session);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not select a cross-play bowling renderer", exception);
            broadcast(session, "表示の初期化に失敗したためゲームを終了しました。", NamedTextColor.RED);
            cleanup(session, true);
            return;
        }
        session.started = true;
        hideLobbyBossBar(session);
        session.activeIndex = 0;
        session.state = SessionState.AIMING;
        broadcast(session, session.mode.displayName() + " ゲームを開始します（"
                + session.participants.size() + "人）", NamedTextColor.GOLD);
        activateTurn(session);
    }

    private void ensureCompatibleRenderer(Session session) {
        boolean needsFallback = plugin.settings().bedrockArmorStandFallback()
                && session.participants.stream().anyMatch(value -> value.clientProfile.bedrock());
        if (session.renderer.usingArmorStandFallback() == needsFallback) return;
        BowlingRenderer replacement = new BowlingRenderer(session.transform, session.rack,
                renderingProfiler, needsFallback, plugin.settings().maximumDisplayUpdatesPerTick(),
                plugin.settings().pinVisualWidth(), plugin.settings().pinVisualHeight(),
                plugin.wallDodgeSettings().wallMaterial());
        replacement.resetRack(session.active().standing);
        showSecretSwitch(replacement, session.rack, session.mode);
        showWallDodgeStage(replacement, session.transform, session.mode,
                session.active().scoreCard.currentFrameNumber());
        session.renderer.close();
        session.renderer = replacement;
    }

    private void showSecretSwitch(BowlingRenderer renderer, List<Vec2> rack, GameMode mode) {
        if (mode != GameMode.HUNDRED_PIN || !plugin.settings().secretStrikeEnabled()
                || rack.isEmpty()) return;
        double railX = (plugin.settings().secretRailMinimumX()
                + plugin.settings().secretRailMaximumX()) * 0.5;
        double lastPinZ = rack.stream().mapToDouble(Vec2::z).max().orElse(0.0);
        renderer.showSecretSwitch(railX, lastPinZ + plugin.settings().secretSwitchOffset());
    }

    private void showWallDodgeStage(BowlingRenderer renderer, LaneTransform transform,
                                    GameMode mode, int frameNumber) {
        if (mode != GameMode.WALL_DODGE) return;
        renderer.showObstacles(wallDodgeObstacles(transform, frameNumber));
    }

    private List<Obstacle> wallDodgeObstacles(LaneTransform transform, int frameNumber) {
        return WallDodgeCourse.stage(frameNumber,
                plugin.settings().standardLaneHalfWidth(), transform.pinDistance(),
                plugin.wallDodgeSettings().wallThickness(),
                plugin.wallDodgeSettings().restitution());
    }

    private void activateTurn(Session session) {
        Participant active = session.active();
        Player activePlayer = plugin.getServer().getPlayer(active.playerId);
        if (activePlayer == null) {
            return;
        }
        removeBowlingBalls(activePlayer);
        if (!prepareBallSlot(activePlayer, active)) {
            message(activePlayer, NamedTextColor.RED,
                    "投球用ボールを受け取る空きスロットがないため退出しました。");
            removeParticipant(session, active, true);
            return;
        }
        for (int index = 0; index < session.participants.size(); index++) {
            Participant participant = session.participants.get(index);
            Player player = plugin.getServer().getPlayer(participant.playerId);
            if (player == null) continue;
            removeBowlingBalls(player);
            if (participant == active) {
                player.getInventory().setItem(participant.ballSlot,
                        createBall(participant.clientProfile, session.mode));
                player.getInventory().setHeldItemSlot(participant.ballSlot);
                player.teleport(session.transform.playerSpawn(session.lane));
            } else {
                double x = (index - (session.participants.size() - 1) / 2.0) * 0.9;
                Location waiting = session.transform.worldPosition(new Vec2(x, -2.0), 0.0);
                waiting.setYaw(session.lane.playerSpawn().yaw());
                waiting.setPitch(session.lane.playerSpawn().pitch());
                player.teleport(waiting);
                player.sendActionBar(Component.text(active.playerName + " の投球です",
                        NamedTextColor.GRAY));
            }
        }
        session.renderer.resetRack(active.standing);
        showWallDodgeStage(session.renderer, session.transform, session.mode,
                active.scoreCard.currentFrameNumber());
        session.state = SessionState.AIMING;
        session.lastInputNanos = 0L;
        activePlayer.sendActionBar(Component.text(active.playerName + " のターン — 第"
                + active.scoreCard.currentFrameNumber() + "フレーム", NamedTextColor.GREEN));
    }

    private boolean prepareBallSlot(Player player, Participant participant) {
        ItemStack current = player.getInventory().getItem(participant.ballSlot);
        if (current == null || current.getType().isAir() || isBall(current)) {
            return true;
        }
        int replacement = player.getInventory().firstEmpty();
        if (replacement < 0) return false;
        participant.ballSlot = replacement;
        return true;
    }

    private void broadcast(Session session, String text, NamedTextColor color) {
        for (Participant participant : session.participants) {
            Player player = plugin.getServer().getPlayer(participant.playerId);
            if (player != null) message(player, color, text);
        }
    }

    /** Essential effects bypass distance attenuation so they remain audible at the foul line. */
    private void playForParticipants(Session session, Sound sound, float volume, float pitch) {
        for (Participant participant : session.participants) {
            Player listener = plugin.getServer().getPlayer(participant.playerId);
            if (listener != null) {
                listener.playSound(listener.getLocation(), sound, volume, pitch);
            }
        }
    }

    public void leave(Player player) {
        Session session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            message(player, NamedTextColor.RED, "ゲームへ参加していません。");
            return;
        }
        Participant participant = session.participant(player.getUniqueId());
        if (participant != null) removeParticipant(session, participant, true);
        message(player, NamedTextColor.YELLOW, "ボウリングを退出しました。");
    }

    public int activeSessions() {
        return byLane.size();
    }

    public int activeHundredPinSessions() {
        return (int) byLane.values().stream()
                .filter(session -> session.mode == GameMode.HUNDRED_PIN).count();
    }

    public boolean isLaneBusy(String laneId) {
        return byLane.containsKey(laneId);
    }

    public RenderingProfiler.Snapshot renderingProfile() {
        return renderingProfiler.snapshot();
    }

    public ClientProfile clientProfile(Player player) {
        Session session = byPlayer.get(player.getUniqueId());
        Participant participant = session == null ? null : session.participant(player.getUniqueId());
        return participant == null ? crossplay.profile(player.getUniqueId()) : participant.clientProfile;
    }

    public double yawSensitivity(ClientProfile profile) {
        if (!profile.bedrock()) return plugin.settings().javaYawSensitivity();
        return switch (profile.input()) {
            case TOUCH -> plugin.settings().bedrockTouchSensitivity();
            case CONTROLLER, MOTION -> plugin.settings().bedrockControllerSensitivity();
            case KEYBOARD_MOUSE -> plugin.settings().bedrockKeyboardSensitivity();
            case UNKNOWN -> plugin.settings().bedrockUnknownSensitivity();
        };
    }

    private Optional<LaneDefinition> selectLane(String requested, GameMode mode) {
        if (requested != null) {
            return laneManager.find(requested)
                    .filter(LaneDefinition::enabled)
                    .filter(lane -> lane.modes().contains(mode))
                    .filter(lane -> !byLane.containsKey(lane.id()));
        }
        return laneManager.all().stream()
                .filter(LaneDefinition::enabled)
                .filter(lane -> lane.modes().contains(mode))
                .filter(lane -> !byLane.containsKey(lane.id()))
                .findFirst();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isBall(event.getItem())) {
            return;
        }
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session == null || !session.started
                || !session.active().playerId.equals(event.getPlayer().getUniqueId())) return;
        boolean use = event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean alternative = event.getAction() == Action.LEFT_CLICK_AIR
                || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!use && !(alternative && session.active().clientProfile.bedrock()
                && plugin.settings().alternativeBedrockInput())) return;
        event.setCancelled(true);
        handleInput(event.getPlayer(), session);
    }

    private void handleInput(Player player, Session session) {
        long now = System.nanoTime();
        long debounce = plugin.settings().inputDebounceMillis() * 1_000_000L;
        if (now - session.lastInputNanos < debounce) return;
        session.lastInputNanos = now;
        if (session.state == SessionState.AIMING) {
            session.state = SessionState.CHARGING;
            session.chargeStartedNanos = now;
            session.active().yawTracker.reset(player.getYaw());
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.3f);
        } else if (session.state == SessionState.CHARGING) {
            sampleYaw(player, session);
            safeRelease(player, session, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() || !plugin.settings().sneakReleaseSpinEnabled()) return;
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session == null || !session.started || session.state != SessionState.CHARGING
                || !session.active().playerId.equals(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        sampleYaw(event.getPlayer(), session);
        safeRelease(event.getPlayer(), session, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isBall(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean hotbarBall = event.getHotbarButton() >= 0
                && isBall(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        if (isBall(event.getCurrentItem()) || isBall(event.getCursor()) || hotbarBall) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isBall(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isBall(event.getMainHandItem()) || isBall(event.getOffHandItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session != null && session.started
                && session.active().playerId.equals(event.getPlayer().getUniqueId())
                && event.getNewSlot() != session.active().ballSlot) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { cleanupByPlayer(event.getPlayer(), false); }
    @EventHandler public void onKick(PlayerKickEvent event) { cleanupByPlayer(event.getPlayer(), false); }
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isBall);
        cleanupByPlayer(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session != null && event.getPlayer().getWorld() != session.transform.world()) {
            Participant participant = session.participant(event.getPlayer().getUniqueId());
            if (participant != null) removeParticipant(session, participant, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session == null || !session.started || event.getTo() == null
                || event.getTo().getWorld() != session.transform.world()) {
            return;
        }
        Location to = event.getTo();
        if (event.getFrom().getX() == to.getX() && event.getFrom().getZ() == to.getZ()) {
            return;
        }
        Vec2 local = session.transform.localPosition(to);
        Vec2 clamped = ApproachBounds.clampToFoulLine(local);
        if (clamped.equals(local)) {
            return;
        }
        Location corrected = session.transform.worldPosition(clamped, 0.0);
        corrected.setY(to.getY());
        corrected.setYaw(to.getYaw());
        corrected.setPitch(to.getPitch());
        event.setTo(corrected);
        if (tick - session.lastFoulWarningTick >= 20L) {
            session.lastFoulWarningTick = tick;
            event.getPlayer().sendActionBar(Component.text(
                    "ファウルラインより前には進めません",
                    NamedTextColor.RED));
        }
    }

    private void release(Player player, Session session, boolean sneakRelease) {
        if (session.state != SessionState.CHARGING) return;
        Participant participant = session.active();
        long heldNanos = Math.max(0L, System.nanoTime() - session.chargeStartedNanos);
        long maximumChargeNanos = plugin.settings().maximumChargeMillis() * 1_000_000L;
        double charge = Math.min(1.0, heldNanos / (double) maximumChargeNanos);
        double minimumSpeed = plugin.settings().minimumThrowSpeed();
        double maximumSpeed = plugin.settings().maximumThrowSpeed();
        double speed = minimumSpeed + (maximumSpeed - minimumSpeed) * Math.sin(charge * Math.PI / 2.0);
        double yawChange = participant.yawTracker.accumulatedDegrees()
                * yawSensitivity(participant.clientProfile);
        if (Math.abs(yawChange) < plugin.settings().spinDeadzoneDegrees()) yawChange = 0.0;
        double maximumSpin = plugin.settings().maximumSpin();
        double spin = Math.max(-maximumSpin, Math.min(maximumSpin,
                yawChange * plugin.settings().spinPerYawDegree()));

        Vec2 direction = session.transform.localDirection(player.getLocation().getDirection());
        if (direction.z() < 0.20) direction = new Vec2(direction.x(), 0.20);
        direction = direction.normalizedOr(new Vec2(0, 1));
        Vec2 playerLocal = session.transform.localPosition(player.getLocation());
        double startLimit = session.mode == GameMode.HUNDRED_PIN
                ? plugin.settings().secretRailMaximumX()
                : plugin.settings().standardLaneHalfWidth() - 0.14;
        Vec2 start = new Vec2(Math.max(-startLimit, Math.min(startLimit, playerLocal.x())), 0.0);
        if (sneakRelease) {
            spin = SpinRelease.sneakSpin(charge, spin, start.x(),
                    plugin.settings().sneakReleaseMinimumSpin(),
                    plugin.settings().sneakReleaseMaximumSpin(), maximumSpin);
        }

        SimulationConfig config = session.mode == GameMode.HUNDRED_PIN
                ? plugin.physicsConfig().forHundredPin()
                : plugin.physicsConfig();
        List<PinState> physicsPins = new ArrayList<>();
        session.physicsToRack.clear();
        int physicsId = 0;
        for (int rackId : participant.standing.stream().sorted().toList()) {
            physicsPins.add(new PinState(physicsId, session.rack.get(rackId), config.pinRadius(),
                    config.pinFallenSweepRadius(), config.pinMass()));
            session.physicsToRack.put(physicsId++, rackId);
        }
        BallState ball = new BallState(start, direction.multiply(speed), spin,
                config.ballRadius(), config.ballMass());
        double halfWidth = session.mode == GameMode.HUNDRED_PIN
                ? plugin.settings().hundredPinLaneHalfWidth()
                : plugin.settings().standardLaneHalfWidth();
        double gutterWidth = session.mode == GameMode.HUNDRED_PIN
                ? plugin.settings().hundredPinGutterWidth()
                : plugin.settings().standardGutterWidth();
        double lastPinZ = session.rack.stream().mapToDouble(Vec2::z).max()
                .orElse(session.transform.pinDistance());
        boolean secretRailPhysics = session.mode == GameMode.HUNDRED_PIN
                && plugin.settings().secretStrikeEnabled();
        List<Obstacle> obstacles = session.mode == GameMode.WALL_DODGE
                ? wallDodgeObstacles(session.transform, participant.scoreCard.currentFrameNumber())
                : List.of();
        boolean sideBumpers = session.mode == GameMode.WALL_DODGE
                ? plugin.wallDodgeSettings().sideBumpers()
                : plugin.settings().sideBumpers(session.mode);
        SimulationWorld world = new SimulationWorld(config,
                new LaneGeometry(halfWidth, lastPinZ + 2.0, gutterWidth,
                        sideBumpers,
                        secretRailPhysics ? plugin.settings().secretRailMinimumX() : 0.0,
                        secretRailPhysics ? plugin.settings().secretRailMaximumX() : 0.0),
                ball, physicsPins, obstacles);
        session.secretStrikeTracker = session.mode == GameMode.HUNDRED_PIN
                && plugin.settings().secretStrikeEnabled()
                ? new SecretStrikeTracker(plugin.settings().secretRailMinimumX(),
                plugin.settings().secretRailMaximumX(), plugin.settings().secretArmingMaximumZ(),
                lastPinZ + plugin.settings().secretSwitchOffset())
                : null;
        session.simulationId = plugin.physicsRuntime().startSimulation(world);
        session.rollStartedTick = tick;
        session.renderer.spawnBall(start);
        session.state = SessionState.ROLLING;
        player.playSound(player, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.7f);
        String style = sneakRelease ? "SHIFTカーブ" : "通常投球";
        player.sendActionBar(Component.text(String.format("%s  速度 %.1f m/s  スピン %.1f",
                        style, speed, spin),
                NamedTextColor.AQUA));
    }

    private void tick() {
        tick++;
        for (Session session : new LinkedHashSet<>(byLane.values())) {
            if (!session.started) {
                if (tick % 20L == 0L) updateLobbyBossBar(session);
                continue;
            }
            Participant participant = session.active();
            Player player = plugin.getServer().getPlayer(participant.playerId);
            if (player == null) {
                removeParticipant(session, participant, false);
                continue;
            }
            try {
                if ((session.state == SessionState.AIMING || session.state == SessionState.CHARGING)
                        && plugin.settings().guideParticles() && tick % 5L == 0L) {
                    showGuide(player, session);
                }
                if (session.state == SessionState.CHARGING) {
                    sampleYaw(player, session);
                    long held = System.nanoTime() - session.chargeStartedNanos;
                    long maximumChargeNanos = plugin.settings().maximumChargeMillis() * 1_000_000L;
                    double ratio = Math.min(1.0, held / (double) maximumChargeNanos);
                    int bars = (int) Math.round(ratio * 20.0);
                    player.sendActionBar(Component.text("投球 " + "■".repeat(bars) + "□".repeat(20 - bars)
                                    + "  |  Shift: カーブリリース",
                            NamedTextColor.GOLD));
                    if (held >= maximumChargeNanos) safeRelease(player, session, false);
                } else if (session.state == SessionState.AIMING && tick % 20L == 0L) {
                    showAimControls(player, session);
                } else if (session.state == SessionState.ROLLING) {
                    updateRolling(player, session);
                } else if (session.state == SessionState.RESETTING && tick >= session.resetAtTick) {
                    resetOrComplete(player, session);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Bowling session failed for " + participant.playerName, exception);
                abortSession(player, session, "ゲーム処理でエラーが発生したため安全に終了しました。");
            }
        }
    }

    private void sampleYaw(Player player, Session session) {
        session.active().yawTracker.sample(player.getYaw());
    }

    private void showGuide(Player player, Session session) {
        Vec2 direction = session.transform.localDirection(player.getLocation().getDirection());
        if (direction.z() < 0.2) direction = new Vec2(direction.x(), 0.2);
        direction = direction.normalizedOr(new Vec2(0, 1));
        Vec2 origin = session.transform.localPosition(player.getLocation());
        for (int index = 1; index <= 12; index++) {
            Vec2 point = origin.add(direction.multiply(index * 0.7));
            Location location = session.transform.worldPosition(point, 0.06);
            player.spawnParticle(Particle.END_ROD, location, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void showAimControls(Player player, Session session) {
        String activate = session.active().clientProfile.bedrock()
                && plugin.settings().alternativeBedrockInput() ? "使用/攻撃" : "右クリック";
        player.sendActionBar(Component.text(
                "← 左右移動 → | 視点: 方向 | " + activate + ": パワー | Shift: カーブ投球",
                NamedTextColor.AQUA));
    }

    private void safeRelease(Player player, Session session, boolean sneakRelease) {
        try {
            release(player, session, sneakRelease);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not release bowling ball for " + session.active().playerName, exception);
            abortSession(player, session, "投球を開始できなかったため安全に終了しました。");
        }
    }

    private void updateRolling(Player player, Session session) {
        if (plugin.physicsRuntime().simulationFailed(session.simulationId)) {
            plugin.physicsRuntime().lastFailure().ifPresent(failure ->
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Physics simulation failed for " + session.active().playerName
                                    + " in lane " + session.lane.id(), failure));
            abortSession(player, session, "物理シミュレーションでエラーが発生しました。");
            return;
        }
        if (tick - session.rollStartedTick > plugin.settings().turnTimeoutSeconds() * 20L) {
            abortSession(player, session, "投球が制限時間内に終了しませんでした。");
            return;
        }
        Optional<PhysicsSnapshot> optional = plugin.physicsRuntime().snapshot(session.simulationId);
        if (optional.isEmpty()) {
            abortSession(player, session, "物理シミュレーションが失われました。");
            return;
        }
        PhysicsSnapshot snapshot = optional.get();
        session.renderer.update(snapshot, session.physicsToRack);
        if (snapshot.ball().active() && tick % 8L == 0L) {
            playForParticipants(session, Sound.ENTITY_MINECART_RIDING, 0.18f, 0.72f);
        }
        if (session.secretStrikeTracker != null
                && session.secretStrikeTracker.sample(snapshot.ball().position(),
                snapshot.ball().active())) {
            finishSecretStrike(player, session);
            return;
        }
        int collisionSounds = 0;
        int wallCollisions = 0;
        for (PhysicsEvent event : plugin.physicsRuntime().drainEvents(session.simulationId)) {
            if (event.type() == PhysicsEvent.Type.BALL_PIN_COLLISION
                    || event.type() == PhysicsEvent.Type.PIN_PIN_COLLISION) {
                collisionSounds++;
            } else if (event.type() == PhysicsEvent.Type.GUTTER_ENTERED) {
                player.sendActionBar(Component.text("ガター", NamedTextColor.RED));
            } else if (event.type() == PhysicsEvent.Type.BUMPER_COLLISION) {
                Location impact = session.transform.worldPosition(snapshot.ball().position(), 0.10);
                session.transform.world().playSound(impact,
                        Sound.BLOCK_SLIME_BLOCK_HIT, 0.75f, 0.85f);
                playForParticipants(session, Sound.BLOCK_SLIME_BLOCK_HIT, 0.45f, 0.85f);
                player.sendActionBar(Component.text("バンパー！", NamedTextColor.AQUA));
            } else if (event.type() == PhysicsEvent.Type.OBSTACLE_COLLISION) {
                wallCollisions++;
            }
        }
        if (wallCollisions > 0) {
            Location impact = session.transform.worldPosition(snapshot.ball().position(), 0.15);
            session.transform.world().playSound(impact,
                    Sound.BLOCK_ANVIL_LAND, 0.55f, 1.45f);
            playForParticipants(session, Sound.BLOCK_ANVIL_LAND, 0.55f, 1.45f);
            player.sendActionBar(Component.text("壁に接触！", NamedTextColor.RED));
        }
        if (collisionSounds > 0) {
            float volume = (float) Math.min(1.0, 0.25 + Math.log1p(collisionSounds) * 0.12);
            session.transform.world().playSound(session.transform.worldPosition(
                    snapshot.ball().position(), 0), Sound.BLOCK_BONE_BLOCK_BREAK, volume, 1.1f);
            playForParticipants(session, Sound.BLOCK_BONE_BLOCK_BREAK,
                    Math.max(0.45f, volume * 0.75f), 1.1f);
        }
        if (snapshot.settled()) finishRoll(player, session, snapshot);
    }

    private void finishSecretStrike(Player player, Session session) {
        Participant participant = session.active();
        int fallen = participant.standing.size();
        int rollNumber = participant.scoreCard.rollInFrame();
        participant.standing.clear();
        try {
            participant.scoreCard.recordRoll(fallen, false);
        } catch (RuntimeException exception) {
            abortSession(player, session, "隠しストライクの得点処理に失敗しました。");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Secret strike score mismatch", exception);
            return;
        }
        plugin.physicsRuntime().removeSimulation(session.simulationId);
        session.simulationId = null;
        session.renderer.removeBall();
        session.renderer.playSecretStrikeCollapse(fullRack(session.rack.size()));
        session.state = SessionState.RESETTING;
        session.resetAtTick = tick + Math.max(45, plugin.settings().pinsetterResetTicks());
        participant.secretStrikes++;
        participant.strikeStreak = rollNumber == 0 ? participant.strikeStreak + 1 : 0;
        showTitle(player,
                Component.text(rollNumber == 0 ? "SECRET STRIKE!" : "SECRET SPARE!",
                        NamedTextColor.LIGHT_PURPLE),
                Component.text("隠しスイッチ作動 — " + fallen + " ピン", NamedTextColor.GOLD),
                5, 45, 15);
        focusOnPins(player, session);
    }

    private void finishRoll(Player player, Session session, PhysicsSnapshot snapshot) {
        Participant participant = session.active();
        Set<Integer> knocked = RollJudgement.knockedPins(
                snapshot, session.physicsToRack, participant.standing);
        int before = participant.standing.size();
        int rollNumber = participant.scoreCard.rollInFrame();
        participant.standing.removeAll(knocked);
        int fallen = knocked.size();
        try {
            participant.scoreCard.recordRoll(fallen, snapshot.ball().inGutter());
        } catch (RuntimeException exception) {
            abortSession(player, session, "倒ピン数の整合性エラーが発生しました。");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Score state mismatch", exception);
            return;
        }
        plugin.physicsRuntime().removeSimulation(session.simulationId);
        session.simulationId = null;
        session.renderer.removeBall();
        session.renderer.beginPinsetter();
        session.state = SessionState.RESETTING;
        session.resetAtTick = tick + plugin.settings().pinsetterResetTicks();

        if (fallen == session.rack.size() && before == session.rack.size()) {
            participant.strikeStreak++;
            String strikeName = participant.strikeStreak == 1 ? "STRIKE!"
                    : participant.strikeStreak == 2 ? "DOUBLE!"
                    : participant.strikeStreak == 3 ? "TURKEY!" : participant.strikeStreak + " BAGGER!";
            showTitle(player, Component.text(strikeName, NamedTextColor.GOLD),
                    Component.text("現在 " + participant.scoreCard.score() + " 点", NamedTextColor.YELLOW),
                    5, 30, 10);
            session.transform.world().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            focusOnPins(player, session);
        } else if (rollNumber == 1 && participant.standing.isEmpty()) {
            participant.strikeStreak = 0;
            showTitle(player, Component.text("SPARE", NamedTextColor.AQUA), Component.empty(), 5, 20, 10);
        } else if (session.mode != GameMode.HUNDRED_PIN && rollNumber == 0
                && TenPinRackRules.isSplit(participant.standing)) {
            participant.strikeStreak = 0;
            showTitle(player, Component.text("SPLIT", NamedTextColor.RED),
                    Component.text(fallen + " ピン", NamedTextColor.WHITE), 5, 20, 10);
        } else {
            participant.strikeStreak = 0;
            player.sendActionBar(Component.text(fallen + " ピン / 現在 " + participant.scoreCard.score() + " 点",
                    NamedTextColor.WHITE));
        }
        if (session.mode == GameMode.HUNDRED_PIN && fallen >= 40) {
            playHundredPinCascade(player, session, fallen);
        }
    }


    private void resetOrComplete(Player player, Session session) {
        Participant participant = session.active();
        if (participant.scoreCard.needsFullRack()) {
            participant.standing = fullRack(session.rack.size());
        }
        if (session.participants.stream().allMatch(value -> value.scoreCard.complete())) {
            completeGame(session);
            return;
        }
        if (participant.scoreCard.complete() || participant.scoreCard.rollInFrame() == 0) {
            advanceTurn(session);
        } else {
            activateTurn(session);
        }
    }

    private void advanceTurn(Session session) {
        int candidate = TurnRotation.nextIncomplete(session.activeIndex,
                session.participants.stream().map(value -> value.scoreCard.complete()).toList());
        if (candidate < 0) completeGame(session);
        else {
            session.activeIndex = candidate;
            activateTurn(session);
        }
    }

    private void completeGame(Session session) {
        int winningScore = session.participants.stream()
                .mapToInt(value -> value.scoreCard.score()).max().orElse(0);
        boolean multiplayer = session.participants.size() > 1;
        Instant completedAt = Instant.now();
        for (Participant participant : session.participants) {
            int score = participant.scoreCard.score();
            Player player = plugin.getServer().getPlayer(participant.playerId);
            if (player != null) {
                boolean winner = multiplayer && score == winningScore;
                showTitle(player, Component.text(winner ? "WINNER!" : "GAME COMPLETE",
                                winner ? NamedTextColor.GOLD : NamedTextColor.AQUA),
                        Component.text(score + " 点", NamedTextColor.WHITE), 10, 50, 15);
            }
            CompletedGame completed = new CompletedGame(
                    participant.playerId, participant.playerName, session.id, session.mode, score,
                    participant.scoreCard.frameScores(), participant.scoreCard.strikes(),
                    participant.scoreCard.spares(), participant.scoreCard.gutters(),
                    multiplayer && score == winningScore, completedAt);
            plugin.supplyIo(() -> {
                plugin.database().saveCompletedGame(completed);
                return null;
            }).whenComplete((ignored, throwable) -> plugin.runSync(() -> {
                if (throwable != null) plugin.reportAsyncFailure("completed game save", throwable);
            }));
        }
        cleanup(session, true);
    }

    private void abortSession(Player player, Session session, String reason) {
        broadcast(session, reason, NamedTextColor.RED);
        cleanup(session, true);
    }

    private void cleanupByPlayer(Player player, boolean returnPlayer) {
        Session session = byPlayer.get(player.getUniqueId());
        Participant participant = session == null ? null : session.participant(player.getUniqueId());
        if (participant != null) removeParticipant(session, participant, returnPlayer);
    }

    private void removeParticipant(Session session, Participant participant, boolean returnPlayer) {
        boolean wasActive = session.started && session.active() == participant;
        int removedIndex = session.participants.indexOf(participant);
        byPlayer.remove(participant.playerId);
        session.participants.remove(participant);
        Player player = plugin.getServer().getPlayer(participant.playerId);
        if (player != null) restoreParticipant(player, participant, returnPlayer);
        if (session.participants.isEmpty()) {
            cleanup(session, false);
            return;
        }
        if (removedIndex < session.activeIndex) session.activeIndex--;
        if (session.activeIndex >= session.participants.size()) session.activeIndex = 0;
        broadcast(session, participant.playerName + " が退出しました。",
                NamedTextColor.YELLOW);
        if (!session.started) updateLobbyBossBar(session);
        if (wasActive) {
            if (session.simulationId != null) plugin.physicsRuntime().removeSimulation(session.simulationId);
            session.simulationId = null;
            session.renderer.removeBall();
            activateTurn(session);
        }
    }

    private void cleanup(Session session, boolean returnPlayer) {
        if (byLane.get(session.lane.id()) != session) return;
        byLane.remove(session.lane.id());
        if (session.simulationId != null) plugin.physicsRuntime().removeSimulation(session.simulationId);
        hideLobbyBossBar(session);
        session.renderer.close();
        for (Participant participant : List.copyOf(session.participants)) {
            byPlayer.remove(participant.playerId);
            Player player = plugin.getServer().getPlayer(participant.playerId);
            if (player != null) restoreParticipant(player, participant, returnPlayer);
        }
        stopTickerIfIdle();
    }

    private void restoreParticipant(Player player, Participant participant, boolean returnPlayer) {
        removeBowlingBalls(player);
        player.getInventory().setHeldItemSlot(participant.previousHeldSlot);
        if (returnPlayer && participant.returnLocation.getWorld() != null) {
            player.teleport(participant.returnLocation);
        }
    }

    private ItemStack createBall(ClientProfile profile, GameMode mode) {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        String ballName = switch (mode) {
            case STANDARD -> "ボウリングボール";
            case HUNDRED_PIN -> "100ピン ボウリングボール";
            case WALL_DODGE -> "かべよけ ボウリングボール";
        };
        meta.displayName(Component.text(ballName, NamedTextColor.AQUA));
        String operation = profile.bedrock() && plugin.settings().alternativeBedrockInput()
                ? "使用 / 攻撃：構える・リリース" : "右クリック：構える・リリース";
        meta.lore(List.of(Component.text("左右移動 + 視点：立ち位置・投球方向", NamedTextColor.GRAY),
                Component.text(operation, NamedTextColor.GRAY),
                Component.text("構え中にShift：タイミング式カーブ投球", NamedTextColor.LIGHT_PURPLE),
                Component.text("モード: " + mode.displayName(), NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(ballKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isBall(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ballKey, PersistentDataType.BYTE);
    }

    private void removeBowlingBalls(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isBall(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        }
    }

    private void ensureTicker() {
        if (ticker == null) ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void stopTickerIfIdle() {
        if (byPlayer.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void updateLobbyBossBar(Session session) {
        if (session.started || session.maximumPlayers <= 1) {
            hideLobbyBossBar(session);
            return;
        }
        float progress = Math.max(0.0f, Math.min(1.0f,
                session.participants.size() / (float) session.maximumPlayers));
        session.lobbyBossBar.name(Component.text("ボウリング募集 " + session.lane.id()
                + "  " + session.participants.size() + "/" + session.maximumPlayers
                + "人  |  /bowmenu で参加", NamedTextColor.YELLOW));
        session.lobbyBossBar.progress(progress);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.showBossBar(session.lobbyBossBar);
        }
    }

    private void hideLobbyBossBar(Session session) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.hideBossBar(session.lobbyBossBar);
        }
    }

    private static Set<Integer> fullRack(int size) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int i = 0; i < size; i++) ids.add(i);
        return ids;
    }

    private void focusOnPins(Player player, Session session) {
        if (!session.rack.isEmpty()) {
            Location target = session.transform.worldPosition(session.rack.get(session.rack.size() / 2), 0.25);
            Location eye = player.getEyeLocation();
            Location facing = eye.clone().setDirection(target.toVector().subtract(eye.toVector()));
            player.setRotation(facing.getYaw(), facing.getPitch());
        }
    }

    private void playHundredPinCascade(Player player, Session session, int fallen) {
        int emitted = 0;
        for (int rackId = 0; rackId < session.rack.size() && emitted < 24; rackId += 4) {
            session.transform.world().spawnParticle(Particle.FIREWORK,
                    session.transform.worldPosition(session.rack.get(rackId), 0.2),
                    1, 0.08, 0.04, 0.08, 0.01);
            emitted++;
        }
        player.playSound(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f,
                fallen == session.rack.size() ? 1.25f : 0.9f);
    }

    private static void message(Player player, NamedTextColor color, String text) {
        player.sendMessage(PREFIX.append(Component.text(text, color)));
    }

    private static void showTitle(Player player, Component title, Component subtitle,
                                  int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L))));
    }

    @Override
    public void close() {
        for (Session session : List.copyOf(byLane.values())) cleanup(session, false);
        if (ticker != null) ticker.cancel();
        ticker = null;
    }

    private enum SessionState { AIMING, CHARGING, ROLLING, RESETTING }

    public record LobbyView(UUID matchId, String laneId, GameMode mode,
                            int players, int maximumPlayers, String ownerName) {
    }

    private static final class Participant {
        private final UUID playerId;
        private final String playerName;
        private final Location returnLocation;
        private int ballSlot;
        private final int previousHeldSlot;
        private final ClientProfile clientProfile;
        private final BowlingScoreCard scoreCard;
        private final YawInputTracker yawTracker = new YawInputTracker();
        private Set<Integer> standing;
        private int strikeStreak;
        private int secretStrikes;

        private Participant(Player player, int ballSlot, ClientProfile clientProfile,
                            int rackSize) {
            this.playerId = player.getUniqueId();
            this.playerName = player.getName();
            this.returnLocation = player.getLocation().clone();
            this.ballSlot = ballSlot;
            this.previousHeldSlot = player.getInventory().getHeldItemSlot();
            this.clientProfile = clientProfile;
            this.scoreCard = new BowlingScoreCard(rackSize);
            this.standing = fullRack(rackSize);
        }
    }

    private static final class Session {
        private final UUID id;
        private final LaneDefinition lane;
        private final LaneTransform transform;
        private final List<Vec2> rack;
        private BowlingRenderer renderer;
        private final GameMode mode;
        private final int maximumPlayers;
        private final List<Participant> participants = new ArrayList<>();
        private final Map<Integer, Integer> physicsToRack = new HashMap<>();
        private SessionState state = SessionState.AIMING;
        private boolean started;
        private int activeIndex;
        private UUID simulationId;
        private long chargeStartedNanos;
        private long lastInputNanos;
        private long resetAtTick;
        private long rollStartedTick;
        private long lastFoulWarningTick = Long.MIN_VALUE / 2;
        private SecretStrikeTracker secretStrikeTracker;
        private final BossBar lobbyBossBar = BossBar.bossBar(Component.empty(), 0.0f,
                BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

        private Session(UUID id, LaneDefinition lane, LaneTransform transform,
                        List<Vec2> rack, BowlingRenderer renderer, GameMode mode,
                        int maximumPlayers, Participant firstParticipant) {
            this.id = id;
            this.lane = lane;
            this.transform = transform;
            this.rack = rack;
            this.renderer = renderer;
            this.mode = mode;
            this.maximumPlayers = maximumPlayers;
            this.participants.add(firstParticipant);
        }

        private Participant active() {
            return participants.get(activeIndex);
        }

        private Participant participant(UUID playerId) {
            return participants.stream().filter(value -> value.playerId.equals(playerId))
                    .findFirst().orElse(null);
        }
    }
}
