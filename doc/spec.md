# Misattitude 仕様書

最終更新: 2026-05-04

## 1. アプリ概要

`Misattitude` は **3 次元剛体姿勢 (attitude / rotation) の 3 表現**
（オイラー角・四元数・回転行列）を、同一の SO(3) 要素として相互変換しながら
可視化する Android 教育用アプリ。

- 対象: SLAM・ロボティクス・CG・航空宇宙でジンバルロックや回転規則の差異を学ぶ学生／エンジニア
- 中核アイデア: 内部状態は **正規化単位四元数 1 個**。オイラー角・行列は派生ビュー。どのパネルで編集しても
  即座に他表現に反映され、3D の物体姿勢が同期する。
- 実装言語: Kotlin (純粋数学はドメイン層、3D 描画は Google Filament、UI は Jetpack Compose)
- 対象 Android API: minSdk 26 / targetSdk 34 / compileSdk 34、JVM target 17

## 2. プロジェクト構成

```
Misattitude/
├── README.md                       初期セットアップ手順 (英語)
├── doc/spec.md                     ★本ドキュメント
├── build.gradle.kts                top-level
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml       Filament 1.54.5, Compose BOM 2024.09.03, JUnit 4.13.2 ほか
├── app/
│   ├── build.gradle.kts            app: Compose / Filament / matc 統合
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/io/github/takarakasai/misattitude/
│   │   │   ├── MainActivity.kt
│   │   │   ├── domain/             ← 純 Kotlin の数学ドメイン (Android 依存なし)
│   │   │   │   ├── Vec3.kt
│   │   │   │   ├── Quaternion.kt
│   │   │   │   ├── RotationMatrix.kt
│   │   │   │   ├── EulerConvention.kt
│   │   │   │   ├── EulerAngles.kt
│   │   │   │   ├── Conversions.kt
│   │   │   │   ├── WorldConvention.kt
│   │   │   │   └── BodyShape.kt
│   │   │   ├── gl/                 ← Filament 描画
│   │   │   │   ├── Meshes.kt
│   │   │   │   ├── AttitudeScene.kt
│   │   │   │   └── FilamentSurfaceView.kt
│   │   │   └── ui/                 ← Compose UI + ViewModel
│   │   │       ├── AttitudeViewModel.kt
│   │   │       ├── MainScreen.kt
│   │   │       ├── EulerPanel.kt
│   │   │       ├── QuaternionPanel.kt
│   │   │       ├── MatrixPanel.kt
│   │   │       ├── PlaybackPanel.kt
│   │   │       └── theme/Theme.kt
│   │   ├── materials/              ← .mat ソース (matc で .filamat に変換)
│   │   │   ├── vertex_color.mat        (transparent, doubleSided)
│   │   │   └── vertex_color_opaque.mat (opaque,      doubleSided)
│   │   └── res/                    Android リソース
│   └── src/test/                   ← JUnit 4 ユニットテスト
│       └── java/io/github/takarakasai/misattitude/domain/ConversionsTest.kt
```

## 3. ビルドフロー

### 3.1 Filament マテリアルの事前コンパイル

`.mat` (テキスト) を Filament の `matc` ツールで `.filamat` (バイナリ) にコンパイルし、
`app/src/main/assets/` に配置する。`app/build.gradle.kts` に組み込まれた
`compileMaterials` Gradle タスクが `preBuild` で自動実行される。

`matc` の場所は次のいずれかで指定する。

- 環境変数 `MATC_PATH=/abs/path/to/filament/bin/matc`
- `~/.gradle/gradle.properties` に `matcPath=/abs/path/to/filament/bin/matc`

`matc` は Filament 公式リリース tarball (`https://github.com/google/filament/releases`) のホスト OS
向けバイナリに同梱されている。Filament Android のバージョン (`libs.versions.toml` の `filament=1.54.5`)
と揃えること。

`compileMaterials` の動作:

```kotlin
matc -p mobile -a opengl -o app/src/main/assets/<name>.filamat app/src/main/materials/<name>.mat
```

### 3.2 Gradle タスク

| タスク | 内容 |
|---|---|
| `compileMaterials` | `.mat → .filamat` 変換 (`preBuild` から依存) |
| `assembleDebug` | デバッグ APK 生成 (`app/build/outputs/apk/debug/app-debug.apk`) |
| `installDebug` | エミュレータ／実機にインストール |
| `test` | JVM ユニットテスト (`ConversionsTest`) を実行 |

## 4. アーキテクチャ

```
┌────────────────────────┐  StateFlow<UiState>   ┌──────────────────────┐
│  Compose UI (ui/*)     │ ◀──────────────────── │  AttitudeViewModel   │
│  - MainScreen          │ ────────────────────▶ │  (ui/AttitudeViewModel)│
│  - EulerPanel          │   メソッド呼び出し     └──────┬───────────────┘
│  - QuaternionPanel     │                                │ canonical: Quaternion
│  - MatrixPanel         │                                ▼
│  - PlaybackPanel       │  AndroidView ──▶  FilamentSurfaceView
│  - WorldConventionBar  │                       (gl/FilamentSurfaceView.kt)
└────────────────────────┘                                │
              ▲                                           ▼
              │ 表示用変換 (Conversions)         AttitudeScene  ── Filament
              │                                      (gl/AttitudeScene.kt)
       ┌──────┴──────────────┐
       │  domain/* (純 Kotlin) │ ← UI 層・GL 層から共通利用
       └─────────────────────┘
```

