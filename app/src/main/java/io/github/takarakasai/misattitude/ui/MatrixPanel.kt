package io.github.takarakasai.misattitude.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.takarakasai.misattitude.domain.Conversions
import io.github.takarakasai.misattitude.domain.Quaternion
import io.github.takarakasai.misattitude.domain.RotationMatrix

/**
 * Rotation-matrix panel: a 3x3 grid display of the body→world matrix R, plus a
 * **per-cell editor** activated by tapping any cell. Once a cell is selected, a
 * slider scrubs that cell's value in [-1, +1]; every drag step rebuilds the full
 * matrix (the other 8 cells frozen at their current canonical values) and pushes
 * it through `onMatrixChange`. The ViewModel orthonormalises before storing, so
 * the cell value the user reads back may "snap" away from where they dragged —
 * that's the orthogonality constraint doing its job, and watching it happen in
 * real time is part of the lesson.
 *
 * Why a tap-to-select editor instead of nine sliders: a 3x3 grid plus 9 sliders
 * doesn't fit comfortably on a phone. One slider re-targeted on tap keeps the
 * panel compact and makes the editing focus explicit (the highlighted cell is
 * what the slider is talking about).
 */
@Composable
fun MatrixPanel(
    canonical: Quaternion,
    onMatrixChange: (RotationMatrix) -> Unit,
    modifier: Modifier = Modifier,
) {
    val m = Conversions.quaternionToMatrix(canonical)
    val det = m[0, 0] * (m[1, 1] * m[2, 2] - m[1, 2] * m[2, 1]) -
        m[0, 1] * (m[1, 0] * m[2, 2] - m[1, 2] * m[2, 0]) +
        m[0, 2] * (m[1, 0] * m[2, 1] - m[1, 1] * m[2, 0])

    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Body→world rotation matrix R. v_world = R · v_body. " +
                "Tap a cell to edit it via the slider; the matrix is re-orthonormalised on every change, " +
                "so cells whose value is fully determined by the others may snap back as you drag.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (r in 0..2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0..2) {
                            MatrixCell(
                                value = m[r, c],
                                selected = selected == (r to c),
                                onClick = { selected = r to c },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            "det(R) = %+.6f  (should be +1 for a proper rotation)".format(det),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )

        // ── Cell editor ──
        // Slider only shows when a cell is selected. Slider value is bound directly
        // to the cell's canonical-derived value, so after orthonormalisation pulls
        // the cell back to a constraint-satisfying value, the thumb follows. The
        // user sees their attempted drag target and the post-constraint result
        // line up (or not), which is the pedagogical point.
        selected?.let { (r, c) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Editing m[$r, $c]",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "  =  %+.4f".format(m[r, c]),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Slider(
                    value = m[r, c].toFloat().coerceIn(-1f, 1f),
                    onValueChange = { newVal ->
                        // Replace the one targeted cell, freeze the other 8 at
                        // their current orthonormalised values, push through.
                        val rows = Array(3) { rr ->
                            DoubleArray(3) { cc ->
                                if (rr == r && cc == c) newVal.toDouble() else m[rr, cc]
                            }
                        }
                        onMatrixChange(RotationMatrix(rows))
                    },
                    valueRange = -1f..1f,
                )
            }
        }
    }
}

/** One cell of the 3x3 grid. Highlighted in the primary container colour when
 *  selected so the user can see at a glance which cell the slider is targeting. */
@Composable
private fun MatrixCell(
    value: Double,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "%+.4f".format(value),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}
