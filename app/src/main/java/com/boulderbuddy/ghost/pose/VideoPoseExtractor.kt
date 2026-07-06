package com.boulderbuddy.ghost.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.video.fullFrameAt
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Offline-Pose-Extraktion für Ghost Climber (Pipeline-Schritt P0-Vorstufe):
 * tastet ein Video mit [GhostTuning.POSE_SAMPLE_FPS] ab und lässt den MediaPipe
 * Pose Landmarker (RunningMode.VIDEO, internes Tracking, echtes visibility/presence)
 * die 33 Landmarks bestimmen. Alle Koordinaten leben im **Analyse-Frame-Raum**
 * (Vollbild, lange Kante = [GhostTuning.POSE_INPUT_LONG_SIDE_PX]) — derselbe Raum
 * wie Anker (M2) und Overlay.
 *
 * **ROI-Crop-Tracking (Stufe 2):** statt immer das ganze (kleine) Vollbild zu
 * analysieren, wird der voll aufgelöste Frame auf die zuletzt bekannte Personen-Box
 * (+[GhostTuning.ROI_EXPAND_FRACTION] je Seite) gecroppt — die Person füllt das
 * Modell-Eingabebild, Extremitäten werden effektiv höher aufgelöst, und die Analyse
 * bindet an den Kletterer statt auf Zuschauer zu springen (Diagnose G). Geht die
 * Person verloren, fällt der nächste Frame aufs Vollbild zurück.
 *
 * Nachverarbeitung (Stufen 1+2): L/R-Konsistenz → anatomische Plausibilität →
 * One-Euro-Glättung → Sichtbarkeits-Hysterese; die Roh-Spur bleibt als
 * [GhostPoseTrack.rawFrames] fürs Debug-Overlay erhalten.
 *
 * Läuft komplett auf [Dispatchers.Default] — eine einmalige Batch-Analyse, bewusst
 * ohne WorkManager (Plan §4). Kooperativ abbrechbar über die aufrufende Coroutine.
 */
class VideoPoseExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Extrahiert die Pose-Spur des Videos. [onProgress] meldet (fertig, gesamt) je Frame —
     * Aufruf erfolgt vom Default-Dispatcher, der Empfänger muss thread-sicher sein.
     */
    suspend fun extract(
        videoUri: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): GhostPoseTrack = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        // detectForVideo verlangt streng aufsteigende Timestamps — gegeben, weil die
        // Sample-Zeiten sequenziell durchlaufen werden. Ein Landmarker pro Video, damit
        // das interne Tracking nicht Spuren verschiedener Videos vermischt.
        val landmarker = PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build(),
                )
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                // 7.5c: Präsenz-/Tracking-Schwelle unter Default (0.5), damit der VIDEO-
                // Tracker bei unsicheren Kletterposen dranbleibt statt abzureißen
                // (weniger Ganzkörper-Aussetzer); Detektion bleibt bei 0.5.
                .setMinPoseDetectionConfidence(GhostTuning.MP_MIN_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(GhostTuning.MP_MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(GhostTuning.MP_MIN_TRACKING_CONFIDENCE)
                .build(),
        )
        try {
            retriever.setDataSource(context, videoUri.toUri())
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val stepMs = (1000.0 / GhostTuning.POSE_SAMPLE_FPS).toLong()
            val sampleTimes = (0 until durationMs step stepMs).toList()
            require(sampleTimes.size >= GhostTuning.MIN_POSE_FRAMES) {
                "Video zu kurz für eine Analyse (mind. " +
                    "${(GhostTuning.MIN_POSE_FRAMES * stepMs) / 1000.0} s nötig)"
            }

            var frameWidth = 0
            var frameHeight = 0
            var roi: RectF? = null
            val frames = ArrayList<GhostPoseFrame>(sampleTimes.size)
            sampleTimes.forEachIndexed { index, timeMs ->
                coroutineContext.ensureActive()
                val full = retriever.fullFrameAt(timeMs)
                if (full != null) {
                    // Alle Frames eines Videos haben dieselben Maße — der (herunter-
                    // skalierte) Analyse-Frame definiert den Koordinatenraum.
                    if (frameWidth == 0) {
                        val scale = GhostTuning.POSE_INPUT_LONG_SIDE_PX.toFloat() /
                            maxOf(full.width, full.height)
                        frameWidth = (full.width * scale).roundToInt()
                        frameHeight = (full.height * scale).roundToInt()
                    }
                    val landmarks = detectLandmarks(
                        landmarker = landmarker,
                        full = full,
                        timeMs = timeMs,
                        analysisWidth = frameWidth,
                        analysisHeight = frameHeight,
                        roi = roi,
                    )
                    full.recycle()
                    frames += GhostPoseFrame(timeMs = timeMs, landmarks = landmarks)
                    roi = nextRoi(roi, landmarks, frameWidth, frameHeight)
                } else {
                    // Nicht dekodierbarer Frame: leerer Eintrag hält die Zeitachse äquidistant.
                    frames += GhostPoseFrame(timeMs = timeMs, landmarks = emptyList())
                    roi = null
                }
                onProgress(index + 1, sampleTimes.size)
            }
            require(frameWidth > 0) { "Video konnte nicht dekodiert werden" }

            GhostPoseTrack(
                videoUri = videoUri,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                durationMs = durationMs,
                sampleFps = GhostTuning.POSE_SAMPLE_FPS,
                // Stufe 2+1: Skalen-Gate/L/R/Plausibilität (auf roh) → Lücken offline
                // interpolieren → One-Euro-Glättung → Sichtbarkeits-Hysterese bilden die
                // Arbeits-Spur; die Roh-Spur bleibt fürs Debug-Overlay erhalten.
                frames = applyVisibilityHysteresis(
                    smoothPoseFrames(fillLandmarkGaps(cleanPoseFrames(frames, frameHeight))),
                ),
                rawFrames = frames,
            )
        } finally {
            retriever.release()
            landmarker.close()
        }
    }

    /**
     * Eine Inferenz: auf dem ROI-Crop des voll aufgelösten Frames (sofern Box bekannt
     * und groß genug), sonst auf dem aufs Analyse-Maß skalierten Vollbild. Ergebnis-
     * Koordinaten immer im Analyse-Frame-Raum.
     */
    private fun detectLandmarks(
        landmarker: PoseLandmarker,
        full: Bitmap,
        timeMs: Long,
        analysisWidth: Int,
        analysisHeight: Int,
        roi: RectF?,
    ): List<GhostLandmark> {
        val scaleX = full.width.toFloat() / analysisWidth
        val scaleY = full.height.toFloat() / analysisHeight
        if (roi != null) {
            val left = (roi.left * scaleX).roundToInt().coerceIn(0, full.width - 1)
            val top = (roi.top * scaleY).roundToInt().coerceIn(0, full.height - 1)
            val width = (roi.width() * scaleX).roundToInt()
                .coerceIn(1, full.width - left)
            val height = (roi.height() * scaleY).roundToInt()
                .coerceIn(1, full.height - top)
            if (width >= MIN_CROP_PX && height >= MIN_CROP_PX) {
                val crop = Bitmap.createBitmap(full, left, top, width, height).asArgb8888()
                val landmarks = runDetection(landmarker, crop, timeMs) { x, y ->
                    // Crop-normiert → Vollbild-Pixel → Analyse-Raum.
                    (left + x * width) / scaleX to (top + y * height) / scaleY
                }
                crop.recycle()
                return landmarks
            }
        }
        val scaled = Bitmap.createScaledBitmap(full, analysisWidth, analysisHeight, true)
            .asArgb8888()
        val landmarks = runDetection(landmarker, scaled, timeMs) { x, y ->
            x * analysisWidth to y * analysisHeight
        }
        scaled.recycle()
        return landmarks
    }

    private inline fun runDetection(
        landmarker: PoseLandmarker,
        bitmap: Bitmap,
        timeMs: Long,
        toAnalysisSpace: (Float, Float) -> Pair<Float, Float>,
    ): List<GhostLandmark> =
        landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timeMs)
            .landmarks().firstOrNull().orEmpty()
            .mapIndexed { type, lm ->
                val (x, y) = toAnalysisSpace(lm.x(), lm.y())
                GhostLandmark(
                    type = type,
                    x = x,
                    y = y,
                    confidence = lm.visibility().orElse(0f),
                    presence = lm.presence().orElse(0f),
                )
            }

    /**
     * Personen-Box für den nächsten Frame: Bounding-Box der sicheren Landmarks,
     * je Seite um [GhostTuning.ROI_EXPAND_FRACTION] erweitert, zeitlich geglättet
     * (ruhige Box → stabileres internes Tracking auf dem Crop-Strom). Zu wenige
     * sichere Landmarks ⇒ null (Person verloren, Vollbild-Suche).
     */
    private fun nextRoi(
        previous: RectF?,
        landmarks: List<GhostLandmark>,
        analysisWidth: Int,
        analysisHeight: Int,
    ): RectF? {
        val confident = landmarks.filter {
            it.confidence >= GhostTuning.MIN_LANDMARK_CONFIDENCE
        }
        if (confident.size < GhostTuning.ROI_MIN_CONFIDENT_LANDMARKS) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        confident.forEach {
            if (it.x < minX) minX = it.x
            if (it.y < minY) minY = it.y
            if (it.x > maxX) maxX = it.x
            if (it.y > maxY) maxY = it.y
        }
        val expandX = (maxX - minX) * GhostTuning.ROI_EXPAND_FRACTION
        val expandY = (maxY - minY) * GhostTuning.ROI_EXPAND_FRACTION
        val box = RectF(
            (minX - expandX).coerceAtLeast(0f),
            (minY - expandY).coerceAtLeast(0f),
            (maxX + expandX).coerceAtMost(analysisWidth.toFloat()),
            (maxY + expandY).coerceAtMost(analysisHeight.toFloat()),
        )
        if (previous == null) return box
        val t = GhostTuning.ROI_BOX_SMOOTHING
        return RectF(
            previous.left + (box.left - previous.left) * t,
            previous.top + (box.top - previous.top) * t,
            previous.right + (box.right - previous.right) * t,
            previous.bottom + (box.bottom - previous.bottom) * t,
        )
    }

    companion object {
        /**
         * Modell-Asset in app/src/main/assets/ (MediaPipe lädt nicht selbst nach).
         * "heavy" (7.5c): Eskalationsstufe von "full", nachdem die Kletter-OOD-Posen
         * (Rücken zur Kamera, Überkopf-Reaches) mit "full" zu unsicher erkannt wurden —
         * sichtbar als Blinken/Morphen der Glieder. Heavy erkennt die Roh-Posen
         * deutlich stabiler; Kosten: ~+21 MB APK und langsamere (offline, einmalige)
         * Analyse. Entscheidung s. Code-Entscheidungen.md.
         */
        const val MODEL_ASSET = "pose_landmarker_heavy.task"

        /** Kleinere Crops als das Modell-Eingabemaß bringen nichts mehr — Vollbild-Fallback. */
        private const val MIN_CROP_PX = 64
    }
}

/** MediaPipe akzeptiert nur ARGB_8888 — MediaMetadataRetriever liefert je nach
 *  Gerät auch RGB_565. Konvertiert nur bei Bedarf (Kopie ersetzt das Original). */
private fun Bitmap.asArgb8888(): Bitmap {
    if (config == Bitmap.Config.ARGB_8888) return this
    val converted = copy(Bitmap.Config.ARGB_8888, false)
    recycle()
    return converted
}