レイヤー方針:

- **`domain`** … Android / Filament 依存ゼロ。JVM ユニットテスト対象。
- **`gl`** … Filament Engine と Compose のブリッジ。`AttitudeScene` が GPU リソースを所有。
- **`ui`** … Compose で `UiState` を観測。ユーザ操作を ViewModel 経由でドメインに届ける。

## 5. ドメイン層 (回転数学)

### 5.1 数学規約 (重要)

| 項目 | 規約 |
|---|---|
| 四元数 | **Hamilton convention** `q = w + x i + y j + z k`、`(w, x, y, z)` で格納 |
| 乗算順 | `(qa * qb)` をベクトルに作用 → **qb を先に**、qa を後に適用。行列の `R(qa)*R(qb)` と一致 |
| 回転行列 | 列ベクトルに左から作用 `v_world = R · v_body` (3×3、行優先記憶) |
| 内在 (Intrinsic) XYZ | `R = R_X(a1) · R_Y(a2) · R_Z(a3)` — 各回転は **その時点の body 軸** |
| 外在 (Extrinsic) XYZ | `R = R_Z(a3) · R_Y(a2) · R_X(a1)` — 各回転は **固定 world 軸** |
| 一意性 | `q` と `-q` は同じ回転。表示／比較時は `canonical()` で `w >= 0` に揃える |
| 単位 | 角度はすべて **ラジアン** (UI では deg ⇄ rad を `EulerPanel` 内で変換) |

### 5.2 `Vec3` (`domain/Vec3.kt`)

```
data class Vec3(x, y, z): plus / minus / times(scalar) / dot / length / normalized
companion: X = (1,0,0), Y = (0,1,0), Z = (0,0,1), ZERO
```

### 5.3 `Quaternion` (`domain/Quaternion.kt`)

```
data class Quaternion(w, x, y, z)
  operator times(o)        ハミルトン積 (R(this)*R(o))
  unaryMinus               (−w, −x, −y, −z)
  conjugate                (w, −x, −y, −z)
  norm / normalized
  canonical                w<0 のとき −this を返す (二重被覆の解消)
  dot
  companion:
    IDENTITY = (1,0,0,0)
    fromAxisAngle(axis: Vec3, angleRad)
    slerp(a, b, t)         t∈[0,1]、cosθ<0 で b 反転、cosθ>0.9995 は LERP+正規化にフォールバック
extension:
  isFinite()               全成分有限か
  approxEquals(o, eps=1e-9) ±同一視のうえ成分差 < eps
```

### 5.4 `RotationMatrix` (`domain/RotationMatrix.kt`)

行優先 3×3。`m[r][c]` で要素アクセス。

```
times(o), transpose, orthonormalized()  Gram-Schmidt で SO(3) に射影
companion:
  IDENTITY
  rotX(a) / rotY(a) / rotZ(a)            標準軸まわり 3×3 回転
  rotAxis(axis: Axis, a)                  Axis enum によるディスパッチ
```

`orthonormalized()` は **`MatrixPanel` で手入力された (おそらく非正規) 行列** を SO(3) に射影してから
四元数化するために `AttitudeViewModel.setMatrix` で使用。

### 5.5 `Axis` / `EulerConvention` / `EulerAngles`

```
enum Axis { X, Y, Z }        unit: Vec3, index: 0..2
enum FrameKind { Intrinsic, Extrinsic }

data class EulerConvention(axis1, axis2, axis3, frame)
  init: 隣接軸が異なることを require (axis1 != axis2 && axis2 != axis3)
  isProperEuler  axis1 == axis3
  isTaitBryan    !isProperEuler
  label          "ZYX (intrinsic)" のような表示名
  companion:
    ALL          12 順序 × 2 frame = 24 要素 (Tait-Bryan 6 + Proper Euler 6)
    DEFAULT      ZYX intrinsic (航空姿勢の慣例)

data class EulerAngles(a1, a2, a3) [radians]
```

#### 12 Euler 順序の内訳

| 種別 | 順序 |
|---|---|
| Tait-Bryan (3 軸が全て異なる) | XYZ, XZY, YXZ, YZX, ZXY, ZYX |
| Proper Euler (1=3 が一致) | XYX, XZX, YXY, YZY, ZXZ, ZYZ |

これらが `Intrinsic` / `Extrinsic` 各々で計 24 通り存在する。

### 5.6 `Conversions` (`domain/Conversions.kt`)

#### 5.6.1 四元数 ⇄ 行列

- `quaternionToMatrix(q)` — 標準展開
- `matrixToQuaternion(m)` — **Shepperd の方法**（trace > 0 / 主対角成分の最大値で 4 分岐）。
  全 SO(3) で数値安定。`s = sqrt(1 + dominant_diagonal_term) * 2` で除算する戦略。

