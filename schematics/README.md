# MonaBowling Phase 5 — 1レーン建築データ

Phase 5既定設定に合わせた、スタンダード／100ピン兼用の1レーンです。

## 収録形式

- `MonaBowling-Phase5-OneLane.litematic` — Fabric + Litematica用
- `MonaBowling-Phase5-OneLane.schem` — WorldEdit / FastAsyncWorldEdit用（Sponge v3）
- `SHA256SUMS.txt` — ファイル破損確認用

寸法は外寸 **幅11 × 高さ8 × 長さ33ブロック** です。入口から奥壁へ向かう方向が
ファイル内の `+Z` です。設計にはエンティティ、コマンドブロック、コンテナ、看板を
含めていません。

## 色付きマーカー

| 色・ブロック | 意味 | ファイル内座標 |
|---|---|---:|
| 赤（レッドコンクリート） | 投球線 | `Z=5` |
| 赤線中央 | 作成・スポーン登録地点 | `X=5, Y=1, Z=5` |
| 金（ゴールドブロック） | 先頭ピン中心 | `X=5, Y=1, Z=23` |
| 水色（ライトブルーコンクリート） | 100ピン最終列の目安 | `Z=28` |
| 黄（イエローコンクリート） | 左右ガードの目安 | `X=2/8, Z=29` |
| 紫（マゼンタコンクリート） | 隠しスイッチ判定の目安 | `X=5, Y=1, Z=29` |

`Y=1` は床ブロック上のプレイヤーの足元座標です。投球線中央と先頭ピン中心の
床高さを変えないでください。

## Litematicaで読み込む

1. `.litematic` をMinecraftインスタンスの `schematics` フォルダーへ入れます。
2. ゲーム内で `M` を押し、`Load Schematics` からファイルを読み込みます。
3. `Schematic Placements` で配置を作り、入口が使いたい向きになるよう回転します。
4. 赤い投球線の手前が5ブロック空く位置に配置します。
5. Litematicaの建築支援または貼り付け機能で建築します。

## WorldEdit / FAWEで読み込む

1. `.schem` を `plugins/FastAsyncWorldEdit/schematics/` または
   `plugins/WorldEdit/schematics/` に入れます。
2. 貼り付け基準に立って、次を実行します。

```text
//schem load MonaBowling-Phase5-OneLane
//paste -a
```

向きが違う場合は貼り付け前に `//rotate 90`、`//rotate 180`、`//rotate 270` の
いずれかを実行します。`-a` は空気を貼らないため、既存建築を完全に空洞化したい場合は
付けずに貼り付けてください。

## MonaBowlingへ登録する

1. 赤線中央 `X=5, Y=1, Z=5` に立ち、金ブロックへ正確に向きます。
2. 次を実行します。

```text
/bowling admin create lane01
/bowling admin setlane lane01
/bowling admin setspawn lane01
```

3. 金ブロック `X=5, Y=1, Z=23` の上へ移動します。
4. 次を実行します。

```text
/bowling admin setpins lane01
/bowling admin debug
```

5. 両モードを確認します。

```text
/bowling join standard lane01
/bowling join hundred-pin lane01
```

## 再生成と検証

Python 3.12以降で次を実行します。生成処理は両NBTを再読込し、寸法、データ
バージョン、パレット、2,904セル、重要マーカーを自動検証します。

```powershell
python -m pip install -r tools/requirements-schematic.txt
python tools/generate_lane_schematics.py
```

対象MinecraftデータバージョンはPaper 26.1.2の実ワールドから取得した `4790` です。
