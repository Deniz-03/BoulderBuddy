package com.boulderbuddy.ghost.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlin.math.roundToInt

/**
 * Dekodiert den Frame nahe [timeMs], korrekt rotiert und auf [longSide] px (lange Kante)
 * skaliert — die EINE Stelle, die den Koordinatenraum der Ghost-Climber-Analyse definiert:
 * Pose-Extraktion (M1) und Anker-Standbilder (M2) müssen identisch dekodieren, sonst
 * passen Keypoints und Anker nicht zusammen.
 *
 * OPTION_CLOSEST (statt CLOSEST_SYNC) ist pro Frame teurer, liefert aber den tatsächlich
 * zeitnächsten Frame — bei nur wenigen Keyframes pro GOP wären Sync-Frames viel zu grob.
 */
/**
 * Voll aufgelöster, korrekt rotierter Frame nahe [timeMs] — für das ROI-Crop-Tracking
 * der Pose-Extraktion (Stufe 2): der Crop braucht die volle Auflösung, damit die
 * Person im Ausschnitt effektiv höher aufgelöst ist als im 720er-Vollbild.
 * (getFrameAtTime rotiert zuverlässig; nur die skalierte Variante hat das latente
 * Rotations-Problem aus Diagnose F.)
 */
internal fun MediaMetadataRetriever.fullFrameAt(timeMs: Long): Bitmap? =
    getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)

internal fun MediaMetadataRetriever.scaledFrameAt(timeMs: Long, longSide: Int): Bitmap? {
    val timeUs = timeMs * 1000
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        // Skaliert passend in die Ziel-Box unter Erhalt des Seitenverhältnisses.
        return getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, longSide, longSide)
    }
    // API 26: voll dekodieren und selbst herunterskalieren.
    val full = getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
    val scale = longSide.toFloat() / maxOf(full.width, full.height)
    if (scale >= 1f) return full
    val scaled = Bitmap.createScaledBitmap(
        full,
        (full.width * scale).roundToInt(),
        (full.height * scale).roundToInt(),
        true,
    )
    if (scaled !== full) full.recycle()
    return scaled
}
