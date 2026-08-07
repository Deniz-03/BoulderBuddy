package com.boulderbuddy.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nummernbänder je Gerät (Sync-Plan E8) — **abweichend vom Plan als gleitende Fenster.**
 *
 * Das Problem, das gelöst werden muss, steht so im Plan: alle Primärschlüssel sind
 * `autoGenerate`-`Int`, beide Geräte zählen nach einem Abgleich ab demselben Stand weiter
 * und vergeben **dieselbe Nummer für verschiedene neue Zeilen** (Ablauf 7).
 *
 * Der Lösungsvorschlag des Plans — feste Bänder (Gerät 1 ab 1, Gerät 2 ab einer Milliarde)
 * und `sqlite_sequence` nach jedem Abgleich auf den eigenen Bandanfang zurücksetzen —
 * **wirkt nicht.** SQLite vergibt bei `AUTOINCREMENT` nicht `sqlite_sequence + 1`, sondern
 * `max(sqlite_sequence, größte id in der Tabelle) + 1`. Nach einem Abgleich liegen die
 * übernommenen Zeilen des anderen Geräts aber physisch in der Tabelle. Das Gerät im unteren
 * Band setzte seine Sequenz brav auf 1 zurück — und bekäme trotzdem als nächste Nummer
 * `größte fremde id + 1`, also mitten im fremden Band. Beide Geräte vergäben ab dem nächsten
 * Boulder dieselben Nummern; genau der Fehler, den E8 verhindern sollte.
 *
 * Stattdessen: Fenster **über** dem gemeinsamen Höchstwert. Nach jedem Abgleich setzt Gerät
 * mit Band 0 die Sequenz auf die höchste vorhandene Nummer, Gerät mit Band 1 auf höchste
 * Nummer + [FENSTER]. Beide zählen von dort aufwärts, in getrennten Bereichen, und beide
 * Bereiche liegen über allem, was schon existiert — also greift die `max(...)`-Regel nicht
 * mehr gegen sie.
 *
 * Überschneiden könnte sich das nur, wenn ein Gerät zwischen zwei Abgleichen mehr als
 * [FENSTER] Zeilen anlegt. Passiert das doch, gibt es keinen stillen Datenverlust: der
 * Vergleich erkennt „gleiche Nummer, verschiedene Zeilen" und fragt nach
 * ([KonfliktArt.GLEICHE_NUMMER]).
 */
object NummernBand {
    /**
     * Wie viele Nummern jedem Gerät zwischen zwei Abgleichen zur Verfügung stehen.
     *
     * Eine Million ist reichlich für eine App, in der ein voller Klettertag ein paar Dutzend
     * Zeilen erzeugt — und klein genug, dass der `Int`-Bereich für Tausende von Abgleichen
     * reicht.
     */
    const val FENSTER = 1_000_000

    const val ANZAHL = 2

    /** Wie weit über dem gemeinsamen Höchstwert dieses Gerät weiterzählt. */
    fun versatz(band: Int): Int = band * FENSTER

    /**
     * Wer welches Band bekommt, folgt allein aus den beiden Geräte-IDs: die kleinere ID
     * bekommt Band 0. Beide Geräte rechnen das getrennt aus und kommen aufs selbe Ergebnis
     * — deshalb muss die Vergabe nirgends übertragen und nirgends in der DB festgehalten
     * werden (siehe [com.boulderbuddy.data.db.entity.StandMetaEntity]).
     */
    fun bandFuer(eigeneId: String, fremdeId: String): Int =
        if (eigeneId < fremdeId) 0 else 1
}

/** Was dieses Gerät über sich selbst weiß — nie Teil des abgeglichenen Standes. */
data class Identitaet(
    /** Dauerhafte, zufällige ID dieses Geräts. Entscheidet Rollen und Band. */
    val geraeteId: String,
    /** `null` = noch nie abgeglichen. Sonst 0 oder 1 (siehe [NummernBand]). */
    val band: Int?,
    /** Geräte-ID der Gegenstelle des letzten Abgleichs; `null` = noch keine. */
    val partnerId: String?,
    /**
     * Hinweis fürs UI („es gibt etwas abzugleichen"), **nie Entscheidungsgrundlage**
     * (Ablauf 35). Rooms `InvalidationTracker` meldet auch die Schreibvorgänge des
     * Abgleichs selbst und tut das asynchron — der Schalter kann also schlicht falsch
     * stehen. Was wirklich geändert wurde, sagt allein der Vergleich mit `basis.db`.
     */
    val geaendertSeitAbgleich: Boolean,
) {
    val hatAbgeglichen: Boolean get() = band != null
}

/**
 * Geräte-ID und Nummernband, in einem **eigenen** DataStore.
 *
 * Der eigene Speicher ist kein Ordnungssinn, sondern Voraussetzung für E14: Android-
 * Backup-Regeln schließen Dateien aus, keine einzelnen Schlüssel. Läge die Geräte-ID in
 * `settings.preferences_pb`, ließe sie sich nicht ausschließen, ohne alle Einstellungen
 * mit auszuschließen — und ein wiederhergestelltes Backup ergäbe zwei Geräte mit derselben
 * ID im selben Band, deren Kollisionen nicht einmal auffielen (Ablauf 26).
 */
@Singleton
class GeraeteIdentitaet @Inject constructor(
    @GeraeteStore private val dataStore: DataStore<Preferences>,
) {

    val identitaet: Flow<Identitaet> = dataStore.data.map { prefs ->
        Identitaet(
            geraeteId = prefs[KEY_GERAETE_ID].orEmpty(),
            band = prefs[KEY_BAND],
            partnerId = prefs[KEY_PARTNER_ID],
            geaendertSeitAbgleich = prefs[KEY_GEAENDERT] ?: false,
        )
    }

    /**
     * Liefert die Geräte-ID und legt sie beim allerersten Aufruf an.
     *
     * Bewusst zufällig statt aus `ANDROID_ID` oder der Hardware abgeleitet: die soll die App
     * gar nicht erst lesen, und für zwei Geräte im selben Raum reicht Zufall.
     */
    suspend fun eigeneId(): String {
        val vorhanden = dataStore.data.first()[KEY_GERAETE_ID]
        if (!vorhanden.isNullOrEmpty()) return vorhanden
        // edit() ist atomar — bei zwei gleichzeitigen Aufrufen gewinnt der erste.
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[KEY_GERAETE_ID].orEmpty().ifEmpty { UUID.randomUUID().toString() }
            prefs[KEY_GERAETE_ID] = id
        }
        return id
    }

    /** Hält nach dem ersten Abgleich fest, mit wem und in welchem Band. */
    suspend fun koppele(partnerId: String, band: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_PARTNER_ID] = partnerId
            prefs[KEY_BAND] = band
        }
    }

    /**
     * Verwirft die Kopplung: das Gerät gilt wieder als „noch nie abgeglichen" (Ablauf 36).
     * Die Geräte-ID bleibt — sie ist die Identität, nicht die Beziehung.
     */
    suspend fun loeseKopplung() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PARTNER_ID)
            prefs.remove(KEY_BAND)
        }
    }

    suspend fun setzeGeaendert(geaendert: Boolean) {
        dataStore.edit { it[KEY_GEAENDERT] = geaendert }
    }

    private companion object {
        val KEY_GERAETE_ID = stringPreferencesKey("geraete_id")
        val KEY_BAND = intPreferencesKey("nummern_band")
        val KEY_PARTNER_ID = stringPreferencesKey("partner_geraete_id")
        val KEY_GEAENDERT = booleanPreferencesKey("geaendert_seit_abgleich")
    }
}
