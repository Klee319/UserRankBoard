package com.codezhangborui.pixelRank.handler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 移動距離の端数を持ち越す集計器（純ロジックのみ）。
 *
 * <p><b>なぜ必要か（2026-08-18 の不具合修正）</b>: {@code PlayerMoveEvent} は 1 tick ごとに飛ぶが、
 * 1 tick あたりの移動量は<b>歩行で約 0.215 ブロック、スプリントでも約 0.28 ブロック</b>しかない。
 * 旧実装は 1 イベントごとに {@code Math.round(from.distance(to))} で丸めてから
 * {@code if (distance > 0)} で弾いていたため、<b>歩行もスプリントも全部 0 に落ちて 1 ブロックも
 * 計上されていなかった</b>。実際に数えられていたのはスプリントジャンプの頂点付近など、
 * 1 tick で 0.5 ブロックを超えた瞬間だけだった。
 *
 * <p>その結果「採掘 41,947 ブロックなのに移動距離 6,989」という物理的にあり得ない値になり、
 * 隣に並ぶジャンプ回数（{@code PlayerJumpEvent} で 1 回 1 加算＝正しい）だけが
 * 異常に多く見えていた。
 *
 * <p>この集計器は端数を持ち越し、<b>1 ブロック貯まったぶんだけ</b>整数で返す。
 * 長時間の合計は実距離に一致し、誤差は常に 1 ブロック未満に収まる。</p>
 */
public final class MovementAccumulator {

    private final Map<UUID, Double> carry = new ConcurrentHashMap<>();

    /**
     * 移動量を積み、繰り上がった整数ブロック数を返す。端数は次回へ持ち越す。
     *
     * @param playerId プレイヤーの UUID
     * @param distance このイベントぶんの移動量（ブロック）。0 以下・非有限値は無視して 0 を返す。
     * @return データベースへ加算すべきブロック数（0 なら加算しない）
     */
    public long accumulate(UUID playerId, double distance) {
        if (playerId == null || !Double.isFinite(distance) || distance <= 0.0) {
            return 0L;
        }
        // compute で読み書きを 1 回にまとめる（同じプレイヤーの move が並行して来ても壊れない）。
        double[] whole = new double[1];
        carry.compute(playerId, (key, previous) -> {
            double total = (previous == null ? 0.0 : previous) + distance;
            whole[0] = Math.floor(total);
            return total - whole[0];
        });
        return (long) whole[0];
    }

    /**
     * 退出したプレイヤーの端数を捨てる。持ち越しは 1 ブロック未満なので失っても実害はなく、
     * 放置すると再ログインしないプレイヤーぶんが永久に残ってしまう。
     */
    public void forget(UUID playerId) {
        if (playerId != null) {
            carry.remove(playerId);
        }
    }

    /** 保持中のプレイヤー数（テストと診断用）。 */
    public int trackedPlayers() {
        return carry.size();
    }
}
