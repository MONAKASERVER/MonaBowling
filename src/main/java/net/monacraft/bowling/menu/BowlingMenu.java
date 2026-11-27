package net.monacraft.bowling.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.monacraft.bowling.MonaBowlingPlugin;
import net.monacraft.bowling.game.BowlingGameManager;
import net.monacraft.bowling.model.GameMode;
import net.monacraft.bowling.model.LaneDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BowlingMenu implements Listener {
    private final MonaBowlingPlugin plugin;
    private final BowlingGameManager games;

    public BowlingMenu(MonaBowlingPlugin plugin, BowlingGameManager games) {
        this.plugin = plugin;
        this.games = games;
    }

    public void open(Player player) {
        openMain(player, GameMode.STANDARD, null, 1);
    }

    private void openMain(Player player, GameMode mode, String requestedLane, int maximumPlayers) {
        List<LaneDefinition> lanes = games.availableLanes(mode);
        String laneId = lanes.stream().map(LaneDefinition::id)
                .filter(id -> id.equals(requestedLane)).findFirst()
                .orElse(lanes.isEmpty() ? null : lanes.getFirst().id());
        MainHolder holder = new MainHolder(player.getUniqueId(), mode, laneId, maximumPlayers);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("ボウリングメニュー", NamedTextColor.DARK_AQUA));
        holder.inventory = inventory;

        if (games.isInMatch(player.getUniqueId())) {
            inventory.setItem(11, item(Material.CLOCK, "参加中のゲーム",
                    games.isWaitingLobby(player.getUniqueId())
                            ? List.of("ロビーでメンバーを待っています")
                            : List.of("現在ゲーム進行中です")));
            if (games.isLobbyOwner(player.getUniqueId())) {
                inventory.setItem(13, item(Material.LIME_CONCRETE, "現在の人数で開始",
                        List.of("クリックしてゲーム開始")));
            }
            inventory.setItem(15, item(Material.BARRIER, "退出", List.of("参加中のゲームから抜ける")));
        } else {
            Material modeIcon = switch (mode) {
                case STANDARD -> Material.BONE_BLOCK;
                case HUNDRED_PIN -> Material.GOLD_BLOCK;
                case WALL_DODGE -> Material.POLISHED_BLACKSTONE_WALL;
            };
            inventory.setItem(10, item(modeIcon,
                    "モード: " + mode.displayName(), List.of("クリックで切り替え")));
            inventory.setItem(12, item(Material.COMPASS, "レーン: " + (laneId == null ? "空きなし" : laneId),
                    List.of("クリックで空きレーンを切り替え")));
            inventory.setItem(14, item(Material.PLAYER_HEAD, "人数: " + maximumPlayers + "人",
                    List.of("クリックで1〜4人を切り替え",
                            maximumPlayers == 1 ? "すぐにスタート" : "公開ロビーを作成")));
            inventory.setItem(16, item(Material.LIME_CONCRETE, "ゲームを作成",
                    laneId == null ? List.of("利用できるレーンがありません")
                            : List.of(mode.displayName(), laneId, maximumPlayers + "人用")));
        }
        inventory.setItem(20, item(Material.CHEST, "公開ロビー一覧",
                List.of("他のプレイヤーのゲームに参加")));
        inventory.setItem(22, item(Material.BOOK, "戦績", List.of("選択中モードの戦績を表示")));
        inventory.setItem(24, item(Material.NETHER_STAR, "ランキング",
                List.of("選択中モードのTOP 10を表示")));
        player.openInventory(inventory);
    }

    private void openLobbies(Player player) {
        LobbyHolder holder = new LobbyHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("参加可能なロビー", NamedTextColor.DARK_GREEN));
        holder.inventory = inventory;
        List<BowlingGameManager.LobbyView> lobbies = games.openLobbies();
        holder.lobbies = lobbies;
        for (int index = 0; index < Math.min(45, lobbies.size()); index++) {
            BowlingGameManager.LobbyView lobby = lobbies.get(index);
            inventory.setItem(index, item(Material.PAPER,
                    lobby.laneId() + " / " + lobby.mode().displayName(),
                    List.of("作成者: " + lobby.ownerName(),
                            "人数: " + lobby.players() + "/" + lobby.maximumPlayers(),
                            "クリックで参加")));
        }
        if (lobbies.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, "公開ロビーはありません", List.of()));
        }
        inventory.setItem(49, item(Material.ARROW, "戻る", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof MainHolder holder) {
            event.setCancelled(true);
            if (!holder.owner.equals(player.getUniqueId())) return;
            int slot = event.getRawSlot();
            if (slot == 10 && !games.isInMatch(player.getUniqueId())) {
                GameMode mode = nextMode(holder.mode);
                openMain(player, mode, null, holder.maximumPlayers);
            } else if (slot == 12 && !games.isInMatch(player.getUniqueId())) {
                List<LaneDefinition> lanes = games.availableLanes(holder.mode);
                String next = nextLane(lanes, holder.laneId);
                openMain(player, holder.mode, next, holder.maximumPlayers);
            } else if (slot == 14 && !games.isInMatch(player.getUniqueId())) {
                int limit = plugin.settings().maximumPlayersPerLane();
                openMain(player, holder.mode, holder.laneId, holder.maximumPlayers % limit + 1);
            } else if (slot == 16 && !games.isInMatch(player.getUniqueId()) && holder.laneId != null) {
                player.closeInventory();
                games.createMatch(player, holder.laneId, holder.mode, holder.maximumPlayers);
            } else if (slot == 13 && games.isLobbyOwner(player.getUniqueId())) {
                player.closeInventory();
                games.startLobby(player);
            } else if (slot == 15 && games.isInMatch(player.getUniqueId())) {
                player.closeInventory();
                games.leave(player);
            } else if (slot == 20) {
                openLobbies(player);
            } else if (slot == 22) {
                player.closeInventory();
                player.performCommand("bowling stats " + holder.mode.configKey());
            } else if (slot == 24) {
                player.closeInventory();
                player.performCommand("bowling top " + holder.mode.configKey());
            }
        } else if (event.getInventory().getHolder() instanceof LobbyHolder holder) {
            event.setCancelled(true);
            if (!holder.owner.equals(player.getUniqueId())) return;
            int slot = event.getRawSlot();
            if (slot == 49) {
                open(player);
            } else if (slot >= 0 && slot < holder.lobbies.size() && slot < 45) {
                BowlingGameManager.LobbyView lobby = holder.lobbies.get(slot);
                player.closeInventory();
                games.joinLobby(player, lobby.laneId());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MainHolder
                || event.getInventory().getHolder() instanceof LobbyHolder) {
            event.setCancelled(true);
        }
    }

    private static String nextLane(List<LaneDefinition> lanes, String current) {
        if (lanes.isEmpty()) return null;
        for (int index = 0; index < lanes.size(); index++) {
            if (lanes.get(index).id().equals(current)) {
                return lanes.get((index + 1) % lanes.size()).id();
            }
        }
        return lanes.getFirst().id();
    }

    private GameMode nextMode(GameMode current) {
        GameMode[] modes = GameMode.values();
        for (int offset = 1; offset <= modes.length; offset++) {
            GameMode candidate = modes[(current.ordinal() + offset) % modes.length];
            if (plugin.settings().enabledModes().contains(candidate)) return candidate;
        }
        return current;
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static final class MainHolder implements InventoryHolder {
        private final UUID owner;
        private final GameMode mode;
        private final String laneId;
        private final int maximumPlayers;
        private Inventory inventory;

        private MainHolder(UUID owner, GameMode mode, String laneId, int maximumPlayers) {
            this.owner = owner;
            this.mode = mode;
            this.laneId = laneId;
            this.maximumPlayers = maximumPlayers;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class LobbyHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;
        private List<BowlingGameManager.LobbyView> lobbies = List.of();

        private LobbyHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
