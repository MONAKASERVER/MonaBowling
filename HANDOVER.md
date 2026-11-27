# MonaBowling 引き継ぎ

## 最終更新

- 日時: 2026-11-27 17:00（JST）
- AI: Codex（GPT-5系、詳細なモデル識別子は取得不能）
- 利用枠: 5時間・日間・週間・月間とも、この環境では残量を取得不能

## プロジェクトの目的

Paper 26.1.2 / Java 25 のHub上で、Java版・統合版の双方から遊べるWii Sports Resort風ボウリングを提供する。

## 最新の要求と進捗

最新要求は、かべよけの壁へ触れていないように見える位置で衝突音と反射が発生する問題の修正。

- `0.6.0-alpha.17`で修正済み
- Hub本番へ導入済み
- GitHub `main`へpush済み
- GitHub prerelease `v0.6.0-alpha.17`公開済み
- 実クライアントで衝突タイミングが自然に見えることの最終確認のみ未完了

## 実装済みの修正

- 保存済みレーンY座標の上下を走査し、実際の床に支えられた空気ブロックへ壁を配置するよう変更
- Java/統合版向け描画器の切替時、旧描画器の後片付けが新しい壁を消す順序問題を修正
- 壁を1個も設置できなかった場合に診断警告をログへ出力
- 物理壁を設計座標ではなく実際に設置できた各ブロックの中心から生成
- 1mブロックの判定を各辺0.10m縮小し、見た目より早い衝突を防止
- 表示されなかった壁に不可視の当たり判定が残らないよう修正
- リリース作成スクリプトへJARコピーとSHA-256ファイル生成を追加
- 既存の球の転がり音、ピン・壁・バンパー音の参加者向け直接再生を維持

## 変更ファイル

- `src/main/java/net/monacraft/bowling/game/BowlingRenderer.java`
  - 床面検出と壁配置件数の返却
- `src/main/java/net/monacraft/bowling/game/BowlingGameManager.java`
  - 描画器交換順序の修正とゼロ配置警告
- `build.gradle.kts`
  - バージョンを`0.6.0-alpha.17`へ更新
- `src/main/resources/config.yml`
  - バージョン表記更新
- `tools/package_release.ps1`
  - JAR梱包とチェックサム生成を修正
- `README.md`, `docs/ADMIN_SETUP.md`, `TEST_RESULTS.md`
  - alpha.17向け更新

## ビルド・テスト・本番状態

- コマンド: `.\gradlew.bat clean test shadowJar --no-daemon`
- 結果: 成功
- 自動テスト: 81件成功、0件失敗
- JAR: `dist/MonaBowling-0.6.0-alpha.17.jar`
- SHA-256: `ff7cd7e14bc0521098870ff9f845064a0a9a0e0ee0d91b19b51519a024d4ff61`
- Hub起動: `MonaBowling v0.6.0-alpha.17`、5レーン読込、Paper起動完了
- Hub / Main / Bungee: すべて`active`
- 本番バックアップ: `/home/minecraft/backups/monabowling-alpha17-20261127-165656`
- GitHub release: `https://github.com/MONAKASERVER/MonaBowling/releases/tag/v0.6.0-alpha.17`

## 既知の注意・未確認事項

- Java版または統合版の実クライアントで、壁への接触タイミングをユーザーに再確認してもらう必要がある。
- `Wall-dodge stage ... placed no visible blocks`が出た場合は、対象レーンの床構造と保存Y座標を調査する。
- Hub停止時に別プラグイン由来のH2 `NoClassDefFoundError`が記録されることがある。MonaBowlingの起動には影響していない。
- 本番データベースと5レーン定義は維持すること。
- Java/統合版互換のため、壁はDisplay Entityではなく一時的な実ブロックを使う設計を維持すること。

## 次に実行すべき作業

1. `/bowmenu`からかべよけを開始し、壁の横を通した際に早すぎる衝突がないか実機確認する。
2. まだ早い場合は`wall-dodge.hitbox-inset`を0.12～0.15へ調整して再起動する。
3. 壁の中央へ投げた際は確実に反射と衝突音が出ることを確認する。
4. Java版・統合版の両方で同じ位置関係になることを確認する。

## 開発再開コマンド

```powershell
Set-Location "C:\Users\tokyo\Documents\Codex\2026-10-17\files-pasted-by-the-user-monaindustry\MonaBowling"
git status --short
.\gradlew.bat clean test shadowJar --no-daemon
.\tools\package_release.ps1
```

## Git状態

- 最新実装コミット: `fd41f8c Align wall collision with rendered blocks`
- 引き継ぎ完了時点で作業ツリーがクリーンであることを確認する。
