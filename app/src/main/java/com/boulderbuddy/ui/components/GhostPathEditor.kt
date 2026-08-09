package com.boulderbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.boulderbuddy.R
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.RouteGreen
import com.boulderbuddy.ui.theme.RouteOrange

/**
 * Routenpfad-Korrektur (M3, P3): zeigt die geglättete Hüfttrajektorie der Referenz
 * (gestrichelt, orange) als Kontext und den editierbaren Pfad (grün) darüber.
 * Tippen hängt einen Stützpunkt ANS ENDE — so lässt sich der Vorschlag bis zum Top
 * verlängern (P4c: der Pfad soll weiter reichen als ein abgebrochener Versuch).
 */
@Composable
fun GhostPathEditor(
    frame: ImageBitmap?,
    trajectory: List<GhostPoint>,
    path: List<GhostPoint>,
    onAddPoint: (GhostPoint) -> Unit,
    onRemoveLastPoint: () -> Unit,
    onResetToSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        if (frame == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(BoulderBuddy.colors.surfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frame.width.toFloat() / frame.height)
                    .clip(MaterialTheme.shapes.medium),
            ) {
                Image(
                    bitmap = frame,
                    contentDescription = stringResource(R.string.ghost_standbild_pfad),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(frame) {
                            detectTapGestures { offset ->
                                val scale = size.width.toFloat() / frame.width
                                onAddPoint(GhostPoint(offset.x / scale, offset.y / scale))
                            }
                        },
                ) {
                    val scale = size.width / frame.width
                    fun toCanvas(p: GhostPoint) = Offset(p.x * scale, p.y * scale)

                    // Kontext: die rohe (geglättete) Hüfttrajektorie, gestrichelt.
                    if (trajectory.size >= 2) {
                        val trajectoryPath = Path().apply {
                            moveTo(toCanvas(trajectory.first()).x, toCanvas(trajectory.first()).y)
                            trajectory.drop(1).forEach { val c = toCanvas(it); lineTo(c.x, c.y) }
                        }
                        drawPath(
                            path = trajectoryPath,
                            color = RouteOrange.copy(alpha = 0.6f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                ),
                            ),
                        )
                    }

                    // Der editierbare Routenpfad mit Stützpunkten.
                    path.zipWithNext().forEach { (a, b) ->
                        drawLine(
                            color = RouteGreen,
                            start = toCanvas(a),
                            end = toCanvas(b),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    path.forEach { point ->
                        drawCircle(
                            color = RouteGreen,
                            radius = 6.dp.toPx(),
                            center = toCanvas(point),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = toCanvas(point),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.ghost_stuetzpunkte,
                    path.size,
                    path.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = BoulderBuddy.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRemoveLastPoint,
                enabled = path.isNotEmpty(),
            ) { Text(stringResource(R.string.ghost_letzten_entfernen)) }
            TextButton(onClick = onResetToSuggestion) {
                Text(stringResource(R.string.ghost_vorschlag_knopf))
            }
        }
    }
}
