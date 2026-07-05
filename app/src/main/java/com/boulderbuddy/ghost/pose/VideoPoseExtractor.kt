package com.boulderbuddy.ghost.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.video.scaledFrameAt
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

/**
 * Offline-Pose-Extraktion für Ghost Climber (Pipeline-Schritt P0-Vorstufe):
 * tastet ein Video mit [GhostTuning.POSE_SAMPLE_FPS] ab, dekodiert jeden Sample-Frame
 * (herunterskaliert auf [GhostTuning.POSE_INPUT_LONG_SIDE_PX]) und lässt den MediaPipe
 * Pose Landmarker die 33 Landmarks bestimmen.
 *
 * Seit Stufe 3 (FABLE_GHOSTCLIMBER_STABILISIERUNG.md) MediaPipe statt ML Kit:
 * **RunningMode.VIDEO** trackt intern über die Frames (zeitliche Konsistenz statt
 * unabhängiger Einzelbilder) und liefert echtes **visibility** (→ `confidence`) und
 * **presence** pro Landmark — die Basis für Filter und Hysterese (Stufe 1).
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
            val frames = ArrayList<GhostPoseFrame>(sampleTimes.size)
            sampleTimes.forEachIndexed { index, timeMs ->
                coroutineContext.ensureActive()
                val bitmap = retriever.scaledFrameAt(timeMs, GhostTuning.POSE_INPUT_LONG_SIDE_PX)
                    ?.asArgb8888()
                if (bitmap != null) {
                    // Alle Frames eines Videos haben dieselben (skalierten) Maße —
                    // sie definieren den Koordinatenraum der Keypoints.
                    if (frameWidth == 0) {
                        frameWidth = bitmap.width
                        frameHeight = bitmap.height
                    }
                    val result = landmarker.detectForVideo(
                        BitmapImageBuilder(bitmap).build(),
                        timeMs,
                    )
                    frames += GhostPoseFrame(
                        timeMs = timeMs,
                        // Landmarks kommen normalisiert (0–1) — zurück in den Pixelraum
                        // des Analyse-Frames, in dem auch Anker + Overlay leben.
                        landmarks = result.landmarks().firstOrNull().orEmpty()
                            .mapIndexed { type, lm ->
                                GhostLandmark(
                                    type = type,
                                    x = lm.x() * bitmap.width,
                                    y = lm.y() * bitmap.height,
                                    confidence = lm.visibility().orElse(0f),
                                    presence = lm.presence().orElse(0f),
                                )
                            },
                    )
                    bitmap.recycle()
                } else {
                    // Nicht dekodierbarer Frame: leerer Eintrag hält die Zeitachse äquidistant.
                    frames += GhostPoseFrame(timeMs = timeMs, landmarks = emptyList())
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
                frames = frames,
            )
        } finally {
            retriever.release()
            landmarker.close()
        }
    }

    companion object {
        /**
         * Modell-Asset in app/src/main/assets/ (MediaPipe lädt nicht selbst nach).
         * "full" statt "heavy": Kompromiss aus Genauigkeit und APK-Größe/Analysezeit
         * (heavy wäre die Eskalationsstufe, falls die Kletter-OOD-Posen mit full
         * nicht stabil genug werden) — Entscheidung s. Code-Entscheidungen.md.
         */
        const val MODEL_ASSET = "pose_landmarker_full.task"
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
