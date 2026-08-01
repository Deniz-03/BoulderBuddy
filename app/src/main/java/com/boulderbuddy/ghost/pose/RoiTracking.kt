package com.boulderbuddy.ghost.pose

import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.bodyScale
import kotlin.math.hypot

// =============================================================================
// A1 (7.5e) — ROI-Box der Pose-Extraktion, als reine Geometrie
// =============================================================================
//
// Die Box für den nächsten Frame entsteht aus den Landmarks, die IM VORIGEN CROP
// erkannt wurden. Das ist ein Regelkreis, und ohne Bremsen ist er positiv rückgekoppelt:
// eine zu kleine Box schneidet Gliedmaßen ab → im nächsten Frame fehlen sie → die Box
// wird noch kleiner → das Skelett kollabiert. Beobachtbar als "Schrumpfen" und, weil
// mit der Box auch die Skalierung des Modell-Eingabebilds wandert, als "Morphen".
//
// Bewusst Android-frei (kein RectF): das hier ist die Logik, die nicht unbemerkt
// zurückfallen darf, und als reine Kotlin-Datei ist sie mit JVM-Tests absicherbar.

/** Achsenparallele Box im Analyse-Frame-Raum. */
data class PoseRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height
}

/** Ausgang eines Box-Schritts — Rohdaten der Extraktions-Diagnose (A7). */
enum class RoiOutcome {
    /** Erste Box (kein Vorgänger) — oder erste nach einem Verlust. */
    FRESH,

    /** Neue Box akzeptiert und mit der Vorgängerin geglättet. */
    SMOOTHED,

    /** Neue Box unplausibel (Einbruch/Sprung) — Vorgängerin behalten. */
    REJECTED,

    /** Person verloren — nächster Frame als Vollbild-Suche. */
    LOST,
}

data class RoiStep(val roi: PoseRoi?, val outcome: RoiOutcome)

/**
 * Personen-Box für den nächsten Frame, mit fünf Bremsen gegen die Rückkopplung:
 *
 *  1. Nur Landmarks mit ausreichender **Präsenz** spannen die Box auf — visibility
 *     allein reicht nicht, MediaPipe meldet sie auch für erfundene Positionen hoch.
 *  2. Erweiterung relativ zur **Körpergröße**, nicht zur Box (sonst schnürt sich eine
 *     geschrumpfte Box selbst weiter ein).
 *  3. Harte **Mindestgröße** aus Körpergröße UND Frame-Anteil — das ist die eigentliche
 *     Garantie: selbst wenn nur der Rumpf erkannt wurde, umfasst die Box eine ganze Person.
 *  4. Seitenverhältnis auf das des Frames **fixiert**, damit das Modell die Person nie
 *     in wechselnder Verzerrung sieht.
 *  5. **Plausibilitätsprüfung** gegen die Vorgängerin: bricht die Fläche ein oder springt
 *     das Zentrum weg, wird die neue Box verworfen und die alte behalten.
 */
