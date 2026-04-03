# PixelRank

Minecraft（Paper）サーバー向けの軽量プレイヤーランキングプラグイン。採掘・設置・プレイ時間・死亡・移動距離・モブ討伐の6種類のランキングをスコアボードに自動表示します。
リポジトリ：https://github.com/Klee319/UserRankBoard.git

## 動作環境

- Paper 1.20.1+
- Java 17+

## 機能

### ランキング種別

| 種別 | 説明 | トリガー |
|------|------|----------|
| 採掘 (Mining) | ブロック破壊数 | ブロックを壊すたびに+1 |
| 設置 (Placement) | ブロック設置数 | ブロックを置くたびに+1 |
| プレイ時間 (Online Time) | サーバー接続時間 | 60秒ごとに+1（オンライン中のみ） |
| 死亡 (Death) | 死亡回数 | 死亡するたびに+1 |
| 移動距離 (Movement) | 移動した総距離（ブロック単位） | 移動するたびに距離を加算 |
| モブ討伐 (Mob Kills) | 敵モブの討伐数 | 敵モブを倒すたびに+1 |

各ランキングは `config.yml` で個別に有効/無効を設定できます。

### スコアボード

- サイドバーに上位プレイヤーを自動表示
- 有効なランキングを設定間隔で自動切替
- プレイヤーごとに `/pixelrank toggle` で表示/非表示を切り替え可能
- 新規プレイヤーの初期表示状態を設定可能

### データ永続化

- SQLite でローカル保存
- メモリ上のデータを定期的に非同期でDBに保存
- サーバー停止時にも自動保存

## インストール

1. [Releases](https://github.com/CodeZhangBorui/PixelRank/releases) から最新のJARをダウンロード
2. サーバーの `plugins/` フォルダに配置
3. サーバーを起動
4. 生成された `config.yml` で設定をカスタマイズ

## コマンド

| コマンド | 説明 | 権限 |
|----------|------|------|
| `/pixelrank` | バージョン情報を表示 | なし |
| `/pixelrank help` | ヘルプを表示 | なし |
| `/pixelrank rank` | ランキング種別の選択肢を表示 | なし |
| `/pixelrank rank mine` | 採掘ランキング（上位50位）を表示 | なし |
| `/pixelrank rank place` | 設置ランキング（上位50位）を表示 | なし |
| `/pixelrank rank time` | プレイ時間ランキング（上位50位）を表示 | なし |
| `/pixelrank rank death` | 死亡ランキング（上位50位）を表示 | なし |
| `/pixelrank rank move` | 移動距離ランキング（上位50位）を表示 | なし |
| `/pixelrank rank mobkill` | モブ討伐ランキング（上位50位）を表示 | なし |
| `/pixelrank toggle` | スコアボードの表示/非表示を切り替え | なし（プレイヤーのみ） |
| `/pixelrank reload` | 設定を再読み込み＆データ保存 | `pixelrank.admin` または OP |

### ランキング表示の色分け

- 🥇 1位: 金色
- 🥈 2位: 黄色
- 🥉 3位: 緑色
- 4位以降: 灰色
- 自分の順位: マゼンタ色でハイライト

## 設定 (config.yml)

```yaml
ranks:
  # 新規プレイヤーのスコアボード初期表示（true=表示, false=非表示）
  scoreboard_default: true
  # 各ランキングの有効/無効
  mining_rank: true
  placing_rank: true
  online_time_rank: true
  death_rank: false
  movement_rank: true
  mob_kill_rank: true
  # スコアボードのランキング切替間隔（秒）
  switch_interval: 15
  # ランキングから除外するプレイヤー名の正規表現
  ignore_username_regex: "Input_a_regex_here_to_ignore_specific_usernames"

storage:
  # SQLite データベースファイル名
  database: "database.db"
  # データベースへの保存間隔（秒）
  save_interval: 60

leaderboards:
  # 各ランキングのスコアボード表示タイトル
  mining_rank: "Mining"
  placing_rank: "Placement"
  online_time_rank: "Online Time"
  death_rank: "Death"
  movement_rank: "Movement"
  mob_kill_rank: "Mob Kills"
  # スコアボードに表示する最大人数
  max_leaderboard_size: 10
```

## ビルド

```bash
./gradlew shadowJar
```

成果物は `build/libs/` に生成されます。

## バグ報告・提案

[Issues](https://github.com/CodeZhangBorui/PixelRank/issues) ページからお願いします。

## ライセンス

[MIT License](https://opensource.org/license/MIT) | Author: CodeZhangBorui
