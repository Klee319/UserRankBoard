package com.codezhangborui.pixelRank.handler;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.database.RankStat;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Location;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EventListener implements Listener {

    private final JavaPlugin plugin;

    public EventListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Database.initializePlayer(event.getPlayer().getName());
        // スコアボードの表示設定を適用（読み込みは非同期）
        LeaderboardHandler.applyDefaultVisibility(plugin, event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        String playerName = event.getPlayer().getName();
        Database.initializePlayer(playerName);
        Database.increment(RankStat.MINING, playerName, 1);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        String playerName = event.getPlayer().getName();
        Database.initializePlayer(playerName);
        Database.increment(RankStat.PLACING, playerName, 1);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String playerName = event.getEntity().getName();
        Database.initializePlayer(playerName);
        Database.increment(RankStat.DEATH, playerName, 1);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // 視点回転だけの移動は数えない
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        String playerName = event.getPlayer().getName();
        Database.initializePlayer(playerName);
        long distance = Math.round(from.distance(to));
        if (distance > 0) {
            Database.increment(RankStat.MOVEMENT, playerName, distance);
        }
    }

    /**
     * ジャンプ回数。PlayerJumpEvent は Paper 独自イベントで、Bukkit には存在しない。
     */
    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        String playerName = event.getPlayer().getName();
        Database.initializePlayer(playerName);
        Database.increment(RankStat.JUMP, playerName, 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        String playerName = killer.getName();
        Database.initializePlayer(playerName);
        Database.increment(RankStat.MOB_KILL, playerName, 1);
    }
}
