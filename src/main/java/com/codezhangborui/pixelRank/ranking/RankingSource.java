package com.codezhangborui.pixelRank.ranking;

import java.util.Map;
import java.util.function.Predicate;

/**
 * 順位表 1 種類分の供給元。
 *
 * <p>内蔵の統計（採掘・設置・ジャンプなど）も、将来 TrinityForge 等の外部プラグインから
 * 受け取る統計も、この 1 つのインタフェースの実装として扱う。
 * 外部プラグイン連携を足すときは、この実装を 1 つ作って
 * {@link RankingRegistry#register(RankingSource)} へ登録するだけでよい。</p>
 */
public interface RankingSource {

    /** 一意な識別子。内蔵統計では rank_stats.stat と同じ値。 */
    String id();

    /** /pixelrank rank &lt;alias&gt; で使う短縮名。 */
    String alias();

    /** 順位表のタイトル（config から取得）。 */
    String title();

    /**
     * この順位表が有効かどうか（= {@code /pixelrank rank} の一覧に出て、順位を引けるか）。
     *
     * <p><b>サイドバーに出るかどうかとは別</b>。両者を 1 つのフラグで兼ねていた頃は、
     * 「全スキルの順位を引けるようにする」＝「サイドバーが 25 枚を巡回する」だった。</p>
     */
    boolean isEnabled();

    /**
     * サイドバー（スコアボード）の巡回対象に入れるかどうか。
     *
     * <p>既定は「有効なものは全部巡回する」＝ {@link #isEnabled()} と同じ。
     * 巡回だけ絞りたい実装がこれを上書きする。<b>有効でないものを巡回させてはいけない</b>ので、
     * 上書き側は必ず {@code isEnabled()} との AND を取ること。</p>
     */
    default boolean isOnScoreboard() {
        return isEnabled();
    }

    /**
     * 上位 limit 件を値の降順で返す。
     *
     * @param ignore 除外するプレイヤー名の判定（null 可）
     */
    Map<String, Long> top(int limit, Predicate<String> ignore);
}
