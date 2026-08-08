package com.boulderbuddy.sync

import java.util.Locale

/**
 * Namensregeln der inhaltsadressierten Medien (Sync-Plan E5).
 *
 * **Der Name ist der Hash.** Daraus folgt alles Weitere: zwei Geräte, die dieselbe Datei
 * haben, nennen sie gleich; der Abgleich muss nur Namenslisten tauschen und überträgt, was
 * fehlt; und nach dem einmaligen Umzug wird nie wieder gehasht — nur neue Aufnahmen einmal
 * beim Speichern.
 *
 * Der Umzug läuft auf beiden Geräten **getrennt** (Ablauf 18). Dass dabei zweimal dasselbe
 * herauskommt, hängt allein daran, dass der Name aus dem Inhalt folgt und aus sonst nichts —
 * kein Zeitstempel, kein Zähler, keine Reihenfolge. Sonst wäre der erste Abgleich ein
 * Konflikt auf jeder Zeile mit Foto.
 */

/** Unterordner in `filesDir`. Identisch mit dem Aufnahme-Ordner — es ist derselbe Topf. */
const val MEDIEN_ORDNER = "aufnahmen"

private val HEX = Regex("[0-9a-f]{64}")

private val ENDUNG_ZU_MIME = mapOf(
    "image/jpeg" to "jpg",
    "image/jpg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
    "image/heic" to "heic",
    "image/heif" to "heif",
    "video/mp4" to "mp4",
    "video/3gpp" to "3gp",
    "video/webm" to "webm",
    "video/quicktime" to "mov",
)

private val ERLAUBTE_ENDUNGEN = ENDUNG_ZU_MIME.values.toSet()

/** `<sha256>.<endung>` — mehr steckt nicht drin, und das ist der Punkt. */
fun medienDateiname(sha256: String, endung: String): String = "$sha256.$endung"

/**
 * Erkennt einen bereits inhaltsadressierten Namen. Der Umzug überspringt solche Dateien und
 * lässt sich damit gefahrlos wiederholen — er muss abbruchfest sein, weil er über alle
 * Medien läuft und das dauern kann.
 */
fun istInhaltsadressiert(dateiname: String): Boolean {
    val punkt = dateiname.lastIndexOf('.')
    if (punkt <= 0) return false
    return HEX.matches(dateiname.take(punkt)) &&
        dateiname.substring(punkt + 1) in ERLAUBTE_ENDUNGEN
}

/**
 * Endung aus MIME-Typ, ersatzweise aus dem Quellnamen.
 *
 * Der MIME-Typ steht vorn, weil er die verlässlichere Auskunft ist: eine Galerie-URI hat oft
 * gar keinen Namen, und ein `.jpeg` gegen ein `.jpg` ergäbe für dieselbe Datei zwei Namen.
 */
fun endungFuer(mimeTyp: String?, quellname: String?): String {
    ENDUNG_ZU_MIME[mimeTyp?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim()]
        ?.let { return it }

    val ausName = quellname?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    if (!ausName.isNullOrEmpty()) {
        // "jpeg" auf "jpg" ziehen, damit dieselbe Datei nicht zwei Namen bekommen kann.
        val vereinheitlicht = if (ausName == "jpeg") "jpg" else ausName
        if (vereinheitlicht in ERLAUBTE_ENDUNGEN) return vereinheitlicht
    }
    // Unbekannt: die Datei reist trotzdem mit, sie ist nur nicht als Bild/Video erkennbar.
    return "bin"
}
