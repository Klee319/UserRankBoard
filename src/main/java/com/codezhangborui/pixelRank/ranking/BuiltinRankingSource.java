package com.codezhangborui.pixelRank.ranking;

import com.codezhangborui.pixelRank.Configuration;
import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.database.RankStat;
import com.codezhangborui.pixelRank.database.StatAggregator;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * rank_stats に保存されている内蔵統計を供給する。
 * 集計（SUM / MAX）は {@link Database#aggregated(RankStat)} 側で行う。
 */
public class BuiltinRankingSource implements RankingSource {

    private final RankStat stat;
    private final BooleanSupplier extraGate;

    public BuiltinRankingSource(RankStat stat) {
        this(stat, () -> true);
    }

    /**
     * @param extraGate config の有効フラグに加えて満たすべき条件（所持金の Vault 有無など）
     */
    public BuiltinRankingSource(RankStat stat, BooleanSupplier extraGate) {
        this.stat = stat;
        this.extraGate = extraGate;
    }

    public RankStat stat() {
        return stat;
    }

    @Override
    public String id() {
        return stat.statId();
    }

    @Override
    public String alias() {
        return stat.alias();
    }

    @Override
    public String title() {
        String title = Configuration.getString("leaderboards." + stat.configKey());
        return title == null ? stat.configKey() : title;
    }

    @Override
    public boolean isEnabled() {
        return Configuration.getBoolean("ranks." + stat.configKey()) && extraGate.getAsBoolean();
    }

    @Override
    public boolean isOnScoreboard() {
        // 内蔵項目はもともと全部サイドバーに出ていたので、キーが無いときの既定は true。
        // （素の getBoolean だと未設定 = false になり、更新した瞬間サイドバーが空になる）
        return isEnabled() && Configuration.getBoolean("scoreboard." + stat.configKey(), true);
    }

    @Override
    public Map<String, Long> top(int limit, Predicate<String> ignore) {
        return StatAggregator.top(Database.aggregated(stat), limit, ignore);
    }
}
