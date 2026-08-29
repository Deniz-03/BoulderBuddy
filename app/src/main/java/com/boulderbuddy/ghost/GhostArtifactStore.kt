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
 * Die Pose-Extraktion ist der teuerste Schritt der Pipeline (Minuten pro Video), deshalb
 * wird die Pose-Spur gecacht. Der Schlüssel ist mehr als die URI: Abtastrate UND ein
 * Marker der Pipeline-Fassung stecken mit drin (siehe `poseTrackFile`), damit eine
 * Änderung an Modell oder Filterkette alte Spuren automatisch veralten lässt, statt sie
 * mit anderer Semantik weiterzuverwenden.
 *
 * Weil die URI-Zeichenkette Teil des Schlüssels ist, muss dieselbe Datei überall dieselbe
 * URI ergeben. Deshalb reichen sowohl der Aufnahme-Screen als auch die Liste „Aus der App"
 * ihre Dateien durch denselben FileProvider — zwei Schreibweisen derselben Datei wären
 * zwei Cache-Einträge und damit zweimal dieselbe Rechnung.
 */
class GhostArtifactStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val filesDir = context.filesDir
    private val dir = File(filesDir, ORDNER)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun savePoseTrack(track: GhostPoseTrack) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        poseTrackFile(track.videoUri).writeText(json.encodeToString(track))
    }

    /** Gecachte Pose-Spur oder null (nicht vorhanden/nicht mehr lesbar). */
    suspend fun loadPoseTrack(videoUri: String): GhostPoseTrack? =
        loadPoseTrackFromPath(poseTrackPath(videoUri))

    /**
     * Pose-Spur über den Pfad, wie er in der DB steht — für gespeicherte Analysen (M5).
     *
     * Erwartet den **relativen** Pfad aus [poseTrackPath] und löst ihn erst hier gegen den
     * `filesDir` dieses Geräts auf. Ein absoluter Pfad wird noch akzeptiert, damit eine
     * `vorher.db` aus der Zeit vor Schema v7 nicht zum Absturz führt.
     */
    suspend fun loadPoseTrackFromPath(path: String): GhostPoseTrack? =
        withContext(Dispatchers.IO) {
            val file = if (path.startsWith("/")) File(path) else File(filesDir, path)
            if (!file.exists()) return@withContext null
            // Defektes/verändertes Schema → wie "kein Cache" behandeln, neu analysieren.
            runCatching { json.decodeFromString<GhostPoseTrack>(file.readText()) }.getOrNull()
        }

    /**
     * Pfad der (gecachten) Pose-Spur eines Videos, **relativ zu `filesDir`** — genau so
     * landet er in der DB (M5).
     *
     * Absolut wäre er gerätelokal, und eine gerätelokale Spalte in einer verglichenen Zeile
     * macht aus jeder Analyse einen Dauerkonflikt: nach dem Übernehmen trüge dieselbe
     * Analyse auf beiden Geräten einen anderen Wert, und der nächste Abgleich fände
     * „gleiche Nummer, verschiedener Inhalt" — jedes Mal, für immer (Sync-Plan Ablauf 31,
     * E15). Auf zwei Emulatoren fiele das nie auf, weil beide `/data/user/0/…` benutzen.
     */
    fun poseTrackPath(videoUri: String): String = "$ORDNER/${poseTrackFile(videoUri).name}"

    // Abtastrate UND Pipeline-Marker stecken im Cache-Schlüssel: ändert sich
    // GhostTuning.POSE_SAMPLE_FPS oder die Pose-Pipeline (Modell/Filter-Semantik),
    // veralten alte Spuren automatisch statt mit falschen Werten weiterzuleben.
    // "mp-heavy-20" (S8a/b) = die Fußknochen sind Teil der rigiden Rekonstruktion (sie
    // wurden gezeichnet, waren aber als einzige unbeschränkt: Morph 1,4-1,8 % gegen
    // 0,43 %), und die Box-Prüfung greift nur noch bei echter Abweichung ein statt in
    // jedem Prüftakt (Rest-Periodik Faktor 1,8).
    // "mp-heavy-19" (S7b) = die Spur entsteht ausnahmslos auf dem ROI-Crop; die
    // periodische Box-Prüfung läuft mit einem eigenen Landmarker auf einem geweiteten
    // Ausschnitt und geht nicht mehr in die Spur ein. Vorher wechselte jeder 12. Frame
    // aufs 720er-Vollbild — ein Maßstabssprung im Modell-Eingabebild, gemessen als
    // 1-Hz-Störung von 17 % der Körpergröße roh / 3,7 % gefiltert.
    // "mp-heavy-18" (S6a) = die rigide Rekonstruktion ist positions-neutral: sie darf
    // die Form ändern, aber nicht mehr das Rumpfzentrum verschieben.
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
        File(dir, "pose_${sha1("$videoUri@${GhostTuning.POSE_SAMPLE_FPS}@mp-heavy-20")}.json")

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        /** Unterordner in `filesDir`; zugleich das Präfix der relativen Pfade in der DB. */
        const val ORDNER = "ghost"
    }
}
