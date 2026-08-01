package com.boulderbuddy.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.analysis.PoseQualityMetrics
import com.boulderbuddy.ghost.analysis.PoseSecondStats
import com.boulderbuddy.ghost.analysis.perSecondStats
import com.boulderbuddy.ghost.analysis.qualityMetrics
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ui.theme.RouteBlue
import com.boulderbuddy.ui.theme.RouteOrange
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Video-Player mit Skelett-Overlay für Ghost Climber. Folgt dem Lifecycle-Muster
 * von [VideoPlayer] (pausiert im Hintergrund, release beim Dispose), hält den ExoPlayer
 * aber selbst, weil das Overlay die aktuelle Wiedergabeposition braucht.
 *
 * Das Skelett wird als Compose-Canvas ÜBER die PlayerView gelegt (Plan §4). Die
 * Keypoints leben im Pixelraum des Analyse-Frames ([GhostPoseTrack.frameWidth/Height]);
 * die PlayerView rendert mit RESIZE_MODE_FIT (Letterbox) — dieselbe Fit-Transformation
 * wird hier fürs Mapping Keypoint → Canvas nachgerechnet.
 *
 * [ghostTrack] (M2+): zweite Spur im SELBEN Referenzraum (bereits homographie-
 * transformiert), halbtransparent als "Geist" darübergelegt. [ghostTimeForPosition]
 * mappt die Wiedergabezeit des Referenz-Videos auf die Zeitachse des Geists —
 * Identität, bis das DTW-Alignment (M3) das echte Mapping liefert.
 */
@Composable
fun GhostSkeletonPlayer(
    uri: String,
    poseTrack: GhostPoseTrack,
    modifier: Modifier = Modifier,
    skeletonColor: Color = RouteOrange,
    ghostTrack: GhostPoseTrack? = null,
    ghostColor: Color = RouteBlue,
    ghostTimeForPosition: (Long) -> Long = { it },
    /** Ein-/Ausblenden der einzelnen Skelette im Overlay (7.5c). */
    showSkeleton: Boolean = true,
    showGhost: Boolean = true,
    /** Abbruchzeitpunkte (P4c): das jeweilige Skelett faded dort aus. Referenz auf
     *  der Video-Zeitachse, Geist auf der Zeitachse des Vergleichs-Videos. */
    abortTimeMs: Long? = null,
    ghostAbortTimeMs: Long? = null,
    /**
     * Debug-Modus (Stufe 0, Diagnose-Doc): zeichnet zusätzlich die UNgefilterten
     * Roh-Keypoints (sofern [GhostPoseTrack.rawFrames] vorhanden) und ein HUD mit
     * den Qualitäts-Kennzahlen (Jitter/Dropout/Flip) + Werten der aktuellen Sekunde.
     *
     * S0 (7.5d) ergänzt die SYNC-Diagnose: Warp-Kurve, lokale Warp-Steigung dCmp/dRef
     * und die aktiven Tuning-Werte — ohne die ließe sich die Glättung aus S1 nur
     * erraten statt messen.
     */
    debug: Boolean = false,
    /**
     * Zeichnet die UNGEFILTERTEN Roh-Keypoints (rot) zusätzlich ins Bild. Getrennt von
     * [debug], weil beides nichts miteinander zu tun hat: das HUD liefert Zahlen, die
     * Roh-Spur zeigt Rohdaten. Zusammengeschaltet lagen im Debug-Modus vier Skelette
     * übereinander, zwei davon per Definition wackelig — nicht wiederzuerkennen, ob das
     * Ergebnis unruhig ist oder nur die Rohdaten daneben. Deshalb standardmäßig aus.
     */
    showRawOverlay: Boolean = false,
    /** Normalisierte DTW-Restdistanz (Anteil der Routenlänge) fürs Debug-HUD. */
    dtwDistanceFraction: Double? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri.toUri()))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Wiedergabeposition ~30x/s abfragen — ExoPlayer hat keinen Positions-Listener,
    // Polling ist das übliche Muster für positionsgebundene Overlays.
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition
            delay(33)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
        )
        // Ohne pointerInput konsumiert der Canvas keine Touches — die Player-Controls
        // darunter bleiben bedienbar.
        Canvas(modifier = Modifier.matchParentSize()) {
            // Geist zuerst, Referenz obendrauf — die eigene Bewegung bleibt führend.
            if (ghostTrack != null && showGhost) {
                drawSkeletonOverlay(
                    track = ghostTrack,
                    timeMs = ghostTimeForPosition(positionMs),
                    color = ghostColor.copy(alpha = 0.75f),
                    abortTimeMs = ghostAbortTimeMs,
                )
            }
            if (showSkeleton) {
                drawSkeletonOverlay(
                    track = poseTrack,
                    timeMs = positionMs,
                    color = skeletonColor,
                    abortTimeMs = abortTimeMs,
                )
            }
            if (showRawOverlay) {
                // Roh vs. gefiltert: die ungefilterten Keypoints in Signalfarbe darüber —
                // der sichtbare Abstand zum gefilterten Skelett IST der Filter-Effekt.
                // Dass diese Spur wackelt, ist ihr Zweck und kein Mangel am Ergebnis.
                poseTrack.rawFrames?.let { raw ->
                    drawSkeletonOverlay(
                        track = poseTrack.copy(frames = raw),
                        timeMs = positionMs,
                        color = DebugRawColor,
                    )
                }
                ghostTrack?.rawFrames?.let { raw ->
                    drawSkeletonOverlay(
                        track = ghostTrack.copy(frames = raw),
                        timeMs = ghostTimeForPosition(positionMs),
                        color = DebugRawColor.copy(alpha = 0.6f),
                    )
                }
            }
            if (debug) {
                // Warp-Kurve (S0): macht die Treppenstufen des Alignments sichtbar.
                if (ghostTrack != null) {
                    drawWarpCurve(
                        ghostTimeForPosition = ghostTimeForPosition,
                        refDurationMs = poseTrack.durationMs,
                        cmpDurationMs = ghostTrack.durationMs,
                        positionMs = positionMs,
                    )
                }
            }
        }
        if (debug) {
            DebugHud(
                poseTrack = poseTrack,
                ghostTrack = ghostTrack,
                positionMs = positionMs,
                ghostTimeForPosition = ghostTimeForPosition,
                dtwDistanceFraction = dtwDistanceFraction,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
    }
}

