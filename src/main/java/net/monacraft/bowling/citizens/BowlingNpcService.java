package net.monacraft.bowling.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.monacraft.bowling.MonaBowlingPlugin;
import net.monacraft.bowling.menu.BowlingMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class BowlingNpcService implements CommandExecutor, Listener {
    private static final String NPC_MARKER = "monabowling-menu-npc";
    private static final String DEFAULT_NAME = "ボウリング受付";
    private static final String DEFAULT_SKIN_URL =
            "https://raw.githubusercontent.com/MONAKASERVER/mona-resource-studio/main/"
                    + "assets/monabowling/bowling-pin.png";

    private final MonaBowlingPlugin plugin;
    private final BowlingMenu menu;

    public BowlingNpcService(MonaBowlingPlugin plugin, BowlingMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはNPCを置く場所でゲーム内から実行してください。");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("使い方: /setnpc");
            return true;
        }
        try {
            NPC npc = findReceptionist();
            boolean created = npc == null;
            if (created) {
                String name = plugin.getConfig().getString("citizens.npc-name", DEFAULT_NAME);
                npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
                npc.data().setPersistent(NPC_MARKER, true);
                npc.setProtected(true);
                npc.setUseMinecraftAI(false);
                SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
                skin.setFetchDefaultSkin(false);
                if (!npc.spawn(player.getLocation())) {
                    npc.destroy();
                    player.sendMessage("NPCをこの場所に召喚できませんでした。安全な足場で再実行してください。");
                    return true;
                }
            } else {
                npc.data().setPersistent(NPC_MARKER, true);
                if (npc.isSpawned()) {
                    npc.teleport(player.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                } else if (!npc.spawn(player.getLocation())) {
                    player.sendMessage("保存済みNPCをこの場所に召喚できませんでした。");
                    return true;
                }
            }

            plugin.getConfig().set("citizens.npc-id", npc.getId());
            plugin.saveConfig();
            CitizensAPI.getNPCRegistry().saveToStore();
            applyConfiguredSkin(npc);
            player.sendMessage(created
                    ? "ボウリング受付NPCを作成しました。右クリックでメニューが開きます。"
                    : "ボウリング受付NPCをこの場所へ移動しました。");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("NPC設定エラー: " + exception.getMessage());
            plugin.getLogger().warning("Could not configure bowling NPC: " + exception.getMessage());
        } catch (RuntimeException exception) {
            player.sendMessage("NPCの作成に失敗しました。サーバーログを確認してください。");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not create bowling NPC", exception);
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(NPCRightClickEvent event) {
        if (!isReceptionist(event.getNPC())) {
            return;
        }
        event.setCancelled(true);
        menu.open(event.getClicker());
    }

    private NPC findReceptionist() {
        int configuredId = plugin.getConfig().getInt("citizens.npc-id", -1);
        if (configuredId >= 0) {
            NPC configured = CitizensAPI.getNPCRegistry().getById(configuredId);
            if (configured != null && isReceptionist(configured)) {
                return configured;
            }
        }
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (isReceptionist(npc)) {
                return npc;
            }
        }
        return null;
    }

    private boolean isReceptionist(NPC npc) {
        return npc.data().get(NPC_MARKER, false);
    }

    private void applyConfiguredSkin(NPC npc) {
        String configured = plugin.getConfig().getString("citizens.skin-url", DEFAULT_SKIN_URL);
        String skinUrl = SkinUrlPolicy.requirePublicPng(configured);
        CommandSender console = plugin.getServer().getConsoleSender();
        CitizensAPI.getDefaultNPCSelector().select(console, npc);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean accepted = plugin.getServer().dispatchCommand(console, "npc skin --url " + skinUrl);
            CitizensAPI.getDefaultNPCSelector().deselect(console);
            if (!accepted) {
                plugin.getLogger().warning("Citizens rejected skin command for bowling NPC " + npc.getId());
            }
        });
    }
}
