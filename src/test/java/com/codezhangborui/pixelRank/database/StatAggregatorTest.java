package com.codezhangborui.pixelRank.database;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class StatAggregatorTest {

    private static List<StatRow> threeServerRows(String stat, long main, long resource, long dev) {
        List<StatRow> rows = new ArrayList<>();
        rows.add(new StatRow("Klee", "main", stat, main));
        rows.add(new StatRow("Klee", "resource", stat, resource));
        rows.add(new StatRow("Klee", "dev", stat, dev));
        return rows;
    }

    @Test
    void 積み上げ項目は3サーバーを合算する() {
        Map<String, Long> merged = StatAggregator.merge(threeServerRows("mining", 100, 20, 3), Aggregation.SUM);
        assertEquals(123L, merged.get("Klee"));
    }

    @Test
    void 所持金は合算せず最大値を採用する() {
        // 3 台が共通の経済データベースを見ているので同じ残高を報告する。
        // SUM にすると 15000 と 3 倍に化ける。
        Map<String, Long> merged = StatAggregator.merge(threeServerRows("money", 5000, 5000, 5000), Aggregation.MAX);
        assertEquals(5000L, merged.get("Klee"));
    }

    @Test
    void 所持金の集計方法はMAXに固定されている() {
        assertEquals(Aggregation.MAX, RankStat.MONEY.aggregation());
        for (RankStat stat : RankStat.values()) {
            if (stat != RankStat.MONEY) {
                assertEquals(Aggregation.SUM, stat.aggregation(), stat + " は SUM のはず");
            }
        }
    }

    @Test
    void 自サーバーの行を除外して集計できる() {
        Map<String, Long> merged = StatAggregator.mergeExcludingServer(
                threeServerRows("mining", 100, 20, 3), Aggregation.SUM, "main");
        assertEquals(23L, merged.get("Klee"));
    }

    @Test
    void 除外後の集計へ自サーバーの最新値を重ねると全体の合計になる() {
        Map<String, Long> remote = StatAggregator.mergeExcludingServer(
                threeServerRows("mining", 100, 20, 3), Aggregation.SUM, "main");
        Map<String, Long> local = new HashMap<>();
        local.put("Klee", 150L); // main で計測中の最新値

        Map<String, Long> total = StatAggregator.overlay(remote, local, Aggregation.SUM);
        assertEquals(173L, total.get("Klee"));
    }

    @Test
    void 所持金の重ね合わせもMAXになる() {
        Map<String, Long> remote = new HashMap<>();
        remote.put("Klee", 5000L);
        Map<String, Long> local = new HashMap<>();
        local.put("Klee", 5000L);

        assertEquals(5000L, StatAggregator.overlay(remote, local, Aggregation.MAX).get("Klee"));
    }

    @Test
    void 他サーバーにしか記録が無いプレイヤーも順位表に残る() {
        List<StatRow> rows = new ArrayList<>();
        rows.add(new StatRow("OnlyOnDev", "dev", "mining", 42L));
        Map<String, Long> remote = StatAggregator.mergeExcludingServer(rows, Aggregation.SUM, "main");
        Map<String, Long> local = new HashMap<>();
        local.put("OnlyOnMain", 1L);

        Map<String, Long> total = StatAggregator.overlay(remote, local, Aggregation.SUM);
        assertEquals(42L, total.get("OnlyOnDev"));
        assertEquals(1L, total.get("OnlyOnMain"));
    }

    @Test
    void 上位N件は降順で同値ならプレイヤー名昇順になる() {
        Map<String, Long> values = new HashMap<>();
        values.put("a", 5L);
        values.put("b", 10L);
        values.put("c", 5L);
        values.put("d", 1L);

        Map<String, Long> top = StatAggregator.top(values, 3, null);
        assertIterableEquals(List.of("b", "a", "c"), top.keySet());
    }

    @Test
    void 除外判定に一致する名前は順位表から外れる() {
        Map<String, Long> values = new HashMap<>();
        values.put("Bot_1", 100L);
        values.put("Klee", 10L);

        Map<String, Long> top = StatAggregator.top(values, 10, name -> name.startsWith("Bot_"));
        assertIterableEquals(List.of("Klee"), top.keySet());
    }

    @Test
    void 上限0件や負数でも例外にならない() {
        Map<String, Long> values = new HashMap<>();
        values.put("Klee", 10L);
        assertEquals(0, StatAggregator.top(values, 0, null).size());
        assertEquals(0, StatAggregator.top(values, -5, null).size());
    }
}
