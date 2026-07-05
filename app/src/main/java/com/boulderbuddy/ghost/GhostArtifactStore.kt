package com.boulderbuddy.ghost

import android.content.Context
import com.boulderbuddy.ghost.model.GhostPoseTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Ablage der Ghost-Climber-Analyse-Artefakte als JSON-Dateien im App-Storage
 * (Plan A.2: Keypoints & Co. NICHT als DB-BLOBs — nur Pfade landen später in der DB).
 *
 * Die Pose-Extraktion ist der teuerste Schritt der Pipeline (Minuten pro Video),
 * deshalb wird die Pose-Spur pro Video-URI gecacht: gleiche URI + gleiche Abtastrate
 * ⇒ Extraktion entfällt.
 */
class GhostArtifactStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dir = File(context.filesDir, "ghost")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun savePoseTrack(track: GhostPoseTrack) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        poseTrackFile(track.videoUri).writeText(json.encodeToString(track))
    }

    /** Gecachte Pose-Spur oder null (nicht vorhanden/nicht mehr lesbar). */
    suspend fun loadPoseTrack(videoUri: String): GhostPoseTrack? =
        loadPoseTrackFromPath(poseTrackFile(videoUri).absolutePath)

    /** Pose-Spur direkt über ihren Dateipfad — für gespeicherte Analysen (M5),
     *  deren Pfade in der DB stehen und auch nach Tuning-Änderungen gültig bleiben. */
    suspend fun loadPoseTrackFromPath(path: String): GhostPoseTrack? =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@withContext null
            // Defektes/verändertes Schema → wie "kein Cache" behandeln, neu analysieren.
            runCatching { json.decodeFromString<GhostPoseTrack>(file.readText()) }.getOrNull()
        }

    /** Dateipfad der (gecachten) Pose-Spur eines Videos — wird in der DB referenziert (M5). */
    fun poseTrackPath(videoUri: String): String = poseTrackFile(videoUri).absolutePath

    // Abtastrate UND Pipeline-Marker stecken im Cache-Schlüssel: ändert sich
    // GhostTuning.POSE_SAMPLE_FPS oder die Pose-Pipeline (Modell/Filter-Semantik),
    // veralten alte Spuren automatisch statt mit falschen Werten weiterzuleben.
    // "mp-full-1" = MediaPipe pose_landmarker_full, Stufe 3 (ML-Kit-Spuren hätten
    // InFrameLikelihood statt visibility als confidence — nicht vergleichbar).
    private fun poseTrackFile(videoUri: String): File =
        File(dir, "pose_${sha1("$videoUri@${GhostTuning.POSE_SAMPLE_FPS}@mp-full-1")}.json")

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
