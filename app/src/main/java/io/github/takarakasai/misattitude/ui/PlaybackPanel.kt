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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.takarakasai.misattitude.R

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
    /** When false, all Playback controls are replaced with an upgrade wall.
     *  Playback is positioned as the headline Pro feature ("record/playback /
     *  Slerp-vs-Euler-LERP comparison") so the wall is full-panel rather than
     *  a subtle inline disable. */
    proActive: Boolean = true,
    onBuyPro: () -> Unit = {},
) {
    if (!proActive) {
        PlaybackProWall(onBuyPro = onBuyPro, modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCaptureStart, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pb_set_start))
            }
            OutlinedButton(onClick = onCaptureEnd, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pb_set_end))
            }
        }

        Text(
            stringResource(
                R.string.pb_start_end,
                formatQuat(state.start.w, state.start.x, state.start.y, state.start.z),
                formatQuat(state.end.w, state.end.x, state.end.y, state.end.z),
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.playbackMode == PlaybackMode.Slerp,
                onClick = { onModeChange(PlaybackMode.Slerp) },
                label = { Text(stringResource(R.string.pb_mode_slerp)) },
            )
            FilterChip(
                selected = state.playbackMode == PlaybackMode.EulerLerp,
                onClick = { onModeChange(PlaybackMode.EulerLerp) },
                label = { Text(stringResource(R.string.pb_mode_eulerlerp)) },
            )
            FilterChip(
                selected = state.playbackMode == PlaybackMode.Step,
                onClick = { onModeChange(PlaybackMode.Step) },
                label = { Text(stringResource(R.string.pb_mode_step)) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pb_t, state.playbackT),
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
                Text(stringResource(if (state.isPlaying) R.string.pb_pause else R.string.pb_play))
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Text(stringResource(R.string.pb_reset))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.showSteps, onCheckedChange = { onToggleSteps() })
            Text(stringResource(R.string.pb_show_steps),
                style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.showComparison, onCheckedChange = { onToggleComparison() })
            Text(stringResource(R.string.pb_comparison_ghost),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatQuat(w: Double, x: Double, y: Double, z: Double): String =
    "(%+.3f, %+.3f, %+.3f, %+.3f)".format(w, x, y, z)

/**
 * Full-panel upgrade wall shown in place of the Playback controls for free
 * users. Plays the value-prop strongly: the unique pedagogical thing Playback
 * does — letting you record two attitudes and watch Slerp vs Euler-LERP race
 * — is the kind of "aha" feature worth gating.
 *
 * Style intent: friendly, not aggressive. Background uses primaryContainer
 * (the same hue as the AdMob CTA above the banner) so the row reads as part
 * of the app, not a hostile paywall popup.
 */
@Composable
private fun PlaybackProWall(
    onBuyPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.pb_wall_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.pb_wall_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.pb_wall_bullets),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onBuyPro,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pb_wall_button))
            }
        }
    }
}
