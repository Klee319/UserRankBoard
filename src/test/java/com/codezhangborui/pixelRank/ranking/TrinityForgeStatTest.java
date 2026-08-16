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

    @Test
    void 既定で有効なスキル別レベルは代表3つだけ() {
        int enabledSkills = 0;
        for (TrinityForgeStat stat : TrinityForgeStat.values()) {
            boolean perSkill = stat.tfStat().startsWith("skill_")
                    && !stat.tfStat().equals("skill_total_level")
                    && stat != TrinityForgeStat.SKILL_POWER;
            if (perSkill && stat.defaultEnabled()) {
                enabledSkills++;
            }
        }
        assertEquals(3, enabledSkills, "既定 ON のスキルを増やすとスコアボードのスライドが冗長になる");
    }
}
