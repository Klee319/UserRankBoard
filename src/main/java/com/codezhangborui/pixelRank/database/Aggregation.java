package com.codezhangborui.pixelRank.database;

/**
 * 複数サーバーの記録を 1 つの順位表へまとめるときの集計方法。
 *
 * <p>SUM: サーバーごとに独立して積み上がる値（採掘数・設置数・プレイ時間など）。
 * 3 台の値を足して合計を出す。</p>
 *
 * <p>MAX: 3 台が「同じ 1 つの値」を報告する項目（所持金）。
 * 経済データベースが共通なので 3 台とも同じ残高を書き込む。
 * これを SUM すると所持金が 3 倍に表示されるため、必ず MAX を使う。</p>
 */
public enum Aggregation {
    SUM {
        @Override
        public long merge(long a, long b) {
            return a + b;
        }

        @Override
        public String sqlFunction() {
            return "SUM";
        }
    },
    MAX {
        @Override
        public long merge(long a, long b) {
            return Math.max(a, b);
        }

        @Override
        public String sqlFunction() {
            return "MAX";
        }
    };

    /** 同一プレイヤーの 2 つの値を 1 つにまとめる。 */
    public abstract long merge(long a, long b);

    /** SQL 側の集計関数名（SUM / MAX）。 */
    public abstract String sqlFunction();
}
