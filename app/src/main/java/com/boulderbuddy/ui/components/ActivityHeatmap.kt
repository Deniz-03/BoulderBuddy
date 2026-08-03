package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Kantenlänge einer Heatmap-Zelle, lokal gehalten. Der Abstand kommt aus paddingXS.
private val HeatmapCell = 16.dp

// Aktivitäts-Heatmap (Statistik-Screen) im Stil eines Contribution-Graphs.
// Jede Zelle steht für einen Tag; intensity (0f..1f) steuert die Deckkraft der
// Zellfarbe — je aktiver, desto kräftiger. Die Zellen werden zeilenweise nach
// `columns` umgebrochen.
//
// fillWidth = true: Die Zellen verteilen sich gleichmäßig über die volle Breite
//   (weight + quadratisch), unabhängig von der Datenmenge. Eine angebrochene
//   letzte Zeile wird mit unsichtbaren Platzhaltern aufgefüllt, damit das Raster
//   bündig bleibt. fillWidth = false (Default): feste Zellgröße (HeatmapCell).
@Composable
fun ActivityHeatmap(
    intensities: List<Float>,
    modifier: Modifier = Modifier,
    columns: Int = 7,
    cellColor: Color = BoulderBuddy.colors.routes.green,
    fillWidth: Boolean = false,
) {
    Column(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
    ) {
        intensities.chunked(columns).forEach { rowCells ->
            Row(
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
            ) {
                rowCells.forEach { intensity ->
                    HeatmapCell(intensity = intensity, cellColor = cellColor, fillWidth = fillWidth)
                }
                // Letzte Zeile bündig halten: fehlende Spalten als leere Slots auffüllen.
                if (fillWidth) {
                    repeat(columns - rowCells.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// Eine einzelne Tageszelle. fillWidth steuert, ob sie sich die Breite teilt
// (weight + quadratisch) oder eine feste Kantenlänge hat.
@Composable
private fun RowScope.HeatmapCell(
    intensity: Float,
    cellColor: Color,
    fillWidth: Boolean,
) {
    val sizeModifier = if (fillWidth) {
        Modifier
            .weight(1f)
            .aspectRatio(1f)
    } else {
        Modifier.size(HeatmapCell)
    }
    Box(
        modifier = sizeModifier
            .clip(MaterialTheme.shapes.extraSmall)
            // Auch inaktive Tage bleiben als blasse Zelle sichtbar (Sockel 0.15).
            .background(
                cellColor.copy(
                    alpha = (0.15f + 0.85f * intensity).coerceIn(0f, 1f),
                ),
            ),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6)
@Composable
private fun ActivityHeatmapPreview() {
    BoulderBuddyTheme {
        ActivityHeatmap(
            intensities = listOf(
                0f, 0.2f, 0f, 0.6f, 1f, 0.4f, 0f,
                0.3f, 0f, 0.8f, 0.5f, 0f, 0.2f, 0.9f,
                1f, 0.7f, 0.3f, 0f, 0.6f, 0.4f, 0.1f,
                0f, 0.5f, 0.9f, 0.2f, 0.7f, 0f, 0.3f,
            ),
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}
