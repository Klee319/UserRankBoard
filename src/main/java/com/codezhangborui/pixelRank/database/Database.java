package com.codezhangborui.pixelRank.database;

import com.codezhangborui.pixelRank.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 順位表データの永続化。
 *
 * <p>3 サーバー横断の要点:</p>
 * <ul>
 *   <li>各サーバーは rank_stats の <b>自分の server 行だけ</b>を upsert する。
 *       他サーバーの行には触れないので、旧実装の「DELETE してから全件 INSERT」による
 *       相互破壊が起きない。</li>
 *   <li>表示は読み出し時に集計する。積み上げ項目は SUM。</li>
 *   <li><b>所持金は保存しない</b>（{@link RankStat#persisted()} が false）。経済プラグインが 3 台共通で
 *       持っている「今この瞬間の値」なので、サーバー別の行にすると停止中のサーバーの行が
 *       古い残高で凍り、集計へ混ざって誤表示になる。稼働中のサーバーは毎回 Vault から
 *       全員分を読み直せるため、保存しなくても単独で正しい順位表を作れる。</li>
 * </ul>
 */
public class Database {

    /** 自サーバーで計測中の値。イベントリスナはこれを加算する。 */
    private static final Map<RankStat, Map<String, Long>> LOCAL = new EnumMap<>(RankStat.class);
    /** 他サーバー分の集計結果のキャッシュ（非同期に更新）。 */
    private static volatile Map<RankStat, Map<String, Long>> remoteCache = new EnumMap<>(RankStat.class);

    private static StorageSettings settings;
    private static JavaPlugin plugin;
    private static Logger logger;

    static {
        for (RankStat stat : RankStat.values()) {
            LOCAL.put(stat, new ConcurrentHashMap<>());
        }
    }

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getLogger();
        settings = readSettings(plugin.getDataFolder());
        if (settings.dialect() == SqlDialect.MYSQL) {
            loadDriver("org.mariadb.jdbc.Driver");
        } else {
            loadDriver("org.sqlite.JDBC");
        }
    }

    private static void loadDriver(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            logger.log(Level.WARNING, "JDBC ドライバ " + className + " を読み込めませんでした。", e);
        }
    }

    /** config.yml の storage: セクションを読む。 */
    private static StorageSettings readSettings(File dataFolder) {
        SqlDialect dialect = SqlDialect.fromConfig(Configuration.getString("storage.type"));
        String configuredName = Configuration.getString("storage.server-name");
        String serverName = (configuredName == null || configuredName.isBlank())
                ? StorageSettings.autoDetectServerName()
                : StorageSettings.sanitizeServerName(configuredName);

        if (dialect == SqlDialect.MYSQL) {
            String url = StorageSettings.buildMysqlUrl(
                    Configuration.getString("storage.host"),
                    Configuration.getInt("storage.port"),
                    Configuration.getString("storage.database-name"),
                    Configuration.getString("storage.parameters"));
            return new StorageSettings(dialect, url,
                    Configuration.getString("storage.username"),
                    Configuration.getString("storage.password"),
                    serverName);
        }
        String url = StorageSettings.buildSqliteUrl(dataFolder, Configuration.getString("storage.database"));
        return new StorageSettings(dialect, url, null, null, serverName);
    }

    public static StorageSettings settings() {
        return settings;
    }

    public static String serverName() {
        return settings == null ? "default" : settings.serverName();
    }

    /** 自サーバーで計測中の値のマップ（イベントリスナが直接加算する）。 */
    public static Map<String, Long> local(RankStat stat) {
        return LOCAL.get(stat);
    }

    public static void increment(RankStat stat, String player, long delta) {
        LOCAL.get(stat).merge(player, delta, Long::sum);
    }

    public static void initializePlayer(String player) {
        for (RankStat stat : RankStat.values()) {
            if (stat == RankStat.MONEY) {
                // 所持金は経済プラグインが持つ値なので 0 を置かない
                continue;
            }
            LOCAL.get(stat).putIfAbsent(player, 0L);
        }
    }

    /**
     * 表示用の集計値。他サーバー分のキャッシュへ自サーバーの最新値を重ねる。
     */
    public static Map<String, Long> aggregated(RankStat stat) {
        Map<String, Long> remote = remoteCache.getOrDefault(stat, Collections.emptyMap());
        return StatAggregator.overlay(remote, LOCAL.get(stat), stat.aggregation());
    }

    private static Connection openConnection() throws SQLException {
        if (settings.dialect() == SqlDialect.MYSQL) {
            return DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
        }
        return DriverManager.getConnection(settings.jdbcUrl());
    }

    /**
     * テーブル作成・旧データ移行・自サーバー分の読み戻しを行う。
     */
    public static boolean load() {
        try (Connection conn = openConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute(settings.dialect().createRankStatsSql());
                st.execute(settings.dialect().createScoreboardSettingsSql());
            }
            migrateLegacySqliteIfNeeded(conn);
            loadOwnServerRows(conn);
            refreshRemoteCache(conn);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "データベースへ接続できませんでした (" + settings.jdbcUrl() + ")", e);
            return false;
        }
    }

    /** 再起動後もカウンタが続くよう、自サーバーの行をメモリへ読み戻す。 */
    private static void loadOwnServerRows(Connection conn) throws SQLException {
        Map<String, RankStat> byId = new HashMap<>();
        for (RankStat stat : RankStat.values()) {
            if (!stat.persisted()) {
                // 保存しない項目は読み戻しもしない（古い行が残っていても拾わない）
                continue;
            }
            byId.put(stat.statId(), stat);
        }
        try (PreparedStatement ps = conn.prepareStatement(SqlDialect.selectOwnServerSql())) {
            ps.setString(1, settings.serverName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RankStat stat = byId.get(rs.getString("stat"));
                    if (stat == null) {
                        continue;
                    }
                    LOCAL.get(stat).put(rs.getString("player"), rs.getLong("value"));
                }
            }
        }
    }

    /** 他サーバー分の集計をキャッシュへ取り込む。 */
    private static void refreshRemoteCache(Connection conn) throws SQLException {
        Map<RankStat, Map<String, Long>> fresh = new EnumMap<>(RankStat.class);
        for (RankStat stat : RankStat.values()) {
            Map<String, Long> values = new HashMap<>();
            if (!stat.persisted()) {
                // 保存しない項目に「他サーバー分」は存在しない。
                // ここで読みに行くと、停止中サーバーが残した古い行を拾ってしまう。
                fresh.put(stat, values);
                continue;
            }
            String sql = SqlDialect.selectAggregatedExcludingServerSql(stat.aggregation());
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, stat.statId());
                ps.setString(2, settings.serverName());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        values.put(rs.getString("player"), rs.getLong("v"));
                    }
                }
            }
            fresh.put(stat, values);
        }
        remoteCache = fresh;
    }

    /**
     * 保存と読み直しを非同期で行う。
     * 自サーバーの行だけを upsert するので他サーバーの記録は消えない。
     */
    public static boolean save() {
        if (settings == null) {
            return false;
        }
        Map<RankStat, Map<String, Long>> snapshot = new EnumMap<>(RankStat.class);
        for (RankStat stat : RankStat.values()) {
            snapshot.put(stat, new HashMap<>(LOCAL.get(stat)));
        }
        Runnable task = () -> {
            try (Connection conn = openConnection()) {
                writeSnapshot(conn, snapshot);
                refreshRemoteCache(conn);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "データベースへの保存に失敗しました。", e);
            }
        };
        runAsync(task);
        return true;
    }

    /** サーバー停止時など、非同期スケジューラが使えない場面で同期的に保存する。 */
    public static boolean saveBlocking() {
        if (settings == null) {
            return false;
        }
        Map<RankStat, Map<String, Long>> snapshot = new EnumMap<>(RankStat.class);
        for (RankStat stat : RankStat.values()) {
            snapshot.put(stat, new HashMap<>(LOCAL.get(stat)));
        }
        try (Connection conn = openConnection()) {
            writeSnapshot(conn, snapshot);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "データベースへの保存に失敗しました。", e);
            return false;
        }
    }

    private static void writeSnapshot(Connection conn, Map<RankStat, Map<String, Long>> snapshot) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(settings.dialect().upsertRankStatSql())) {
            // 何を書くかの判断は StatRow.rowsToPersist（純関数・テスト済み）に寄せる。
            // 所持金など persisted() が false の項目はここへ来ない。
            for (StatRow row : StatRow.rowsToPersist(snapshot, settings.serverName())) {
                ps.setString(1, row.player());
                ps.setString(2, row.server());
                ps.setString(3, row.stat());
                ps.setLong(4, row.value());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static void runAsync(Runnable task) {
        if (plugin != null && plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            new Thread(task, "PixelRank-DB").start();
        }
    }

    /**
     * 1.2 以前の SQLite（player/value の 6 テーブル）を rank_stats へ取り込む。
     *
     * <p>取り込んだ値は「自サーバーの行」として登録する。
     * 完了したら config の storage.migrate-from-sqlite を false にして二度と走らせない。</p>
     */
    private static void migrateLegacySqliteIfNeeded(Connection targetConnection) {
        if (!Configuration.getBoolean("storage.migrate-from-sqlite")) {
            return;
        }
        String fileName = Configuration.getString("storage.database");
        File legacyFile = new File(plugin.getDataFolder(), fileName == null || fileName.isBlank() ? "database.db" : fileName);
        if (!legacyFile.exists()) {
            Configuration.set("storage.migrate-from-sqlite", false);
            return;
        }
        String legacyUrl = StorageSettings.buildSqliteUrl(plugin.getDataFolder(), fileName);
        loadDriver("org.sqlite.JDBC");

        List<StatRow> imported = new ArrayList<>();
        try (Connection legacy = DriverManager.getConnection(legacyUrl)) {
            for (RankStat stat : RankStat.values()) {
                if (stat.legacyTable() == null) {
                    continue;
                }
                if (!tableExists(legacy, stat.legacyTable())) {
                    continue;
                }
                try (Statement st = legacy.createStatement();
                     ResultSet rs = st.executeQuery("SELECT player, value FROM " + stat.legacyTable())) {
                    while (rs.next()) {
                        imported.add(new StatRow(rs.getString("player"), settings.serverName(),
                                stat.statId(), rs.getLong("value")));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "旧 SQLite からの移行に失敗しました。移行はスキップします。", e);
            return;
        }

        if (!imported.isEmpty()) {
            try (PreparedStatement ps = targetConnection.prepareStatement(settings.dialect().upsertRankStatSql())) {
                for (StatRow row : imported) {
                    ps.setString(1, row.player());
                    ps.setString(2, row.server());
                    ps.setString(3, row.stat());
                    ps.setLong(4, row.value());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "旧 SQLite データの書き込みに失敗しました。", e);
                return;
            }
        }
        logger.info("旧 SQLite から " + imported.size() + " 件を server=" + settings.serverName() + " として取り込みました。");
        Configuration.set("storage.migrate-from-sqlite", false);
    }

    private static boolean tableExists(Connection conn, String tableName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * プレイヤーのスコアボード表示設定を読み込む。保存が無ければ null。
     */
    public static Boolean loadScoreboardSetting(UUID uuid) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT enabled FROM scoreboard_settings WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("enabled") == 1 : null;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "スコアボード設定の読み込みに失敗しました: " + uuid, e);
            return null;
        }
    }

    /** プレイヤーのスコアボード表示設定を保存する（非同期）。 */
    public static void saveScoreboardSetting(UUID uuid, boolean enabled) {
        runAsync(() -> {
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(settings.dialect().upsertScoreboardSettingSql())) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, enabled ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "スコアボード設定の保存に失敗しました: " + uuid, e);
            }
        });
    }
}
