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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.model.GhostSkeleton
import com.boulderbuddy.ghost.model.landmarksAt
import com.boulderbuddy.ui.theme.RouteBlue
import com.boulderbuddy.ui.theme.RouteOrange
import kotlinx.coroutines.delay
import kotlin.math.min

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
            // Fit-Transformation der PlayerView nachrechnen (Letterbox, zentriert).
            val scale = min(
                size.width / poseTrack.frameWidth,
                size.height / poseTrack.frameHeight,
            )
            val offsetX = (size.width - poseTrack.frameWidth * scale) / 2f
            val offsetY = (size.height - poseTrack.frameHeight * scale) / 2f

            // Geist zuerst, Referenz obendrauf — die eigene Bewegung bleibt führend.
            if (ghostTrack != null) {
                drawSkeleton(
                    track = ghostTrack,
                    timeMs = ghostTimeForPosition(positionMs),
                    color = ghostColor.copy(alpha = 0.75f),
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                )
            }
            drawSkeleton(
                track = poseTrack,
                timeMs = positionMs,
                color = skeletonColor,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        }
    }
}

private fun DrawScope.drawSkeleton(
    track: GhostPoseTrack,
    timeMs: Long,
    color: Color,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
) {
    val landmarks = track.landmarksAt(timeMs)
        .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
    if (landmarks.isEmpty()) return

    fun toCanvas(x: Float, y: Float) = Offset(offsetX + x * scale, offsetY + y * scale)

    val byType = landmarks.associateBy { it.type }
    GhostSkeleton.BONES.forEach { (fromType, toType) ->
        val from = byType[fromType] ?: return@forEach
        val to = byType[toType] ?: return@forEach
        drawLine(
            color = color,
            start = toCanvas(from.x, from.y),
            end = toCanvas(to.x, to.y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    landmarks.forEach { landmark ->
        if (landmark.type in GhostSkeleton.JOINT_TYPES) {
            drawCircle(
                color = color,
                radius = 4.dp.toPx(),
                center = toCanvas(landmark.x, landmark.y),
            )
        }
    }
}
