package com.codezhangborui.pixelRank.database;

/**
 * 内蔵ランキング項目の定義。
 *
 * <p>statId は rank_stats.stat へそのまま書かれる永続的な識別子なので、後から変えない。
 * legacyTable は 1.2 以前の SQLite テーブル名（移行に使う）。</p>
 */
public enum RankStat {
    MINING("mining", "mining_rank", "mining_rank", "mine", Aggregation.SUM),
    PLACING("placing", "placing_rank", "placing_rank", "place", Aggregation.SUM),
    ONLINE_TIME("online_time", "online_time_rank", "online_time_rank", "time", Aggregation.SUM),
    DEATH("death", "death_rank", "death_rank", "death", Aggregation.SUM),
    MOVEMENT("movement", "movement_rank", "movement_rank", "move", Aggregation.SUM),
    MOB_KILL("mob_kill", "mob_kill_rank", "mob_kill_rank", "mobkill", Aggregation.SUM),
    JUMP("jump", "jump_rank", null, "jump", Aggregation.SUM),
    /**
     * 所持金。<b>唯一 rank_stats へ保存しない項目</b>（{@link #persisted()} が false）。
     *
     * <p>他の項目は「そのサーバーで積み上げた量」なのでサーバーごとに行を持ち、読み出し時に合算する。
     * しかし所持金は経済プラグインが 3 台共通で持っている<b>今この瞬間の値</b>であって、
     * サーバーごとの取り分という概念が無い。</p>
     *
     * <p>これをサーバー別の行として保存すると、<b>停止中のサーバーの行がその時点の残高で凍りつく</b>。
     * その後プレイヤーが所持金を使っても凍った行は下がらないので、MAX 集計が過去の高額を拾い続け、
     * 所持金ランキングが永久に誤表示になる（Dev サーバーは常時稼働ではないので必ず起きる）。
     * 稼働中のサーバーは {@code EconomyHandler} が全プレイヤー分の残高を毎回 Vault から読み直すため、
     * 保存しなくても単独で完全な順位表を作れる。よって保存しないのが正しい。</p>
     */
    MONEY("money", "money_rank", null, "money", Aggregation.MAX, false);

    private final String statId;
    private final String configKey;
    private final String legacyTable;
    private final String alias;
    private final Aggregation aggregation;
    private final boolean persisted;

    RankStat(String statId, String configKey, String legacyTable, String alias, Aggregation aggregation) {
        this(statId, configKey, legacyTable, alias, aggregation, true);
    }

    RankStat(String statId, String configKey, String legacyTable, String alias, Aggregation aggregation,
             boolean persisted) {
        this.statId = statId;
        this.configKey = configKey;
        this.legacyTable = legacyTable;
        this.alias = alias;
        this.aggregation = aggregation;
        this.persisted = persisted;
    }

    /** rank_stats.stat に書かれる値。 */
    public String statId() {
        return statId;
    }

    /** config.yml の ranks.* / leaderboards.* のキー。 */
    public String configKey() {
        return configKey;
    }

    /** 1.2 以前の SQLite テーブル名。移行対象でなければ null。 */
    public String legacyTable() {
        return legacyTable;
    }

    /** /pixelrank rank &lt;alias&gt; で使う短縮名。 */
    public String alias() {
        return alias;
    }

    public Aggregation aggregation() {
        return aggregation;
    }

    /**
     * rank_stats テーブルへ保存し、他サーバー分と突き合わせる項目かどうか。
     *
     * <p>false の項目は「稼働中のサーバーが毎回外部から取り直せる、今この瞬間の値」であり、
     * 保存すると停止中サーバーの古い行が集計へ混ざるだけで害しかない。詳細は {@link #MONEY}。</p>
     */
    public boolean persisted() {
        return persisted;
    }
}
