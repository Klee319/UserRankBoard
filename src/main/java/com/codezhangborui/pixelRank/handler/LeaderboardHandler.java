package com.codezhangborui.pixelRank.handler;

import com.codezhangborui.pixelRank.Configuration;
import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.ranking.RankingRegistry;
import com.codezhangborui.pixelRank.ranking.RankingSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class LeaderboardHandler {

    /** 現在表示中の順位表（RankingRegistry.enabled() の中での位置ではなく id で保持する）。 */
    private static String currentSourceId = null;

    // スコアボードを非表示にしているプレイヤー
    private static final Set<UUID> disabledPlayers = new HashSet<>();

    /**
     * スコアボードの表示を切り替える。
     * @return 切り替え後に表示されていれば true
     */
    public static boolean toggleScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            updateScoreboardForPlayer(player);
            Database.saveScoreboardSetting(uuid, true);
            return true;
        } else {
            disabledPlayers.add(uuid);
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getNewScoreboard());
            }
            Database.saveScoreboardSetting(uuid, false);
            return false;
        }
    }

    public static boolean isScoreboardEnabled(Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }

    /**
     * 参加時に表示設定を適用する。
     *
     * <p>MariaDB 構成では設定の読み込みがネットワーク越しになるため、
     * 読み込みは非同期で行い、適用だけメインスレッドへ戻す。</p>
     */
    public static void applyDefaultVisibility(JavaPlugin plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Boolean savedSetting = Database.loadScoreboardSetting(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean enabled = savedSetting != null
                        ? savedSetting
                        : Configuration.getBoolean("ranks.scoreboard_default");
                if (enabled) {
                    disabledPlayers.remove(uuid);
                } else {
                    disabledPlayers.add(uuid);
                }
            });
        });
    }

    /** 現在表示すべき順位表。無効化などで見失ったら先頭へ戻す。 */
    private static RankingSource currentSource() {
        // enabled() ではなく onScoreboard()。順位表として存在することと、
        // サイドバーを勝手に流れてくることは別（scoreboard.* が後者を決める）。
        List<RankingSource> enabled = RankingRegistry.onScoreboard();
        if (enabled.isEmpty()) {
            return null;
        }
        for (RankingSource source : enabled) {
            if (source.id().equals(currentSourceId)) {
                return source;
            }
        }
        currentSourceId = enabled.get(0).id();
        return enabled.get(0);
    }

    /** 次の順位表へ切り替える（巡回対象のみ）。 */
    public static void switchLeaderboard() {
        List<RankingSource> enabled = RankingRegistry.onScoreboard();
        if (enabled.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < enabled.size(); i++) {
            if (enabled.get(i).id().equals(currentSourceId)) {
                index = i;
                break;
            }
        }
        currentSourceId = enabled.get((index + 1) % enabled.size()).id();
        updateScoreboard();
    }

    private static String formatTitle(String title) {
        return "§7« §f" + title + " §7»";
    }

    private static void applyLeaderboardData(Objective objective) {
        RankingSource source = currentSource();
        if (source == null) {
            return;
        }
        objective.setDisplayName(formatTitle(source.title()));
        int maxSize = Configuration.getInt("leaderboards.max_leaderboard_size");
        updateScores(objective, source.top(maxSize, ignorePredicate()));
    }

    /** config の正規表現に一致するプレイヤー名を除外する判定を作る。 */
    public static Predicate<String> ignorePredicate() {
        String regex = Configuration.getString("ranks.ignore_username_regex");
        if (regex == null || regex.isBlank()) {
            return name -> false;
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            return name -> pattern.matcher(name).matches();
        } catch (RuntimeException e) {
            return name -> false;
        }
    }

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
            if (!disabledPlayers.contains(player.getUniqueId())) {
                // 他プラグインが SIDEBAR を使っている場合は奪わない
                if (hasExternalSidebar(player)) {
                    return;
                }
                player.setScoreboard(scoreboard);
            }
        });
    }

    private static boolean hasExternalSidebar(Player player) {
        Scoreboard current = player.getScoreboard();
        Objective sidebarObj = current.getObjective(DisplaySlot.SIDEBAR);
        if (sidebarObj == null) {
            return false;
        }
        return !sidebarObj.getName().equals("leaderboard");
    }

    private static final int MAX_NAME_LENGTH = 10;

    private static void updateScores(Objective objective, Map<String, Long> topEntries) {
        int rank = 0;
        for (Map.Entry<String, Long> entry : topEntries.entrySet()) {
            rank++;

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