#### 5.6.2 オイラー角 → 四元数 / 行列

```
eulerToQuaternion(angles, c)
  Intrinsic:  q = q_a1 * q_a2 * q_a3   (左から「先に適用される」順で並ぶ)
  Extrinsic:  q = q_a3 * q_a2 * q_a1
eulerToMatrix(angles, c)               同様の積で R を構成
```

#### 5.6.3 四元数 / 行列 → オイラー角

`matrixToEuler(m, c)` は **外在を「軸順序を反転した内在」に還元** する恒等式

```
R_extrinsic(A,B,C; a1,a2,a3) = R_intrinsic(C,B,A; a3,a2,a1)
```

を使い、内在の抽出ロジック `extractIntrinsic` 1 本で 24 規則すべてを処理する。

##### Tait-Bryan 抽出 (`extractTaitBryan`)

R = R_i(a1) · R_j(a2) · R_k(a3) を分解して、

```
M[i][k] =  σ · sin(a2)
M[j][k] = -σ · cos(a2) · sin(a1)
M[k][k] =       cos(a2) · cos(a1)
M[i][i] =       cos(a2) · cos(a3)
M[i][j] = -σ · cos(a2) · sin(a3)
```

から `a2 = asin(σ · M[i][k])`、`cos(a2) > GIMBAL_EPS` のとき
`a1 = atan2(-σ M[j][k], M[k][k])`、`a3 = atan2(-σ M[i][j], M[i][i])`。
σ は (i, j, k) の偶/奇置換に応じた ±1。

##### Proper Euler 抽出 (`extractProperEuler`)

R = R_i(a1) · R_j(a2) · R_i(a3) (k は 3 - i - j) で

```
M[i][i] =  cos(a2)
M[i][j] =  sin(a2) · sin(a3)
M[i][k] =  σ · sin(a2) · cos(a3)
M[j][i] =       sin(a1) · sin(a2)
M[k][i] = -σ · cos(a1) · sin(a2)
```

から `a2 = acos(M[i][i])`、`sin(a2) > GIMBAL_EPS` のとき
`a1 = atan2(M[j][i], -σ M[k][i])`、`a3 = atan2(M[i][j], σ M[i][k])`。

##### ジンバルロック分岐

| 種別 | 特異条件 | 処理 |
|---|---|---|
| Tait-Bryan | `cos(a2) ≈ 0`  (a2 → ±π/2) | `a3 = 0` に固定し、`a1 ± a3` を `a1` に集約 |
| Proper Euler | `sin(a2) ≈ 0` (a2 → 0 or π) | 同上 |

`a1` の符号は `sign(s2)` または `sign(c2)` で決定される。詳細は実装コメント参照。

`EulerExtraction` は `(angles, gimbalLock: Boolean)` を返し、UI 層が警告表示に使う。
`isNearGimbalLock(angles, c, eps=1e-3)` は **特異点に近いか** を返すヘルパで、
EulerPanel が「a1/a3 がもうすぐ独立性を失う」ことを警告するのに使用。

#### 5.6.4 ステップ分解 (`stepOrientations`)

オイラー分解の途中姿勢 4 個を返す:

```
[ q0, q1, q2, q3 ]
  q0 = identity
  q1 = a1 だけ適用後
  q2 = a1, a2 適用後
  q3 = 完全姿勢 (eulerToQuaternion と一致)
```

Intrinsic では `[I, q1, q1*q2, q1*q2*q3]`、Extrinsic では `[I, q1, q2*q1, q3*q2*q1]`。
直感としては「ユーザが規則どおりに 1 個ずつ角度を増やしていく中間状態」。

#### 5.6.5 連続版 `stepInterpolated(angles, c, t)`

`t ∈ [0, 1]` を 3 区間に分け、現在区間内では「その軸まわりの部分回転」を線形に増やす。
`t = 1` を含めるため `seg = (t * 3).coerceAtMost(2.999999)` でクランプ。

#### 5.6.6 補間 (Slerp / Euler-LERP)

```
slerp(a, b, t)               Quaternion.slerp と同一 (object-level エイリアス)
eulerLerp(start, end, c, t)  Euler 角を成分線形補間 → eulerToQuaternion(...)
```

`eulerLerp` は **SO(3) 上の測地線ではなく**、教材上「Slerp と比較して悪さ」を示すために存在。

## 6. 世界座標系の規約 (`domain/WorldConvention.kt`)

```
enum UpAxis { Y, Z }
enum Handedness { RightHanded, LeftHanded }
data class WorldConvention(upAxis = Y, handedness = RightHanded)
  companion:
    GraphicsDefault = (Y, RightHanded)   OpenGL/Unity 系
    Robotics        = (Z, RightHanded)   ROS / 航空・宇宙
```

- 数値演算（四元数・行列）は **規約に依存しない**。世界規約はカメラ配置とラベルビルボードの up 方向にのみ影響する。
- 同一の四元数を「Y-up RH」「Z-up RH」両方で見ることで、規約の違いが**回転量を変えない**ことを学べる。
- 左手系は **片軸を反転**して描画する：camera を Z 軸対称に配置することで「DirectX っぽい見た目」を再現。

