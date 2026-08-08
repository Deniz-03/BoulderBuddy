package com.boulderbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme

// Geometrie lokal gehalten (nur hier genutzt).
private val RingSize = 220.dp
private val RingStroke = 14.dp

// Fortschritts-Ring des Hangboard-Timers. Zeichnet einen vollen Track-Kreis plus
// einen farbigen Fortschritts-Arc (drawArc) und zeigt Zeit + Phase in der Mitte.
// ringColor signalisiert die Phase (grün = HANG, andere Farbe = REST) und färbt
// sowohl den Arc als auch das Phasen-Label.
@Composable
fun TimerRing(
    progress: Float,
    time: String,
    phaseLabel: String,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    // Farben außerhalb des DrawScope lesen (Canvas-Lambda ist nicht @Composable).
    val trackColor = BoulderBuddy.colors.borderSubtle

    Box(
        modifier = modifier.size(RingSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            // Durchmesser um die Strichbreite verkleinern, damit der Arc nicht abgeschnitten wird.
            val diameter = size.minDimension - stroke
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )

            // Track: voller Kreis als dezenter Hintergrund.
            drawArc(
                color = trackColor,
                startAngle = -90f,   // Start oben (12 Uhr)
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Fortschritt: farbiger Arc im Uhrzeigersinn.
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelLarge,
                color = ringColor,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun TimerRingHangPreview() {
    BoulderBuddyTheme {
        TimerRing(
            progress = 0.7f,
            time = "00:07",
            phaseLabel = "HANG",
            ringColor = BoulderBuddy.colors.routes.green,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun TimerRingRestPreview() {
    BoulderBuddyTheme {
        TimerRing(
            progress = 0.3f,
            time = "00:03",
            phaseLabel = "REST",
            ringColor = BoulderBuddy.colors.routes.orange,
        )
    }
}
