package com.codezhangborui.pixelRank.handler;

import com.codezhangborui.pixelRank.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class OnlineTimeHandler {

    public static void incrementOnlineTime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            Database.online_time_rank.compute(name, (k, currentTime) -> (currentTime == null ? 0 : currentTime) + 1);
        }
    }
}