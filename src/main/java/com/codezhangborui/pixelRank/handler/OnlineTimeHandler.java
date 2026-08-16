package com.codezhangborui.pixelRank.handler;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.database.RankStat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class OnlineTimeHandler {

    public static void incrementOnlineTime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Database.increment(RankStat.ONLINE_TIME, player.getName(), 1);
        }
    }
}
