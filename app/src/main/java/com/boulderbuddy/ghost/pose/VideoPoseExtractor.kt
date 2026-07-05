package com.boulderbuddy.ghost.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Offline-Pose-Extraktion für Ghost Climber (M1, Pipeline-Schritt P0-Vorstufe):
 * tastet ein Video mit [GhostTuning.POSE_SAMPLE_FPS] ab, dekodiert jeden Sample-Frame
 * (herunterskaliert auf [GhostTuning.POSE_INPUT_LONG_SIDE_PX]) und lässt ML Kit Pose
 * Detection (accurate, SINGLE_IMAGE_MODE) die 33 Landmarks bestimmen.
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
        val detector = PoseDetection.getClient(
            AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
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
                val bitmap = decodeScaledFrame(retriever, timeMs)
                if (bitmap != null) {
                    // Alle Frames eines Videos haben dieselben (skalierten) Maße —
                    // sie definieren den Koordinatenraum der Keypoints.
                    if (frameWidth == 0) {
                        frameWidth = bitmap.width
                        frameHeight = bitmap.height
                    }
                    val pose = detector.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
                    frames += GhostPoseFrame(
                        timeMs = timeMs,
                        landmarks = pose.allPoseLandmarks.map {
                            GhostLandmark(
                                type = it.landmarkType,
                                x = it.position.x,
                                y = it.position.y,
                                confidence = it.inFrameLikelihood,
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
            detector.close()
        }
    }

    /**
     * Dekodiert den Frame nahe [timeMs], korrekt rotiert und auf die Analyse-Größe
     * skaliert. OPTION_CLOSEST (statt CLOSEST_SYNC) ist pro Frame teurer, liefert aber
     * den tatsächlich zeitnächsten Frame — bei nur wenigen Keyframes pro GOP wären
     * die Sync-Frames viel zu grob für ein 6-fps-Raster.
     */
    private fun decodeScaledFrame(retriever: MediaMetadataRetriever, timeMs: Long): Bitmap? {
        val timeUs = timeMs * 1000
        val longSide = GhostTuning.POSE_INPUT_LONG_SIDE_PX
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Skaliert passend in die Ziel-Box unter Erhalt des Seitenverhältnisses.
            return retriever.getScaledFrameAtTime(
                timeUs, MediaMetadataRetriever.OPTION_CLOSEST, longSide, longSide,
            )
        }
        // API 26: voll dekodieren und selbst herunterskalieren.
        val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: return null
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
}

/**
 * Minimaler Task→Coroutine-Adapter. Bewusst statt der kotlinx-coroutines-play-services-
 * Dependency: eine einzige Await-Stelle rechtfertigt keinen weiteren Katalog-Eintrag.
 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { e -> cont.resumeWithException(e) }
    addOnCanceledListener { cont.cancel() }
}
