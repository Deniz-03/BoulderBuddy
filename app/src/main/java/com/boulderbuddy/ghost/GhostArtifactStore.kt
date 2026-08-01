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
    // "mp-heavy-17" = Pose-Gates benutzen dieselbe Körpergrößen-Referenz wie
    // Rekonstruktion und Kennzahlen (personScales, Fenster 12 statt eigener 15).
    // "mp-heavy-16" (S5, 7.5e) = Rumpfkanten sind Teil der rigiden Rekonstruktion, und
    // Pass wie Kennzahlen normieren gegen DIESELBE Körpergrößen-Referenz (personScales).
    // "mp-heavy-15" (S4, 7.5e) = geglättete Körpergröße als Soll-Referenz der rigiden
    // Rekonstruktion, Neuverankerung erst nach mehreren verworfenen ROI-Boxen.
    // "mp-heavy-14" (S3, 7.5e) = Ruck-Gate über die Zentroid-Beschleunigung, rigide
    // Rekonstruktion ans Ende der Kette (nach der Hysterese) und iterativ, sowie
    // Vollbild-Neuverankerung nach einer verworfenen ROI-Box.
    // "mp-heavy-13" (S2, 7.5e) = rigide Rekonstruktion der Gliedmaßenketten (normiertes
    // Knochen-Verhältnis statt absoluter Pixellänge), Pose-Gates gegen ROLLIERENDEN
    // Median mit engeren Schwellen, begrenzte Interpolationslänge, und nur noch EIN
    // Überbrückungs-Mechanismus (Gap-Fill; das Halten alter Positionen ist raus).
    // "mp-heavy-12" (A1, 7.5e) = ROI-Box neu: presence-gefiltert, Erweiterung und
    // Mindestgröße an der Körpergröße statt an der Box, festes Seitenverhältnis,
    // Schrumpf-/Sprung-Bremse und periodischer Vollbild-Reset. Die Box bestimmt, was
    // das Modell überhaupt sieht — also andere Roh-Landmarks, also neue Spur-Semantik.
    // "mp-heavy-11" = heavy + gesenkte MP-Schwellen + ROI-Crop + Pose-Konsistenz-Gate
    // (Skala + Position) + L/R-Konsistenz + Plausibilität-KLEMMEN → Lücken-Interpolation
    // → One-Euro → Hysterese (gesenkte Zeichen-Schwelle). "-11": robustere Multi-Cue-
    // Körpergröße + Positions-Gate gegen verschobene Skelette — neue Spur-Semantik,
    // alte Spuren veralten, erneute Analyse extrahiert neu.
    private fun poseTrackFile(videoUri: String): File =
        File(dir, "pose_${sha1("$videoUri@${GhostTuning.POSE_SAMPLE_FPS}@mp-heavy-17")}.json")

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
