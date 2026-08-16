package com.codezhangborui.pixelRank.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * rank_stats テーブルの 1 行。(player, server, stat) が主キー。
 */
public final class StatRow {

    /**
     * メモリ上のスナップショットから、実際に rank_stats へ書く行だけを組み立てる。
     *
     * <p>データベースに触らない純関数なので、JUnit から直接検証できる。
     * {@link RankStat#persisted()} が false の項目（所持金）はここで落とす。
     * 保存してしまうとサーバー停止中にその行が古い値で凍り、集計へ混ざって誤表示になる。</p>
     *
     * @param snapshot   項目ごとの「プレイヤー名 → 値」
     * @param serverName 書き込み主のサーバー名
     */
    public static List<StatRow> rowsToPersist(Map<RankStat, Map<String, Long>> snapshot, String serverName) {
        List<StatRow> rows = new ArrayList<>();
        for (Map.Entry<RankStat, Map<String, Long>> statEntry : snapshot.entrySet()) {
            RankStat stat = statEntry.getKey();
            if (!stat.persisted()) {
                continue;
            }
            for (Map.Entry<String, Long> row : statEntry.getValue().entrySet()) {
                Long value = row.getValue();
                rows.add(new StatRow(row.getKey(), serverName, stat.statId(), value == null ? 0L : value));
            }
        }
        return rows;
    }

    private final String player;
    private final String server;
    private final String stat;
    private final long value;

    public StatRow(String player, String server, String stat, long value) {
        this.player = player;
        this.server = server;
        this.stat = stat;
        this.value = value;
    }

    public String player() {
        return player;
    }

    public String server() {
        return server;
    }

    public String stat() {
        return stat;
    }

    public long value() {
        return value;
    }

    @Override
    public String toString() {
        return "StatRow{" + player + ", " + server + ", " + stat + ", " + value + '}';
    }
}
