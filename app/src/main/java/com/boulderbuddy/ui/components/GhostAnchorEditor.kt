package com.boulderbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.boulderbuddy.R
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.RouteBlue
import com.boulderbuddy.ui.theme.RouteGreen
import com.boulderbuddy.ui.theme.RouteOrange
import com.boulderbuddy.ui.theme.RoutePink
import com.boulderbuddy.ui.theme.RoutePurple
import com.boulderbuddy.ui.theme.RouteRed
import com.boulderbuddy.ui.theme.RouteYellow

// Markerfarben zyklisch nach Anker-Index — Index N hat in BEIDEN Videos dieselbe
// Farbe, das macht die Korrespondenz beim Tippen sofort sichtbar.
private val AnchorColors = listOf(
    RouteRed, RouteBlue, RouteGreen, RouteOrange, RoutePurple, RoutePink, RouteYellow,
)

internal fun anchorColor(index: Int): Color = AnchorColors[index % AnchorColors.size]

/**
 * Anker-Erfassung für die Homographie (M2, A.3.2): Standbild des Videos, auf dem der
 * Nutzer ≥4 markante Wandpunkte (Griffe/Volumes) antippt — in beiden Videos dieselben
 * Punkte in derselben Reihenfolge. Der Slider wählt das Standbild (z.B. einen Moment,
 * in dem die Wand frei ist); die Anker sind Wandpunkte und damit zeitunabhängig.
 *
 * Getippte Koordinaten werden in den Analyse-Frame-Raum ([frame]-Pixel) zurückgerechnet —
 * denselben Raum, in dem auch die Keypoints liegen.
 */
@Composable
fun GhostAnchorEditor(
    frame: ImageBitmap?,
    anchors: List<GhostPoint>,
    frameTimeMs: Long,
    durationMs: Long,
    onFrameTimeSelected: (Long) -> Unit,
    onAddAnchor: (GhostPoint) -> Unit,
    onRemoveLastAnchor: () -> Unit,
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
            // Box exakt im Frame-Seitenverhältnis: Bild füllt sie vollständig,
            // Mapping Canvas ↔ Frame ist dann ein einzelner Skalierungsfaktor.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frame.width.toFloat() / frame.height)
                    .clip(MaterialTheme.shapes.medium),
            ) {
                Image(
                    bitmap = frame,
                    contentDescription = stringResource(R.string.ghost_standbild_anker),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        // key(frame): der Tap-Handler rechnet mit den Maßen des
                        // aktuell angezeigten Frames.
                        .pointerInput(frame) {
                            detectTapGestures { offset ->
                                val scale = size.width.toFloat() / frame.width
                                onAddAnchor(GhostPoint(offset.x / scale, offset.y / scale))
                            }
                        },
                ) {
                    val scale = size.width / frame.width
                    anchors.forEachIndexed { index, anchor ->
                        val center = Offset(anchor.x * scale, anchor.y * scale)
                        val color = anchorColor(index)
                        drawCircle(color = color, radius = 10.dp.toPx(), center = center)
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = center,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint().apply {
                                this.color = android.graphics.Color.WHITE
                                textSize = 12.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                isFakeBoldText = true
                            }
                            canvas.nativeCanvas.drawText(
                                "${index + 1}",
                                center.x,
                                // Vertikal zentrieren: Baseline um die halbe Texthöhe absenken.
                                center.y - (paint.ascent() + paint.descent()) / 2f,
                                paint,
                            )
                        }
                    }
                }
            }
        }

        // Standbild-Zeitpunkt. Neu dekodiert wird erst beim Loslassen (onValueChangeFinished),
        // nicht bei jedem Zwischenwert.
        if (durationMs > 0) {
            var sliderValue by remember(frameTimeMs) {
                mutableFloatStateOf(frameTimeMs.toFloat() / durationMs)
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    onFrameTimeSelected((sliderValue * durationMs).toLong())
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.ghost_anker_zaehler,
                    anchors.size,
                    GhostTuning.MIN_ANCHORS,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (anchors.size >= GhostTuning.MIN_ANCHORS) {
                    BoulderBuddy.colors.textSecondary
                } else {
                    BoulderBuddy.colors.textTertiary
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRemoveLastAnchor,
                enabled = anchors.isNotEmpty(),
            ) { Text(stringResource(R.string.ghost_letzten_entfernen)) }
        }
    }
}
