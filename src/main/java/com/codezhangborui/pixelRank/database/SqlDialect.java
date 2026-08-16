package com.codezhangborui.pixelRank.database;

import java.util.Locale;

/**
 * SQL の方言差（DDL と upsert 構文）を吸収する。
 *
 * <p>SQL 文の組み立てはすべて純関数なので、データベース無しで JUnit から検証できる。</p>
 */
public enum SqlDialect {
    /** 単体サーバー向け。従来どおりローカルファイルへ保存する。 */
    SQLITE,
    /** 複数サーバー共有向け。MariaDB / MySQL。 */
    MYSQL;

    /**
     * config.yml の storage.type を解釈する。不明な値は SQLITE 扱い（後方互換）。
     */
    public static SqlDialect fromConfig(String type) {
        if (type == null) {
            return SQLITE;
        }
        switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "mysql":
            case "mariadb":
                return MYSQL;
            default:
                return SQLITE;
        }
    }

    /** rank_stats テーブルの DDL。 */
    public String createRankStatsSql() {
        if (this == MYSQL) {
            return "CREATE TABLE IF NOT EXISTS rank_stats ("
                    + "player VARCHAR(36) NOT NULL, "
                    + "server VARCHAR(32) NOT NULL, "
                    + "stat VARCHAR(32) NOT NULL, "
                    + "value BIGINT NOT NULL, "
                    + "PRIMARY KEY (player, server, stat)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        return "CREATE TABLE IF NOT EXISTS rank_stats ("
                + "player TEXT NOT NULL, "
                + "server TEXT NOT NULL, "
                + "stat TEXT NOT NULL, "
                + "value INTEGER NOT NULL, "
                + "PRIMARY KEY (player, server, stat)"
                + ")";
    }

    /** スコアボード表示設定テーブルの DDL。 */
    public String createScoreboardSettingsSql() {
        if (this == MYSQL) {
            return "CREATE TABLE IF NOT EXISTS scoreboard_settings ("
                    + "uuid VARCHAR(36) NOT NULL, "
                    + "enabled TINYINT NOT NULL, "
                    + "PRIMARY KEY (uuid)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        return "CREATE TABLE IF NOT EXISTS scoreboard_settings ("
                + "uuid TEXT PRIMARY KEY, enabled INTEGER NOT NULL)";
    }

    /**
     * 自サーバーの行だけを更新する upsert。
     *
     * <p>DELETE を使わないので、他サーバーの行には一切触れない。
     * 旧実装の「DELETE してから全件 INSERT」を共有データベースで動かすと
     * 60 秒ごとに他サーバーの記録を消し合って全損する。</p>
     */
    public String upsertRankStatSql() {
        if (this == MYSQL) {
            return "INSERT INTO rank_stats (player, server, stat, value) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE value = VALUES(value)";
        }
        return "INSERT INTO rank_stats (player, server, stat, value) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player, server, stat) DO UPDATE SET value = excluded.value";
    }

    /** スコアボード表示設定の upsert。 */
    public String upsertScoreboardSettingSql() {
        if (this == MYSQL) {
            return "INSERT INTO scoreboard_settings (uuid, enabled) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
        }
        return "INSERT INTO scoreboard_settings (uuid, enabled) VALUES (?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET enabled = excluded.enabled";
    }

    /**
     * 他サーバー分を集計して読み出す SELECT。
     *
     * <p>自サーバー分はメモリ上の最新値で上書きするため、ここでは除外する。
     * プレースホルダは順に stat, server。</p>
     */
    public static String selectAggregatedExcludingServerSql(Aggregation aggregation) {
        return "SELECT player, " + aggregation.sqlFunction() + "(value) AS v FROM rank_stats "
                + "WHERE stat = ? AND server <> ? GROUP BY player ORDER BY v DESC";
    }

    /**
     * 全サーバーを合算して上位 N 件を読み出す SELECT。
     * プレースホルダは順に stat, limit。
     */
    public static String selectTopSql(Aggregation aggregation) {
        return "SELECT player, " + aggregation.sqlFunction() + "(value) AS v FROM rank_stats "
                + "WHERE stat = ? GROUP BY player ORDER BY v DESC LIMIT ?";
    }

    /** 自サーバー分だけを読み戻す SELECT（再起動時にカウンタを引き継ぐ）。 */
    public static String selectOwnServerSql() {
        return "SELECT player, stat, value FROM rank_stats WHERE server = ?";
    }
}
