package com.boulderbuddy.data.camera

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Die Videos, die in der App selbst entstanden sind — `filesDir/aufnahmen`.
 *
 * **Warum es diese Liste braucht:** eine eigene Aufnahme liegt app-intern und taucht in
 * keiner Galerie auf. Der Medien-Picker findet sie also nie wieder. Solange die URI im
 * Bildschirm-Zustand stand, war das kein Problem — verlässt man den Ghost Climber aber vor
 * dem Speichern, stirbt sein ViewModel und mit ihm die einzige Spur zur Datei. Die Aufnahme
 * war danach **da, aber unerreichbar**: Minuten Analyse für nichts, obwohl sowohl das Video
 * als auch die gerechnete Pose-Spur (GhostArtifactStore) noch auf der Platte lagen.
 *
 * Es ist derselbe Ordner, in dem auch der Geräte-Abgleich seine inhaltsadressierten Medien
 * ablegt (`MedienNamen.MEDIEN_ORDNER`) — bewusst, es ist ein Topf. Deshalb wird hier auch
 * nicht nach dem `BB_`-Präfix gefiltert: ein übernommenes Video ist genauso app-intern und
 * genauso unauffindbar wie ein selbst aufgenommenes.
 */
@Singleton
class EigeneAufnahmen @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val ordner = File(context.filesDir, AUFNAHME_ORDNER)

    /**
     * Alle Videos im Aufnahme-Ordner, das neueste zuerst.
     *
     * Sortiert wird nach `lastModified` und nicht nach dem Namen: der Zeitstempel steckt nur
     * in den selbst aufgenommenen Namen (`BB_<ms>.mp4`), die übernommenen heißen nach ihrem
     * Hash. Eine Namenssortierung mischte beide Sorten willkürlich.
     */
    suspend fun videos(): List<EigeneAufnahme> = withContext(Dispatchers.IO) {
        ordner.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in VIDEO_ENDUNGEN }
            .sortedByDescending { it.lastModified() }
            .map {
                EigeneAufnahme(
                    uri = uriFuer(it).toString(),
                    aufgenommenAm = it.lastModified(),
                    groesseBytes = it.length(),
                )
            }
    }

    /**
     * `content://` und nicht `file://` — aus demselben Grund wie in
     * [CameraCaptureController]: nur so beantwortet der ContentResolver den MIME-Typ, und nur
     * so ist die URI dieselbe wie die, die eine frische Aufnahme liefert. Wären es zwei
     * verschiedene Schreibweisen derselben Datei, ginge der Pose-Cache daneben — sein
     * Schlüssel ist die URI-Zeichenkette.
     */
    private fun uriFuer(datei: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        datei,
    )

    private companion object {
        /** Videoendungen, die im Ordner vorkommen können — Spiegel von `MedienNamen`. */
        val VIDEO_ENDUNGEN = setOf("mp4", "3gp", "webm", "mov")
    }
}

/** Eine app-interne Videodatei, so wie die Auswahl sie braucht. */
data class EigeneAufnahme(
    val uri: String,
    /** Zeitpunkt der letzten Änderung — bei einer Aufnahme ihr Ende. */
    val aufgenommenAm: Long,
    val groesseBytes: Long,
)
