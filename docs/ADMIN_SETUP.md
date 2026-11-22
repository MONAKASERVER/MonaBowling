# MonaBowling 管理者向け設置手順（Phase 6）

## 導入前提

- Paper 26.1.2
- Java 25
- BungeeCord配下のHub
- 書き込み可能なプラグインデータディレクトリ

Citizens、WorldGuard、PlaceholderAPI、Floodgate、Geyserは任意依存です。存在しない場合も
Java版専用として安全に起動します。

## 導入

1. `MonaBowling-0.6.0-alpha.14.jar`をHubのプラグイン領域へ配置します。
2. サーバーを再起動します。Paperの全体reloadは使用しません。
3. 起動ログでSQLite schema version 1とMonaBowling enabledを確認します。
4. `/bowling admin debug`でDBと任意連携の状態を確認します。

本番投入前には既存Hub一式のバックアップを取得してください。Paper全体reloadや
PlugMan系の強制reloadは使わず、停止・JAR交換・起動の順で更新してください。

## Citizens受付NPC

1. Mainと同じCitizens 2.0.43 build 4230をHubへ導入します。
2. NPCを置きたい場所に立ち、向きを合わせて`/setnpc`を実行します。
3. 同じコマンドを再実行するとNPCは増えず、保存済みの受付NPCが移動します。
4. NPCを右クリックし、`/bowmenu`と同じメニューが開くことを確認します。

NPC IDは`citizens.npc-id`へ保存され、受付識別子はCitizensの永続メタデータへ保存されます。
スキンURLは`citizens.skin-url`で変更できます。公開HTTPSのPNG直リンクだけを指定してください。
画像取得に失敗した場合もNPCとメニュー連携は残り、次回`/setnpc`で再取得できます。

## レーン登録

1. 投球開始位置に立ち、ピン方向を向きます。
2. `/bowling admin create lane01`を実行します。初期先頭ピン位置は視線方向18mです。
3. 正確な投球位置で `/bowling admin setspawn lane01` を実行します。
4. レーン基準位置で `/bowling admin setlane lane01` を実行します。
5. 先頭ピンの中心で `/bowling admin setpins lane01` を実行します。
6. `/bowling admin debug`で登録件数を確認します。

`setspawn` と `setpins` の水平距離は3m以上必要です。通常は約18mを推奨します。
`setspawn` の足元を赤い投球線に合わせてください。保存位置とピンの方向から
ファウル面を作り、参加プレイヤーがピン側へ越えるのを防ぎます。
表示ピンの底面が床に合うよう両地点のY座標を同じ高さにしてください。使用中の
レーンは削除・座標変更できません。

## 動作確認

1. `/bowling join lane01` で参加し、専用スノーボールが選択されることを確認します。
2. 右クリックで構え、ゲージ中に視点を左右へ動かし、再度右クリックします。
3. ボールとピンが表示され、停止後に倒ピン数が確定することを確認します。
4. 左右壁でボールが減速反射し、ガター判定にならないことを確認します。
5. 10フレーム完了後に元の位置へ戻り、`/bowling stats standard`へ記録されることを確認します。
6. `/bowling leave`、死亡、切断、ワールド移動後に表示が残らないことを確認します。
7. Java版と統合版で `/bowling client` を実行し、EditionとInputを確認します。
8. `/bowmenu`で2〜4人ロビーを作成し、公開一覧から参加・満員自動開始・
   作成者の早期開始・フレーム単位の順番交代を確認します。

1レーンの人数上限は `matches.max-players-per-lane` で1〜4人に制限できます。

## 100ピン確認

1. `config.yml`の`game-modes.hundred-pin`が`true`、
   `matches.max-concurrent-hundred-pin`が1以上であることを確認します。
2. `/bowling join hundred-pin lane01`で100本が表示されることを確認します。
3. 通常投球、左右バンパー反射、ストライク、スペア、10フレーム完了を確認します。
4. 同時上限を超えた100ピン参加が拒否され、スタンダードには影響しないことを確認します。
5. 片側の細い塀上ラインへ投球開始直後から乗せ、後端の赤いスイッチまで通した投球が
   シークレットストライクになり、途中からガターへ入れた投球では発動しないことを確認します。
6. レーン側から塀へ当てるとバンパー反射し、塀上ラインを外すとハーフブロックの
   ガター扱いになることを確認します。

100ピンではレーン物理幅を広げています。表示が壁や隣レーンへ重なる場合は、実際の
建築間隔を広げてください。描画負荷は`rendering.maximum-display-updates-per-tick`、
同時負荷は`matches.max-concurrent-hundred-pin`で制限できます。

## かべよけ確認

1. `/bowling join wall-dodge lane01`で開始し、1フレーム目の壁が表示されることを確認します。
2. 壁へ正面衝突させ、球が壁を貫通せず減速反射することを確認します。
3. フレームが進むたびに配置が変わり、10フレーム目まで通れる隙間が残ることを確認します。
4. Java版と統合版で壁のブロック位置が一致することを確認します。
5. 退出、切断、異常終了、Hub停止後に壁ブロックが残らないことを確認します。

壁は空気ブロックだけを`wall-dodge.wall-material`へ一時的に置き換えます。既存建築に
重なる地点は変更しません。各レーンの投球線からピンまで6ブロック以上必要です。

## BungeeCord・統合版

プロキシだけにFloodgateを置いた構成ではHub側APIから統合版判定ができません。
HubにもFloodgateを導入し、プロキシ側の`send-floodgate-data: true`、BungeeCordの
`ip_forward: true`、同一の`key.pem`を設定してください。秘密鍵は配布物やGitへ
絶対に含めないでください。詳細は `docs/CROSSPLAY.md` を参照してください。

IDは小文字英数字、`_`、`-`の1〜32文字です。レーン座標の設定はすべて同じ
ワールド内で行う必要があります。

## 復旧

- 不正なconfig値ではプラグインが安全に無効化され、DBは変更されません。
- DBはWALモードです。バックアップ時はサーバーを停止し、DB本体と付随ファイルを
  同時に保存してください。
- `/bowling admin reload`は進行中ゲームがある場合は拒否されます。configとレーン
  キャッシュのみ再読込します。JAR更新時は
  必ずHubを再起動してください。
