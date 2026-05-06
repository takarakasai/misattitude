package io.github.takarakasai.misattitude.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackPanel(
    state: AttitudeViewModel.UiState,
    onCaptureStart: () -> Unit,
    onCaptureEnd: () -> Unit,
    onModeChange: (PlaybackMode) -> Unit,
    onTChange: (Double) -> Unit,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit,
    onToggleSteps: () -> Unit,
    onToggleComparison: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCaptureStart, modifier = Modifier.weight(1f)) {
                Text("Set start = current")
            }
            OutlinedButton(onClick = onCaptureEnd, modifier = Modifier.weight(1f)) {
                Text("Set end = current")
            }
        }

        Text(
            "Start q: " + formatQuat(state.start.w, state.start.x, state.start.y, state.start.z) + "\n" +
            "End   q: " + formatQuat(state.end.w, state.end.x, state.end.y, state.end.z),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.playbackMode == PlaybackMode.Slerp,
                onClick = { onModeChange(PlaybackMode.Slerp) },
                label = { Text("Slerp") },
            )
            FilterChip(
                selected = state.playbackMode == PlaybackMode.EulerLerp,
                onClick = { onModeChange(PlaybackMode.EulerLerp) },
                label = { Text("Euler-LERP") },
            )
            FilterChip(
                selected = state.playbackMode == PlaybackMode.Step,
                onClick = { onModeChange(PlaybackMode.Step) },
                label = { Text("Step (3-axis)") },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("t = %.2f".format(state.playbackT),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = state.playbackT.toFloat(),
                onValueChange = { onTChange(it.toDouble()) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTogglePlay, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Text(if (state.isPlaying) "  Pause" else "  Play")
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Text("  Reset to t = 0")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.showSteps, onCheckedChange = { onToggleSteps() })
            Text("  Show step axes (current attitude's 3-axis decomposition)",
                style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.showComparison, onCheckedChange = { onToggleComparison() })
            Text("  Comparison ghost (other interpolation as wireframe)",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatQuat(w: Double, x: Double, y: Double, z: Double): String =
    "(%+.3f, %+.3f, %+.3f, %+.3f)".format(w, x, y, z)
