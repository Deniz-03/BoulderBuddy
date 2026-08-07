package com.boulderbuddy.sync

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der Stand als Datei über SAF (Sync-Plan S8) — der Zweitweg neben Nearby.
 *
 * Gedacht war er für „die Geräte finden sich partout nicht", Datensicherung und
 * Gerätewechsel. Seit E14 die Medien aus der Android-Sicherung ausschließt, ist er
 * zusätzlich der einzige Weg, den eigenen Stand überhaupt aus dem Gerät herauszubekommen.
 *
 * Der Abgleich selbst ist derselbe wie über Nearby — nur der Transport ist ein anderer.
 * Ein Unterschied bleibt: über die Datei läuft er zwangsläufig in **zwei Durchgängen**, weil
 * das abgebende Gerät nicht erfährt, was das einlesende gerechnet hat. Nach dem Einlesen
 * gibt also das andere Gerät ab, und dann sind beide gleich. Die Lagebestimmung ist darauf
 * vorbereitet ([Lage.GegenseiteWeiter]).
 */
@Singleton
class StandDatei @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dateien: AbgleichDateien,
) {

    /**
     * Schreibt den eigenen Stand in ein vom Nutzer gewähltes Dokument.
     *
     * Kopiert wird eine **frisch eingecheckpointete** Kopie, nicht die laufende Datei: ohne
     * `wal_checkpoint(FULL)` stünde das meiste noch im WAL und die Datei wäre praktisch leer
     * (siehe [AbgleichDateien]).
     */
    suspend fun exportiere(ziel: Uri): Long = withContext(Dispatchers.IO) {
        val kopie = dateien.kopiereStand(File(dateien.empfangsOrdner(), "abgabe.db"))
        try {
            context.contentResolver.openOutputStream(ziel, "wt")?.use { aus ->
                kopie.inputStream().use { ein -> ein.copyTo(aus) }
            } ?: error("Das gewählte Ziel lässt sich nicht beschreiben.")
            kopie.length()
        } finally {
            kopie.delete()
        }
    }

    /**
     * Holt ein gewähltes Dokument in den Empfangsordner.
     *
     * Bewusst erst kopieren, statt direkt aus der SAF-URI zu lesen: SQLite braucht einen
     * echten Dateipfad, und der Stand wird mehrfach gelesen (Schema, Herkunft, Zeilen).
     */
    suspend fun importiere(quelle: Uri): File = withContext(Dispatchers.IO) {
        val ziel = File(dateien.empfangsOrdner(), "empfangen.db")
        context.contentResolver.openInputStream(quelle)?.use { ein ->
            ziel.outputStream().use { aus -> ein.copyTo(aus) }
        } ?: error("Die gewählte Datei lässt sich nicht lesen.")
        ziel
    }

    /** Vorschlag für den Dateinamen beim Abgeben — datiert, damit zwei Abgaben unterscheidbar sind. */
    fun abgabeName(zeitstempelMs: Long): String {
        val tag = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY)
            .format(java.util.Date(zeitstempelMs))
        return "BoulderBuddy-Stand-$tag.db"
    }
}
