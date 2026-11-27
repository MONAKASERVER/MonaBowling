# MonaBowling 引き継ぎ

## 最終更新

- 日時: 2026-11-27（JST）
- AI: Codex（GPT-5系、詳細なモデル識別子は取得不能）
- 利用枠: 5時間・日間・週間・月間とも、この環境では残量を取得不能

## プロジェクトの目的

Paper 26.1.2 / Java 25 のHub上で、Java版・統合版の双方から遊べるWii Sports Resort風ボウリングを提供する。

## 最新の要求と進捗

最新要求は、かべよけモードで障害物の壁が表示されない問題の修正。

- `0.6.0-alpha.16`で修正済み
- Hub本番へ導入済み
- GitHub `main`へpush済み
- GitHub prerelease `v0.6.0-alpha.16`公開済み
- 実クライアントで壁が見えることの最終確認のみ未完了

## 実装済みの修正

- 保存済みレーンY座標の上下を走査し、実際の床に支えられた空気ブロックへ壁を配置するよう変更
- Java/統合版向け描画器の切替時、旧描画器の後片付けが新しい壁を消す順序問題を修正
- 壁を1個も設置できなかった場合に診断警告をログへ出力
- リリース作成スクリプトへJARコピーとSHA-256ファイル生成を追加
- 既存の球の転がり音、ピン・壁・バンパー音の参加者向け直接再生を維持

## 変更ファイル

- `src/main/java/net/monacraft/bowling/game/BowlingRenderer.java`
  - 床面検出と壁配置件数の返却
- `src/main/java/net/monacraft/bowling/game/BowlingGameManager.java`
  - 描画器交換順序の修正とゼロ配置警告
- `build.gradle.kts`
  - バージョンを`0.6.0-alpha.16`へ更新
- `src/main/resources/config.yml`
  - バージョン表記更新
- `tools/package_release.ps1`
  - JAR梱包とチェックサム生成を修正
- `README.md`, `docs/ADMIN_SETUP.md`, `TEST_RESULTS.md`
  - alpha.16向け更新

## ビルド・テスト・本番状態

- コマンド: `.\gradlew.bat clean test shadowJar --no-daemon`
- 結果: 成功
- 自動テスト: 79件成功、0件失敗
- JAR: `dist/MonaBowling-0.6.0-alpha.16.jar`
- SHA-256: `bc76730cc38beaa95a45b11d20be6d1cf26c6494575280fc4dacea6243820e2d`
- Hub起動: `MonaBowling v0.6.0-alpha.16`、5レーン読込、Paper起動完了
- Hub / Main / Bungee: すべて`active`
- 本番バックアップ: `/home/minecraft/backups/monabowling-alpha16-20261123-012041`
- GitHub release: `https://github.com/MONAKASERVER/MonaBowling/releases/tag/v0.6.0-alpha.16`

## 既知の注意・未確認事項

- Java版または統合版の実クライアントで、かべよけ開始時に壁が表示されることをユーザーに再確認してもらう必要がある。
- `Wall-dodge stage ... placed no visible blocks`が出た場合は、対象レーンの床構造と保存Y座標を調査する。
- Hub停止時に別プラグイン由来のH2 `NoClassDefFoundError`が記録されることがある。MonaBowlingの起動には影響していない。
- 本番データベースと5レーン定義は維持すること。
- Java/統合版互換のため、壁はDisplay Entityではなく一時的な実ブロックを使う設計を維持すること。

## 次に実行すべき作業

1. `/bowmenu`からかべよけを開始し、1フレーム目の壁が見えるか実機確認する。
2. 見えない場合はHubログで`Wall-dodge stage`警告を検索する。
3. 警告がある場合は対象レーンの床ブロックYと障害物座標をサーバー上で採取する。
4. 見える場合は、フレーム進行時に旧壁が消えて新壁が表示されることを確認する。

## 開発再開コマンド

```powershell
Set-Location "C:\Users\tokyo\Documents\Codex\2026-10-17\files-pasted-by-the-user-monaindustry\MonaBowling"
git status --short
.\gradlew.bat clean test shadowJar --no-daemon
.\tools\package_release.ps1
```

## Git状態

- 最新実装コミット: `8f64cd3 Fix missing wall-dodge obstacle rendering`
- 引き継ぎ完了時点で作業ツリーがクリーンであることを確認する。
