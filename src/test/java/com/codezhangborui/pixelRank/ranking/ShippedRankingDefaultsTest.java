package com.codezhangborui.pixelRank.ranking;

import com.codezhangborui.pixelRank.database.RankStat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出荷 {@code config.yml} と enum の既定値が食い違っていないことを固定する。
 *
 * <p>既定値の出所が 2 つあるのが事故のもと。<b>新規配備は出荷 config.yml が配られ、
 * 既存サーバは {@code Configuration#setDefault} が enum の既定値で欠けたキーだけを埋める。</b>
 * 片方だけ直すと「新しく入れたサーバと前から動いているサーバで挙動が違う」という、
 * ログにも出ない割れ方をする。実際 {@code ranks.tf_skill_*} が 12 件 false のまま出荷され、
 * 「魔法と重武器のレベルしか順位表が無い」状態が続いていた。</p>
 */
class ShippedRankingDefaultsTest {

    private static final Path SHIPPED_CONFIG = Path.of("src/main/resources/config.yml");

    /** {@code セクション名 -> (キー -> 真偽値)}。真偽値以外の行は読み飛ばす。 */
    private static Map<String, Map<String, Boolean>> readBooleanSections() throws IOException {
        assertTrue(Files.exists(SHIPPED_CONFIG), "出荷 config.yml が見つかりません: " + SHIPPED_CONFIG);
        Pattern section = Pattern.compile("^([a-z_]+):\\s*$");
        Pattern entry = Pattern.compile("^\\s{2}([a-z0-9_-]+):\\s*(true|false)\\s*$");
        Map<String, Map<String, Boolean>> out = new LinkedHashMap<>();
        String current = null;
        for (String line : Files.readAllLines(SHIPPED_CONFIG, StandardCharsets.UTF_8)) {
            Matcher head = section.matcher(line);
            if (head.matches()) {
                current = head.group(1);
                out.computeIfAbsent(current, k -> new LinkedHashMap<>());
                continue;
            }
            if (current == null) {
                continue;
            }
            Matcher m = entry.matcher(line);
            if (m.matches()) {
                out.get(current).put(m.group(1), Boolean.parseBoolean(m.group(2)));
            }
        }
        return out;
    }

    @Test
    void 出荷configのranksはenumのdefaultEnabledと一致する() throws IOException {
        Map<String, Boolean> ranks = readBooleanSections().get("ranks");
        assertTrue(ranks != null && !ranks.isEmpty(), "ranks: セクションを読めていない(この検査は空振りしている)");

        List<String> problems = new ArrayList<>();
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            Boolean shipped = ranks.get(stat.configKey());
            if (shipped == null) {
                problems.add("ranks." + stat.configKey() + " が出荷 config に無い");
            } else if (shipped != stat.defaultEnabled()) {
                problems.add("ranks." + stat.configKey() + " = " + shipped
                        + " だが enum の既定は " + stat.defaultEnabled());
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void 出荷configのscoreboardはenumのdefaultOnScoreboardと一致する() throws IOException {
        Map<String, Boolean> board = readBooleanSections().get("scoreboard");
        assertTrue(board != null && !board.isEmpty(),
                "scoreboard: セクションを読めていない(この検査は空振りしている)");

        List<String> problems = new ArrayList<>();
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            Boolean shipped = board.get(stat.configKey());
            if (shipped == null) {
                problems.add("scoreboard." + stat.configKey() + " が出荷 config に無い");
            } else if (shipped != stat.defaultOnScoreboard()) {
                problems.add("scoreboard." + stat.configKey() + " = " + shipped
                        + " だが enum の既定は " + stat.defaultOnScoreboard());
            }
        }
        // 内蔵項目も巡回対象を持つ(従来どおり全部 true)
        for (RankStat stat : RankStat.values()) {
            if (!board.containsKey(stat.configKey())) {
                problems.add("scoreboard." + stat.configKey() + " が出荷 config に無い(内蔵項目)");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void 出荷configにTFスキルの順位表がひとつも欠けていない() throws IOException {
        Map<String, Boolean> ranks = readBooleanSections().get("ranks");
        int enabledSkills = 0;
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            boolean perSkill = stat.tfStat().startsWith("skill_")
                    && !stat.tfStat().equals("skill_total_level")
                    && stat != TrinityForgeStat.SKILL_POWER;
            if (perSkill && Boolean.TRUE.equals(ranks.get(stat.configKey()))) {
                enabledSkills++;
            }
        }
        assertEquals(15, enabledSkills,
                "出荷 config で有効なスキル別ランキングが 15 件ない。"
                        + "サイドバーが冗長になるのが理由なら scoreboard: を false にすること"
                        + "(ranks: を false にすると /pixelrank rank からも引けなくなる)");
    }

    @Test
    void 既存configを直す一度きりの移行フラグが出荷configに載っている() throws IOException {
        Map<String, Boolean> ranks = readBooleanSections().get("ranks");
        assertEquals(Boolean.TRUE, ranks.get("migrate-tf-ranks"),
                "ranks.migrate-tf-ranks が無い/false。既に配ってある config の "
                        + "tf_skill_*: false は setDefault では直らない(キーが存在するため)");
    }
}