### 6.1 規約ごとのデフォルト視点 (`AttitudeScene.applyCurrentConvention`)

| upAxis | handedness | cameraPos | worldUp | 備考 |
|---|---|---|---|---|
| Y | RH | (2.4, 1.9,  +2.6) | (0, 1, 0) | OpenGL 標準 |
| Y | LH | (2.4, 1.9,  −2.6) | (0, 1, 0) | DirectX 風 (Z 反転) |
| Z | RH | (+2.4, 2.6, 1.9) | (0, 0, 1) | ROS。原視点を world Z 軸に +90° 回した位置 |
| Z | LH | (−2.4, 2.6, 1.9) | (0, 0, 1) | LH ミラー |

Z-up RH は単に「+X +Y +Z 象限から見る」のではなく、**world +X が画面左、+Y が画面右** に来るよう
オリジナルのロボティクス視点 (X-forward) を **+90° about Z** だけ回した配置にしている。
これは「ROS 教科書で見るロボットを正面寄り左から眺めた図」と一致する。

## 7. ボディ形状 (`domain/BodyShape.kt`)

```
enum BodyShape { Cube, Tux, Capsule }
```

| Cube | 半透明 (α=0.30) のリファレンスキューブ。原点と body 軸が透けて見える教材向け |
| Tux  | Linux マスコット風スタイライズドペンギン。プリミティブ合成 (楕円体・球・コーン) |
| Capsule | カプセル型 (円筒 + 上下半球) のロボット風中庸ボディ |

### 7.1 Z-up での「立たせる」オフセット

Tux / Capsule は **メッシュ自体が +Y up / +Z forward** で設計されている。
Z-up world (Robotics) では mesh local +Y を world +Z に、mesh local +Z を world +X に送る必要があり、
この 2 軸スワップは **120° about (1,1,1)/√3** = 四元数 `(0.5, 0.5, 0.5, 0.5)` で実現する。

```
zUpStandOffset = Quaternion(0.5, 0.5, 0.5, 0.5)
bodyShapeOffset() = if (currentBody in {Tux, Capsule} && upAxis == Z) zUpStandOffset else IDENTITY
```

**重要**：このオフセットは **ボディメッシュにのみ** 適用し、body 軸 (X/Y/Z 矢印) と body ラベルには
適用しない。理由:

- body 軸は「q が identity のとき world 軸と一致する」ことが教材上の不変量。
- メッシュの「向き」は見栄えのため。

実装上は `setBodyAttitude(q)` が `bodyAxes.entity` に `q` をそのまま、`bodyMesh.entity` には
`q * bodyShapeOffset()` を適用する。

## 8. Filament 描画 (`gl/`)

### 8.1 マテリアル

| 名前 | blending | doubleSided | shadingModel | 用途 |
|---|---|---|---|---|
| `vertex_color` (`vertex_color.mat`) | transparent | true | unlit | 半透明 cube・ゴースト・step axes・ラベル |
| `vertex_color_opaque` (`vertex_color_opaque.mat`) | opaque | true | unlit | 不透明 axes・Tux・Capsule |

vertex フォーマットはどちらも **位置 float3 + 色 float4 (RGBA)** インターリーブ (stride 28 bytes)。

### 8.2 `Mesh` (`gl/Meshes.kt`)

`Mesh(entity, vertexBuffer, indexBuffer)` の薄いラッパ。`destroy(engine)` で全リソース解放。

#### 8.2.1 メッシュビルダー一覧

| 関数 | 出力 | 用途 |
|---|---|---|
| `buildAxisArrows(...)` | 軸 3 本 (シャフト + コーン) を 1 メッシュに統合 | body / world / step1 / step2 軸 |
| `buildAxes(...)` | 線分の ±X/Y/Z 6 本 (LINES) | (現状未使用、デバッグ用) |
| `buildColoredCube(size, alpha)` | 6 面別色のキューブ。alpha<1 のとき `culling(false)` で両面描画、`priority=6` | body cube |
| `buildCubeWireframe(...)` | 12 本のエッジ (LINES) | ゴーストキューブ |
| `buildLetter(strokes, rgb, alpha)` | XY 平面の太線文字 (X/Y/Z) | 軸先端ラベル |
| `appendEllipsoid(...)` | UV 楕円体を共有バッファに追加。`colorFn(lx,ly,lz)` で局所座標→色 | Tux / Capsule の内部用 |
| `appendCone(...)` | 任意方向のコーン | Tux のくちばし |
| `finalizeMesh(...)` | 共有バッファを VB/IB/Renderable に確定 | 内部 |
| `buildTux(scale)` | 黒背中／白腹の楕円体ボディ + 頭 + オレンジくちばし + 足 2 個 | body shape Tux |
| `buildCapsule(radius, halfH, bodyColor, capColor)` | 円筒 + 上下半球 | body shape Capsule |

#### 8.2.2 文字メッシュ (`buildLetter`)

各文字 (X/Y/Z) は XY 平面上のストローク列として定義 (`Stroke(x1,y1,x2,y2)` のリスト)。
ストロークごとに 2 つの三角形 (太線の四角形) を生成。実体配置は `AttitudeScene.billboardTransform`
が「文字の +X = 画面右、+Y = 画面上、+Z = カメラ向き」となる行列を毎フレーム書き換える。

