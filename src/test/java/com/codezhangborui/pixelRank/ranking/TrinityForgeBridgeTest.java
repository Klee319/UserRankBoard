package com.codezhangborui.pixelRank.ranking;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrinityForge 連携のリフレクション層の検証。
 *
 * <p>Bukkit にも TrinityForge にも依存しないので、ここだけで
 * 「TF が居ないとき壊れないこと」を実際に走らせて確かめられる。</p>
 */
class TrinityForgeBridgeTest {

    /** TF の com.trinityforge.ranking.RankingEntry を模した record。 */
    public record FakeEntry(UUID uuid, String name, long value) {
    }

    /** TF 本体を模したプラグイン。署名は rankingTop(String, int)。 */
    public static class FakeTrinityForge {
        final List<String> calls = new ArrayList<>();

        public List<FakeEntry> rankingTop(String stat, int limit) {
            calls.add(stat + "/" + limit);
            return List.of(
                    new FakeEntry(UUID.nameUUIDFromBytes("a".getBytes()), "Klee", 42L),
                    new FakeEntry(UUID.nameUUIDFromBytes("b".getBytes()), "", 7L));
        }
    }

    /** rankingTop を持たない = API 不一致。 */
    public static class WrongApiPlugin {
        public List<FakeEntry> somethingElse(String stat, int limit) {
            return List.of();
        }
    }

    /** 呼ぶと必ず落ちるプラグイン。 */
    public static class ThrowingPlugin {
        public List<FakeEntry> rankingTop(String stat, int limit) {
            throw new IllegalStateException("boom");
        }
    }

    /** 要素に null が混ざるプラグイン。 */
    public static class NullElementPlugin {
        public List<Object> rankingTop(String stat, int limit) {
            return Arrays.asList(new FakeEntry(UUID.nameUUIDFromBytes("c".getBytes()), "Klee", 3L), null);
        }
    }

    /** List を返さない = 想定外の返り値。 */
    public static class NonListPlugin {
        public String rankingTop(String stat, int limit) {
            return "not a list";
        }
    }

    private static final class Warnings {
        final List<String> messages = new ArrayList<>();

        void accept(String message) {
            messages.add(message);
        }
    }

    @Test
    void recordアクセサから値を読み出せる() {
        Warnings warnings = new Warnings();
        FakeTrinityForge tf = new FakeTrinityForge();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> tf, warnings::accept);

        List<TrinityForgeBridge.Entry> entries = bridge.top("skill_MINING_level", 100);

        assertEquals(2, entries.size());
        assertEquals("Klee", entries.get(0).name());
        assertEquals(42L, entries.get(0).value());
        assertEquals(UUID.nameUUIDFromBytes("a".getBytes()), entries.get(0).uuid());
        // 名前は空文字のことがある。そのまま空文字として返し、埋めるのは呼び出し側の責任。
        assertEquals("", entries.get(1).name());
        assertEquals(List.of("skill_MINING_level/100"), tf.calls);
        assertTrue(warnings.messages.isEmpty(), "正常系で警告を出してはいけない");
    }

    @Test
    void 解決したMethodはキャッシュされ再lookupされない() {
        FakeTrinityForge tf = new FakeTrinityForge();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> tf, m -> {
        });

        bridge.top("collection_items", 10);
        Method first = bridge.resolvedMethod();
        Method firstAccessor = bridge.resolvedValueAccessor();
        assertNotNull(first);
        assertNotNull(firstAccessor);

        bridge.top("collection_mobs", 10);
        // getMethod は呼ぶたびに別インスタンスを返すので、同一インスタンスなら再 lookup していない証拠
        assertSame(first, bridge.resolvedMethod());
        assertSame(firstAccessor, bridge.resolvedValueAccessor());
    }

    @Test
    void TF不在なら空リストへ倒れ警告は1回だけ() {
        Warnings warnings = new Warnings();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> null, warnings::accept);

        assertTrue(bridge.top("glyphs_unlocked", 10).isEmpty());
        assertTrue(bridge.top("glyphs_unlocked", 10).isEmpty());
        assertTrue(bridge.top("collection_items", 10).isEmpty());

        assertEquals(1, warnings.messages.size(), "毎 tick ログを埋めてはいけない");
        assertFalse(bridge.isAvailable());
    }

    @Test
    void API不一致なら空リストへ倒れ以後は問い合わせない() {
        Warnings warnings = new Warnings();
        TrinityForgeBridge bridge = new TrinityForgeBridge(WrongApiPlugin::new, warnings::accept);

        assertTrue(bridge.top("skill_total_level", 10).isEmpty());
        assertTrue(bridge.isApiMismatch());
        assertTrue(bridge.top("skill_total_level", 10).isEmpty());
        assertEquals(1, warnings.messages.size());
    }

    @Test
    void invokeが例外を投げても外へ伝播せず空リストになる() {
        Warnings warnings = new Warnings();
        ThrowingPlugin plugin = new ThrowingPlugin();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> plugin, warnings::accept);

        assertTrue(bridge.top("collection_mobs", 10).isEmpty());
        assertTrue(bridge.top("collection_mobs", 10).isEmpty());
        assertEquals(1, warnings.messages.size());
    }

    @Test
    void 想定外の返り値でも空リストになる() {
        Warnings warnings = new Warnings();
        NonListPlugin plugin = new NonListPlugin();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> plugin, warnings::accept);

        assertTrue(bridge.top("collection_items", 10).isEmpty());
        assertEquals(1, warnings.messages.size());
    }

    @Test
    void プラグイン取得自体が例外でも外へ伝播しない() {
        Warnings warnings = new Warnings();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> {
            throw new NoClassDefFoundError("TrinityForge");
        }, warnings::accept);

        assertTrue(bridge.top("collection_items", 10).isEmpty());
        assertEquals(1, warnings.messages.size());
        assertFalse(bridge.isAvailable());
    }

    @Test
    void 読めない要素は捨てて残りを返す() {
        Warnings warnings = new Warnings();
        NullElementPlugin plugin = new NullElementPlugin();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> plugin, warnings::accept);

        List<TrinityForgeBridge.Entry> entries = bridge.top("collection_items", 10);
        assertEquals(1, entries.size());
        assertEquals("Klee", entries.get(0).name());
    }

    @Test
    void 警告シンクが落ちても順位表は壊れない() {
        AtomicInteger attempts = new AtomicInteger();
        TrinityForgeBridge bridge = new TrinityForgeBridge(() -> null, message -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("logger broken");
        });

        assertTrue(bridge.top("collection_items", 10).isEmpty());
        assertTrue(bridge.top("collection_items", 10).isEmpty());
        assertEquals(1, attempts.get());
    }
}
