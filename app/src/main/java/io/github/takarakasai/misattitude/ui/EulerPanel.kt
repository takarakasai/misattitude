package io.github.takarakasai.misattitude.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.takarakasai.misattitude.domain.Axis
import io.github.takarakasai.misattitude.domain.Conversions
import io.github.takarakasai.misattitude.domain.EulerAngles
import io.github.takarakasai.misattitude.domain.EulerConvention
import io.github.takarakasai.misattitude.domain.FrameKind
import io.github.takarakasai.misattitude.domain.Quaternion
import kotlin.math.PI

/** Convert radians to degrees for display, keeping wrapping into (-180, 180]. */
private fun Double.toDeg(): Double = this * 180.0 / PI
private fun Double.toRad(): Double = this * PI / 180.0

private data class AxisOrder(val a1: Axis, val a2: Axis, val a3: Axis, val isProper: Boolean) {
    val label: String = "${a1.name}${a2.name}${a3.name}"
}

private val taitBryanOrders = listOf(
    AxisOrder(Axis.X, Axis.Y, Axis.Z, false),
    AxisOrder(Axis.X, Axis.Z, Axis.Y, false),
    AxisOrder(Axis.Y, Axis.X, Axis.Z, false),
    AxisOrder(Axis.Y, Axis.Z, Axis.X, false),
    AxisOrder(Axis.Z, Axis.X, Axis.Y, false),
    AxisOrder(Axis.Z, Axis.Y, Axis.X, false),
)
private val properEulerOrders = listOf(
    AxisOrder(Axis.X, Axis.Y, Axis.X, true),
    AxisOrder(Axis.X, Axis.Z, Axis.X, true),
    AxisOrder(Axis.Y, Axis.X, Axis.Y, true),
    AxisOrder(Axis.Y, Axis.Z, Axis.Y, true),
    AxisOrder(Axis.Z, Axis.X, Axis.Z, true),
    AxisOrder(Axis.Z, Axis.Y, Axis.Z, true),
)

/**
 * Pure attitude-input panel: 3 angle sliders + gimbal-lock warning + helper text.
 * The Euler convention itself (order + intrinsic/extrinsic) is now picked from the
 * shared SettingsBottomSheet on MainScreen, so this panel can stay focused on the
 * frequently-touched sliders.
 *
 * Edit model: **immediate apply** — every slider drag re-computes the quaternion
 * and pushes it through `onAnglesChange`, so the 3D scene tracks the slider in
 * real time. The slider value is bound directly to the angles extracted from the
 * current canonical, which means after-commit corrections (gimbal-lock pinning,
 * floating-point round-trip) appear automatically. No local draft state.
 */