ラベルは `priority = 7` (もっとも後ろ＝最後に描く) で、透明マテリアルなので深度を書かない →
ジオメトリを遮蔽せず、軸の先端に重ねて読める。

#### 8.2.3 描画順序 (`priority`)

| priority | 種類 |
|---|---|
| 4 | 不透明 (opaque) ジオメトリ — body axes, world axes, Tux, Capsule |
| 6 | 半透明ジオメトリ — translucent cube, step axes, ghost wireframe |
| 7 | ラベル / wireframe outline (最後に描画) |

### 8.3 `AttitudeScene` (`gl/AttitudeScene.kt`)

Filament の `Engine` / `Scene` / `View` / `Camera` / `Renderer` / 全エンティティを所有。
`destroy()` で全リソース解放。

#### 8.3.1 構成エンティティ

| エンティティ | 種類 | 詳細 |
|---|---|---|
| worldAxes | 不透明 | 長さ 1.45、shaft 0.012、tip 0.040、tipLength 0.16、低彩度 |
| bodyCube | 半透明 (α=0.30) | size=0.55 |
| bodyTux | 不透明 | scale=1.0 |
| bodyCapsule | 不透明 | デフォルトカラー |
| bodyAxes | 不透明 | 長さ 1.05、shaft 0.018、tip 0.060、tipLength 0.18、彩度高 |
| step1Axes / step2Axes | 半透明 (α=0.40) | 長さ 0.95、ステップ分解の途中フレーム |
| ghostCube | 半透明 wireframe (α=0.55) | Slerp ↔ Euler-LERP 比較用 |
| bodyLabels[3] | 半透明 文字 (α=0.95) | body 軸先端の X/Y/Z |
| worldLabels[3] | 半透明 文字 (α=0.80) | world 軸先端の X/Y/Z |

ボディ形状は **3 メッシュを常時保持**し、`scene.addEntity / removeEntity` で表示中の 1 つを差し替える
（生成・破棄を繰り返さない）。

#### 8.3.2 配色ポリシー

低彩度の「モノリシック」配色。背景はチャコール `(0.07, 0.08, 0.10)` の Skybox。

```
bodyAxisColors  X = (0.82, 0.42, 0.34) terracotta
                Y = (0.55, 0.72, 0.45) sage green
                Z = (0.42, 0.58, 0.82) dusty steel blue
worldAxisColors 各色を更にニュートラルに脱彩 (X=灰みベージュ・Y=モスグレー・Z=スレートグレー)
```

body 軸が視覚的に主役、world 軸はリファレンスとして控えめになるよう輝度差を持たせている。
post-processing は `view.isPostProcessingEnabled = false` で無効化（unlit + リテラルカラー）。

#### 8.3.3 ビルボード変換 (`billboardTransform`)

ラベル位置 `tip` を中心に、**z 軸をカメラ方向**に向ける 4×4 列優先行列を構築:

```
forward = normalize(cameraPos - tip)
up      = worldUp  (forward と平行なら直交ヘルパへフォールバック)
right   = normalize(up × forward)
newUp   = forward × right
```

returned matrix の column 0=right, 1=newUp, 2=forward, 3=tip。
カメラを動かすたび (`pushCamera`) 全 6 ラベル分を再計算する。

#### 8.3.4 カメラ操作 API

```
setConvention(c)            world 規約変更。default 視点に戻り pushCamera。Tux/Capsule のオフセットも再適用
orbitAzimuth(deltaRad)      world up 軸まわりに eye を回転 (Rodrigues)
orbitElevation(deltaRad)    eye×up の右軸まわりに回転、|cos(eye, up)|>0.985 で極クランプ
zoom(scale)                 |eye| を [1.5, 12.0] でクランプ
resetCamera()               default 視点に戻す
applyCurrentConvention()    視点 + worldUp を currentConvention から設定 (lookAt はしない)
pushCamera()                lookAt → updateWorldLabels → updateBodyLabels
```

`rotateAroundAxis(v, axis, angle)` は Rodrigues の回転公式 `v cosθ + (a×v) sinθ + a (a·v)(1−cosθ)`。

### 8.4 `FilamentSurfaceView` (`gl/FilamentSurfaceView.kt`)

`SurfaceView` を継承し、

- `UiHelper(DONT_CHECK)` で `Surface` イベントを `SwapChain` 生成／破棄に橋渡し
- `Choreographer` フックで毎フレーム `renderer.beginFrame → render → endFrame`
- `GestureDetector` (drag / double tap) と `ScaleGestureDetector` (pinch) を取り付け

#### タッチ仕様 (B1)

| ジェスチャ | 動作 |
|---|---|
| 1 本指ドラッグ右 (dx < 0) | カメラ azimuth を CW (上から見て時計回り) に dx/width × π → ボディが指に追従して画面右に回り込む |
| 1 本指ドラッグ上 (dy > 0) | カメラ elevation を上向きに dy/height × π |
| 2 本指ピンチアウト (scaleFactor>1) | `zoom(1.0/scaleFactor)` → 寄り |
| ダブルタップ | `resetCamera()` で規約デフォルト視点に戻す |

