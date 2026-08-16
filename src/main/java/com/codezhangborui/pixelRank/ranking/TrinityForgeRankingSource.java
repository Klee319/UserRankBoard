package com.codezhangborui.pixelRank.ranking;

import com.codezhangborui.pixelRank.Configuration;
import com.codezhangborui.pixelRank.database.StatAggregator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * TrinityForge の統計 1 種類を順位表として供給する。
 *
 * <p><b>取得は必ず非同期・表示はキャッシュから。</b>
 * TF 側の {@code rankingTop} はメインスレッドから呼ぶと SQLite への同期 I/O が走るため、
 * {@link #refreshAll()} を非同期スケジューラから回して {@link #snapshot} を更新し、
 * {@link #top(int, Predicate)} はキャッシュを読むだけにしている。</p>
 *
 * <p>TrinityForge が見つからない場合はそもそも {@link RankingRegistry} へ登録しないので、
 * 順位表に空の板が出ることはない。</p>
 */
public final class TrinityForgeRankingSource implements RankingSource {

    /** キャッシュしておく件数。/pixelrank rank が最大 50 件表示するので余裕を持たせる。 */
    private static final int CACHE_SIZE = 100;

    /** 登録済みの TF 供給元（非同期更新の対象）。 */
    private static final List<TrinityForgeRankingSource> REGISTERED = new ArrayList<>();

    private final TrinityForgeStat stat;
    private final TrinityForgeBridge bridge;

    /** 直近の取得結果（プレイヤー名 → 値）。非同期更新なので volatile で丸ごと差し替える。 */
    private volatile Map<String, Long> snapshot = Map.of();

    TrinityForgeRankingSource(TrinityForgeStat stat, TrinityForgeBridge bridge) {
        this.stat = stat;
        this.bridge = bridge;
    }

    /**
     * TrinityForge が居るときだけ TF 項目を登録する。
     *
     * @return 1 つでも登録したら true
     */
    public static boolean registerAll(Plugin plugin) {
        Logger logger = plugin.getLogger();
        TrinityForgeBridge bridge = new TrinityForgeBridge(
                () -> Bukkit.getPluginManager().getPlugin("TrinityForge"),
                logger::warning);
        return registerAll(bridge);
    }

    /** テストや差し替え用。ブリッジを直接渡す版。 */
    static boolean registerAll(TrinityForgeBridge bridge) {
        REGISTERED.clear();
        if (!bridge.isAvailable()) {
            // TF 不在。登録しないので順位表・コマンド・スコアボードに一切現れない。
            return false;
        }
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            TrinityForgeRankingSource source = new TrinityForgeRankingSource(stat, bridge);
            REGISTERED.add(source);
            RankingRegistry.register(source);
        }
        return true;
    }

    /** TF 項目が登録されているか（非同期更新タスクを回すかどうかの判定）。 */
    public static boolean isRegistered() {
        return !REGISTERED.isEmpty();
    }

    /**
     * 有効な TF 項目のキャッシュを更新する。
     * <b>必ず非同期スレッドから呼ぶこと。</b>
     */
    public static void refreshAll() {
        for (TrinityForgeRankingSource source : REGISTERED) {
            if (source.isEnabled()) {
                source.refresh();
            }
        }
    }

    public TrinityForgeStat stat() {
        return stat;
    }

    @Override
    public String id() {
        return "trinityforge:" + stat.tfStat();
    }

    @Override
    public String alias() {
        return stat.alias();
    }

    @Override
    public String title() {
        String title = Configuration.getString("leaderboards." + stat.configKey());
        return title == null ? stat.defaultTitle() : title;
    }

    @Override
    public boolean isEnabled() {
        return Configuration.getBoolean("ranks." + stat.configKey());
    }

    @Override
    public Map<String, Long> top(int limit, Predicate<String> ignore) {
        // キャッシュを読むだけ。ここから TF を叩かない（メインスレッドで I/O させない）。
        return StatAggregator.top(snapshot, limit, ignore);
    }

    /** TF から取り直してキャッシュを差し替える（非同期スレッド専用）。 */
    void refresh() {
        List<TrinityForgeBridge.Entry> entries = bridge.top(stat.tfStat(), CACHE_SIZE);
        Map<String, Long> updated = new HashMap<>();
        for (TrinityForgeBridge.Entry entry : entries) {
            String name = displayName(entry);
            if (name == null || name.isBlank()) {
                continue;
            }
            // 同名が来ることは無いはずだが、来たら大きい方を残す
            Long current = updated.get(name);
            if (current == null || current < entry.value()) {
                updated.put(name, entry.value());
            }
        }
        snapshot = updated;
    }

    /** name() は空文字のことがある（ランキングミラーに一度も書かれていないプレイヤー）。 */
    private static String displayName(TrinityForgeBridge.Entry entry) {
        if (entry.name() != null && !entry.name().isBlank()) {
            return entry.name();
        }
        UUID uuid = entry.uuid();
        if (uuid == null) {
            return null;
        }
        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            return offline == null ? null : offline.getName();
        } catch (Throwable t) {
            return null;
        }
    }
}
