package com.codezhangborui.pixelRank;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.handler.EconomyHandler;
import com.codezhangborui.pixelRank.handler.LeaderboardHandler;
import com.codezhangborui.pixelRank.handler.OnlineTimeHandler;
import com.codezhangborui.pixelRank.ranking.TrinityForgeRankingSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Scheduler {

    public static void start(JavaPlugin plugin) {
        long switchInterval = Configuration.getInt("ranks.switch_interval");
        long saveInterval = Configuration.getInt("storage.save_interval");
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, OnlineTimeHandler::incrementOnlineTime, 0L, 1200L);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, LeaderboardHandler::switchLeaderboard, 0L, switchInterval * 20L);
        // 保存本体は Database.save() の中で非同期タスクへ回している
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Database::save, 0L, saveInterval * 20L);
        // 残高の収集は非同期（メインスレッドで全員分の getBalance を回すとサーバーが固まる）
        if (EconomyHandler.isAvailable()) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, EconomyHandler::refreshBalancesAsync, 100L, 1200L);
        }
        // TF のランキング取得は SQLite への同期 I/O なので必ず非同期で回し、表示はキャッシュから行う
        if (TrinityForgeRankingSource.isRegistered()) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                    TrinityForgeRankingSource::refreshAll, 40L, 600L);
        }
    }
}
