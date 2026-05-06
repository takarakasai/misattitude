package io.github.takarakasai.misattitude.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.takarakasai.misattitude.BuildConfig
import io.github.takarakasai.misattitude.domain.BodyShape
import io.github.takarakasai.misattitude.domain.EulerConvention
import io.github.takarakasai.misattitude.domain.FrameKind
import io.github.takarakasai.misattitude.domain.Handedness
import io.github.takarakasai.misattitude.domain.UpAxis
import io.github.takarakasai.misattitude.domain.WorldConvention
import io.github.takarakasai.misattitude.gl.FilamentSurfaceView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AttitudeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    // Animation pump while playing.
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) return@LaunchedEffect
        var prev = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - prev) / 1e9
            prev = now
            viewModel.advance(dt)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Compact 1-line summary; tap to open the settings bottom sheet ───
            ConventionSummaryBar(
                worldConvention = state.worldConvention,
                bodyShape = state.bodyShape,
                eulerConvention = state.convention,
                onClick = { showSettings = true },
                modifier = Modifier.fillMaxWidth(),
            )

            // ─── 3D canvas (now 340dp tall thanks to the slimmer top bar) ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
            ) {
                FilamentCanvas(
                    body = state.canonical,
                    steps = viewModel.stepFrames(state),
                    ghost = viewModel.ghostQuaternion(state),
                    worldConvention = state.worldConvention,
                    bodyShape = state.bodyShape,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ─── Reset attitude button ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { viewModel.resetToIdentity() }, modifier = Modifier.weight(1f)) {
                    Text("Reset attitude (q = identity)")
                }
            }

            // ─── Tabbed control panels ───
            var tab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Euler", "Quaternion", "Matrix", "Playback")
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (tab) {
                    0 -> EulerPanel(
                        canonical = state.canonical,
                        convention = state.convention,
                        onAnglesChange = viewModel::setEuler,
                    )
                    1 -> QuaternionPanel(
                        canonical = state.canonical,
                        onQuaternionChange = viewModel::setQuaternion,
                    )
                    2 -> MatrixPanel(canonical = state.canonical)
                    3 -> PlaybackPanel(
                        state = state,
                        onCaptureStart = viewModel::captureStart,
                        onCaptureEnd = viewModel::captureEnd,
                        onModeChange = viewModel::setPlaybackMode,
                        onTChange = viewModel::setPlaybackT,
                        onTogglePlay = viewModel::togglePlaying,
                        onReset = viewModel::resetPlayback,
                        onToggleSteps = viewModel::toggleSteps,
                        onToggleComparison = viewModel::toggleComparison,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ─── Modal bottom sheet for all setup-time settings ───
        if (showSettings) {
            SettingsBottomSheet(
                state = state,
                onUpAxisChange = viewModel::setUpAxis,
                onHandednessChange = viewModel::setHandedness,
                onGraphicsPreset = viewModel::applyGraphicsPreset,
                onRoboticsPreset = viewModel::applyRoboticsPreset,
                onBodyShapeChange = viewModel::setBodyShape,
                onConventionChange = viewModel::setConvention,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun FilamentCanvas(
    body: io.github.takarakasai.misattitude.domain.Quaternion,
    steps: Pair<io.github.takarakasai.misattitude.domain.Quaternion, io.github.takarakasai.misattitude.domain.Quaternion>?,
    ghost: io.github.takarakasai.misattitude.domain.Quaternion?,
    worldConvention: WorldConvention,
    bodyShape: BodyShape,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { FilamentSurfaceView(context) },
        update = { view ->
            view.setWorldConvention(worldConvention)
            view.setBodyShape(bodyShape)
            view.setBodyAttitude(body)
            view.setStepAttitudes(steps?.first, steps?.second)
            view.setGhostAttitude(ghost)
        },
        onRelease = { it.shutdown() },
    )
}

/**
 * Single-row summary at the top of the screen. Shows the current world convention,
 * body shape, and Euler convention in one glance, and lets the user tap to open the
 * full settings bottom sheet. Replaces the older 3-row toolbar to free up vertical
 * space for the 3D canvas and the Euler sliders below it.
 */
@Composable
private fun ConventionSummaryBar(
    worldConvention: WorldConvention,
    bodyShape: BodyShape,
    eulerConvention: EulerConvention,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val up = if (worldConvention.upAxis == UpAxis.Y) "Y-up" else "Z-up"
    val hand = if (worldConvention.handedness == Handedness.RightHanded) "RH" else "LH"
    val order = "${eulerConvention.axis1.name}${eulerConvention.axis2.name}${eulerConvention.axis3.name}"
    val frame = if (eulerConvention.frame == FrameKind.Intrinsic) "intr" else "extr"
    val body = bodyShape.name

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Open settings",
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "$up · $hand · $order $frame · $body",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Bottom sheet aggregating all setup-time settings:
 *   1. World convention (Up axis / Handedness / Preset shortcut)
 *   2. Body shape (Cube / Capsule / Teapot / Spot)
 *   3. Euler convention (12 orderings × intrinsic/extrinsic)
 *
 * Each section is its own labeled block so the user can scan / decide quickly. The
 * sheet stays open while making changes (e.g. trying different body shapes) — the
 * user dismisses it explicitly by swiping down or tapping outside.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsBottomSheet(
    state: AttitudeViewModel.UiState,
    onUpAxisChange: (UpAxis) -> Unit,
    onHandednessChange: (Handedness) -> Unit,
    onGraphicsPreset: () -> Unit,
    onRoboticsPreset: () -> Unit,
    onBodyShapeChange: (BodyShape) -> Unit,
    onConventionChange: (EulerConvention) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── World convention ──
            Text(
                "World convention",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingRow(label = "Up") {
                FilterChip(
                    selected = state.worldConvention.upAxis == UpAxis.Y,
                    onClick = { onUpAxisChange(UpAxis.Y) },
                    label = { Text("Y") },
                )
                FilterChip(
                    selected = state.worldConvention.upAxis == UpAxis.Z,
                    onClick = { onUpAxisChange(UpAxis.Z) },
                    label = { Text("Z") },
                )
            }
            SettingRow(label = "Hand") {
                FilterChip(
                    selected = state.worldConvention.handedness == Handedness.RightHanded,
                    onClick = { onHandednessChange(Handedness.RightHanded) },
                    label = { Text("RH") },
                )
                FilterChip(
                    selected = state.worldConvention.handedness == Handedness.LeftHanded,
                    onClick = { onHandednessChange(Handedness.LeftHanded) },
                    label = { Text("LH") },
                )
            }
            SettingRow(label = "Preset") {
                OutlinedButton(
                    onClick = onGraphicsPreset,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp,
                    ),
                ) { Text("Graphics (Y-up, RH)", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    onClick = onRoboticsPreset,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp,
                    ),
                ) { Text("Robotics (Z-up, RH)", style = MaterialTheme.typography.labelSmall) }
            }

            HorizontalDivider()

            // ── Body shape ──
            Text(
                "Body shape",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingRow(label = "Body") {
                FilterChip(
                    selected = state.bodyShape == BodyShape.Cube,
                    onClick = { onBodyShapeChange(BodyShape.Cube) },
                    label = { Text("Cube") },
                )
                FilterChip(
                    selected = state.bodyShape == BodyShape.Capsule,
                    onClick = { onBodyShapeChange(BodyShape.Capsule) },
                    label = { Text("Capsule") },
                )
                FilterChip(
                    selected = state.bodyShape == BodyShape.Teapot,
                    onClick = { onBodyShapeChange(BodyShape.Teapot) },
                    label = { Text("Teapot") },
                )
                FilterChip(
                    selected = state.bodyShape == BodyShape.Spot,
                    onClick = { onBodyShapeChange(BodyShape.Spot) },
                    label = { Text("Spot") },
                )
            }

            HorizontalDivider()

            // ── Euler convention ──
            Text(
                "Euler convention",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConventionPicker(
                    convention = state.convention,
                    onChange = onConventionChange,
                    modifier = Modifier.weight(1f),
                )
                FrameSwitch(
                    convention = state.convention,
                    onChange = onConventionChange,
                )
            }

            HorizontalDivider()

            // ── About / アプリ情報 ──
            // 開発者情報・バージョン・OSS クレジットの表示。
            // BuildConfig.VERSION_NAME / VERSION_CODE は app/build.gradle.kts の
            // defaultConfig.versionName / versionCode に対応する。
            // 増えてきたら専用ダイアログ (ⓘ アイコンで開く) に分離するのが綺麗。
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Misattitude  v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "© takarakasai",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Includes:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "• Filament — Google (Apache 2.0)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "• Spot model — Keenan Crane (CC0 / Public Domain)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Common row layout used inside the bottom sheet: a fixed-width left label and a
 *  flexible right-hand area for chips/buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(
    label: String,
    content: @Composable FlowRowScope.() -> Unit,
) {
    // FlowRow lets a row that grew past one line (e.g. five Body chips) wrap
    // gracefully to a second line. With 1-2 children it visually identical to
    // a regular Row, so existing Up/Hand/Preset rows keep their look.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(60.dp).padding(top = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}
