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

    // --- スキル別レベル ---
    SKILL_MINING("skill_MINING_level", "tf_skill_mining_rank", "tfmining", "Mining Lv", "採掘レベル", true),
    SKILL_WOODCUTTING("skill_WOODCUTTING_level", "tf_skill_woodcutting_rank", "tfwoodcutting", "Woodcutting Lv", "伐採レベル", false),
    SKILL_DIGGING("skill_DIGGING_level", "tf_skill_digging_rank", "tfdigging", "Digging Lv", "掘削レベル", false),
    SKILL_FARMING("skill_FARMING_level", "tf_skill_farming_rank", "tffarming", "Farming Lv", "農業レベル", false),
    SKILL_FISHING("skill_FISHING_level", "tf_skill_fishing_rank", "tffishing", "Fishing Lv", "釣りレベル", false),
    SKILL_SMITHING("skill_SMITHING_level", "tf_skill_smithing_rank", "tfsmithing", "Smithing Lv", "鍛冶レベル", false),
    SKILL_ALCHEMY("skill_ALCHEMY_level", "tf_skill_alchemy_rank", "tfalchemy", "Alchemy Lv", "錬金レベル", false),
    SKILL_ENCHANTING("skill_ENCHANTING_level", "tf_skill_enchanting_rank", "tfenchanting", "Enchanting Lv", "エンチャントレベル", false),
    SKILL_ARCHERY("skill_ARCHERY_level", "tf_skill_archery_rank", "tfarchery", "Archery Lv", "弓術レベル", false),
    SKILL_HEAVY_WEAPONS("skill_HEAVY_WEAPONS_level", "tf_skill_heavy_weapons_rank", "tfheavyweapons", "Heavy Weapon Lv", "重武器レベル", true),
    SKILL_LIGHT_WEAPONS("skill_LIGHT_WEAPONS_level", "tf_skill_light_weapons_rank", "tflightweapons", "Light Weapon Lv", "軽武器レベル", false),
    SKILL_HEAVY_ARMOR("skill_HEAVY_ARMOR_level", "tf_skill_heavy_armor_rank", "tfheavyarmor", "Heavy Armor Lv", "重装レベル", false),
    SKILL_LIGHT_ARMOR("skill_LIGHT_ARMOR_level", "tf_skill_light_armor_rank", "tflightarmor", "Light Armor Lv", "軽装レベル", false),
    SKILL_ARS_MAGIC("skill_ARS_MAGIC_level", "tf_skill_ars_magic_rank", "tfarsmagic", "Magic Lv", "魔法レベル", true),
    SKILL_ARS_SMITHING("skill_ARS_SMITHING_level", "tf_skill_ars_smithing_rank", "tfarssmithing", "Magic Smithing Lv", "魔法鍛冶レベル", false),

    // --- 総合 / 合計 ---
    // POWER は他スキルの成長から派生する「総合」レベル。
    // 一方 skill_total_level は POWER を除いた各スキルの「合計」。
    // 別物なので、表示名でも取り違えないよう明示的に書き分けている。
    SKILL_POWER("skill_POWER_level", "tf_skill_power_rank", "tfpower", "Overall Lv (Power)", "総合レベル(POWER)", true),
    SKILL_TOTAL("skill_total_level", "tf_skill_total_rank", "tfskilltotal", "Skill Lv Sum", "スキル合計レベル", true),

    // --- 図鑑・グリフ ---
    COLLECTION_ITEMS("collection_items", "tf_collection_items_rank", "tfitems", "Item Collection", "図鑑(アイテム)", true),
    COLLECTION_MOBS("collection_mobs", "tf_collection_mobs_rank", "tfmobs", "Mob Collection", "図鑑(モブ)", true),
    GLYPHS_UNLOCKED("glyphs_unlocked", "tf_glyphs_rank", "tfglyphs", "Glyphs Unlocked", "グリフ解放数", true);

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
