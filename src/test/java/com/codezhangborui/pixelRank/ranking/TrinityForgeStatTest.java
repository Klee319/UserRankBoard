package com.codezhangborui.pixelRank.ranking;

import com.codezhangborui.pixelRank.database.RankStat;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrinityForgeStatTest {

    @Test
    void モブ討伐は内蔵版と二重になるのでTF側から取り込まない() {
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            assertFalse(stat.tfStat().equals("mob_kills"),
                    "mob_kills は内蔵の mob_kill_rank と二重表示になるため取り込まない");
        }
        // 内蔵側は残っていること
        assertEquals("mob_kill_rank", RankStat.MOB_KILL.configKey());
    }

    @Test
    void 短縮名は内蔵とも互いにも衝突しない() {
        Set<String> aliases = new HashSet<>();
        for (RankStat stat : RankStat.values()) {
            aliases.add(stat.alias());
        }
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            assertTrue(aliases.add(stat.alias()), stat + " の短縮名 " + stat.alias() + " が衝突している");
        }
    }

    @Test
    void configキーは互いに衝突せずtf接頭辞を持つ() {
        Set<String> keys = new HashSet<>();
        for (RankStat stat : RankStat.values()) {
            keys.add(stat.configKey());
        }
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            assertTrue(stat.configKey().startsWith("tf_"), stat + " の config キーに tf_ 接頭辞が無い");
            assertTrue(keys.add(stat.configKey()), stat + " の config キーが衝突している");
        }
    }

    @Test
    void stat文字列はTFが受け付ける6形式のいずれか() {
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            String s = stat.tfStat();
            boolean known = s.equals("collection_items")
                    || s.equals("collection_mobs")
                    || s.equals("glyphs_unlocked")
                    || s.equals("skill_total_level")
                    || (s.startsWith("skill_") && s.endsWith("_level"));
            assertTrue(known, s + " は TF の rankingTop が受け付けない形式");
        }
    }

    @Test
    void 総合POWERと合計は別項目として両方ある() {
        assertEquals("skill_POWER_level", TrinityForgeStat.SKILL_POWER.tfStat());
        assertEquals("skill_total_level", TrinityForgeStat.SKILL_TOTAL.tfStat());
        // 表示名で取り違えないこと
        assertFalse(TrinityForgeStat.SKILL_POWER.defaultTitle()
                .equals(TrinityForgeStat.SKILL_TOTAL.defaultTitle()));
        assertFalse(TrinityForgeStat.SKILL_POWER.japaneseLabel()
                .equals(TrinityForgeStat.SKILL_TOTAL.japaneseLabel()));
    }

    /** 個別スキル（POWER と合計を除く）だけを数える。 */
    private static boolean isPerSkill(TrinityForgeStat stat) {
        return stat.tfStat().startsWith("skill_")
                && !stat.tfStat().equals("skill_total_level")
                && stat != TrinityForgeStat.SKILL_POWER;
    }

    @Test
    void スキル別レベルはTFの16種ぶん全部そろっている() {
        // TF の SkillId.ALL は POWER を含めて 16 種。ここでは POWER を別枠で持っているので
        // 個別スキルは 15 種になる。数が減ったら「一部のスキルだけ順位表が無い」状態の再来。
        int perSkill = 0;
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            if (isPerSkill(stat)) {
                perSkill++;
            }
        }
        assertEquals(15, perSkill, "TF のスキル(POWER 除く 15 種)に対して順位表が足りていない");
        assertTrue(TrinityForgeStat.SKILL_POWER.defaultEnabled(), "総合(POWER)が既定で無効になっている");
    }

    @Test
    void 順位表は全項目が既定で有効() {
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            assertTrue(stat.defaultEnabled(),
                    stat + " が既定 OFF。/pixelrank rank から引けないスキルを作らない");
        }
    }

    @Test
    void サイドバーの巡回は代表スキル3つに絞ってある() {
        // 「引けること」と「勝手に流れてくること」を分けたので、ここだけは絞ったまま。
        // 全部 ON にするとスライドが 25 枚になり、視界を塞ぐだけで誰も最後まで待てない。
        int onScoreboard = 0;
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            if (isPerSkill(stat) && stat.defaultOnScoreboard()) {
                onScoreboard++;
            }
        }
        assertEquals(3, onScoreboard, "既定で巡回するスキルを増やすとスコアボードのスライドが冗長になる");
    }

    @Test
    void 巡回対象は必ず有効な項目の部分集合() {
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            if (stat.defaultOnScoreboard()) {
                assertTrue(stat.defaultEnabled(),
                        stat + " は巡回対象なのに順位表として無効。存在しない板を流そうとしている");
            }
        }
    }
}
