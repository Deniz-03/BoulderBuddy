package com.boulderbuddy.ghost.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.net.toUri
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostLandmark
import com.boulderbuddy.ghost.model.GhostPoseFrame
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.video.fullFrameAt
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
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
 * (+[GhostTuning.ROI_EXPAND_BODY_FRACTION] je Seite) gecroppt — die Person füllt das
 * Modell-Eingabebild, Extremitäten werden effektiv höher aufgelöst, und die Analyse
 * bindet an den Kletterer statt auf Zuschauer zu springen (Diagnose G). Geht die
 * Person verloren, fällt der nächste Frame aufs Vollbild zurück.
 *
 * **Ein Weg für die Spur, ein zweiter für die Box (S7b):** Crop und Vollbild zeigen dem
 * Modell die Person in völlig verschiedenem Maßstab und liefern deshalb systematisch
 * verschiedene Landmarks. Jeder Wechsel zwischen beiden ist ein Sprung im Ergebnis — im
 * festen Takt wiederholt eine periodische Störung, die keine nachgelagerte Glättung mehr
 * entfernen kann und die als regelmäßiges Zucken des Skeletts sichtbar wird. Die Spur
 * läuft deshalb ausnahmslos über den Crop-Weg; die Kontrolle, ob die Box noch stimmt,
 * läuft über einen SEPARATEN Landmarker auf einem geweiteten Ausschnitt und beeinflusst
 * ausschließlich die Box.
 *
 * Nachverarbeitung, in dieser Reihenfolge (die Kette steht ausgeschrieben unten bei der
 * Erzeugung des [GhostPoseTrack], samt Begründung für die Position des letzten Glieds):
 * Pose-Gates inkl. L/R-Konsistenz und anatomischer Klemmen → begrenzte
 * Lücken-Interpolation → One-Euro-Glättung → Sichtbarkeits-Hysterese → rigide
 * Rekonstruktion der Gliedmaßenketten. Die Roh-Spur bleibt daneben als
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
        val landmarker = createLandmarker(RunningMode.VIDEO)
        // Zweiter Landmarker allein für die Box-Prüfung (S7b). Getrennt, weil der
        // VIDEO-Modus einen internen Tracker führt: schob man ihm alle 12 Frames ein
        // andersartiges Eingabebild unter, war nicht nur DIESER Frame gestört, sondern
        // auch der darauf folgende — gemessen war der Nachfolger sogar der schlechtere
        // (18,2 % gegen 17,0 % Versatz). Der IMAGE-Modus hat keinen solchen Zustand;
        // die Prüfung läuft damit vollständig neben der Spur her.
        val checkLandmarker = createLandmarker(RunningMode.IMAGE)
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
            var roi: PoseRoi? = null
            var consecutiveRoiRejects = 0
            val roiStats = IntArray(RoiOutcome.entries.size)
            var fullFrameDetections = 0
            var boxChecks = 0
            var boxReanchors = 0
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
                    // Die SPUR entsteht immer auf demselben Weg: Crop aus der vollen
                    // Auflösung, sobald eine Box bekannt ist. Vollbild nur noch dort, wo
                    // es keine Alternative gibt — beim ersten Frame und nach einem echten
                    // Verlust. Jeder Wechsel zwischen beiden Wegen ist ein Sprung im
                    // Maßstab des Modell-Eingabebilds und damit ein Sprung im Ergebnis;
                    // regelmäßig wiederholt ergab das die 1-Hz-Störung aus S7 (Details in
                    // GhostTuning.ROI_CHECK_WIDEN_FACTOR).
                    if (roi == null) fullFrameDetections++
                    val landmarks = detectLandmarks(
                        full = full,
                        analysisWidth = frameWidth,
                        analysisHeight = frameHeight,
                        roi = roi,
                    ) { image -> landmarker.detectForVideo(image, timeMs) }
                    frames += GhostPoseFrame(timeMs = timeMs, landmarks = landmarks)

                    // Box-Pflege, getrennt von der Spur (S7b): periodisch — und zusätzlich
                    // nach MEHREREN verworfenen Boxen in Folge (S3d/S4b; eine einzelne
                    // Verwerfung ist normale Rauschabwehr) — wird auf einem geweiteten
                    // Ausschnitt nachgesehen, ob die Box überhaupt noch auf der Person
                    // sitzt. Ohne diese Prüfung bliebe ein einmal eingelaufener Box-Fehler
                    // bis zum Videoende bestehen.
                    val dueForCheck = roi != null && (
                        index % GhostTuning.ROI_BOX_CHECK_INTERVAL_FRAMES == 0 ||
                            consecutiveRoiRejects >= GhostTuning.ROI_REANCHOR_AFTER_REJECTS
                        )
                    val checked = if (dueForCheck) {
                        boxChecks++
                        checkRoi(
                            checkLandmarker = checkLandmarker,
                            full = full,
                            roi = requireNotNull(roi),
                            analysisWidth = frameWidth,
                            analysisHeight = frameHeight,
                        )
                    } else {
                        null
                    }
                    full.recycle()
                    if (checked != null) boxReanchors++
                    // Die Prüfung darf nur helfen, nie schaden: findet sie niemanden oder
                    // stimmt die Box ohnehin (S8b), entscheidet der normale Weg.
                    val step = checked ?: nextRoi(roi, landmarks, frameWidth, frameHeight)
                    roiStats[step.outcome.ordinal]++
                    roi = step.roi
                    consecutiveRoiRejects = when {
                        step.outcome != RoiOutcome.REJECTED -> 0
                        // Nach dem Neuverankern die Serie zurücksetzen, sonst löst jede
                        // weitere Verwerfung sofort wieder eine Prüfung aus.
                        dueForCheck -> 0
                        else -> consecutiveRoiRejects + 1
                    }
                } else {
                    // Nicht dekodierbarer Frame: leerer Eintrag hält die Zeitachse äquidistant.
                    frames += GhostPoseFrame(timeMs = timeMs, landmarks = emptyList())
                    roi = null
                    consecutiveRoiRejects = 0
                }
                onProgress(index + 1, sampleTimes.size)
            }
            require(frameWidth > 0) { "Video konnte nicht dekodiert werden" }

            // Extraktions-Diagnose (A7): der ROI-Regelkreis ist im fertigen Track nicht
            // mehr sichtbar — viele REJECTED/LOST heißen, dass die Box laufend gegen die
            // Bremsen läuft und die Roh-Erkennung das eigentliche Problem ist.
            //
            // "Vollbild" ist seit S7b die Zahl, auf die es ankommt: jeder dieser Frames
            // sieht die Person in einem anderen Maßstab als alle übrigen und liegt
            // deshalb systematisch daneben. Erwartet wird eine kleine einstellige Zahl
            // (Start + echte Verluste); steigt sie, ist die Box-Verfolgung das Problem.
            Log.d(
                LOG_TAG,
                "ROI $videoUri: frisch=${roiStats[RoiOutcome.FRESH.ordinal]} " +
                    "geglättet=${roiStats[RoiOutcome.SMOOTHED.ordinal]} " +
                    "verworfen=${roiStats[RoiOutcome.REJECTED.ordinal]} " +
                    "verloren=${roiStats[RoiOutcome.LOST.ordinal]} " +
                    // Prüfungen/Neuverankerungen (S8b): greift die Prüfung fast immer,
                    // steht das Totband zu eng und die Box springt im Prüftakt — genau
                    // die periodische Störung, die sie beseitigen soll. Greift sie nie,
                    // ist es zu weit und ein verlaufener Kasten bleibt unbemerkt.
                    "Box-Prüfungen=$boxChecks davon eingegriffen=$boxReanchors " +
                    "Vollbild=$fullFrameDetections/${sampleTimes.size}",
            )

            GhostPoseTrack(
                videoUri = videoUri,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                durationMs = durationMs,
                sampleFps = GhostTuning.POSE_SAMPLE_FPS,
                // Pose-Gates/L/R/Geschwindigkeit (auf roh) → Lücken offline interpolieren
                // → One-Euro-Glättung → Sichtbarkeits-Hysterese → rigide Rekonstruktion.
                // Die Rekonstruktion steht bewusst ganz am ENDE (S3b): die Hysterese
                // blendet Landmarks wieder ein, deren Confidence sie anhebt — lief die
                // Rekonstruktion davor, blieben genau diese ungeprüft.
                frames = enforceRigidSkeleton(
                    applyVisibilityHysteresis(
                        smoothPoseFrames(
                            fillLandmarkGaps(
                                cleanPoseFrames(frames, frameHeight) { stats ->
                                    Log.d(
                                        LOG_TAG,
                                        "Gates $videoUri: Skala=${stats.scaleInvalid} " +
                                            "Shift=${stats.shiftInvalid} " +
                                            "Ruck=${stats.jerkInvalid} " +
                                            "geleert=${stats.dropped} von ${stats.total}",
                                    )
                                },
                            ),
                        ),
                    ),
                ),
                rawFrames = frames,
            )
        } finally {
            retriever.release()
            landmarker.close()
            checkLandmarker.close()
        }
    }

    private fun createLandmarker(mode: RunningMode): PoseLandmarker =
        PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build(),
                )
                .setRunningMode(mode)
                .setNumPoses(1)
                // 7.5c: Präsenz-/Tracking-Schwelle unter Default (0.5), damit der VIDEO-
                // Tracker bei unsicheren Kletterposen dranbleibt statt abzureißen
                // (weniger Ganzkörper-Aussetzer); Detektion bleibt bei 0.5.
                .setMinPoseDetectionConfidence(GhostTuning.MP_MIN_DETECTION_CONFIDENCE)
                .setMinPosePresenceConfidence(GhostTuning.MP_MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(GhostTuning.MP_MIN_TRACKING_CONFIDENCE)
                .build(),
        )

    /**
     * Prüft auf einem geweiteten Ausschnitt nach, wo die Person wirklich steht, und gibt
     * die daraus frisch aufgespannte Box zurück (S7b) — oder null, wenn dort niemand zu
     * finden war ODER die laufende Box ohnehin stimmt (S8b). In beiden Fällen soll die
     * Box nicht angetastet werden, und das ist der Normalfall.
     *
     * Bewusst [nextRoi] ohne Vorgängerin: die Plausibilitätsbremsen (Schrumpf-/Sprung-
     * Limit) sind dafür da, das laufende Tracking gegen Rauschen zu schützen — hier
     * würden sie ausgerechnet den Fall abwehren, für den die Prüfung existiert, nämlich
     * eine Box, die längst woanders sitzt.
     */
    private fun checkRoi(
        checkLandmarker: PoseLandmarker,
        full: Bitmap,
        roi: PoseRoi,
        analysisWidth: Int,
        analysisHeight: Int,
    ): RoiStep? {
        val wide = roi.widened(
            factor = GhostTuning.ROI_CHECK_WIDEN_FACTOR,
            frameWidth = analysisWidth,
            frameHeight = analysisHeight,
        )
        val landmarks = detectLandmarks(
            full = full,
            analysisWidth = analysisWidth,
            analysisHeight = analysisHeight,
            roi = wide,
        ) { image -> checkLandmarker.detect(image) }
        val step = nextRoi(null, landmarks, analysisWidth, analysisHeight)
        val checked = step.roi ?: return null
        return step.takeIf { needsReanchor(roi, checked) }
    }

    /**
     * Eine Inferenz: auf dem ROI-Crop des voll aufgelösten Frames (sofern Box bekannt
     * und groß genug), sonst auf dem aufs Analyse-Maß skalierten Vollbild. Ergebnis-
     * Koordinaten immer im Analyse-Frame-Raum.
     *
     * [detect] bestimmt, WELCHER Landmarker läuft — der Spur-Landmarker im VIDEO-Modus
     * oder der Prüf-Landmarker im IMAGE-Modus. Das Zuschneiden und das Zurückrechnen der
     * Koordinaten sind für beide identisch und sollen es auch bleiben.
     */
    private inline fun detectLandmarks(
        full: Bitmap,
        analysisWidth: Int,
        analysisHeight: Int,
        roi: PoseRoi?,
        detect: (MPImage) -> PoseLandmarkerResult,
    ): List<GhostLandmark> {
        val scaleX = full.width.toFloat() / analysisWidth
        val scaleY = full.height.toFloat() / analysisHeight
        if (roi != null) {
            val left = (roi.left * scaleX).roundToInt().coerceIn(0, full.width - 1)
            val top = (roi.top * scaleY).roundToInt().coerceIn(0, full.height - 1)
            val width = (roi.width * scaleX).roundToInt()
                .coerceIn(1, full.width - left)
            val height = (roi.height * scaleY).roundToInt()
                .coerceIn(1, full.height - top)
            if (width >= MIN_CROP_PX && height >= MIN_CROP_PX) {
                val crop = Bitmap.createBitmap(full, left, top, width, height).asArgb8888()
                val landmarks = runDetection(crop, detect) { x, y ->
                    // Crop-normiert → Vollbild-Pixel → Analyse-Raum.
                    (left + x * width) / scaleX to (top + y * height) / scaleY
                }
                crop.recycle()
                return landmarks
            }
        }
        val scaled = Bitmap.createScaledBitmap(full, analysisWidth, analysisHeight, true)
            .asArgb8888()
        val landmarks = runDetection(scaled, detect) { x, y ->
            x * analysisWidth to y * analysisHeight
        }
        scaled.recycle()
        return landmarks
    }

    private inline fun runDetection(
        bitmap: Bitmap,
        detect: (MPImage) -> PoseLandmarkerResult,
        toAnalysisSpace: (Float, Float) -> Pair<Float, Float>,
    ): List<GhostLandmark> =
        detect(BitmapImageBuilder(bitmap).build())
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

    // Die Box-Logik selbst lebt Android-frei in RoiTracking.kt (JVM-testbar).

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

        /** Logcat-Tag der Extraktions-Diagnose (A7). */
        private const val LOG_TAG = "GhostPoseExtract"
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
