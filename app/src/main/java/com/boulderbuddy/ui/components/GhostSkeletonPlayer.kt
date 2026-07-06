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
import androidx.compose.ui.graphics.Color
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
     */
    debug: Boolean = false,
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
            if (debug) {
                // Roh vs. gefiltert: die ungefilterten Keypoints in Signalfarbe darüber —
                // der sichtbare Abstand zum gefilterten Skelett IST der Filter-Effekt.
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
        }
        if (debug) {
            DebugHud(
                poseTrack = poseTrack,
                ghostTrack = ghostTrack,
                positionMs = positionMs,
                ghostPositionMs = ghostTrack?.let { ghostTimeForPosition(positionMs) },
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
    ghostPositionMs: Long?,
    modifier: Modifier = Modifier,
) {
    val refMetrics = remember(poseTrack) { poseTrack.qualityMetrics() }
    val refSeconds = remember(poseTrack) { poseTrack.perSecondStats() }
    val ghostMetrics = ghostTrack?.let { track -> remember(track) { track.qualityMetrics() } }
    val ghostSeconds = ghostTrack?.let { track -> remember(track) { track.perSecondStats() } }

    LaunchedEffect(refMetrics, ghostMetrics) {
        Log.d("GhostPoseMetrics", "Referenz: ${refMetrics.hudLine()}")
        ghostMetrics?.let { Log.d("GhostPoseMetrics", "Geist: ${it.hudLine()}") }
    }

    val text = buildString {
        append("Ref  ").append(refMetrics.hudLine())
        appendLine()
        append("     @${positionMs / 1000}s ")
        append(refSeconds[positionMs / 1000].hudLine())
        if (ghostMetrics != null && ghostSeconds != null && ghostPositionMs != null) {
            appendLine()
            append("Geist ").append(ghostMetrics.hudLine())
            appendLine()
            append("     @${ghostPositionMs / 1000}s ")
            append(ghostSeconds[ghostPositionMs / 1000].hudLine())
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
