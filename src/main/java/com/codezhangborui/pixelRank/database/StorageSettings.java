package com.codezhangborui.pixelRank.database;

import java.io.File;
import java.io.IOException;

/**
 * config.yml の storage: セクションを解釈した結果。
 */
public final class StorageSettings {

    private final SqlDialect dialect;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String serverName;

    public StorageSettings(SqlDialect dialect, String jdbcUrl, String username, String password, String serverName) {
        this.dialect = dialect;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.serverName = serverName;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    /** rank_stats.server に書き込む自サーバーの名前。 */
    public String serverName() {
        return serverName;
    }

    /**
     * MariaDB / MySQL 用の JDBC URL を組み立てる（純関数）。
     *
     * <p>同梱している MariaDB Connector/J は jdbc:mariadb:// スキームで統一して使う。
     * config の type が mysql でも MariaDB / MySQL 双方のプロトコルを話せる。</p>
     */
    public static String buildMysqlUrl(String host, int port, String database, String extraParameters) {
        StringBuilder sb = new StringBuilder("jdbc:mariadb://");
        sb.append(host == null || host.isBlank() ? "127.0.0.1" : host.trim());
        sb.append(':').append(port <= 0 ? 3306 : port);
        sb.append('/').append(database == null || database.isBlank() ? "pixelrank" : database.trim());
        if (extraParameters != null && !extraParameters.isBlank()) {
            String params = extraParameters.trim();
            sb.append(params.startsWith("?") ? params : "?" + params);
        }
        return sb.toString();
    }

    /** SQLite 用の JDBC URL を組み立てる（純関数）。 */
    public static String buildSqliteUrl(File dataFolder, String fileName) {
        File file = new File(dataFolder, fileName == null || fileName.isBlank() ? "database.db" : fileName);
        String path;
        try {
            path = file.getCanonicalPath();
        } catch (IOException e) {
            path = file.getAbsolutePath();
        }
        return "jdbc:sqlite:" + path.replace('\\', '/');
    }

    /**
     * サーバー名の自動判定。作業ディレクトリ名（Main_Server など）を使う。
     * 判定できなければ "default"。
     */
    public static String autoDetectServerName() {
        try {
            String name = new File(".").getCanonicalFile().getName();
            if (name != null && !name.isBlank()) {
                return sanitizeServerName(name);
            }
        } catch (IOException ignored) {
            // 判定できなければ既定値
        }
        return "default";
    }

    /** server 列は VARCHAR(32) なので長さを切り詰める。 */
    public static String sanitizeServerName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "default";
        }
        String trimmed = raw.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }
}
