# MonaBowling 引き継ぎ

## 最終更新

- 日時: 2026-11-27 17:20（JST）
- AI: Codex（GPT-5系、詳細なモデル識別子は取得不能）
- 利用枠: 5時間・日間・週間・月間とも、この環境では残量を取得不能

## プロジェクトの目的

Paper 26.1.2 / Java 25 のHub上で、Java版・統合版の双方から遊べるWii Sports Resort風ボウリングを提供する。

## 最新の要求と進捗

最新要求は、かべよけの配置をゲームごとに変化させ、鉄ブロックを塀へ変更すること。

- `0.6.0-alpha.18`で実装済み
- Hub本番へ導入済み
- GitHub `main`へpush済み
- GitHub prerelease `v0.6.0-alpha.18`公開済み
- Java版・統合版で複数ゲームの配置差を確認する実機試験のみ未完了

## 実装済みの修正

- 保存済みレーンY座標の上下を走査し、実際の床に支えられた空気ブロックへ壁を配置するよう変更
- Java/統合版向け描画器の切替時、旧描画器の後片付けが新しい壁を消す順序問題を修正
- 壁を1個も設置できなかった場合に診断警告をログへ出力
- 物理壁を設計座標ではなく実際に設置できた各ブロックの中心から生成
- 1mブロックの判定を各辺0.10m縮小し、見た目より早い衝突を防止
- 表示されなかった壁に不可視の当たり判定が残らないよう修正
- ゲームごとのランダムseedから10フレームの配置を決定
- 同一ゲーム・同一フレームでは配置を固定し、別ゲームでは位置と奥行きを変更
- 全ランダム配置で外側の安全経路を保証
- 障害物とメニューアイコンを`POLISHED_BLACKSTONE_WALL`へ変更
- 塀の細い形状に合わせ、判定を各辺0.24m内側へ縮小
- リリース作成スクリプトへJARコピーとSHA-256ファイル生成を追加
- 既存の球の転がり音、ピン・壁・バンパー音の参加者向け直接再生を維持

## 変更ファイル

- `src/main/java/net/monacraft/bowling/game/BowlingRenderer.java`
  - 床面検出と壁配置件数の返却
- `src/main/java/net/monacraft/bowling/game/BowlingGameManager.java`
  - 描画器交換順序の修正とゼロ配置警告
- `build.gradle.kts`
  - バージョンを`0.6.0-alpha.18`へ更新
- `src/main/resources/config.yml`
  - バージョン表記更新
- `tools/package_release.ps1`
  - JAR梱包とチェックサム生成を修正
- `README.md`, `docs/ADMIN_SETUP.md`, `TEST_RESULTS.md`
  - alpha.18向け更新

## ビルド・テスト・本番状態

- コマンド: `.\gradlew.bat clean test shadowJar --no-daemon`
- 結果: 成功
- 自動テスト: 82件成功、0件失敗
- JAR: `dist/MonaBowling-0.6.0-alpha.18.jar`
- SHA-256: `f8559c8105e359c0664c8641ba1dbc5669efff0bee4cc94673df030acabba813`
- Hub起動: `MonaBowling v0.6.0-alpha.18`、5レーン読込、Paper起動完了
- Hub / Main / Bungee: すべて`active`
- 本番バックアップ: `/home/minecraft/backups/monabowling-alpha18-20261127-171725`
- GitHub release: `https://github.com/MONAKASERVER/MonaBowling/releases/tag/v0.6.0-alpha.18`

## 既知の注意・未確認事項

- Java版・統合版で、新規ゲームごとに配置が変わることをユーザーに再確認してもらう必要がある。
- `Wall-dodge stage ... placed no visible blocks`が出た場合は、対象レーンの床構造と保存Y座標を調査する。
- Hub停止時に別プラグイン由来のH2 `NoClassDefFoundError`が記録されることがある。MonaBowlingの起動には影響していない。
- 本番データベースと5レーン定義は維持すること。
- Java/統合版互換のため、壁はDisplay Entityではなく一時的な実ブロックを使う設計を維持すること。

## 次に実行すべき作業

1. `/bowmenu`からかべよけを複数回新規作成し、配置が変わることを確認する。
2. 同一ゲーム中の同じフレームでは、参加者が交代しても配置が変わらないことを確認する。
3. 磨かれたブラックストーンの塀がJava版・統合版の両方で見えることを確認する。
4. 塀の横を通した際の衝突タイミングが自然か確認する。

## 開発再開コマンド

```powershell
Set-Location "C:\Users\tokyo\Documents\Codex\2026-10-17\files-pasted-by-the-user-monaindustry\MonaBowling"
git status --short
.\gradlew.bat clean test shadowJar --no-daemon
.\tools\package_release.ps1
```

## Git状態

- 最新実装コミット: `4710b96 Randomize wall-dodge courses and use wall blocks`
- 引き継ぎ完了時点で作業ツリーがクリーンであることを確認する。