両ディテクタに常時イベントを流し、`pinchGesture.isInProgress` 中はドラッグを無視する。

公開 API:

```
setBodyAttitude(q)              本体姿勢
setStepAttitudes(q1, q2)        step 軸 (null なら非表示)
setGhostAttitude(q)             ゴースト (null なら非表示)
setWorldConvention(c)
setBodyShape(shape)
shutdown()                      Choreographer 解除 + Filament Engine 破棄
```

## 9. UI 層

### 9.1 Compose ツリー (`MainScreen.kt`)

```
Surface
└─ Column
   ├─ WorldConventionBar           Up Y/Z  Hand RH/LH  /  Preset Graphics/Robotics  /  Body Cube/Tux/Capsule (3 行)
   ├─ Box (height 300dp)            FilamentCanvas (= AndroidView<FilamentSurfaceView>)
   ├─ Row                           "Reset attitude (q = identity)" ボタン
   ├─ TabRow [Euler | Quaternion | Matrix | Playback]
   └─ Box (verticalScroll)
      └─ when (tab) →
         0 EulerPanel
         1 QuaternionPanel
         2 MatrixPanel
         3 PlaybackPanel
```

`FilamentCanvas` の `update` ブロックで現在の `state.canonical / steps / ghost / worldConvention / bodyShape` を
`FilamentSurfaceView` の各 setter に流し込む。`onRelease` で `shutdown()` を呼ぶ。

`LaunchedEffect(state.isPlaying)` がアニメーションポンプ。`withFrameNanos` でフレームごとに
`viewModel.advance(dt)` を呼ぶ。

### 9.2 `EulerPanel.kt`

- 上段: 順序プルダウン (Tait-Bryan 6 / Proper Euler 6) + Intrinsic/Extrinsic スイッチ
- ジンバルロック警告:
  - **isLocked** (`extraction.gimbalLock`) → 強い琥珀色 AssistChip + 強アンバーで a1/a3 をハイライト
  - **isNearLock** (`isNearGimbalLock(eps=3°)`) → 弱い琥珀色 AssistChip + 弱アンバー
  - a2 は **特異点の原因** だがそれ自体は usable なため強調しない (WarnLevel.None)
- 3 つの `AngleSlider`:
  - 表示: 「軸名₁ (a1)」「軸名₂ (a2)」「軸名₃ (a3)」、現在値を `%+7.2f°` で右寄せ表示
  - 範囲: 既定 [-180, 180]°、a2 のみ Tait-Bryan は [-90, 90]°、Proper Euler は [0, 180]°
- 末尾に**人間可読な合成説明**を表示
  ```
  ZYX (intrinsic) — apply rotations as: rotate a1 about Zₒ, then a2 about new Yₒ, then a3 about new Xₒ
  ```

#### WarnLevel と配色

| 状態 | 行背景 | スライダー Thumb | Active Track | Inactive Track |
|---|---|---|---|---|
| Lock | `#FFE2B5` | `#B35A00` | `#B35A00` | `#E8C7A0` |
| Near | `#FFF4D9` | `#CC8800` | `#CC8800` | `#EDD9A8` |
| None | Transparent | M3 default | default | default |

#### ジンバルロックメッセージ

```
Gimbal lock (a2 = ±90°): Z₁ and X₃ axes coincide, a3 pinned to 0°    (Tait-Bryan)
Gimbal lock (a2 = 0° or 180°): Z₁ and Z₃ axes coincide, a3 pinned to 0°  (Proper Euler)
Approaching gimbal lock — a1 and a3 are losing independence            (near case)
```

### 9.3 `QuaternionPanel.kt`

- 4 つの数値入力 (w, x, y, z)。ユーザ入力をパースして `setQuaternion` に渡す。
  内部で `normalized().canonical()` 化。
- 派生表示として **軸-角度形式** "axis = (...), angle = ...°" を等幅で表示。
  `w ≈ 1` のとき `sinHalf < 1e-9` を識別して `(1, 0, 0)` + 0.00° (identity) を表示。
- 入力がパース失敗中はテキストを保持し、上流値が `1e-4` 以上ずれたときだけリフレッシュ
  (途中編集を破壊しない)。

### 9.4 `MatrixPanel.kt`

- `quaternionToMatrix(canonical)` を 3×3 セル表示 (`%+.4f`)。
- 末尾に **det(R) = ...** を表示 (proper rotation か確認用、理想値 +1)。
- 編集機能は持たないビュー。直接行列を編集したい場合は将来拡張で `setMatrix(...)` を介して
  `orthonormalized → matrixToQuaternion → canonical` の経路で反映する想定（API は ViewModel に既に存在）。

### 9.5 `PlaybackPanel.kt`