fun nextRoi(
    previous: PoseRoi?,
    landmarks: List<GhostLandmark>,
    analysisWidth: Int,
    analysisHeight: Int,
): RoiStep {
    val usable = landmarks.filter {
        it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE &&
            it.presence >= GhostTuning.ROI_MIN_PRESENCE
    }
    if (usable.size < GhostTuning.ROI_MIN_CONFIDENT_LANDMARKS) {
        return RoiStep(null, RoiOutcome.LOST)
    }
    // Ohne messbare Körpergröße fehlt der Maßstab für Erweiterung und Mindestgröße —
    // dann lieber Vollbild als eine Box auf gut Glück.
    val scale = bodyScale(usable, minPresence = GhostTuning.ROI_MIN_PRESENCE)
        ?: return RoiStep(null, RoiOutcome.LOST)

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    usable.forEach {
        if (it.x < minX) minX = it.x
        if (it.y < minY) minY = it.y
        if (it.x > maxX) maxX = it.x
        if (it.y > maxY) maxY = it.y
    }
    val margin = (scale * GhostTuning.ROI_EXPAND_BODY_FRACTION).toFloat()

    // Mindestgröße: eine Körpergröße ist etwa eine Schulterbreite, eine ganze Person
    // also grob 4 davon — der Faktor deckt sie mit Reserve ab.
    val minSide = maxOf(
        (scale * GhostTuning.ROI_MIN_BODY_MULTIPLE).toFloat(),
        minOf(analysisWidth, analysisHeight) * GhostTuning.ROI_MIN_FRAME_FRACTION,
    )
    var width = maxOf(maxX - minX + 2 * margin, minSide)
    var height = maxOf(maxY - minY + 2 * margin, minSide)

    // Seitenverhältnis des Frames erzwingen — der Crop hat damit dieselbe Geometrie
    // wie der Vollbild-Pfad.
    val frameAspect = analysisWidth.toFloat() / analysisHeight
    if (width / height < frameAspect) width = height * frameAspect else height = width / frameAspect

    val box = fitInFrame(
        centerX = (minX + maxX) / 2f,
        centerY = (minY + maxY) / 2f,
        width = width,
        height = height,
        frameWidth = analysisWidth,
        frameHeight = analysisHeight,
    )
    if (previous == null) return RoiStep(box, RoiOutcome.FRESH)

    val shrankTooFast = box.area < previous.area * GhostTuning.ROI_MAX_SHRINK_PER_FRAME
    val jumped = hypot(
        (box.centerX - previous.centerX).toDouble(),
        (box.centerY - previous.centerY).toDouble(),
    ) > scale * GhostTuning.ROI_MAX_CENTER_JUMP_BODY_FRACTION
    if (shrankTooFast || jumped) return RoiStep(previous, RoiOutcome.REJECTED)

    val t = GhostTuning.ROI_BOX_SMOOTHING
    return RoiStep(
        PoseRoi(
            left = previous.left + (box.left - previous.left) * t,
            top = previous.top + (box.top - previous.top) * t,
            right = previous.right + (box.right - previous.right) * t,
            bottom = previous.bottom + (box.bottom - previous.bottom) * t,
        ),
        RoiOutcome.SMOOTHED,
    )
}

/**
 * Geweitete Box für die periodische PRÜFUNG (S7b): dieselbe Mitte, [factor]-fache
 * Kantenlänge, Seitenverhältnis und Rahmen-Bindung wie bei jeder anderen Box.
 *
 * Sie ersetzt den früheren Vollbild-Reset. Der hat die Person dem Modell in einem völlig
 * anderen Maßstab gezeigt als der normale Crop (gemessen: 57 px Schulterbreite gegen
 * formatfüllend), und dieser Wechsel im Takt 1:11 war die 1-Hz-Störung, die als Wackeln
 * sichtbar wurde. Die Prüfung braucht diesen Maßstabssprung nicht — sie muss nur
 * feststellen können, ob die Box noch auf der Person sitzt, und dafür reicht ein
 * Ausschnitt, der großzügig um die aktuelle Box herumgreift.
 */
fun PoseRoi.widened(factor: Float, frameWidth: Int, frameHeight: Int): PoseRoi =
    fitInFrame(
        centerX = centerX,
        centerY = centerY,
        width = width * factor,
        height = height * factor,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
    )

/**
 * Box der Größe [width]×[height] um ([centerX], [centerY]), vollständig im Frame:
 * verschoben statt beschnitten, damit das erzwungene Seitenverhältnis erhalten bleibt.
 * Passt sie gar nicht hinein, wird sie auf Frame-Größe reduziert.
 */
private fun fitInFrame(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    frameWidth: Int,
    frameHeight: Int,
): PoseRoi {
    val w = width.coerceAtMost(frameWidth.toFloat())
    val h = height.coerceAtMost(frameHeight.toFloat())
    val left = (centerX - w / 2f).coerceIn(0f, frameWidth - w)
    val top = (centerY - h / 2f).coerceIn(0f, frameHeight - h)
    return PoseRoi(left, top, left + w, top + h)
}
