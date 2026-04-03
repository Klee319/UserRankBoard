package com.codezhangborui.pixelRank;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.handler.EconomyHandler;
import com.codezhangborui.pixelRank.handler.LeaderboardHandler;
import com.codezhangborui.pixelRank.handler.OnlineTimeHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Scheduler {

    public static void start(JavaPlugin plugin) {
        long switchInterval = Configuration.getInt("ranks.switch_interval");
        long saveInterval = Configuration.getInt("storage.save_interval");
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, OnlineTimeHandler::incrementOnlineTime, 0L, 1200L);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, LeaderboardHandler::switchLeaderboard, 0L, switchInterval * 20L);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Database::save, 0L, saveInterval * 20L);
        // Refresh economy balances cache every 60 seconds (if Vault is available)
        if (EconomyHandler.isAvailable()) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, EconomyHandler::refreshBalances, 1200L, 1200L);
        }
    }
}