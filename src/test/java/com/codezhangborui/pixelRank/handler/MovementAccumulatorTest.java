package com.codezhangborui.pixelRank.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovementAccumulatorTest {

    /** 歩行 1 tick ぶんの移動量。 */
    private static final double WALK_PER_TICK = 0.215;
    /** スプリント 1 tick ぶんの移動量。 */
    private static final double SPRINT_PER_TICK = 0.28;

    @Test
    @DisplayName("歩行はイベント単位では 1 未満だが、貯まればブロック数として計上される")
    void walkingIsCountedOnceItAddsUpToAWholeBlock() {
        MovementAccumulator accumulator = new MovementAccumulator();
        UUID player = UUID.randomUUID();

        // 旧実装は Math.round(0.215)=0 で毎回捨てていたので、ここが 0 のままだった。
        long firstFourTicks = 0;
        for (int tick = 0; tick < 4; tick++) {
            firstFourTicks += accumulator.accumulate(player, WALK_PER_TICK);
        }
        assertEquals(0L, firstFourTicks, "4 tick(0.86ブロック)では、まだ 1 ブロックに満たない");

        assertEquals(1L, accumulator.accumulate(player, WALK_PER_TICK), "5 tick 目で 1 ブロック繰り上がる");
    }

    @Test
    @DisplayName("長時間の合計は実距離に一致し、誤差は 1 ブロック未満に収まる")
    void totalMatchesTheRealDistance() {
        MovementAccumulator accumulator = new MovementAccumulator();
        UUID player = UUID.randomUUID();

        int ticks = 20 * 60 * 10; // 10 分ぶんスプリントし続ける
        long counted = 0;
        for (int tick = 0; tick < ticks; tick++) {
            counted += accumulator.accumulate(player, SPRINT_PER_TICK);
        }

        double real = SPRINT_PER_TICK * ticks;
        assertTrue(Math.abs(real - counted) < 1.0,
                "実距離 " + real + " に対し計上 " + counted + " ── 誤差が 1 ブロック以上ある");
        assertTrue(counted > 3000, "スプリント10分で 3000 ブロック超は数えられるはずが " + counted);
    }

    @Test
    @DisplayName("端数はプレイヤーごとに独立していて、混ざらない")
    void carryIsPerPlayer() {
        MovementAccumulator accumulator = new MovementAccumulator();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        // 2 人が 0.6 ずつ動いても、合算して 1 ブロック繰り上がってはいけない。
        assertEquals(0L, accumulator.accumulate(alice, 0.6));
        assertEquals(0L, accumulator.accumulate(bob, 0.6));
        assertEquals(1L, accumulator.accumulate(alice, 0.6), "alice 自身の 1.2 ブロックぶんだけ繰り上がる");
    }

    @Test
    @DisplayName("大きな移動は繰り上がりを取りこぼさずそのまま返す")
    void largeJumpsAreReturnedWhole() {
        MovementAccumulator accumulator = new MovementAccumulator();
        UUID player = UUID.randomUUID();

        assertEquals(120L, accumulator.accumulate(player, 120.4));
        assertEquals(1L, accumulator.accumulate(player, 0.7), "持ち越した 0.4 と合わせて 1 ブロック");
    }

    @Test
    @DisplayName("0・負値・非有限値は無視する")
    void ignoresNonPositiveAndNonFiniteDistances() {
        MovementAccumulator accumulator = new MovementAccumulator();
        UUID player = UUID.randomUUID();

        assertEquals(0L, accumulator.accumulate(player, 0.0));
        assertEquals(0L, accumulator.accumulate(player, -5.0));
        assertEquals(0L, accumulator.accumulate(player, Double.NaN));
        assertEquals(0L, accumulator.accumulate(player, Double.POSITIVE_INFINITY));
        assertEquals(0L, accumulator.accumulate(null, 10.0));
        assertEquals(0, accumulator.trackedPlayers(), "無視した入力で状態を持ってはいけない");
    }

    @Test
    @DisplayName("退出したプレイヤーの端数は捨てられる")
    void forgetDropsTheCarry() {
        MovementAccumulator accumulator = new MovementAccumulator();
        UUID player = UUID.randomUUID();

        accumulator.accumulate(player, 0.9);
        assertEquals(1, accumulator.trackedPlayers());

        accumulator.forget(player);
        assertEquals(0, accumulator.trackedPlayers());
        assertEquals(0L, accumulator.accumulate(player, 0.9), "持ち越しが消えているので繰り上がらない");
    }
}
