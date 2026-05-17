# Misattitude — ストア掲載情報 (Store Listing)

Play Console の「メインのストア掲載情報」に貼り付けるテキスト案。
日本語版と英語版を併記しているので、デフォルト言語に応じて使い分ける。
必要に応じて手直しして使う。

---

## 短い説明 / Short description (80 文字以内)

### 日本語
> 3D 回転姿勢を直感的に学べる教育アプリ。Euler 角・四元数・行列を同時表示。

(36 文字 — 余裕あり)

### English
> Educational 3D app to learn rotation attitudes — Euler angles, quaternions, matrix side by side.

(94 文字 — もう少し縮める必要あり、案として：)
> Learn 3D rotations interactively: Euler angles, quaternions and matrix in one view.

(80 文字 — OK)

---

## 詳細な説明 / Full description (4000 文字以内)

### 日本語

```
3D 回転姿勢を直感的に学べる教育用アプリです。

Euler 角・四元数 (Quaternion)・回転行列 — ロボティクスや 3D グラフィックス
で頻出する 3 つの表現を、同じ姿勢に対して「同時に」表示し、ひとつをいじると
他の表現も連動して更新されます。「Z₁Y₂X₃ の Euler 角を 30°, -45°, 60° にしたら
四元数はどうなる？」「ジンバルロックって何が起きてるの？」を、教科書ではなく
画面上の 3D オブジェクトで体感できます。

— 主な機能 —

【3 つの姿勢表現の同期表示】
・Euler 角 (12 通りの回転順序すべて対応 / Intrinsic / Extrinsic 切替可)
・Quaternion (Hamilton 規約、軸角度プレビュー付き)
・回転行列 (3×3、要素を直接編集可能、リアルタイム直交化)

【座標系プリセット】
・Graphics convention (Y-up, RH) — OpenGL / Filament の慣習
・Robotics convention (Z-up, RH) — ROS / 多くのロボット制御の慣習
・Y-up ↔ Z-up、右手系 ↔ 左手系をボタン 1 つで切替

【ボディ形状】
・透過立方体 (原点・軸の確認用)
・カプセル (汎用ロボットボディ)
・Utah Teapot (グラフィックス史上の名物。注ぎ口で前方が一目瞭然)
・Spot (Keenan Crane 氏の CG 教材用 CC0 モデル、子牛)

【補間アニメーション】
・Slerp (球面線形補間 — 四元数の自然な軌道)
・Euler-LERP (Euler 角の線形補間 — Slerp との違いが目に見える)
・3-axis Step (回転を 3 軸ステップに分解、教育用)
・2 つの軌道を「メイン + 半透明ゴースト」として同時表示し、Slerp と
  Euler-LERP の軌道差を視覚的に対比できる

【ジンバルロック警告】
・特異点に近づくと自動で警告を表示
・a1 と a3 の独立性が失われる様子をスライダーの動きで可視化

— こんな人に —

・ロボット工学・SLAM・ドローン制御を学んでいる学生
・3D グラフィックスを勉強し始めて回転の表現に迷っている人
・「四元数って何で w, x, y, z の 4 つなの？」を直感的に理解したい人
・教育者として授業で姿勢の概念を実演したい人

— プライバシー —

本アプリは完全オフラインで動作し、いかなる利用者データも収集・送信しません。
インターネット接続権限すら要求しません。

— 開発元 / クレジット —

開発: takarakasai
3D エンジン: Google Filament (Apache 2.0)
Spot モデル: Keenan Crane (CC0 / Public Domain)
```

### English

```
An educational app for understanding 3D rotation attitudes — intuitively.

Euler angles, quaternions, and rotation matrices: three of the most-used
representations in robotics and 3D graphics, all displayed simultaneously for
the same attitude. Tweak one and the others update live. Questions like
"what's the quaternion for an XYZ Euler angle of (30°, -45°, 60°)?" or
"what actually happens during gimbal lock?" become things you can see on
screen — not just read about in textbooks.

— Features —

[Three synchronized representations]
- Euler angles (all 12 rotation orderings; intrinsic / extrinsic toggle)
- Quaternion (Hamilton convention, axis-angle preview)
- Rotation matrix (3x3, with directly-editable cells and live re-orthonormalisation)

[Coordinate-frame presets]
- Graphics convention (Y-up, right-handed) — the OpenGL / Filament default
- Robotics convention (Z-up, right-handed) — the ROS default
- One-tap switch between Y-up / Z-up and right-handed / left-handed

[Body shapes]
- Translucent cube (great for seeing the origin and the axes through it)
- Capsule (a neutral generic robot body)
- The Utah Teapot (graphics-history classic — spout makes "forward" obvious)
- Spot (Keenan Crane's CC0 educational cow model)

[Interpolation animation]
- Slerp (spherical linear interpolation — the natural geodesic on SO(3))
- Euler-LERP (linear interpolation in Euler-angle space — visibly different)
- 3-axis Step decomposition (educational)
- Show both trajectories side-by-side as "active body + translucent ghost"
  so the divergence between Slerp and Euler-LERP becomes visible

[Gimbal-lock warning]
- Automatic warning when you approach a singularity
- See a1 / a3 lose independence as you drag the sliders

— Who is this for? —

- Students of robotics, SLAM, or drone control
- Anyone starting with 3D graphics who is puzzled by rotations
- People who want to intuit "why does a quaternion have w, x, y, z?"
- Educators who want a live demo of attitude concepts during a class

— Privacy —

This app runs entirely offline. It does not collect or transmit ANY user
data. It does not even declare the INTERNET permission.

— Credits —

Developed by: takarakasai
3D engine: Google Filament (Apache 2.0)
Spot model: Keenan Crane (CC0 / Public Domain)
```

