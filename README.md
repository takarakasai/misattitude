# Misattitude

An Android educational app for inspecting how the same rotation is described as
**Euler angles**, **quaternion**, and **rotation matrix**, with all 12 Euler
orderings (Tait-Bryan + proper Euler) selectable in either intrinsic (body-frame)
or extrinsic (world-frame) form.

3D rendering via [Google Filament](https://google.github.io/filament/), UI in
Jetpack Compose.

## Features

- **3 representations, kept in sync.** Internal canonical state is a unit
  quaternion; Euler / matrix are derived views. Editing any panel updates the
  cube's attitude immediately.
- **All 12 Euler orderings.** Tait-Bryan: `XYZ XZY YXZ YZX ZXY ZYX`.
  Proper Euler: `XYX XZX YXY YZY ZXZ ZYZ`. Toggle between **intrinsic** and
  **extrinsic** with a switch — the math under each option is consistent and
  documented in `domain/Conversions.kt`.
- **Gimbal-lock indicator.** When the middle Euler angle approaches the
  singularity for the chosen ordering, the panel shows a warning and pins the
  third angle to 0 (the convention for handling the lost degree of freedom).
- **Step-by-step decomposition.** Toggle "Show step axes" to render faded body
  axes after the first and second elementary rotations of the current Euler
  decomposition.
- **Slerp vs Euler-LERP comparison.** Capture a start and end attitude, then
  play back the interpolation. Three modes:
  - **Slerp** — geodesic on SO(3).
  - **Euler-LERP** — naive component-wise linear interpolation of Euler angles
    (intentionally bad — useful for demonstrating *why* Slerp matters).
  - **Step (3-axis)** — replay the end attitude's three elementary rotations
    in order, illustrating how the chosen Euler ordering builds the rotation.
  Toggle "Comparison ghost" to overlay the alternative trajectory as a
  wireframe cube.

## Project layout

```
app/src/main/
├── java/io/github/takarakasai/misattitude/
│   ├── MainActivity.kt
│   ├── domain/         # pure-Kotlin math (no Android deps)
│   │   ├── Vec3.kt
│   │   ├── Quaternion.kt
│   │   ├── RotationMatrix.kt
│   │   ├── EulerConvention.kt
│   │   ├── EulerAngles.kt
│   │   └── Conversions.kt
│   ├── gl/             # Filament rendering
│   │   ├── Meshes.kt
│   │   ├── AttitudeScene.kt
│   │   └── FilamentSurfaceView.kt
│   └── ui/             # Compose UI + ViewModel
│       ├── AttitudeViewModel.kt
│       ├── MainScreen.kt
│       ├── EulerPanel.kt
│       ├── QuaternionPanel.kt
│       ├── MatrixPanel.kt
│       ├── PlaybackPanel.kt
│       └── theme/Theme.kt
├── materials/
│   └── vertex_color.mat   # Filament material source (compiled by matc)
└── res/...
```

## Building

### One-time setup: Filament `matc`

Filament materials must be compiled to `.filamat` binaries with the `matc`
tool, which ships in the [Filament release tarball](https://github.com/google/filament/releases).

1. Download the Filament Android release matching the version in
   `gradle/libs.versions.toml` (currently 1.54.5) for **your build host OS**
   (Linux / macOS / Windows). The host-binaries archive is named e.g.
   `filament-v1.54.5-linux.tgz`.
2. Extract it. The binary you need is `filament/bin/matc`.
3. Tell Gradle where to find it, by either:
   - exporting `MATC_PATH=/abs/path/to/filament/bin/matc`, **or**
   - adding `matcPath=/abs/path/to/filament/bin/matc` to
     `~/.gradle/gradle.properties`.

The `compileMaterials` Gradle task runs as part of `preBuild` and writes
`app/src/main/assets/vertex_color.filamat`.

### Build & run

```sh
./gradlew :app:installDebug
```

Min SDK 26, target SDK 34. Tested on the Android emulator (API 34).

## Math conventions

- **Hamilton quaternion** `q = w + xi + yj + zk`. Multiplication composes
  rotations such that `(qa * qb)` applies `qb` first, then `qa` — matching
  the matrix product `R(qa) * R(qb)`.
- **Rotation matrix** acts on column vectors: `v_world = R · v_body`.
- **Intrinsic XYZ** with angles `(a1, a2, a3)`:
  `R = R_X(a1) · R_Y(a2) · R_Z(a3)` — each rotation is about the **current
  body axis**.
- **Extrinsic XYZ** with angles `(a1, a2, a3)`:
  `R = R_Z(a3) · R_Y(a2) · R_X(a1)` — each rotation is about a **fixed world
  axis**. Equivalent to intrinsic ZYX with the angle order reversed; this
  identity drives `Conversions.matrixToEuler`.

## Things to try

- Pick **ZYX intrinsic** (a common aerospace yaw–pitch–roll choice) and drag
  the middle slider to ±90°: watch the gimbal-lock warning appear and the
  third axis collapse onto the first.
- Capture a 90° X-rotation as the start, a 90° Y-rotation as the end, then
  play back **Slerp vs Euler-LERP** with the comparison ghost on. The two
  trajectories diverge clearly — Euler-LERP is not a geodesic on SO(3).
- Switch ordering between **XYZ intrinsic** and **ZYX extrinsic** while a
  non-trivial attitude is held: the Euler values change but the cube does
  not move, illustrating that the same SO(3) element has different
  three-angle representations.