- **start / end の取得** ボタン (現在の姿勢をキャプチャ)
- 現在の `start q / end q` を等幅で表示
- 補間モード切替: Slerp / Euler-LERP / Step (3-axis) FilterChip
- t スライダー (0..1) — ドラッグで姿勢シーク
- Play/Pause トグル + Reset (t=0)
- Switch:
  - **Show step axes** … 現在姿勢の `stepFrames(state)` を 2 ステップの薄い軸で重畳描画
  - **Comparison ghost** … 現在モードの「もう片方」をワイヤフレームキューブで重畳
    - Slerp 中: Euler-LERP のゴースト
    - Euler-LERP 中: Slerp のゴースト
    - Step 中: identity → end への Slerp ゴースト

### 9.6 `WorldConventionBar` (`MainScreen.kt` 内)

3 行の `Row` レイアウト。

```
Row 1:  Up [Y][Z]   Hand [RH][LH]
Row 2:  Preset  [Graphics (Y-up, RH)]  [Robotics (Z-up, RH)]
Row 3:  Body  [Cube]  [Tux]  [Capsule]
```

すべて `FilterChip` または `OutlinedButton` で構成。

## 10. ViewModel (`ui/AttitudeViewModel.kt`)

```
data class UiState(
  canonical: Quaternion = IDENTITY,
  convention: EulerConvention = ZYX intrinsic,
  worldConvention: WorldConvention = GraphicsDefault,
  bodyShape: BodyShape = Cube,
  start: Quaternion = IDENTITY,
  end: Quaternion = IDENTITY,
  playbackMode: PlaybackMode = Slerp,
  playbackT: Double = 0.0,
  isPlaying: Boolean = false,
  playbackSpeed: Double = 0.4,    // t/sec
  showSteps: Boolean = false,
  showComparison: Boolean = false,
)

enum PlaybackMode { Slerp, EulerLerp, Step }
```

API:

| 用途 | メソッド |
|---|---|
| 姿勢編集 | `setEuler(angles)`, `setQuaternion(q)`, `setMatrix(m)`, `resetToIdentity()` |
| 規則編集 | `setConvention(c)`, `setWorldConvention(c)`, `setUpAxis(a)`, `setHandedness(h)`, `applyGraphicsPreset()`, `applyRoboticsPreset()`, `setBodyShape(s)` |
| Playback | `captureStart`, `captureEnd`, `setPlaybackMode`, `setPlaybackT`, `togglePlaying`, `resetPlayback`, `advance(dt)` |
| 表示切替 | `toggleSteps`, `toggleComparison` |
| 派生 | `stepFrames(s): Pair<Q,Q>?`, `ghostQuaternion(s): Q?`, `isCanonicalIdentity()` |

`canonical` の更新は常に **Hamilton 規約 + canonical (`w >= 0`) + 正規化** を保つ
（双重被覆問題を表示・比較で意識せずに済む）。

`setMatrix` は **入力行列をまず Gram-Schmidt で正規直交化**してから四元数化（手入力で多少崩れていても
SO(3) に射影してから保持）。

`advance(dt)`:
```
newT = clamp(playbackT + dt * playbackSpeed, 0, 1)
playing = newT < 1.0      // 端点で停止
canonical = trajectory(s, newT)
```

`trajectory(s, t)`:

```
Slerp     : Quaternion.slerp(start, end, t)
EulerLerp : eulerLerp(quaternionToEuler(start, c), quaternionToEuler(end, c), c, t)
Step      : stepInterpolated(quaternionToEuler(end, c), c, t)
                ↑ start は識別性のため identity 固定
```

`ghostQuaternion(s)`: `showComparison && state` に応じて、現在モードの **対照側軌道** を
返す（同 t における Slerp ↔ Euler-LERP の差を視覚化）。

`stepFrames(s)`: `showSteps` のとき `stepOrientations(currentAngles, c)` の `[1]` と `[2]` を返す
（`[0]` = identity は world 軸が既に表示されているので省略）。

## 11. テスト (`app/src/test/java/...`)

JUnit 4。テストファイル `ConversionsTest.kt` が **23 テストケース** を保持し、現在 0 fail / 0 error。

| カテゴリ | テスト |
|---|---|
| 単純な単位回転 | `rotX90QuaternionAndMatrixAgree`, `rotY90...`, `rotZ90...`, `rotZ90SendsXAxisToY`, `axisAngleQuaternionRotatesAxisToAxis`, `cosSinSanity`, `composedXyzRotates100` |
| 行列の妥当性 | `identityQuaternionToMatrixIsIdentity`, `rotationMatrixIsActuallyOrthogonal`, `quaternionMultiplicationOrderMatchesMatrixMultiplication` |
| Q ↔ Matrix 往復 | `matrixToQuaternionRoundTripStableAcrossAllOctants` (Shepperd の各分岐) |
| Euler 変換 | `eulerToQuaternionMatchesEulerToMatrixForAllConventions`, `eulerRoundTripPreservesRotationForAllConventions` (24 規則すべて), `zyxIntrinsic90AboutZIsRotZ90`, `extrinsicReversesIntrinsicForReversedAngles` |
| ジンバルロック | `taitBryanReportsGimbalLockAt90DegreeMiddleAngle`, `properEulerReportsGimbalLockAtZeroOrPiMiddleAngle`, `gimbalLockExtractionRoundTripsToSameRotation`, `isNearGimbalLockMatchesExactCases` |
| 補間 | `slerpEndpoints`, `eulerLerpEndpoints` |
| ステップ分解 | `stepOrientationsRecomposeToFullRotationForAllConventions`, `stepInterpolatedHitsBoundariesExactly` (`coerceAtMost(2.999999)` の影響で許容差 1e-5) |

