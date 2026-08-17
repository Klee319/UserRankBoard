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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EventListener implements Listener {

    private final JavaPlugin plugin;
    private final MovementAccumulator movement = new MovementAccumulator();

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

    /**
     * 移動距離。
     *
     * <p><b>1 イベントごとに丸めてはいけない</b>: 1 tick あたりの移動量は歩行 0.215 /
     * スプリント 0.28 ブロックしかなく、旧実装の {@code Math.round} では全部 0 に落ちて
     * <b>歩行もスプリントも 1 ブロックも計上されていなかった</b>。端数は
     * {@link MovementAccumulator} で持ち越す。</p>
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // 視点回転だけの移動は数えない(座標が完全に同じなら距離0)。
        // ブロック座標で比べると、ブロックをまたがない移動まで丸ごと捨ててしまう。
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        // PlayerTeleportEvent は PlayerMoveEvent の子なのでここへも飛ぶ。ワールドをまたぐと
        // Location#distance が IllegalArgumentException を投げるため、先に弾く。
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        long distance = movement.accumulate(player.getUniqueId(), from.distance(to));
        if (distance <= 0) {
            return;
        }
        String playerName = player.getName();
        Database.initializePlayer(playerName);
        Database.increment(RankStat.MOVEMENT, playerName, distance);
    }

    /** 退出したプレイヤーの持ち越し端数(1ブロック未満)を捨てる。 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        movement.forget(event.getPlayer().getUniqueId());
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
