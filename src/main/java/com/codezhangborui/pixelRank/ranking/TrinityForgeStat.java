package com.codezhangborui.pixelRank.ranking;

/**
 * TrinityForge から取り込むランキング項目の定義。
 *
 * <p>{@code tfStat} は TF の {@code rankingTop(String stat, int limit)} へそのまま渡す文字列。
 * TF が受け付けるのは {@code collection_items} / {@code collection_mobs} /
 * {@code glyphs_unlocked} / {@code mob_kills} / {@code skill_<id>_level} /
 * {@code skill_total_level} の 6 形式。</p>
 *
 * <p><b>mob_kills を意図的に載せていない理由:</b>
 * 内蔵の MOB_KILL ランキング（config の {@code mob_kill_rank}）と同じものを二重に出すことになる。
 * 内蔵側はすでに 3 サーバー横断で合算される仕様なので、そちらを正とし、
 * TF 側の {@code mob_kills} はここへ追加しない。</p>
 */
public enum TrinityForgeStat {

    // 第4引数 defaultTitle は【スコアボード右側の見出し】の既定値（config の leaderboards.* が無いとき）。
    // 第5引数 japaneseLabel は /pixelrank rank の一覧に出す名前。
    // 2 つ分けてあるのは、サイドバーの見出しが長いと表全体の横幅が広がって視界を塞ぐため
    // （見出しは短く、一覧は誤解の無いよう長めに、を別々に決められるようにしてある）。
    // どちらも日本語で書く。英語に戻すと config を配り直したサーバだけ英語になり、見た目が割れる。

    // --- スキル別レベル ---
    SKILL_MINING("skill_MINING_level", "tf_skill_mining_rank", "tfmining", "採掘レベル", "採掘レベル", true),
    SKILL_WOODCUTTING("skill_WOODCUTTING_level", "tf_skill_woodcutting_rank", "tfwoodcutting", "伐採レベル", "伐採レベル", false),
    SKILL_DIGGING("skill_DIGGING_level", "tf_skill_digging_rank", "tfdigging", "掘削レベル", "掘削レベル", false),
    SKILL_FARMING("skill_FARMING_level", "tf_skill_farming_rank", "tffarming", "農業レベル", "農業レベル", false),
    SKILL_FISHING("skill_FISHING_level", "tf_skill_fishing_rank", "tffishing", "釣りレベル", "釣りレベル", false),
    SKILL_SMITHING("skill_SMITHING_level", "tf_skill_smithing_rank", "tfsmithing", "鍛冶レベル", "鍛冶レベル", false),
    SKILL_ALCHEMY("skill_ALCHEMY_level", "tf_skill_alchemy_rank", "tfalchemy", "錬金レベル", "錬金レベル", false),
    SKILL_ENCHANTING("skill_ENCHANTING_level", "tf_skill_enchanting_rank", "tfenchanting", "付与レベル", "エンチャントレベル", false),
    SKILL_ARCHERY("skill_ARCHERY_level", "tf_skill_archery_rank", "tfarchery", "弓術レベル", "弓術レベル", false),
    SKILL_HEAVY_WEAPONS("skill_HEAVY_WEAPONS_level", "tf_skill_heavy_weapons_rank", "tfheavyweapons", "重武器レベル", "重武器レベル", true),
    SKILL_LIGHT_WEAPONS("skill_LIGHT_WEAPONS_level", "tf_skill_light_weapons_rank", "tflightweapons", "軽武器レベル", "軽武器レベル", false),
    SKILL_HEAVY_ARMOR("skill_HEAVY_ARMOR_level", "tf_skill_heavy_armor_rank", "tfheavyarmor", "重装レベル", "重装レベル", false),
    SKILL_LIGHT_ARMOR("skill_LIGHT_ARMOR_level", "tf_skill_light_armor_rank", "tflightarmor", "軽装レベル", "軽装レベル", false),
    SKILL_ARS_MAGIC("skill_ARS_MAGIC_level", "tf_skill_ars_magic_rank", "tfarsmagic", "魔法レベル", "魔法レベル", true),
    SKILL_ARS_SMITHING("skill_ARS_SMITHING_level", "tf_skill_ars_smithing_rank", "tfarssmithing", "魔法鍛冶レベル", "魔法鍛冶レベル", false),

    // --- 総合 / 合計 ---
    // POWER は他スキルの成長から派生する「総合」レベル。
    // 一方 skill_total_level は POWER を除いた各スキルの「合計」。
    // 別物なので、表示名でも取り違えないよう明示的に書き分けている。
    SKILL_POWER("skill_POWER_level", "tf_skill_power_rank", "tfpower", "総合レベル", "総合レベル", true),
    SKILL_TOTAL("skill_total_level", "tf_skill_total_rank", "tfskilltotal", "スキル合計", "スキル合計レベル", true),

    // --- 図鑑・グリフ ---
    COLLECTION_ITEMS("collection_items", "tf_collection_items_rank", "tfitems", "図鑑(アイテム)", "図鑑(アイテム)", true),
    COLLECTION_MOBS("collection_mobs", "tf_collection_mobs_rank", "tfmobs", "図鑑(モブ)", "図鑑(モブ)", true),
    GLYPHS_UNLOCKED("glyphs_unlocked", "tf_glyphs_rank", "tfglyphs", "グリフ解放数", "グリフ解放数", true);

    private final String tfStat;
    private final String configKey;
    private final String alias;
    private final String defaultTitle;
    private final String japaneseLabel;
    private final boolean defaultEnabled;

    TrinityForgeStat(String tfStat, String configKey, String alias,
                     String defaultTitle, String japaneseLabel, boolean defaultEnabled) {
        this.tfStat = tfStat;
        this.configKey = configKey;
        this.alias = alias;
        this.defaultTitle = defaultTitle;
        this.japaneseLabel = japaneseLabel;
        this.defaultEnabled = defaultEnabled;
    }

    /** TF の rankingTop へ渡す stat 文字列。 */
    public String tfStat() {
        return tfStat;
    }

    /** config.yml の ranks.* / leaderboards.* のキー。 */
    public String configKey() {
        return configKey;
    }

    /** /pixelrank rank &lt;alias&gt; で使う短縮名。内蔵の短縮名と衝突しないよう tf 接頭辞を付けている。 */
    public String alias() {
        return alias;
    }

    /** leaderboards.* の既定タイトル（スコアボードの見出し）。 */
    public String defaultTitle() {
        return defaultTitle;
    }

    /** /pixelrank rank の一覧で使う日本語名。 */
    public String japaneseLabel() {
        return japaneseLabel;
    }

    /**
     * 既定で有効かどうか。
     *
     * <p>16 種のスキルを全部 ON にするとスコアボードのスライドが冗長になるので、
     * 既定 ON は採取・戦闘・魔法の代表 1 つずつ（採掘 / 重武器 / 魔法）と、
     * 総合・合計・図鑑・グリフだけにしてある。</p>
     */
    public boolean defaultEnabled() {
        return defaultEnabled;
    }
}
