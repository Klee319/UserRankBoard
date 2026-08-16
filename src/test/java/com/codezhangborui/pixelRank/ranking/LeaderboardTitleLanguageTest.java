package com.codezhangborui.pixelRank.ranking;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * スコアボードの見出しが日本語であることを固定する。
 *
 * <p>見出しの出所は 2 つあり、<b>片方だけ直すと直したつもりで英語が残る</b>:
 * <ul>
 *   <li>出荷 {@code config.yml} の {@code leaderboards.*} … 新規配備時にそのまま配られる</li>
 *   <li>{@link TrinityForgeStat#defaultTitle()} と {@code PixelRank#setDefault} …
 *       config に該当キーが無いときだけ使われる。ここが英語のままだと
 *       「キーが欠けたサーバだけ英語」という割れ方をする</li>
 * </ul>
 *
 * <p>判定は「ASCII の英字を含まないこと」。文言そのものを固定すると言い回しを変えるたびに
 * テストを直すことになり、守りたいこと（英語に戻っていないか）とずれる。</p>
 */
class LeaderboardTitleLanguageTest {

    private static final Path SHIPPED_CONFIG = Path.of("src/main/resources/config.yml");

    /** 見出しに ASCII 英字が混ざっていたら英語（または英語の残骸）とみなす。 */
    private static boolean containsAsciiLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    @Test
    void 出荷configのランキング見出しはすべて日本語() throws IOException {
        assertTrue(Files.exists(SHIPPED_CONFIG), "出荷 config.yml が見つかりません: " + SHIPPED_CONFIG);
        List<String> lines = Files.readAllLines(SHIPPED_CONFIG, StandardCharsets.UTF_8);

        // leaderboards: セクションの `  key: "値"` だけを拾う（max_leaderboard_size は数値なので拾わない）
        Pattern entry = Pattern.compile("^\\s{2}([a-z0-9_]+):\\s*\"(.*)\"\\s*$");
        boolean inSection = false;
        List<String> english = new ArrayList<>();
        int checked = 0;

        for (String line : lines) {
            if (line.startsWith("leaderboards:")) { inSection = true; continue; }
            if (!inSection) { continue; }
            if (!line.isBlank() && !line.startsWith(" ")) { break; }

            Matcher m = entry.matcher(line);
            if (!m.matches()) { continue; }
            checked++;
            String value = m.group(2);
            if (containsAsciiLetter(value)) {
                english.add(m.group(1) + " = \"" + value + "\"");
            }
        }

        assertTrue(checked >= 25,
                "leaderboards の見出しを " + checked + " 件しか拾えていない。"
                        + "セクションの書式が変わってこのテストが素通りしている可能性がある。");
        assertTrue(english.isEmpty(),
                "スコアボードの見出しに英語が残っています:\n  " + String.join("\n  ", english));
    }

    @Test
    void TF連携項目の既定見出しはすべて日本語() {
        List<String> english = new ArrayList<>();
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            if (containsAsciiLetter(stat.defaultTitle())) {
                english.add(stat.name() + ".defaultTitle = \"" + stat.defaultTitle() + "\"");
            }
            if (containsAsciiLetter(stat.japaneseLabel())) {
                english.add(stat.name() + ".japaneseLabel = \"" + stat.japaneseLabel() + "\"");
            }
        }
        assertTrue(english.isEmpty(),
                "config にキーが無いサーバだけ英語表示になります:\n  " + String.join("\n  ", english));
    }

    @Test
    void 見出しは長すぎない() {
        // サイドバーの見出しが長いと表全体の横幅が広がってプレイヤーの視界を塞ぐ。
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            assertTrue(stat.defaultTitle().length() <= 8,
                    stat.name() + " の見出し『" + stat.defaultTitle() + "』が長すぎます（全角8文字まで）");
        }
    }

    @Test
    void 総合と合計は見出しでも区別が付く() {
        // POWER(他スキルから派生する総合)と skill_total_level(各スキルの単純合計)は別物。
        // 見出しが同じだとプレイヤーが取り違える。
        assertFalse(TrinityForgeStat.SKILL_POWER.defaultTitle()
                .equals(TrinityForgeStat.SKILL_TOTAL.defaultTitle()));
    }
}
