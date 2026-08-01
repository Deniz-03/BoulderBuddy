package com.boulderbuddy.ghost.video

import android.graphics.Bitmap
import android.graphics.Matrix
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
        val scaled = getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST,
            longSide,
            longSide,
        )
        return scaled?.let { withGuardedOrientation(it) }
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

/**
 * Orientierungs-Guard (Stufe 4, Diagnose F): getScaledFrameAtTime wendet auf manchen
 * Geräten die Rotations-Metadata der skalierten Variante NICHT an — der Frame liegt
 * dann quer, Anker- und Pose-Raum passen nicht mehr zusammen (Skelett 90° verdreht).
 * Erwartete Anzeige-Orientierung aus den Metadaten ableiten und bei Mismatch selbst
 * nachrotieren.
 */
private fun MediaMetadataRetriever.withGuardedOrientation(bitmap: Bitmap): Bitmap {
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull() ?: return bitmap
    if (rotation % 180 == 0) return bitmap
    val codedWidth = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        ?.toIntOrNull() ?: return bitmap
    val codedHeight = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        ?.toIntOrNull() ?: return bitmap
    if (codedWidth == codedHeight) return bitmap
    // Bei 90°/270° muss das Display-Bild das GEDREHTE Seitenverhältnis haben. Liegt es
    // noch im kodierten Verhältnis, hat das Gerät die Rotation verschluckt → selbst drehen.
    val bitmapPortrait = bitmap.height > bitmap.width
    val displayPortrait = codedWidth > codedHeight // gedreht: kodiert quer ⇒ Anzeige hoch
    if (bitmapPortrait == displayPortrait) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
