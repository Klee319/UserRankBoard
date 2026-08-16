package com.codezhangborui.pixelRank.handler;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.database.RankStat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Vault 経済からの残高取得。
 *
 * <p>注意 1: 残高取得は経済プラグインが同期でデータベースを叩く可能性がある。
 * 全プレイヤー分をメインスレッドで回すとサーバーが固まるため、
 * 収集は必ず非同期スケジューラで行い、結果の反映だけをメインスレッドへ戻す。</p>
 *
 * <p>注意 2: 経済データベースを 3 台で共有している場合、3 台とも同じ残高を報告する。
 * そのため所持金は合算せず、さらに <b>rank_stats へ保存もしない</b>
 * （{@link RankStat#MONEY} の javadoc を参照）。保存するとサーバー停止中に古い残高が凍り、
 * 集計がその過去の値を拾い続けてしまう。ここで集めた値がそのまま順位表になる。</p>
 */
public class EconomyHandler {

    private static Economy economy = null;
    private static boolean vaultAvailable = false;
    /** 非同期スレッドから書かれ、メインスレッドから読まれる。 */
    private static volatile Map<String, Long> cachedBalances = new HashMap<>();
    private static JavaPlugin plugin;

    public static boolean setup(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        vaultAvailable = true;
        return true;
    }

    public static boolean isAvailable() {
        return vaultAvailable;
    }

    public static Map<String, Long> getCachedBalances() {
        return cachedBalances;
    }

    /**
     * 残高を非同期で収集し、収集後にメインスレッドで順位表用のマップへ反映する。
     */
    public static void refreshBalancesAsync() {
        if (!vaultAvailable || plugin == null || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Long> balances = collectBalances();
            // 反映だけメインスレッドへ戻す
            Bukkit.getScheduler().runTask(plugin, () -> applyBalances(balances));
        });
    }

    /**
     * 既知プレイヤー全員の残高を集める。非同期スレッドから呼ぶこと。
     *
     * <p>対象は「このサーバーに来たことがある人（Bukkit.getOfflinePlayers）」に加えて
     * 「rank_stats に記録がある人」。後者を入れないと、他サーバーにしか居ない人が
     * 所持金ランキングから消える。</p>
     */
    private static Map<String, Long> collectBalances() {
        Map<String, Long> balances = new HashMap<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String name = player.getName();
            if (name == null) {
                continue;
            }
            addBalance(balances, name, player);
        }
        for (String name : knownPlayerNames()) {
            if (balances.containsKey(name)) {
                continue;
            }
            @SuppressWarnings("deprecation")
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
            addBalance(balances, name, offlinePlayer);
        }
        return balances;
    }

    private static void addBalance(Map<String, Long> balances, String name, OfflinePlayer player) {
        try {
            double balance = economy.getBalance(player);
            if (balance > 0) {
                balances.put(name, Math.round(balance));
            }
        } catch (RuntimeException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINE, "残高の取得に失敗しました: " + name, e);
            }
        }
    }

    /** 順位表に載っている（＝どこかのサーバーで記録がある）プレイヤー名の集合。 */
    private static Set<String> knownPlayerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (RankStat stat : RankStat.values()) {
            if (stat == RankStat.MONEY) {
                continue;
            }
            names.addAll(Database.aggregated(stat).keySet());
        }
        return names;
    }

    /** メインスレッドから呼ぶこと。 */
    private static void applyBalances(Map<String, Long> balances) {
        cachedBalances = balances;
        // 所持金はメモリ上のマップだけで完結する（rank_stats へは書かない）。
        Map<String, Long> moneyMap = Database.local(RankStat.MONEY);
        // 残高が 0 になった人はキーを消さずに 0 を書く。
        // 消すと前回の高い残高がマップに残り、順位表の上位へ居座り続ける。
        for (String known : moneyMap.keySet()) {
            if (!balances.containsKey(known)) {
                moneyMap.put(known, 0L);
            }
        }
        moneyMap.putAll(balances);
    }
}
