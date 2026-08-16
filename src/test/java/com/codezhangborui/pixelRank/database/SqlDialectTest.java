package com.codezhangborui.pixelRank.database;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDialectTest {

    @Test
    void configの値から方言を決める() {
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromConfig("mysql"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.fromConfig("MariaDB"));
        assertEquals(SqlDialect.SQLITE, SqlDialect.fromConfig("sqlite"));
        // 未設定・不明な値は後方互換のため sqlite 扱い
        assertEquals(SqlDialect.SQLITE, SqlDialect.fromConfig(null));
        assertEquals(SqlDialect.SQLITE, SqlDialect.fromConfig("nonsense"));
    }

    @Test
    void rankStatsのDDLはサーバー別の複合主キーを持つ() {
        String mysql = SqlDialect.MYSQL.createRankStatsSql();
        assertTrue(mysql.contains("player VARCHAR(36)"), mysql);
        assertTrue(mysql.contains("server VARCHAR(32)"), mysql);
        assertTrue(mysql.contains("stat VARCHAR(32)"), mysql);
        assertTrue(mysql.contains("value BIGINT"), mysql);
        assertTrue(mysql.contains("PRIMARY KEY (player, server, stat)"), mysql);

        String sqlite = SqlDialect.SQLITE.createRankStatsSql();
        assertTrue(sqlite.contains("PRIMARY KEY (player, server, stat)"), sqlite);
    }

    @Test
    void upsertはDELETEを含まず自分の行だけを更新する() {
        String mysql = SqlDialect.MYSQL.upsertRankStatSql();
        assertEquals("INSERT INTO rank_stats (player, server, stat, value) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE value = VALUES(value)", mysql);
        // 旧実装の DELETE FROM <table> が残っていると共有 DB で他サーバーの記録を全消しする
        assertFalse(mysql.toUpperCase().contains("DELETE"), mysql);

        String sqlite = SqlDialect.SQLITE.upsertRankStatSql();
        assertEquals("INSERT INTO rank_stats (player, server, stat, value) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player, server, stat) DO UPDATE SET value = excluded.value", sqlite);
        assertFalse(sqlite.toUpperCase().contains("DELETE"), sqlite);
    }

    @Test
    void 集計SELECTは集計方法に応じてSUMとMAXを使い分ける() {
        assertEquals("SELECT player, SUM(value) AS v FROM rank_stats "
                        + "WHERE stat = ? AND server <> ? GROUP BY player ORDER BY v DESC",
                SqlDialect.selectAggregatedExcludingServerSql(Aggregation.SUM));

        assertEquals("SELECT player, MAX(value) AS v FROM rank_stats "
                        + "WHERE stat = ? AND server <> ? GROUP BY player ORDER BY v DESC",
                SqlDialect.selectAggregatedExcludingServerSql(Aggregation.MAX));
    }

    @Test
    void 上位N件のSELECTも組み立てられる() {
        assertEquals("SELECT player, SUM(value) AS v FROM rank_stats "
                        + "WHERE stat = ? GROUP BY player ORDER BY v DESC LIMIT ?",
                SqlDialect.selectTopSql(Aggregation.SUM));
        assertEquals("SELECT player, MAX(value) AS v FROM rank_stats "
                        + "WHERE stat = ? GROUP BY player ORDER BY v DESC LIMIT ?",
                SqlDialect.selectTopSql(Aggregation.MAX));
    }

    @Test
    void スコアボード設定のupsertも方言ごとに正しい() {
        assertTrue(SqlDialect.MYSQL.upsertScoreboardSettingSql().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(SqlDialect.SQLITE.upsertScoreboardSettingSql().contains("ON CONFLICT(uuid)"));
    }

    @Test
    void mysqlのURLを組み立てる() {
        assertEquals("jdbc:mariadb://127.0.0.1:3306/pixelrank?useUnicode=true",
                StorageSettings.buildMysqlUrl("127.0.0.1", 3306, "pixelrank", "useUnicode=true"));
        // 空欄は既定値で補う
        assertEquals("jdbc:mariadb://127.0.0.1:3306/pixelrank",
                StorageSettings.buildMysqlUrl("", 0, "", null));
        // 先頭の ? を二重に付けない
        assertEquals("jdbc:mariadb://db:3307/rank?a=b",
                StorageSettings.buildMysqlUrl("db", 3307, "rank", "?a=b"));
    }

    @Test
    void sqliteのURLを組み立てる() {
        String url = StorageSettings.buildSqliteUrl(new File("plugins/PixelRank"), "database.db");
        assertTrue(url.startsWith("jdbc:sqlite:"), url);
        assertTrue(url.endsWith("/database.db"), url);
        assertFalse(url.contains("\\"), url);
    }

    @Test
    void サーバー名は32文字へ切り詰められる() {
        assertEquals("default", StorageSettings.sanitizeServerName(null));
        assertEquals("default", StorageSettings.sanitizeServerName("   "));
        assertEquals("main", StorageSettings.sanitizeServerName(" main "));
        assertEquals(32, StorageSettings.sanitizeServerName("x".repeat(50)).length());
    }

    @Test
    void statIdは永続識別子なので固定されている() {
        assertEquals("mining", RankStat.MINING.statId());
        assertEquals("jump", RankStat.JUMP.statId());
        assertEquals("money", RankStat.MONEY.statId());
        assertEquals("jump_rank", RankStat.JUMP.configKey());
    }
}