---

## スクリーンショット (推奨セット)

Play Console には **2 〜 8 枚**を提出可。下記の中から目的に合わせて 4 〜 6 枚選ぶ。
すべて既存のディレクトリにある（過去のテスト時に撮影済み）。

### A. すぐ使えるもの（プロジェクトルートにある既存ファイル）

| ファイル | 内容 | 推奨枚目 | キャプション案 |
|---|---|---|---|
| `scr_main.png` | デフォルト起動画面（Spot + 軸 + Euler スライダー） | 1 (アイキャッチ) | 「3 つの表現を同時表示」 |
| `scr_rotated.png` | Euler スライダーで Spot が回転している状態 | 2 | 「スライダーで姿勢を直感操作」 |
| `scr_matrix_dragged2.png` | Matrix セル編集 + 回転反映 | 3 | 「行列要素を直接編集 → 自動直交化」 |
| `scr_ghost_add.png` | ゴースト比較 (Slerp vs Euler-LERP) | 4 | 「Slerp と Euler-LERP の軌道差を可視化」 |

### B. アップロード前に追加撮影推奨

| 内容 | 撮り方 | キャプション案 |
|---|---|---|
| Settings bottom sheet（ボディ・座標系選択） | 起動 → 上部「Y-up · RH · ZYX intr · Spot」帯をタップ | 「座標系・ボディ形状を切替」 |
| Robotics プリセット (Z-up) で Spot が立っている | Settings → Preset 「Robotics (Z-up, RH)」 | 「Graphics / Robotics 慣習をワンタップで」 |
| Quaternion タブのスライダー | Quaternion タブ表示 | 「4 成分を直接編集、軸角度プレビュー付き」 |
| ジンバルロック警告中の画面 | Euler タブで a2=90° に設定 | 「ジンバルロックの動きが見える」 |

### C. タブレット用（任意）
Play Console は 7" / 10" タブレット各 1 枚以上を「強く推奨」する。タブレット非対応で
出すなら不要だが、横向き対応を入れた将来に向けて準備しておくのは良い。

### 撮影上の注意
- 縦向き、3:4 〜 9:16 アスペクト比のもの推奨（既存スクショは多くがこれを満たす）
- 上部のステータスバーは Play Console 側でリサイズされるので気にせず可
- スクリーンショットの拡張子は PNG または JPG どちらでも可

---

## フィーチャー グラフィック (Feature graphic) — 1024×500 PNG/JPG

Play Console で必須。**まだ作っていないので別途準備が必要**。

### 推奨デザイン
- 左 1/3：アプリアイコン or Spot の 3D レンダリング
- 中央：「Misattitude」ロゴ + 「3D 回転姿勢を直感的に学ぶ」
- 右 1/3：3 つの表現（Euler、Quaternion、Matrix）のミニ図
- 背景：本アプリ UI と同じ落ち着いた lavender 系

### 作成ツール案
- Figma / Adobe Illustrator
- 手早く済ませるなら：既存スクショ scr_main.png をクロップして 1024×500 に
  リサイズし、上に「Misattitude」テキストをのせる

---

## アプリアイコン — 512×512 PNG

Play Console で必須。**既存のランチャーアイコンを 512×512 に書き出す必要あり**。

```
ソース: app/src/main/res/mipmap-xxxhdpi/ic_launcher.png (192×192)
→ 512×512 に拡大 (Bicubic 補間でも可)、または adaptive icon の原版から 512px 書き出し
```

または `misattude.png` 原画像（プロジェクト初期に使用）があるならそれを 512×512 にリサイズ。
