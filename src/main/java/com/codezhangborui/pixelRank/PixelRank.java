package com.codezhangborui.pixelRank;

import com.codezhangborui.pixelRank.database.Database;
import com.codezhangborui.pixelRank.database.RankStat;
import com.codezhangborui.pixelRank.handler.EconomyHandler;
import com.codezhangborui.pixelRank.handler.EventListener;
import com.codezhangborui.pixelRank.handler.LeaderboardHandler;
import com.codezhangborui.pixelRank.ranking.RankingRegistry;
import com.codezhangborui.pixelRank.ranking.TrinityForgeRankingSource;
import com.codezhangborui.pixelRank.ranking.TrinityForgeStat;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class PixelRank extends JavaPlugin {
    private static Logger logger;

    private void loadConfig() {
        Configuration.init(this);
        Configuration.setDefault("ranks.scoreboard_default", true, "新規プレイヤーにスコアボードを表示するかどうか");
        Configuration.setDefault("ranks.mining_rank", true, "採掘ランキングを表示するかどうか");
        Configuration.setDefault("ranks.placing_rank", true, "設置ランキングを表示するかどうか");
        Configuration.setDefault("ranks.online_time_rank", true, "プレイ時間ランキングを表示するかどうか");
        Configuration.setDefault("ranks.death_rank", false, "死亡ランキングを表示するかどうか");
        Configuration.setDefault("ranks.movement_rank", true, "移動距離ランキングを表示するかどうか");
        Configuration.setDefault("ranks.mob_kill_rank", true, "モブ討伐ランキングを表示するかどうか");
        Configuration.setDefault("ranks.jump_rank", true, "ジャンプ回数ランキングを表示するかどうか");
        Configuration.setDefault("ranks.money_rank", true, "所持金ランキングを表示するかどうか（Vault が必要）");
        Configuration.setDefault("ranks.switch_interval", 15, "スコアボードの順位表を切り替える間隔（秒）");
        Configuration.setDefault("ranks.ignore_username_regex", "Input_a_regex_here_to_ignore_specific_usernames", "除外するユーザー名の正規表現");

        Configuration.setDefault("storage.type", "sqlite",
                "保存先の種類: sqlite（このサーバー専用）または mysql（MariaDB/MySQL。複数サーバーで共有する場合）");
        Configuration.setDefault("storage.database", "database.db", "sqlite のときのファイル名");
        Configuration.setDefault("storage.host", "127.0.0.1", "mysql のときの接続先ホスト");
        Configuration.setDefault("storage.port", 3306, "mysql のときの接続先ポート");
        Configuration.setDefault("storage.database-name", "pixelrank", "mysql のときのデータベース名");
        Configuration.setDefault("storage.username", "root", "mysql のときのユーザー名");
        Configuration.setDefault("storage.password", "", "mysql のときのパスワード");
        Configuration.setDefault("storage.parameters", "useUnicode=true&characterEncoding=utf8",
                "mysql のときの追加接続パラメータ");
        Configuration.setDefault("storage.server-name", "",
                "このサーバーの名前。空にすると起動ディレクトリ名から自動判定する。",
                "警告: 同じ名前を 2 台以上に付けると互いの記録を上書きし合って消える。必ず 3 台で別の名前にすること。");
        Configuration.setDefault("storage.migrate-from-sqlite", true,
                "起動時に旧 SQLite (database.db) の記録をこのサーバーの記録として取り込むかどうか。取り込み後は自動で false になる。");
        Configuration.setDefault("storage.save_interval", 60, "データベースへ保存する間隔（秒）");

        // ここの既定値は【スコアボード右側の見出し】としてそのまま出る。config に該当キーが
        // 無いときだけ使われるので、英語のまま残すと「キーが欠けたサーバだけ英語」になる。
        // サイドバーの見出しが長いと表全体の横幅が広がって視界を塞ぐため、全角 8 文字程度まで。
        Configuration.setDefault("leaderboards.mining_rank", "採掘数", "採掘ランキングのタイトル");
        Configuration.setDefault("leaderboards.placing_rank", "設置数", "設置ランキングのタイトル");
        Configuration.setDefault("leaderboards.online_time_rank", "プレイ時間", "プレイ時間ランキングのタイトル");
        Configuration.setDefault("leaderboards.death_rank", "死亡回数", "死亡ランキングのタイトル");
        Configuration.setDefault("leaderboards.movement_rank", "移動距離", "移動距離ランキングのタイトル");
        Configuration.setDefault("leaderboards.mob_kill_rank", "モブ討伐数", "モブ討伐ランキングのタイトル");
        Configuration.setDefault("leaderboards.jump_rank", "ジャンプ回数", "ジャンプ回数ランキングのタイトル");
        Configuration.setDefault("leaderboards.money_rank", "所持金", "所持金ランキングのタイトル");
        Configuration.setDefault("leaderboards.max_leaderboard_size", 10, "順位表に表示する最大件数");

        // scoreboard.* は【サイドバーの巡回対象】だけを決める。ranks.* が false のものは
        // ここが true でも出ない（順位表として存在しないため）。内蔵項目は従来どおり全部巡回。
        for (RankStat stat : RankStat.values()) {
            Configuration.setDefault("scoreboard." + stat.configKey(), true,
                    "この順位表をサイドバーの巡回に入れるかどうか（ranks." + stat.configKey() + " が前提）");
        }

        loadTrinityForgeConfig();
    }

    /**
     * TrinityForge 連携項目の設定。
     *
     * <p>TrinityForge が入っていない場合、これらのキーは残るが順位表そのものが登録されないので、
     * スコアボードにもコマンドにも一切現れない（何も壊れない）。</p>
     */
    private void loadTrinityForgeConfig() {
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            Configuration.setDefault("ranks." + stat.configKey(), stat.defaultEnabled(),
                    stat.japaneseLabel() + "ランキングを引けるようにするかどうか（TrinityForge が必要）");
            Configuration.setDefault("scoreboard." + stat.configKey(), stat.defaultOnScoreboard(),
                    stat.japaneseLabel() + "ランキングをサイドバーの巡回に入れるかどうか");
            Configuration.setDefault("leaderboards." + stat.configKey(), stat.defaultTitle(),
                    stat.japaneseLabel() + "ランキングのタイトル");
        }
        migrateTrinityForgeRanks();
    }

    /**
     * 既に配ってある config の {@code ranks.tf_*: false} を 1 回だけ true へ引き上げる。
     *
     * <p><b>{@link Configuration#setDefault} だけでは直らない。</b> setDefault は
     * 「キーが無いときだけ書く」ので、初期の既定値（スキル 12 種が false）で生成済みの
     * config には効かない。実際それで「魔法と重武器のレベルしか順位表が無い」状態が続いていた。</p>
     *
     * <p>巡回が冗長になる心配は要らない ── サイドバーに出るかどうかは
     * {@code scoreboard.*} が別に持っているので、この移行で増えるのは
     * 「{@code /pixelrank rank} から引ける項目」だけ。</p>
     *
     * <p>移行後は自分でフラグを false にするので 2 回目は走らない。あとから個別に
     * 無効化した設定を、次の起動で勝手に戻さないため。</p>
     */
    private void migrateTrinityForgeRanks() {
        Configuration.setDefault("ranks.migrate-tf-ranks", true,
                "起動時に TF ランキングの無効設定を既定（全て有効）へ戻すかどうか。実行後は自動で false になる。");
        if (!Configuration.getBoolean("ranks.migrate-tf-ranks")) {
            return;
        }
        int restored = 0;
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            String path = "ranks." + stat.configKey();
            if (stat.defaultEnabled() && !Configuration.getBoolean(path)) {
                Configuration.set(path, true);
                restored++;
            }
        }
        Configuration.set("ranks.migrate-tf-ranks", false);
        if (restored > 0) {
            getLogger().info("\033[32mTF ランキング " + restored
                    + " 件を有効化しました（/pixelrank rank から引けます）。"
                    + "サイドバーの巡回対象は scoreboard.* で別に指定します。\033[0m");
        }
    }

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("\033[96mPixelRank Version " + getDescription().getVersion() + ". Hello!\033[0m");
        logger.info("\033[32m設定を読み込んでいます...\033[0m");
        loadConfig();
        logger.info("\033[32mデータベースからデータを復元しています...\033[0m");
        Database.init(this);
        if (!Database.load()) {
            logger.severe("データベースからの読み込みに失敗しました。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        logger.info("\033[32mこのサーバーの記録名: " + Database.serverName() + "\033[0m");
        if (EconomyHandler.setup(this)) {
            logger.info("\033[32mVault 経済を検出しました。所持金ランキングを有効にします。\033[0m");
        } else {
            logger.info("\033[33mVault 経済が見つかりません。所持金ランキングを無効にします。\033[0m");
        }
        RankingRegistry.registerBuiltins();
        // TrinityForge が居るときだけ TF 項目を足す（居なければ空の順位表が出ないよう登録自体しない）
        if (TrinityForgeRankingSource.registerAll(this)) {
            logger.info("\033[32mTrinityForge を検出しました。TF ランキングを追加します。\033[0m");
        } else {
            logger.info("\033[33mTrinityForge が見つかりません。TF ランキングは追加しません。\033[0m");
        }
        LeaderboardHandler.updateScoreboard();
        logger.info("\033[32mイベントとコマンドを登録しています...\033[0m");
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        PixelRankCommand commandExecutor = new PixelRankCommand(this);
        this.getCommand("pixelrank").setExecutor(commandExecutor);
        this.getCommand("pixelrank").setTabCompleter(commandExecutor);
        logger.info("\033[32mスケジューラを登録しています...\033[0m");
        Scheduler.start(this);
        logger.info("\033[92mPixelRank has been enabled!\033[0m");
    }

    @Override
    public void onDisable() {
        logger.info("\033[32m全データをデータベースへ保存しています...\033[0m");
        // 無効化中は非同期スケジューラが使えないので同期保存する
        if (!Database.saveBlocking()) {
            logger.severe("データベースへの保存に失敗しました。");
        }
        logger.info("\033[91mPixelRank has been disabled! Goodbye!\033[0m");
    }
}
