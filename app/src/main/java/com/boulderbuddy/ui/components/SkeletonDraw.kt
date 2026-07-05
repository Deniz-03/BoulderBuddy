package com.boulderbuddy.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.model.GhostSkeleton
import com.boulderbuddy.ghost.model.landmarksAt
import kotlin.math.min

/**
 * Gemeinsames Skelett-Rendering für Overlay- und Side-by-Side-Player: Landmarks
 * zur Zeit [timeMs] (interpoliert), Fit-Transformation Frame-Raum → Canvas
 * (Letterbox wie RESIZE_MODE_FIT der PlayerView), Knochen + Gelenke zeichnen.
 */
internal fun DrawScope.drawSkeletonOverlay(
    track: GhostPoseTrack,
    timeMs: Long,
    color: Color,
    /** Zeitpunkt eines erkannten Abbruchs (P4c): danach faded das Skelett aus. */
    abortTimeMs: Long? = null,
) {
    val fade = abortFadeAlpha(timeMs, abortTimeMs)
    if (fade <= 0f) return
    @Suppress("NAME_SHADOWING")
    val color = color.copy(alpha = color.alpha * fade)

    val landmarks = track.landmarksAt(timeMs)
        .filter { it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE }
    if (landmarks.isEmpty()) return

    val scale = min(size.width / track.frameWidth, size.height / track.frameHeight)
    val offsetX = (size.width - track.frameWidth * scale) / 2f
    val offsetY = (size.height - track.frameHeight * scale) / 2f
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

/**
 * Abbruch als Feature (P4c): volle Deckkraft bis zum Abbruchpunkt, danach linearer
 * Fade auf 0 über [GhostTuning.ABORT_FADE_MS] — der gestürzte Versuch verschwindet,
 * der andere klettert weiter.
 */
private fun abortFadeAlpha(timeMs: Long, abortTimeMs: Long?): Float {
    if (abortTimeMs == null || timeMs <= abortTimeMs) return 1f
    return (1f - (timeMs - abortTimeMs).toFloat() / GhostTuning.ABORT_FADE_MS)
        .coerceIn(0f, 1f)
}
