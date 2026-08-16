package com.codezhangborui.pixelRank.ranking;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * TrinityForge のランキング API をリフレクション越しに呼ぶ層。
 *
 * <p><b>なぜリフレクションなのか:</b> このリポジトリ（UserRankBoard）は public であり、
 * TrinityForge の API jar を取り込むと TF 本体 jar を意図せず公開する事故になる。
 * そのため TF へのコンパイル依存を一切持たず、実行時にメソッドを解決する。</p>
 *
 * <p><b>Bukkit に依存しない：</b> プラグイン本体の取得は {@link Supplier} で外から渡す。
 * これにより JUnit から Bukkit 無しで検証できる。</p>
 *
 * <p><b>失敗時の約束:</b> TF 不在・API 不一致・invoke 失敗のいずれでも例外を外へ出さず、
 * 空リストを返す。警告ログはインスタンスごとに 1 回だけ（毎 tick 呼ばれてもログが埋まらない）。</p>
 */
public final class TrinityForgeBridge {

    /** TF の {@code com.trinityforge.ranking.RankingEntry} をこちら側へ写した値。 */
    public record Entry(UUID uuid, String name, long value) {
    }

    /** TF 側のメソッド名。署名は {@code List<RankingEntry> rankingTop(String stat, int limit)}。 */
    static final String METHOD_NAME = "rankingTop";

    private final Supplier<Object> pluginSupplier;
    private final Consumer<String> warningSink;

    // 解決済みのリフレクション要素はキャッシュする（毎回 lookup しない）
    private Method rankingTop;
    private Class<?> entryClass;
    private Method uuidAccessor;
    private Method nameAccessor;
    private Method valueAccessor;

    /** 警告を出したかどうか（1 回だけ出す）。 */
    private boolean warned;
    /** API 不一致が確定したら以後は問い合わせ自体をやめる。 */
    private boolean apiMismatch;

    /**
     * @param pluginSupplier TrinityForge プラグイン本体を返す。不在なら null を返すこと。
     * @param warningSink    警告メッセージの出力先（ロガーなど）。
     */
    public TrinityForgeBridge(Supplier<Object> pluginSupplier, Consumer<String> warningSink) {
        this.pluginSupplier = pluginSupplier;
        this.warningSink = warningSink;
    }

    /** TrinityForge が見つかるかどうか。登録の可否判定に使う（見つからなければ順位表に載せない）。 */
    public boolean isAvailable() {
        try {
            return pluginSupplier.get() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * TF の統計 stat の上位 limit 件を取得する。
     *
     * <p><b>注意: TF 側はこの呼び出しで SQLite への同期 I/O を行う。</b>
     * 必ず非同期スレッドから呼び、結果はキャッシュして表示に使うこと。</p>
     *
     * @return 値の降順。失敗時は常に空リスト（例外は投げない）。
     */
    public synchronized List<Entry> top(String stat, int limit) {
        if (apiMismatch) {
            return List.of();
        }
        Object plugin;
        try {
            plugin = pluginSupplier.get();
        } catch (Throwable t) {
            warnOnce("TrinityForge の取得に失敗しました: " + t);
            return List.of();
        }
        if (plugin == null) {
            warnOnce("TrinityForge が見つからないため、TF ランキングは空のまま表示されます。");
            return List.of();
        }

        Method method = rankingTop;
        if (method == null || !method.getDeclaringClass().isInstance(plugin)) {
            try {
                method = plugin.getClass().getMethod(METHOD_NAME, String.class, int.class);
            } catch (Throwable t) {
                // 署名が変わった／メソッドが無い。以後は問い合わせない。
                apiMismatch = true;
                warnOnce("TrinityForge の API 署名が一致しません（" + METHOD_NAME
                        + "(String,int) が見つかりません）。TF ランキングを無効化します。");
                return List.of();
            }
            rankingTop = method;
        }

        Object raw;
        try {
            raw = method.invoke(plugin, stat, limit);
        } catch (Throwable t) {
            warnOnce("TrinityForge のランキング取得に失敗しました (" + stat + "): " + t);
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            warnOnce("TrinityForge のランキング取得が List を返しませんでした (" + stat + ")。");
            return List.of();
        }

        List<Entry> result = new ArrayList<>(list.size());
        for (Object item : list) {
            Entry entry = readEntry(item);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    /** record アクセサ uuid() / name() / value() をリフレクションで読む。失敗した要素は捨てる。 */
    private Entry readEntry(Object item) {
        if (item == null) {
            return null;
        }
        try {
            if (entryClass != item.getClass()) {
                Class<?> type = item.getClass();
                uuidAccessor = type.getMethod("uuid");
                nameAccessor = type.getMethod("name");
                valueAccessor = type.getMethod("value");
                entryClass = type;
            }
            Object uuid = uuidAccessor.invoke(item);
            Object name = nameAccessor.invoke(item);
            Object value = valueAccessor.invoke(item);
            if (!(uuid instanceof UUID id) || !(value instanceof Number number)) {
                return null;
            }
            return new Entry(id, name instanceof String s ? s : "", number.longValue());
        } catch (Throwable t) {
            warnOnce("TrinityForge の RankingEntry を読めませんでした: " + t);
            entryClass = null;
            return null;
        }
    }

    private void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        try {
            warningSink.accept(message);
        } catch (Throwable ignored) {
            // ログ出力の失敗で順位表を壊さない
        }
    }

    // --- 以下はテスト用の覗き窓（キャッシュが効いていることを確認する） ---

    Method resolvedMethod() {
        return rankingTop;
    }

    Method resolvedValueAccessor() {
        return valueAccessor;
    }

    boolean isApiMismatch() {
        return apiMismatch;
    }
}
