package com.codezhangborui.pixelRank.database;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * rank_stats へ「何を書くか」の決定を固定する。
 *
 * <p>ここで守るのは 1 点だけ:
 * <b>所持金は保存されてはならない</b>。所持金は経済プラグインが 3 台共通で持つ「今この瞬間の値」で、
 * サーバー別の行にすると停止中のサーバーの行が古い残高で凍り、
 * 集計がその過去の値を拾い続けて永久に誤表示になる（Dev サーバーは常時稼働ではない）。</p>
 */
class StatRowTest {

    private static Map<RankStat, Map<String, Long>> snapshotWithEverything() {
        Map<RankStat, Map<String, Long>> snapshot = new EnumMap<>(RankStat.class);
        for (RankStat stat : RankStat.values()) {
            snapshot.put(stat, Map.of("Alice", 100L, "Bob", 200L));
        }
        return snapshot;
    }

    @Test
    void 所持金はrank_statsへ書かれない() {
        List<StatRow> rows = StatRow.rowsToPersist(snapshotWithEverything(), "main");

        assertFalse(rows.stream().anyMatch(r -> RankStat.MONEY.statId().equals(r.stat())),
                "所持金を保存すると、停止中サーバーの行が古い残高で凍り MAX 集計が過去の値を拾い続ける");
    }

    @Test
    void 所持金以外の項目はすべて書かれる() {
        List<StatRow> rows = StatRow.rowsToPersist(snapshotWithEverything(), "main");

        List<String> written = rows.stream().map(StatRow::stat).distinct().sorted().collect(Collectors.toList());
        List<String> expected = java.util.Arrays.stream(RankStat.values())
                .filter(RankStat::persisted)
                .map(RankStat::statId)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(expected, written, "積み上げ項目はサーバー別に保存して読み出し時に合算する");
    }

    @Test
    void 保存対象から外れているのは所持金だけ() {
        List<RankStat> unpersisted = java.util.Arrays.stream(RankStat.values())
                .filter(stat -> !stat.persisted())
                .collect(Collectors.toList());
        assertEquals(List.of(RankStat.MONEY), unpersisted,
                "外部から毎回取り直せる項目以外を保存対象から外すと、再起動でカウンタが消える");
    }

    @Test
    void 書き込む行には自分のサーバー名が入る() {
        List<StatRow> rows = StatRow.rowsToPersist(snapshotWithEverything(), "resource");

        assertTrue(rows.stream().allMatch(r -> "resource".equals(r.server())),
                "他サーバーの行を書くと相互破壊が起きる");
        assertFalse(rows.isEmpty());
    }

    @Test
    void 値がnullの行は0として書かれる() {
        Map<String, Long> withNull = new java.util.HashMap<>();
        withNull.put("Alice", null);
        Map<RankStat, Map<String, Long>> snapshot = new EnumMap<>(RankStat.class);
        snapshot.put(RankStat.MINING, withNull);

        List<StatRow> rows = StatRow.rowsToPersist(snapshot, "main");

        assertEquals(1, rows.size());
        assertEquals(0L, rows.get(0).value());
    }

    @Test
    void 空のスナップショットからは何も書かれない() {
        assertTrue(StatRow.rowsToPersist(new EnumMap<>(RankStat.class), "main").isEmpty());
    }
}
