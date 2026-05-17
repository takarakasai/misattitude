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
import androidx.compose.foundation.layout.systemBarsPadding
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
import io.github.takarakasai.misattitude.ui.ads.AdBanner
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
    // Resolve the hosting Activity once — needed to start the Play Billing
    // purchase flow, which (unlike most Android APIs) requires an Activity
    // rather than any Context. Wrapped in remember so the cast is paid once
    // per composition tree, not on every recomposition.
    val screenContext = LocalContext.current
    val activity = remember(screenContext) { screenContext.findActivity() }

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

    // Android 15 (API 35) の edge-to-edge enforcement に対応。
    // ルートに systemBarsPadding() を当てて、ステータスバー / ナビゲーションバーと
    // UI が重ならないようにする。
    Surface(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
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
            //
            // Tabs marked with 🔒 indicate Pro-gated content: Quaternion is
            // partially gated (readable but not editable), Playback is fully
            // gated (replaced with an upgrade wall). Free users can still tap
            // these tabs — they navigate to the explanation/wall — which is
            // important so users discover what Pro unlocks rather than wondering
            // why a tab is missing.
            var tab by remember { mutableIntStateOf(0) }
            val tabs = remember(state.proActive) {
                if (state.proActive) {
                    listOf("Euler", "Quaternion", "Matrix", "Playback")
                } else {
                    listOf("Euler", "🔒 Quaternion", "Matrix", "🔒 Playback")
                }
            }
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                    )
                }
            }

            val onBuyPro = { activity?.let(viewModel::launchProPurchase); Unit }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                        editable = state.proActive,
                        onBuyPro = onBuyPro,
                    )
                    2 -> MatrixPanel(
                        canonical = state.canonical,
                        onMatrixChange = viewModel::setMatrix,
                    )
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
                        proActive = state.proActive,
                        onBuyPro = onBuyPro,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // AdMob banner — pinned to the bottom of the Column, above the
            // system nav bar. Hidden entirely when the user owns the Pro
            // upgrade: the Composable simply isn't emitted, AdBanner's
            // DisposableEffect tears down the AdView, and the row collapses
            // to zero height (the panel above takes the freed space via its
            // weight(1f) modifier).
            if (!state.proActive) {
                // "Tap to remove ads" call-to-action shown directly above the
                // banner. Visual link: the user sees the ad, and the row right
                // above it offers the way out. Tapping launches the purchase
                // flow without going through the Settings sheet, which is the
                // cheapest possible conversion path for users who already
                // decided they want the upgrade.
                RemoveAdsCta(
                    priceFormatted = viewModel.proPriceFormatted,
                    onBuyPro = { activity?.let(viewModel::launchProPurchase) },
                    modifier = Modifier.fillMaxWidth(),
                )
                AdBanner(modifier = Modifier.fillMaxWidth())
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
                proPriceFormatted = viewModel.proPriceFormatted,
                // launchProPurchase is only invoked from a button that exists
                // when activity != null, so the !! cannot actually trip — but
                // we guard with a no-op fallback to keep the type system happy.
                onBuyPro = { activity?.let(viewModel::launchProPurchase) },
                onRestorePurchases = viewModel::restorePurchases,
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
    proPriceFormatted: String,
    onBuyPro: () -> Unit,
    onRestorePurchases: () -> Unit,
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
            // Pro-only chips (Teapot, Spot) are visually distinguished with a
            // 🔒 prefix and reroute taps to the purchase flow instead of
            // changing the shape. Showing them rather than hiding them is a
            // deliberate conversion lever: free users see the cute Spot cow
            // exists and is one upgrade away.
            Text(
                "Body shape",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingRow(label = "Body") {
                BodyShapeChip(
                    shape = BodyShape.Cube,
                    selected = state.bodyShape == BodyShape.Cube,
                    proActive = state.proActive,
                    onSelect = onBodyShapeChange,
                    onLockedClick = onBuyPro,
                )
                BodyShapeChip(
                    shape = BodyShape.Capsule,
                    selected = state.bodyShape == BodyShape.Capsule,
                    proActive = state.proActive,
                    onSelect = onBodyShapeChange,
                    onLockedClick = onBuyPro,
                )
                BodyShapeChip(
                    shape = BodyShape.Teapot,
                    selected = state.bodyShape == BodyShape.Teapot,
                    proActive = state.proActive,
                    onSelect = onBodyShapeChange,
                    onLockedClick = onBuyPro,
                )
                BodyShapeChip(
                    shape = BodyShape.Spot,
                    selected = state.bodyShape == BodyShape.Spot,
                    proActive = state.proActive,
                    onSelect = onBodyShapeChange,
                    onLockedClick = onBuyPro,
                )
            }

            HorizontalDivider()

            // ── Euler convention ──
            // Free tier exposes 4 of the 12 conventions (ZYX intr/extr,
            // ZYZ intr, XYZ intr — see EulerConvention.FREE_CONVENTIONS).
            // Pro-only entries appear in the dropdown with a 🔒 prefix; tapping
            // them launches the purchase flow.
            Text(
                "Euler convention",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!state.proActive) {
                Text(
                    text = "Free includes 4 conventions (ZYX intr/extr, ZYZ intr, XYZ intr). " +
                        "Unlock all 12 with Pro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConventionPicker(
                    convention = state.convention,
                    onChange = onConventionChange,
                    modifier = Modifier.weight(1f),
                    proActive = state.proActive,
                    onLockedClick = onBuyPro,
                )
                FrameSwitch(
                    convention = state.convention,
                    onChange = { candidate ->
                        // Free users can only toggle between Intrinsic and
                        // Extrinsic if the new combination is in FREE_CONVENTIONS.
                        // Otherwise route to the upgrade flow.
                        if (candidate.isFree || state.proActive) {
                            onConventionChange(candidate)
                        } else {
                            onBuyPro()
                        }
                    },
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

            HorizontalDivider()

            // ── Pro upgrade (in-app purchase) ──
            // One-shot non-consumable that removes the AdMob banner. Two UI
            // states:
            //   * not owned → "Remove ads — <price>" primary button +
            //                  smaller "Restore purchases" outlined button
            //                  (for users who reinstall / switch device).
            //   * owned     → "Pro active ✓" text + still expose Restore so
            //                  support can guide users with mis-synced state.
            // Price text comes from Google Play (already-localised currency
            // string from ProductDetails) so we never hard-code "$5" in code.
            Text(
                "Pro upgrade",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.proActive) {
                Text(
                    "✓  Pro version active — ads disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(
                    onClick = onRestorePurchases,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restore purchases") }
            } else {
                Text(
                    "Remove the banner ad with a one-time purchase. Lifetime entitlement, " +
                        "applied automatically across all your devices signed in to the " +
                        "same Google account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = onBuyPro,
                        modifier = Modifier.weight(1f),
                    ) {
                        val priceTail = if (proPriceFormatted.isNotEmpty()) "  —  $proPriceFormatted" else ""
                        Text("Remove ads$priceTail")
                    }
                    OutlinedButton(
                        onClick = onRestorePurchases,
                    ) { Text("Restore") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Walk the [ContextWrapper] chain to find the hosting [Activity]. Compose can
 * be invoked under non-Activity Contexts (Previews, AndroidViewBinding, etc.),
 * so callers that need an Activity must check for null and degrade gracefully.
 */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> this.baseContext.findActivity()
    else -> null
}

/**
 * Pro-upgrade call-to-action sitting directly above the AdMob banner.
 * Purpose: give users who notice the banner a one-tap path to the purchase
 * flow without making them hunt through Settings.
 *
 * Why two lines / why a "PRO" badge:
 *
 *   The initial single-line version ("✨ Remove ads — ¥800") read more like a
 *   status row than an offer, and user feedback was that the banner alone
 *   wasn't motivating enough to drive conversions. This version is louder by
 *   design:
 *
 *     * A small "PRO" pill on the left visually anchors the row as a product
 *       offering, not just another setting.
 *     * Two lines of copy — headline (action) + subline (benefits) — leave
 *       room to sell "Support development" alongside "no more ads", which is
 *       a real motivator for educational-app users who tend to be sympathetic
 *       to indie developers.
 *     * The price ("¥800 · one-time") is its own column on the right so it
 *       reads as a discrete commitment, not buried mid-sentence.
 *
 *   We still use `primaryContainer` colour rather than a flashy accent: the
 *   row should read as inviting, not desperate.
 */
@Composable
private fun RemoveAdsCta(
    priceFormatted: String,
    onBuyPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onBuyPro,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // PRO badge — a small filled pill on `primary` (not container) so
            // it stands out against the row's `primaryContainer` background.
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "✨ PRO",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            // Headline + subline. weight(1f) lets the price column on the
            // right hug its content while this column absorbs slack.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Remove ads forever",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Support development · one-time purchase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }

            // Price (if known) shown on the right. When the BillingClient
            // hasn't yet loaded ProductDetails we just omit the column rather
            // than show a placeholder — better to wait until Play returns the
            // localised string than to flash "—" at the user.
            if (priceFormatted.isNotEmpty()) {
                Text(
                    text = priceFormatted,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A FilterChip for one [BodyShape] that respects Pro entitlement.
 *
 *   * Pro-only shapes (Teapot, Spot) on the free tier show a 🔒 prefix and
 *     route taps to [onLockedClick] (Pro purchase flow) instead of selecting
 *     the shape. The chip's `selected` state can never be true for a locked
 *     shape because the ViewModel refuses to switch to one (defensive guard
 *     in `setBodyShape`), so the visual stays in a consistent "available but
 *     locked" state.
 *   * Free shapes (Cube, Capsule) behave exactly like the previous
 *     unconditional FilterChips.
 */
@Composable
private fun BodyShapeChip(
    shape: BodyShape,
    selected: Boolean,
    proActive: Boolean,
    onSelect: (BodyShape) -> Unit,
    onLockedClick: () -> Unit,
) {
    val locked = !proActive && !shape.isFree
    FilterChip(
        selected = selected,
        onClick = { if (locked) onLockedClick() else onSelect(shape) },
        label = {
            Text(if (locked) "🔒 ${shape.name}" else shape.name)
        },
    )
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
