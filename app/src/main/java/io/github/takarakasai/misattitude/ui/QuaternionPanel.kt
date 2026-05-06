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
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Quaternion attitude-input panel: 4 sliders (w / x / y / z, each ranged -1..+1)
 * editing a local **draft**. The body attitude is updated only when the user taps
 * ✓ Apply (deferred-apply pattern shared with EulerPanel). Below the sliders, a
 * live axis-angle preview shows what the *normalized* draft represents — so the
 * user can dial values and see immediately how they'd be interpreted as a rotation,
 * without committing.
 *
 * Why sliders instead of text fields:
 *   - For a teaching tool, "drag and watch what changes" beats "type 0.7071…"
 *   - Sliders make the unit-norm constraint visible: pulling one component up
 *     forces the others to relatively shrink after normalization
 *   - The axis-angle readout doubles as a sanity check that the chosen quaternion
 *     is meaningful (e.g. ±1 in w with everything else 0 = identity)
 */
@Composable
fun QuaternionPanel(
    canonical: Quaternion,
    onQuaternionChange: (Quaternion) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local raw draft (NOT normalized); editing one slider does not auto-shrink
    // the others, so the user can see the un-normalized state. The displayed
    // axis-angle and the on-Apply commit both go through normalization.
    var draftW by remember { mutableStateOf(canonical.w.toFloat()) }
    var draftX by remember { mutableStateOf(canonical.x.toFloat()) }
    var draftY by remember { mutableStateOf(canonical.y.toFloat()) }
    var draftZ by remember { mutableStateOf(canonical.z.toFloat()) }
    LaunchedEffect(canonical) {
        // Resync from upstream whenever canonical changes externally (Reset
        // attitude / Euler edit applied / playback driving the canonical state).
        draftW = canonical.w.toFloat()
        draftX = canonical.x.toFloat()
        draftY = canonical.y.toFloat()
        draftZ = canonical.z.toFloat()
    }

    val draftQ = Quaternion(draftW.toDouble(), draftX.toDouble(), draftY.toDouble(), draftZ.toDouble())
    // Equality vs. canonical: compare normalized draft vs. canonical (already a
    // unit quaternion). We canonicalise both sides to avoid the double-cover
    // pitfall (q and -q represent the same rotation).
    val isDirty = run {
        val n = draftQ.norm()
        if (n < 1e-9) true else {
            val a = canonical.canonical()
            val b = draftQ.normalized().canonical()
            kotlin.math.abs(a.w - b.w) > 1e-4 ||
                kotlin.math.abs(a.x - b.x) > 1e-4 ||
                kotlin.math.abs(a.y - b.y) > 1e-4 ||
                kotlin.math.abs(a.z - b.z) > 1e-4
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Hamilton convention: q = w + x i + y j + z k. Sliders edit raw components in -1..+1; " +
                "the value is normalised to a unit quaternion on Apply.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ComponentSlider("w", draftW) { draftW = it }
        ComponentSlider("x", draftX) { draftX = it }
        ComponentSlider("y", draftY) { draftY = it }
        ComponentSlider("z", draftZ) { draftZ = it }

        // Live axis-angle preview computed from the *normalized* draft. If the
        // raw draft has near-zero norm (all sliders at 0) we say so explicitly —
        // a zero quaternion isn't a rotation and Apply will be rejected.
        val axisAngleText = run {
            val n = draftQ.norm()
            if (n < 1e-9) {
                "axis = ?, angle = ? — draft norm is zero (move a slider)"
            } else {
                val q = draftQ.normalized()
                val w = q.w.coerceIn(-1.0, 1.0)
                val angleDeg = 2.0 * acos(w) * 180.0 / PI
                val sinHalf = sqrt(1.0 - w * w)
                if (sinHalf < 1e-9) {
                    "axis = (1, 0, 0), angle = 0.00°  (identity)"
                } else {
                    val ax = q.x / sinHalf
                    val ay = q.y / sinHalf
                    val az = q.z / sinHalf
                    "axis = (%+.3f, %+.3f, %+.3f), angle = %.2f°".format(ax, ay, az, angleDeg)
                }
            }
        }
        Text(
            text = axisAngleText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )

        ApplyRevertRow(
            isDirty = isDirty,
            onRevert = {
                draftW = canonical.w.toFloat()
                draftX = canonical.x.toFloat()
                draftY = canonical.y.toFloat()
                draftZ = canonical.z.toFloat()
            },
            onApply = {
                // ViewModel.setQuaternion already rejects zero-norm; passing the
                // raw draft is fine and lets the ViewModel's normalisation /
                // canonicalisation paths do their job.
                onQuaternionChange(draftQ)
            },
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
