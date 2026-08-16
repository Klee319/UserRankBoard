package com.codezhangborui.pixelRank.database;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 順位表の集計ロジック（純関数のみ）。
 *
 * <p>データベースに一切触れないので JUnit から直接検証できる。
 * 3 サーバー横断ランキングの正しさはここに集約されている。</p>
 */
public final class StatAggregator {

    private StatAggregator() {
    }

    /**
     * 行の集合をプレイヤー単位へ畳み込む。
     *
     * @param rows        rank_stats から読んだ行
     * @param aggregation SUM（積み上げ項目）または MAX（所持金）
     */
    public static Map<String, Long> merge(Collection<StatRow> rows, Aggregation aggregation) {
        return mergeExcludingServer(rows, aggregation, null);
    }

    /**
     * 指定サーバーの行を除外して畳み込む。
     *
     * <p>自サーバーの値はメモリ上の最新値で上書きしたいので、
     * データベースからは他サーバー分だけを取り込む。</p>
     *
     * @param excludedServer 除外するサーバー名。null なら除外しない。
     */
    public static Map<String, Long> mergeExcludingServer(Collection<StatRow> rows,
                                                         Aggregation aggregation,
                                                         String excludedServer) {
        Map<String, Long> result = new HashMap<>();
        for (StatRow row : rows) {
            if (excludedServer != null && excludedServer.equals(row.server())) {
                continue;
            }
            Long current = result.get(row.player());
            result.put(row.player(), current == null ? row.value() : aggregation.merge(current, row.value()));
        }
        return result;
    }

    /**
     * 他サーバー分の集計結果へ、自サーバーのメモリ上の値を重ねる。
     *
     * @param base    他サーバー分の集計結果（変更しない）
     * @param overlay 自サーバーの現在値
     */
    public static Map<String, Long> overlay(Map<String, Long> base,
                                            Map<String, Long> overlay,
                                            Aggregation aggregation) {
        Map<String, Long> result = new HashMap<>(base);
        for (Map.Entry<String, Long> entry : overlay.entrySet()) {
            Long current = result.get(entry.getKey());
            result.put(entry.getKey(),
                    current == null ? entry.getValue() : aggregation.merge(current, entry.getValue()));
        }
        return result;
    }

    /**
     * 上位 N 件を取り出す。値の降順、同値ならプレイヤー名の昇順（表示のちらつき防止）。
     *
     * @param ignore 除外するプレイヤー名の判定。null なら全員を対象にする。
     */
    public static LinkedHashMap<String, Long> top(Map<String, Long> values,
                                                  int limit,
                                                  Predicate<String> ignore) {
        List<Map.Entry<String, Long>> sorted = values.entrySet().stream()
                .filter(entry -> ignore == null || !ignore.test(entry.getKey()))
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(limit, 0))
                .toList();

        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : sorted) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
