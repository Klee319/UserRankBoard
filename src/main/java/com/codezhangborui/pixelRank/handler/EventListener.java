package com.codezhangborui.pixelRank.handler;

import com.codezhangborui.pixelRank.database.Database;
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
        String playerName = event.getPlayer().getName();
        initializePlayerData(playerName);
        // Apply default scoreboard visibility setting
        LeaderboardHandler.applyDefaultVisibility(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        String playerName = event.getPlayer().getName();
        initializePlayerData(playerName);
        Database.mining_rank.put(playerName, Database.mining_rank.getOrDefault(playerName, 0L) + 1);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        String playerName = event.getPlayer().getName();
        initializePlayerData(playerName);
        Database.placing_rank.put(playerName, Database.placing_rank.getOrDefault(playerName, 0L) + 1);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String playerName = event.getEntity().getName();
        initializePlayerData(playerName);
        Database.death_rank.put(playerName, Database.death_rank.getOrDefault(playerName, 0L) + 1);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Only count actual position changes (ignore head rotation)
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        String playerName = event.getPlayer().getName();
        initializePlayerData(playerName);
        long distance = Math.round(from.distance(to));
        if (distance > 0) {
            Database.movement_rank.put(playerName, Database.movement_rank.getOrDefault(playerName, 0L) + distance);
        }
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
        initializePlayerData(playerName);
        Database.mob_kill_rank.put(playerName, Database.mob_kill_rank.getOrDefault(playerName, 0L) + 1);
    }

    private void initializePlayerData(String playerName) {
        Database.mining_rank.putIfAbsent(playerName, 0L);
        Database.placing_rank.putIfAbsent(playerName, 0L);
        Database.online_time_rank.putIfAbsent(playerName, 0L);
        Database.death_rank.putIfAbsent(playerName, 0L);
        Database.movement_rank.putIfAbsent(playerName, 0L);
        Database.mob_kill_rank.putIfAbsent(playerName, 0L);
    }
}