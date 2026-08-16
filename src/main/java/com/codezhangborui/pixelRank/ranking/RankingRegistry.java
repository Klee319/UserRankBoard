package com.codezhangborui.pixelRank.ranking;

import com.codezhangborui.pixelRank.database.RankStat;
import com.codezhangborui.pixelRank.handler.EconomyHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 順位表の一覧。
 *
 * <p>内蔵統計は起動時にここへ登録される。外部プラグイン（TrinityForge など）の統計を
 * 足す場合も {@link #register(RankingSource)} で同じ列に並べるだけでよく、
 * スコアボードとコマンドの側は変更不要。</p>
 */
public final class RankingRegistry {

    private static final List<RankingSource> SOURCES = new ArrayList<>();

    private RankingRegistry() {
    }

    /** 内蔵統計を登録する（onEnable から 1 回だけ呼ぶ）。 */
    public static void registerBuiltins() {
        SOURCES.clear();
        SOURCES.add(new BuiltinRankingSource(RankStat.MINING));
        SOURCES.add(new BuiltinRankingSource(RankStat.PLACING));
        SOURCES.add(new BuiltinRankingSource(RankStat.ONLINE_TIME));
        SOURCES.add(new BuiltinRankingSource(RankStat.DEATH));
        SOURCES.add(new BuiltinRankingSource(RankStat.MOVEMENT));
        SOURCES.add(new BuiltinRankingSource(RankStat.MOB_KILL));
        SOURCES.add(new BuiltinRankingSource(RankStat.JUMP));
        // 所持金は Vault 経済が見つかったときだけ有効
        SOURCES.add(new BuiltinRankingSource(RankStat.MONEY, EconomyHandler::isAvailable));
    }

    /** 外部供給の順位表を追加する。 */
    public static void register(RankingSource source) {
        SOURCES.add(source);
    }

    public static List<RankingSource> all() {
        return Collections.unmodifiableList(SOURCES);
    }

    /** config で有効になっているものだけ。 */
    public static List<RankingSource> enabled() {
        List<RankingSource> result = new ArrayList<>();
        for (RankingSource source : SOURCES) {
            if (source.isEnabled()) {
                result.add(source);
            }
        }
        return result;
    }

    public static Optional<RankingSource> byAlias(String alias) {
        if (alias == null) {
            return Optional.empty();
        }
        for (RankingSource source : SOURCES) {
            if (source.alias().equalsIgnoreCase(alias)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
