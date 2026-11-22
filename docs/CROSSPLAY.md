# MonaBowling Cross-play

## 判定経路

起動時にFloodgate、次にGeyserの公式APIを検出します。どちらも存在しなければ
Javaクライアントとして扱います。プレイヤー名の接頭辞から統合版かどうかを推測
しません。リンク済みFloodgateアカウントでもUUIDをAPIへ渡して判定します。

- Floodgate: `FloodgateApi#getPlayer` / `isFloodgatePlayer`
- Geyser: `GeyserApi#connectionByUuid` / `isBedrockPlayer`
- 端末情報: input mode、platform/device OS、client version

公式資料:

- https://geysermc.org/wiki/floodgate/api/
- https://geysermc.org/wiki/geyser/api/
- https://geysermc.org/wiki/floodgate/setup/

## BungeeCord backend設定

Hubで端末を判定するには、プロキシだけでなくHub backendにもFloodgateが必要です。

1. BungeeCordで`ip_forward: true`を設定します。
2. プロキシFloodgateで`send-floodgate-data: true`を設定します。
3. HubへFloodgate-Spigotを導入します。
4. プロキシとHubで同一の`key.pem`を使用します。
5. プロキシとHubを通常再起動します。

`key.pem`は認証用秘密鍵です。Git、配布ZIP、ログへ含めてはいけません。

## 入力

投球中は各tickのyaw差分を合計し、±180度境界を補正してから入力方式別の倍率を
適用します。小さな揺れはdeadzoneで除外し、最大スピン値で制限します。

- Java: use（通常は右クリック）
- Bedrock: use、または`alternative-input`有効時のattack
- 1回目: チャージ開始
- 2回目: リリース
- 最大チャージ時間: 自動リリース

設定値は`input.*`と`geyser.*`にあります。Floodgateのinput modeは端末・バージョン
によってUNKNOWNになる可能性があるため、`unknown-input-sensitivity`を用意しています。

## 表示

JavaはBlockDisplayで補間・傾斜・回転を表示します。BedrockにはJavaのDisplay Entityが
存在しないため、`geyser.armor-stand-fallback: true`ではGeyserが確実に変換できる
ネイティブItem Entityを使ってピンとボールを表示します。設定キー名は既存configとの
互換性のため維持しています。両方式とも非永続、無敵、無重力で、終了時に削除されます。

互換表示は動作優先で、高品質な球体・ピンモデルではありません。Bedrock専用カスタム
Entityやリソースパックは、Geyserの実験的Entity APIへ固定依存しないようPhase 4には
含めていません。
