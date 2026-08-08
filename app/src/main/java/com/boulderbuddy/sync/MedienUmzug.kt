package com.boulderbuddy.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.boulderbuddy.data.db.dao.MedienDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Was der Umzug bewegt hat — für Protokoll und Fortschrittsanzeige. */
data class UmzugsErgebnis(
    val geprueft: Int,
    val umgezogen: Int,
    /** Medien, die nicht mehr lesbar waren (gelöschte Galerie-Datei, abgelaufene Freigabe). */
    val unlesbar: Int,
)

/**
 * Einmaliger Umzug aller Medien auf inhaltsadressierte Namen (Sync-Plan S3, E5).
 *
 * Danach gehören alle Medien der App: auch Galerie-Videos werden kopiert, denn eine
 * `content://media/…`-URI ist gerätegebunden und nach der Übertragung ein toter Verweis.
 *
 * Der Umzug läuft auf beiden Geräten **getrennt**. Dass dabei beidseitig dasselbe
 * herauskommt, hängt allein daran, dass der Name aus dem Inhalt folgt (Ablauf 18) — sonst
 * wäre der erste Abgleich ein Konflikt auf jeder Zeile mit Foto.
 *
 * Er ist abbruchfest und wiederholbar: jede Zeile wird einzeln umgeschrieben, und bereits
 * umgezogene Medien erkennt [istInhaltsadressiert] wieder.
 *
 * **Was der Umzug bewusst nicht tut:** die gecachten Pose-Spuren mitbenennen. Deren
 * Cache-Schlüssel enthält die Video-URI, nach dem Umzug greift der Cache also nicht mehr —
 * eine erneute Analyse extrahiert einmal neu (Minuten pro Video). Die *gespeicherten*
 * Analysen sind davon nicht betroffen: ihre Keypoint-Pfade stehen ausdrücklich in der Zeile.
 * Die Datei mitzubenennen wäre falsch, weil im Cache-Namen auch der Pipeline-Marker steckt —
 * eine alte Spur unter neuem Namen sähe aus wie eine frische.
 */
@Singleton
class MedienUmzug @Inject constructor(
    private val medienDao: MedienDao,
    private val speicher: MedienSpeicher,
    @GeraeteStore private val dataStore: DataStore<Preferences>,
) {

    /** Läuft nur beim ersten Mal durch; danach ist der Aufruf ein Flag-Lesen. */
    suspend fun stelleSicher(): UmzugsErgebnis? {
        if (dataStore.data.first()[KEY_ERLEDIGT] == true) return null
        val ergebnis = fuehreAus()
        // Erst nach dem Durchlauf merken. Bricht er ab, läuft er beim nächsten Mal erneut —
        // die schon umgezogenen Medien überspringt er dann.
        dataStore.edit { it[KEY_ERLEDIGT] = true }
        return ergebnis
    }

    suspend fun fuehreAus(): UmzugsErgebnis {
        var geprueft = 0
        var umgezogen = 0
        var unlesbar = 0

        for (route in medienDao.routenMitMedien()) {
            geprueft++
            when (val neu = speicher.uebernehme(route.mediaUri)) {
                null -> unlesbar++
                route.mediaUri -> Unit // lag schon inhaltsadressiert vor
                else -> {
                    medienDao.setzeRoutenMedium(route.id, neu)
                    umgezogen++
                }
            }
        }

        for (analyse in medienDao.ghostMedien()) {
            geprueft += 2
            val neuRef = speicher.uebernehme(analyse.refMediaUri)
            val neuCmp = speicher.uebernehme(analyse.cmpMediaUri)
            if (neuRef == null) unlesbar++
            if (neuCmp == null) unlesbar++

            // Eine Analyse hängt an zwei Videos. Ist eins nicht mehr lesbar, bleibt sein
            // alter Verweis stehen — die Zeile halb umzuschreiben ist besser, als sie ganz
            // zu verlieren, und die zweite Hälfte kann später noch gelingen.
            val ref = neuRef ?: analyse.refMediaUri
            val cmp = neuCmp ?: analyse.cmpMediaUri
            if (ref != analyse.refMediaUri || cmp != analyse.cmpMediaUri) {
                medienDao.setzeGhostMedien(analyse.id, ref, cmp)
                if (ref != analyse.refMediaUri) umgezogen++
                if (cmp != analyse.cmpMediaUri) umgezogen++
            }
        }

        return UmzugsErgebnis(geprueft, umgezogen, unlesbar)
    }

    private companion object {
        val KEY_ERLEDIGT = booleanPreferencesKey("medien_umzug_erledigt")
    }
}