/** Signalfarbe der Roh-Keypoints im Debug-Overlay — bewusst keine Theme-Farbe,
 *  sie liegt über Videobild, nicht über App-Hintergrund. */
private val DebugRawColor = Color(0xFFFF1744)

/**
 * Debug-HUD (Stufe 0): Gesamt-Kennzahlen je Spur + Dropout/Confidence der aktuell
 * abgespielten Sekunde. Die Gesamtwerte gehen zusätzlich einmalig ins Log
 * (Tag "GhostPoseMetrics") — so lässt sich die Baseline per Logcat festhalten.
 */
@Composable
private fun DebugHud(
    poseTrack: GhostPoseTrack,
    ghostTrack: GhostPoseTrack?,
    positionMs: Long,
    ghostTimeForPosition: (Long) -> Long,
    dtwDistanceFraction: Double?,
    modifier: Modifier = Modifier,
) {
    val refMetrics = remember(poseTrack) { poseTrack.qualityMetrics() }
    val refSeconds = remember(poseTrack) { poseTrack.perSecondStats() }
    val ghostMetrics = ghostTrack?.let { track -> remember(track) { track.qualityMetrics() } }
    val ghostSeconds = ghostTrack?.let { track -> remember(track) { track.perSecondStats() } }
    val ghostPositionMs = ghostTrack?.let { ghostTimeForPosition(positionMs) }
    val refShape = remember(poseTrack) { poseTrack.shapeLine() }
    val ghostShape = ghostTrack?.let { track -> remember(track) { track.shapeLine() } }

    LaunchedEffect(refShape, ghostShape) {
        Log.d("GhostPoseMetrics", "Referenz: ${refMetrics.hudLine()} · $refShape")
        ghostMetrics?.let {
            Log.d("GhostPoseMetrics", "Geist: ${it.hudLine()} · ${ghostShape.orEmpty()}")
        }
    }

    val text = buildString {
        append("Ref  ").append(refMetrics.hudLine())
        appendLine()
        append("     ").append(refShape)
        appendLine()
        append("     @${positionMs / 1000}s ")
        append(refSeconds[positionMs / 1000].hudLine())
        if (ghostMetrics != null && ghostSeconds != null && ghostPositionMs != null) {
            appendLine()
            append("Geist ").append(ghostMetrics.hudLine())
            appendLine()
            append("     ").append(ghostShape.orEmpty())
            appendLine()
            append("     @${ghostPositionMs / 1000}s ")
            append(ghostSeconds[ghostPositionMs / 1000].hudLine())
            // --- Sync-Diagnose (S0) ---
            appendLine()
            append(
                String.format(
                    Locale.GERMANY,
                    "Sync  t %d→%d ms · Warp %.2f×",
                    positionMs,
                    ghostPositionMs,
                    warpSlope(ghostTimeForPosition, positionMs),
                ),
            )
            appendLine()
            append(
                String.format(
                    Locale.GERMANY,
                    "      λ %.2f · σ %.0f · Slope %.1f–%.1f",
                    GhostTuning.WARP_LINEAR_BLEND,
                    GhostTuning.WARP_SMOOTHING_SIGMA_FRAMES,
                    GhostTuning.WARP_MIN_SLOPE,
                    GhostTuning.WARP_MAX_SLOPE,
                ),
            )
            if (dtwDistanceFraction != null) {
                appendLine()
                append(
                    String.format(
                        Locale.GERMANY,
                        "      DTW-Dist %.4f (Overlay < %.2f)",
                        dtwDistanceFraction,
                        GhostTuning.MODE_DTW_DISTANCE_MAX_FRACTION,
                    ),
                )
            }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(4.dp),
    )
}

private fun PoseQualityMetrics.hudLine(): String = String.format(
    Locale.GERMANY,
    "Jitter %.1fpx · Drop %.0f%% · Flip %.0f%% · Conf %.2f",
    jitterPx,
    dropoutRate * 100,
    flipRate * 100,
    meanConfidence,
)

private fun PoseSecondStats?.hudLine(): String = if (this == null) {
    "—"
} else {
    String.format(Locale.GERMANY, "Drop %.0f%% · Conf %.2f", dropoutRate * 100, meanConfidence)
}

/**
 * Form-Kennzahlen (A7): Morph und Kollaps der gefilterten Spur, in Klammern dieselben
 * Werte der ROHEN Spur. Genau diese Gegenüberstellung beantwortet die Frage, die sich
 * mit bloßem Hinsehen nicht beantworten lässt: arbeitet die Filterkette überhaupt, und
 * wie viel Rest bleibt? Sinkt "Morph" gegenüber roh kaum, liegt es an der Erkennung,
 * nicht an den Filtern.
 */
private fun GhostPoseTrack.shapeLine(): String {
    val filtered = qualityMetrics()
    val raw = rawFrames?.qualityMetrics()
    return if (raw == null) {
        String.format(
            Locale.GERMANY,
            "Unruhe %.2f%% · Morph %.1f%% · Überlang %.1f%% · Kollaps %.1f%%",
            filtered.centroidWobble * 100,
            filtered.boneLengthCv * 100,
            filtered.boneOverExtensionRate * 100,
            filtered.scaleCv * 100,
        )
    } else {
        String.format(
            Locale.GERMANY,
            "Unruhe %.2f%% (roh %.2f) · Morph %.1f%% (roh %.1f) · " +
                "Überlang %.1f%% (roh %.1f) · Kollaps %.1f%% (roh %.1f)",
            filtered.centroidWobble * 100,
            raw.centroidWobble * 100,
            filtered.boneLengthCv * 100,
            raw.boneLengthCv * 100,
            filtered.boneOverExtensionRate * 100,
            raw.boneOverExtensionRate * 100,
            filtered.scaleCv * 100,
            raw.scaleCv * 100,
        )
    }
}

// =============================================================================
// S0 — Sync-Diagnose: Warp-Steigung + Warp-Kurve
// =============================================================================

/** Halbes Fenster der Steigungs-Messung — kürzer wäre von der ms-Rundung dominiert. */
private const val WARP_SLOPE_WINDOW_MS = 250L

/**
 * Lokale Steigung der Warp-Funktion dCmp/dRef per zentraler Differenz. 1,00 = beide
 * Versuche laufen hier gleich schnell; 0,00 = der Geist steht (Plateau), > 2 = er
 * rast (Sprung). Genau dieses Pendeln ist der "laggy"-Eindruck im Overlay.
 */
private fun warpSlope(
    ghostTimeForPosition: (Long) -> Long,
    positionMs: Long,
    windowMs: Long = WARP_SLOPE_WINDOW_MS,
): Double {
    val from = (positionMs - windowMs).coerceAtLeast(0L)
    val to = positionMs + windowMs
    val span = to - from
    if (span <= 0L) return 1.0
    return (ghostTimeForPosition(to) - ghostTimeForPosition(from)).toDouble() / span
}

/** Stützstellen der gezeichneten Warp-Kurve — fein genug für Stufen, billig genug für 30 fps. */
private const val WARP_CURVE_SAMPLES = 64

/**
 * Warp-Kurve unten rechts im Overlay: x = Referenz-Zeit, y = zugeordnete Vergleichs-Zeit
 * (beide auf ihre Videodauer normiert), dazu die Diagonale als Soll und ein Marker an der
 * aktuellen Position. Eine gerade, diagonalnahe Linie = ruhiger Geist; eine Treppe =
 * das Alignment warpt Rauschen weg.
 */
private fun DrawScope.drawWarpCurve(
    ghostTimeForPosition: (Long) -> Long,
    refDurationMs: Long,
    cmpDurationMs: Long,
    positionMs: Long,
) {
    if (refDurationMs <= 0L || cmpDurationMs <= 0L) return
    val plotSize = minOf(96.dp.toPx(), size.minDimension * 0.3f)
    if (plotSize <= 0f) return
    val padding = 8.dp.toPx()
    val left = size.width - plotSize - padding
    val top = size.height - plotSize - padding

    fun plotPoint(refMs: Long, cmpMs: Long): Offset {
        val x = (refMs.toFloat() / refDurationMs).coerceIn(0f, 1f)
        val y = (cmpMs.toFloat() / cmpDurationMs).coerceIn(0f, 1f)
        // y invertiert: späte Vergleichs-Zeit oben.
        return Offset(left + x * plotSize, top + (1f - y) * plotSize)
    }

    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = Size(plotSize, plotSize),
    )
    // Soll-Diagonale (lineares Strecken) als Vergleichsmaß.
    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(left, top + plotSize),
        end = Offset(left + plotSize, top),
        strokeWidth = 1.dp.toPx(),
    )
    var previous = plotPoint(0L, ghostTimeForPosition(0L))
    for (i in 1..WARP_CURVE_SAMPLES) {
        val refMs = refDurationMs * i / WARP_CURVE_SAMPLES
        val current = plotPoint(refMs, ghostTimeForPosition(refMs))
        drawLine(
            color = DebugWarpColor,
            start = previous,
            end = current,
            strokeWidth = 1.5.dp.toPx(),
        )
        previous = current
    }
    drawCircle(
        color = Color.White,
        radius = 3.dp.toPx(),
        center = plotPoint(positionMs, ghostTimeForPosition(positionMs)),
    )
}

/** Farbe der Warp-Kurve — wie [DebugRawColor] bewusst eine Signalfarbe über dem Videobild. */
private val DebugWarpColor = Color(0xFF00E5FF)
