package com.boulderbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ui.theme.RouteBlue
import com.boulderbuddy.ui.theme.RouteOrange
import kotlinx.coroutines.delay

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
    /** Abbruchzeitpunkte (P4c): das jeweilige Skelett faded dort aus. Referenz auf
     *  der Video-Zeitachse, Geist auf der Zeitachse des Vergleichs-Videos. */
    abortTimeMs: Long? = null,
    ghostAbortTimeMs: Long? = null,
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
            if (ghostTrack != null) {
                drawSkeletonOverlay(
                    track = ghostTrack,
                    timeMs = ghostTimeForPosition(positionMs),
                    color = ghostColor.copy(alpha = 0.75f),
                    abortTimeMs = ghostAbortTimeMs,
                )
            }
            drawSkeletonOverlay(
                track = poseTrack,
                timeMs = positionMs,
                color = skeletonColor,
                abortTimeMs = abortTimeMs,
            )
        }
    }
}