ヘルパ `sameRotation(p, q)`: `canonical()` 化のうえ各成分差が `eps` 以内かを比較
（±同一視のうえで等しい）。

## 12. ライフサイクルとリソース管理

- `MainActivity.companion init` で `Filament Utils.init()` を 1 回呼ぶ（ネイティブライブラリのロード）。
- `FilamentSurfaceView.onAttachedToWindow` で `choreographer.postFrameCallback` 開始、`onDetachedFromWindow` で停止。
- `FilamentCanvas` の `onRelease` (Compose の `AndroidView`) で `view.shutdown()` → `AttitudeScene.destroy()` を確実に呼ぶ。
- `AttitudeScene.destroy()` は **全 Mesh** (worldAxes, bodyCube, bodyTux, bodyCapsule, bodyAxes, step1Axes, step2Axes, ghostCube, body/worldLabels[3]×2) と
  全 MaterialInstance / Material / Camera / Scene / Renderer / View / Engine を順次破棄。

## 13. 実装で踏んだ落とし穴と対処（ノウハウ）

1. **半透明面の深度書き込み** … ラベルやゴーストが「キューブを貫通して見える」ためには transparent material が必要。
   ただし不透明 cube も `priority` を上手く積まないと描画順がおかしくなる。`priority = 4 (opaque) → 6 (transparent) → 7 (label)` に固定。

2. **`buildColoredCube` の culling** … cube が α<1 のとき `culling(false)` にしないと、内側から見ると裏面が消えて
   原点が透けない。実装は `.culling(alpha >= 1f)`。

3. **ラベルの極ロック** … world up と forward が平行に近い視点では `right = up × forward` が 0 になり、
   ラベルが回転して読めなくなる。`abs(forward·worldUp) > 0.999` を検出して水平 up にフォールバックする。

4. **Z-up の Tux 立たせ** … メッシュは「+Y up / +Z forward」で設計。Z-up world では `(0.5, 0.5, 0.5, 0.5)` を
   ボディメッシュにのみ右掛けして「mesh +Y → world +Z, mesh +Z → world +X」を実現。
   軸／ラベルにはオフセットを掛けないことで `q = identity → 軸が world と一致` が保たれる。

5. **`stepInterpolated` の端点** … `t = 1` ぴったりで segment 3 が現れないよう、`coerceAtMost(2.999999)` で
   実用上の上限を作っている。テストでは tolerance を 1e-5 に緩めて吸収。

6. **`canonical()` の徹底** … 表示・比較・ストレージのすべてで `w >= 0` に揃えることで `q` と `-q` のスイッチに
   起因する「ジャンプ表示」を予防。

7. **`compileMaterials` の構成キャッシュ** … `project.exec` を `doLast` ブロックで使うため Gradle の構成キャッシュと
   相性が悪い。CI では `--no-configuration-cache` を併用すると安全。

## 14. 付録: 主要な既知の数式

### 14.1 Hamilton 積

```
(qa * qb).w = wa wb − xa xb − ya yb − za zb
(qa * qb).x = wa xb + xa wb + ya zb − za yb
(qa * qb).y = wa yb − xa zb + ya wb + za xb
(qa * qb).z = wa zb + xa yb − ya xb + za wb
```

### 14.2 Slerp

```
cosθ = a · b   (cosθ < 0 なら b ← −b で短弧化)
slerp(a, b, t) =
  cosθ > 0.9995 のとき: normalize(a + t (b − a))
  それ以外: (sin((1−t)θ) / sinθ) a + (sin(t θ) / sinθ) b
```

### 14.3 Rodrigues の回転公式

```
v' = v cosθ + (a × v) sinθ + a (a · v)(1 − cosθ)
```

### 14.4 四元数 → 列優先 4×4 (Filament 用)

```
out[0]  = 1 − 2(yy + zz)
out[1]  =     2(xy + wz)
out[2]  =     2(xz − wy)
out[4]  =     2(xy − wz)
out[5]  = 1 − 2(xx + zz)
out[6]  =     2(yz + wx)
out[8]  =     2(xz + wy)
out[9]  =     2(yz − wx)
out[10] = 1 − 2(xx + yy)
```

(残りの列は `(0,0,0,1)` と translation)

## 15. 今後の拡張余地

- glTF アセットによる Tux 等の差し替え（`gltfio` 経由）。`BodyShape` を `sealed class` にしてアセットパスを保持
- 2D ベクトル軌道プロット (姿勢角の時系列) パネル
- IMU (Accel + Gyro) センサーフュージョンのリアルタイム表示
- 角速度ベクトルの可視化、四元数微分 dq/dt の表示
- Quaternion のテキスト入力ではなくドラッグ操作 (axis-angle gizmo)
- ヘルプモード (各画面に解説オーバーレイ)
- 多言語対応 (現在は UI 一部英語、ドキュメント日本語混在)
