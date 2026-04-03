package com.codezhangborui.pixelRank.handler;

import com.codezhangborui.pixelRank.Configuration;
import com.codezhangborui.pixelRank.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class LeaderboardHandler {
    public static int currentLeaderboard = 0;

    // Set of player UUIDs who have disabled the scoreboard display
    private static final Set<UUID> disabledPlayers = new HashSet<>();
    // Set of player UUIDs who have joined before (to track first-time joins)
    private static final Set<UUID> knownPlayers = new HashSet<>();

    /**
     * Toggle scoreboard visibility for a player
     * @param player The player to toggle
     * @return true if scoreboard is now enabled, false if disabled
     */
    public static boolean toggleScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            // Re-apply the current scoreboard
            updateScoreboardForPlayer(player);
            Database.saveScoreboardSetting(uuid, true);
            return true;
        } else {
            disabledPlayers.add(uuid);
            // Clear the scoreboard for this player
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getNewScoreboard());
            }
            Database.saveScoreboardSetting(uuid, false);
            return false;
        }
    }

    /**
     * Check if a player has the scoreboard enabled
     * @param player The player to check
     * @return true if scoreboard is enabled
     */
    public static boolean isScoreboardEnabled(Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }

    /**
     * Load and apply scoreboard visibility setting for a player on join.
     * Uses the saved DB setting if it exists, otherwise falls back to the config default.
     * @param player The player who joined
     */
    public static void applyDefaultVisibility(Player player) {
        UUID uuid = player.getUniqueId();
        knownPlayers.add(uuid);

        Boolean savedSetting = Database.loadScoreboardSetting(uuid);
        boolean enabled;
        if (savedSetting != null) {
            enabled = savedSetting;
        } else {
            enabled = Configuration.getBoolean("ranks.scoreboard_default");
        }

        if (!enabled) {
            disabledPlayers.add(uuid);
        } else {
            disabledPlayers.remove(uuid);
        }
    }

    /**
     * Update scoreboard for a single player (if they have it enabled)
     */
    private static void updateScoreboardForPlayer(Player player) {
        if (disabledPlayers.contains(player.getUniqueId())) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("leaderboard", "dummy", "Leaderboard");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        applyLeaderboardData(objective);
        player.setScoreboard(scoreboard);
    }

    private static final int RANK_COUNT = 7;

    public static void switchLeaderboard() {
        int startLeaderboard = currentLeaderboard;
        for (int i = 0; i < RANK_COUNT; i++) {
            currentLeaderboard = (startLeaderboard + 1 + i) % RANK_COUNT;
            boolean enabled = switch (currentLeaderboard) {
                case 0 -> Configuration.getBoolean("ranks.mining_rank");
                case 1 -> Configuration.getBoolean("ranks.placing_rank");
                case 2 -> Configuration.getBoolean("ranks.online_time_rank");
                case 3 -> Configuration.getBoolean("ranks.death_rank");
                case 4 -> Configuration.getBoolean("ranks.movement_rank");
                case 5 -> Configuration.getBoolean("ranks.mob_kill_rank");
                case 6 -> Configuration.getBoolean("ranks.money_rank") && EconomyHandler.isAvailable();
                default -> false;
            };
            if (enabled) {
                updateScoreboard();
                return;
            }
        }
    }

    private static String formatTitle(String title) {
        return "§7« §f" + title + " §7»";
    }

    private static void applyLeaderboardData(Objective objective) {
        switch (currentLeaderboard) {
            case 0:
                objective.setDisplayName(formatTitle(Configuration.getString("leaderboards.mining_rank")));
                updateScores(objective, Database.mining_rank);
                break;
            case 1:
                objective.setDisplayName(formatTitle(Configuration.getString("leaderboards.placing_rank")));
                updateScores(objective, Database.placing_rank);
                break;
            case 2:
                objective.setDisplayName(formatTitle(Configuration.getString("leaderboards.online_time_rank")));
                updateScores(objective, Database.online_time_rank);
                break;
            case 3:
                objective.setDisplayName(formatTitle(Configuration.getString("leaderboards.death_rank")));
                updateScores(objective, Database.death_rank);
                break;
            case 4:
                objective.setDisplayName(formatTitle(Configuration.getString("leaderboards.movement_rank")));
                updateScores(objective, Database.movement_rank);
                break;
            case 5:
                objective.setDisplayName(formatTitle(Configuration.getString("leaderboards.mob_kill_rank")));
                updateScores(objective, Database.mob_kill_rank);
                break;
            case 6:
                objective.setDisplayName(formatTitle(Configuration.getString("leaderboards.money_rank")));
                updateScores(objective, EconomyHandler.getCachedBalances());
                break;
        }
    }

    public static void updateScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("leaderboard", "dummy", "Leaderboard");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        applyLeaderboardData(objective);

        Bukkit.getOnlinePlayers().forEach(player -> {
            // Only set scoreboard for players who have it enabled
            if (!disabledPlayers.contains(player.getUniqueId())) {
                // Skip if another plugin is using the SIDEBAR on this player's scoreboard
                if (hasExternalSidebar(player)) {
                    return;
                }
                player.setScoreboard(scoreboard);
            }
        });
    }

    /**
     * Check if a player's current scoreboard has a SIDEBAR objective set by another plugin.
     */
    private static boolean hasExternalSidebar(Player player) {
        Scoreboard current = player.getScoreboard();
        Objective sidebarObj = current.getObjective(DisplaySlot.SIDEBAR);
        if (sidebarObj == null) {
            return false;
        }
        // If the objective name is "leaderboard", it's ours
        return !sidebarObj.getName().equals("leaderboard");
    }

    private static final int MAX_NAME_LENGTH = 10;

    private static void updateScores(Objective objective, HashMap<String, Long> rankData) {
        Pattern ignorePattern = Pattern.compile(Configuration.getString("ranks.ignore_username_regex"));
        int maxSize = Configuration.getInt("leaderboards.max_leaderboard_size");

        List<Map.Entry<String, Long>> sorted = rankData.entrySet().stream()
                .filter(entry -> !ignorePattern.matcher(entry.getKey()).matches())
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(maxSize)
                .toList();

        int totalEntries = sorted.size();
        for (int i = 0; i < totalEntries; i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            int rank = i + 1;

            String name = entry.getKey();
            if (name.length() > MAX_NAME_LENGTH) {
                name = name.substring(0, MAX_NAME_LENGTH - 1) + "..";
            }

            String color;
            if (rank == 1) {
                color = "§6";
            } else if (rank <= 3) {
                color = "§f";
            } else {
                color = "§7";
            }

            String entryText = color + "#" + rank + " " + name;
            Score score = objective.getScore(entryText);
            score.setScore(entry.getValue().intValue());
        }
    }
}