@Composable
fun EulerPanel(
    canonical: Quaternion,
    convention: EulerConvention,
    onAnglesChange: (EulerAngles) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraction = Conversions.quaternionToEuler(canonical, convention)
    val angles = extraction.angles
    val isLocked = extraction.gimbalLock
    // "Near" gimbal lock: a2 within ~3° of the singularity. Warns the user before
    // they actually hit the wall, so they can see the dynamics ramping up.
    val isNearLock = !isLocked && Conversions.isNearGimbalLock(angles, convention, eps = degreesAsRad(3.0))

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLocked) {
            AssistChip(
                onClick = {},
                label = { Text(gimbalLockMessage(convention)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFFFFCC7A),
                    labelColor = Color(0xFF5C2900),
                ),
            )
        } else if (isNearLock) {
            AssistChip(
                onClick = {},
                label = { Text("Approaching gimbal lock — a1 and a3 are losing independence") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFFFFF4D9),
                    labelColor = Color(0xFF7A5A00),
                ),
            )
        }

        // a1 and a3 are the angles that fuse together at the singularity.
        // a2 is the *cause* of the lock and stays usable; only highlight a1/a3.
        val warnLevel = when {
            isLocked -> WarnLevel.Lock
            isNearLock -> WarnLevel.Near
            else -> WarnLevel.None
        }
        AngleSlider(
            label = "${convention.axis1.name}₁ (a1)",
            valueDeg = angles.a1.toDeg(),
            warning = warnLevel,
            onChange = { newDeg -> onAnglesChange(EulerAngles(newDeg.toRad(), angles.a2, angles.a3)) },
        )
        AngleSlider(
            label = "${convention.axis2.name}₂ (a2)",
            valueDeg = angles.a2.toDeg(),
            range = if (convention.isTaitBryan) -90f..90f else 0f..180f,
            warning = WarnLevel.None,
            onChange = { newDeg -> onAnglesChange(EulerAngles(angles.a1, newDeg.toRad(), angles.a3)) },
        )
        AngleSlider(
            label = "${convention.axis3.name}₃ (a3)",
            valueDeg = angles.a3.toDeg(),
            warning = warnLevel,
            onChange = { newDeg -> onAnglesChange(EulerAngles(angles.a1, angles.a2, newDeg.toRad())) },
        )

        Text(
            text = "${convention.label} — apply rotations as: " + composeOrderText(convention),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun degreesAsRad(d: Double): Double = d * PI / 180.0

private fun gimbalLockMessage(c: EulerConvention): String {
    val a1 = "${c.axis1.name}₁"
    val a3 = "${c.axis3.name}₃"
    val cause = if (c.isTaitBryan) "a2 = ±90°" else "a2 = 0° or 180°"
    return "Gimbal lock ($cause): $a1 and $a3 axes coincide, a3 pinned to 0°"
}

private enum class WarnLevel { None, Near, Lock }

private fun composeOrderText(c: EulerConvention): String {
    val a = listOf(c.axis1.name, c.axis2.name, c.axis3.name)
    return if (c.frame == FrameKind.Intrinsic) {
        "rotate a1 about ${a[0]}ₒ, then a2 about new ${a[1]}ₒ, then a3 about new ${a[2]}ₒ"
    } else {
        "rotate a1 about world ${a[0]}, then a2 about world ${a[1]}, then a3 about world ${a[2]}"
    }
}

@Composable
private fun AngleSlider(
    label: String,
    valueDeg: Double,
    range: ClosedFloatingPointRange<Float> = -180f..180f,
    warning: WarnLevel = WarnLevel.None,
    onChange: (Double) -> Unit,
) {
    // Translate the warning state into colors. Lock = strong amber, Near = soft amber.
    val rowBg: Color = when (warning) {
        WarnLevel.Lock -> Color(0xFFFFE2B5)
        WarnLevel.Near -> Color(0xFFFFF4D9)
        WarnLevel.None -> Color.Transparent
    }
    val sliderColors = when (warning) {
        WarnLevel.Lock -> SliderDefaults.colors(
            thumbColor = Color(0xFFB35A00),
            activeTrackColor = Color(0xFFB35A00),
            inactiveTrackColor = Color(0xFFE8C7A0),
        )
        WarnLevel.Near -> SliderDefaults.colors(
            thumbColor = Color(0xFFCC8800),
            activeTrackColor = Color(0xFFCC8800),
            inactiveTrackColor = Color(0xFFEDD9A8),
        )
        WarnLevel.None -> SliderDefaults.colors()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%+7.2f°".format(valueDeg),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(72.dp),
            )
            Slider(
                value = valueDeg.toFloat().coerceIn(range.start, range.endInclusive),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = range,
                colors = sliderColors,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Order picker: dropdown listing 6 Tait-Bryan + 6 Proper Euler orders.
 *  Public so the SettingsBottomSheet can reuse it. */
@Composable
fun ConventionPicker(
    convention: EulerConvention,
    onChange: (EulerConvention) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = AxisOrder(convention.axis1, convention.axis2, convention.axis3, convention.isProperEuler)

    OutlinedButton(onClick = { expanded = true }, modifier = modifier) {
        Text("Order: ${current.label}${if (current.isProper) "  · proper" else "  · Tait-Bryan"}")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Text(
            "Tait-Bryan",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        for (order in taitBryanOrders) {
            DropdownMenuItem(
                text = { Text(order.label) },
                onClick = {
                    onChange(EulerConvention(order.a1, order.a2, order.a3, convention.frame))
                    expanded = false
                },
            )
        }
        Text(
            "Proper Euler",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        for (order in properEulerOrders) {
            DropdownMenuItem(
                text = { Text(order.label) },
                onClick = {
                    onChange(EulerConvention(order.a1, order.a2, order.a3, convention.frame))
                    expanded = false
                },
            )
        }
    }
}

/** Intrinsic/Extrinsic toggle. Public so the SettingsBottomSheet can reuse it. */
@Composable
fun FrameSwitch(
    convention: EulerConvention,
    onChange: (EulerConvention) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (convention.frame == FrameKind.Intrinsic) "Intrinsic" else "Extrinsic",
            modifier = Modifier.padding(end = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Switch(
            checked = convention.frame == FrameKind.Intrinsic,
            onCheckedChange = { intrinsic ->
                onChange(convention.copy(frame = if (intrinsic) FrameKind.Intrinsic else FrameKind.Extrinsic))
            },
        )
    }
}

