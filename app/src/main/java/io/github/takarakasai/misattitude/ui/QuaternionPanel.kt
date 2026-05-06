package io.github.takarakasai.misattitude.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.takarakasai.misattitude.domain.Quaternion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Quaternion attitude-input panel: 4 sliders (w / x / y / z, each ranged -1..+1)
 * driving the body attitude in **real time** — every drag step pushes the current
 * (raw, un-normalized) draft through `onQuaternionChange`, the ViewModel normalises
 * + canonicalises before storing.
 *
 * Why a local draft instead of binding sliders directly to `canonical`:
 *   The displayed quaternion in the ViewModel is always unit-norm. If we bound the
 *   w slider directly to `canonical.w`, dragging w from 1.0 → 0.5 with x=y=z=0
 *   would normalise back to (1, 0, 0, 0) on every step and the slider thumb would
 *   "snap back" to where the user just dragged from. Keeping a raw draft preserves
 *   the values the user actually set; the **conditional** resync below only fires
 *   when canonical changes from outside (Reset attitude, Euler-tab edits, playback)
 *   and not when our own commit comes back round-tripped to the same unit q.
 *
 * Below the sliders, an axis-angle preview shows what the *normalized* draft
 * represents — useful as a sanity-check (e.g. ±1 in w with everything else 0 means
 * identity).
 */
@Composable
fun QuaternionPanel(
    canonical: Quaternion,
    onQuaternionChange: (Quaternion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftW by remember { mutableStateOf(canonical.w.toFloat()) }
    var draftX by remember { mutableStateOf(canonical.x.toFloat()) }
    var draftY by remember { mutableStateOf(canonical.y.toFloat()) }
    var draftZ by remember { mutableStateOf(canonical.z.toFloat()) }

    // Conditional resync: only overwrite the draft when canonical has moved AWAY
    // from what our draft already represents. We compare draft.normalized().canonical()
    // against canonical (already canonical, unit-norm). If they match within epsilon,
    // the canonical change is a round-trip from our own commit and we keep the
    // user's raw draft values. If they differ, an external actor (Reset attitude,
    // Euler tab, playback) changed the attitude and we sync to it.
    LaunchedEffect(canonical) {
        val n = sqrt(
            (draftW * draftW + draftX * draftX + draftY * draftY + draftZ * draftZ).toDouble()
        )
        val matchesExisting = if (n < 1e-9) {
            false
        } else {
            val nq = Quaternion(
                draftW.toDouble() / n,
                draftX.toDouble() / n,
                draftY.toDouble() / n,
                draftZ.toDouble() / n,
            ).canonical()
            val cq = canonical.canonical()
            abs(nq.w - cq.w) < 1e-4 &&
                abs(nq.x - cq.x) < 1e-4 &&
                abs(nq.y - cq.y) < 1e-4 &&
                abs(nq.z - cq.z) < 1e-4
        }
        if (!matchesExisting) {
            draftW = canonical.w.toFloat()
            draftX = canonical.x.toFloat()
            draftY = canonical.y.toFloat()
            draftZ = canonical.z.toFloat()
        }
    }

    /** Push the four current draft values to the ViewModel. Centralised so all four
     *  sliders (and any future input source) commit through the same code path. */
    fun commit() {
        onQuaternionChange(
            Quaternion(draftW.toDouble(), draftX.toDouble(), draftY.toDouble(), draftZ.toDouble())
        )
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Hamilton convention: q = w + x i + y j + z k. Sliders edit raw components in -1..+1; " +
                "the value is normalised to a unit quaternion before being applied.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ComponentSlider("w", draftW) { draftW = it; commit() }
        ComponentSlider("x", draftX) { draftX = it; commit() }
        ComponentSlider("y", draftY) { draftY = it; commit() }
        ComponentSlider("z", draftZ) { draftZ = it; commit() }

        // Live axis-angle preview computed from the *normalized* draft. If the
        // raw draft has near-zero norm (all sliders at 0) we say so explicitly —
        // a zero quaternion isn't a rotation; ViewModel.setQuaternion rejects it.
        val axisAngleText = run {
            val n2 = draftW * draftW + draftX * draftX + draftY * draftY + draftZ * draftZ
            if (n2 < 1e-9f) {
                "axis = ?, angle = ? — draft norm is zero (move a slider)"
            } else {
                val n = sqrt(n2.toDouble())
                val w = (draftW.toDouble() / n).coerceIn(-1.0, 1.0)
                val angleDeg = 2.0 * acos(w) * 180.0 / PI
                val sinHalf = sqrt(1.0 - w * w)
                if (sinHalf < 1e-9) {
                    "axis = (1, 0, 0), angle = 0.00°  (identity)"
                } else {
                    val ax = draftX.toDouble() / n / sinHalf
                    val ay = draftY.toDouble() / n / sinHalf
                    val az = draftZ.toDouble() / n / sinHalf
                    "axis = (%+.3f, %+.3f, %+.3f), angle = %.2f°".format(ax, ay, az, angleDeg)
                }
            }
        }
        Text(
            text = axisAngleText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/**
 * One row of the Quaternion panel: short text label + 7-char monospace numeric
 * readout + a [-1, +1] slider. Layout intentionally matches AngleSlider in
 * EulerPanel so the two panels feel like the same shape of control.
 */
@Composable
private fun ComponentSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "%+7.4f".format(value),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(80.dp),
        )
        Slider(
            value = value.coerceIn(-1f, 1f),
            onValueChange = onChange,
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f),
        )
    }
}
