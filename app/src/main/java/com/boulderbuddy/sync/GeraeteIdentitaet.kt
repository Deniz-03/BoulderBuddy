package com.boulderbuddy.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
     * Wer welches Band bekommt, folgt aus der **Herkunft des gemeinsamen Standes**: das
     * Gerät, das ihn gerechnet hat, zählt im unteren Fenster weiter, das andere im oberen.
     *
     * Der frühere Weg — die beiden Geräte-IDs vergleichen — funktionierte nur über Nearby.
     * Über den Datei-Weg kennt nur die einlesende Seite beide IDs; die abgebende erfährt die
     * fremde ID nie und blieb ohne Band, fiel also auf 0 zurück. Bei jeder zweiten
     * Gerätepaarung landeten damit **beide** im selben Fenster — genau die ID-Kollision aus
     * Ablauf 7, gegen die die Bänder gebaut wurden.
     *
     * `erzeugtVon` steht dagegen in `stand_meta`, ist auf beiden Geräten gleich (E3) und
     * jedes Gerät kennt seine eigene ID. Beide rechnen also getrennt und kommen zwangsläufig
     * auf verschiedene Bänder — ohne dass irgendetwas übertragen oder gespeichert werden
     * muss.
     *
     * Ein Gerät ohne Herkunft hat noch nie abgeglichen und fängt bei 0 an. Trifft es auf ein
     * zweites in derselben Lage, greift die Erstbegegnung: danach hat genau eines von beiden
     * ein `erzeugtVon`, das nicht seine eigene ID ist.
     */
    fun ausHerkunft(erzeugtVon: String?, meineId: String): Int =
        if (erzeugtVon == null || erzeugtVon == meineId) 0 else 1
}

/** Was dieses Gerät über sich selbst weiß — nie Teil des abgeglichenen Standes. */
data class Identitaet(
    /** Dauerhafte, zufällige ID dieses Geräts. Entscheidet Rollen und Band. */
    val geraeteId: String,
    /** Geräte-ID der Gegenstelle des letzten Abgleichs; `null` = noch keine. */
    val partnerId: String?,
    /**
     * Hinweis fürs UI („es gibt etwas abzugleichen"), **nie Entscheidungsgrundlage**
     * (Ablauf 35). Rooms `InvalidationTracker` meldet auch die Schreibvorgänge des
     * Abgleichs selbst und tut das asynchron — der Schalter kann also schlicht falsch
     * stehen. Was wirklich geändert wurde, sagt allein der Vergleich mit `basis.db`.
     */
    val geaendertSeitAbgleich: Boolean,
)

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
    @param:GeraeteStore private val dataStore: DataStore<Preferences>,
) {

    val identitaet: Flow<Identitaet> = dataStore.data.map { prefs ->
        Identitaet(
            geraeteId = prefs[KEY_GERAETE_ID].orEmpty(),
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

    /**
     * Hält fest, mit wem zuletzt abgeglichen wurde. **Ohne Band** — das leitet sich aus der
     * Herkunft ab ([NummernBand.ausHerkunft]) und wird deshalb nirgends gespeichert, wo es
     * veralten könnte.
     */
    suspend fun koppele(partnerId: String) {
        dataStore.edit { prefs -> prefs[KEY_PARTNER_ID] = partnerId }
    }

    /**
     * Verwirft die Kopplung: das Gerät gilt wieder als „noch nie abgeglichen" (Ablauf 36).
     * Die Geräte-ID bleibt — sie ist die Identität, nicht die Beziehung.
     *
     * **Diese Funktion wird zurzeit von niemandem gerufen — sie ist keine Leiche, sondern
     * unverdrahtet.** `Abgleicher.machRueckgaengig()` setzt heute nur die Datentabellen
     * zurueck und laesst Herkunft und Kopplung stehen; die Ausnahme fuer die Erstbegegnung,
     * die der SYNC_PLAN unter Ablauf 36 verlangt, fehlt dort noch. Wer sie einbaut, ruft
     * diese Funktion und `AbgleichDateien.verwirfKopplung()` von dort aus auf.
     */
    suspend fun loeseKopplung() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PARTNER_ID)
        }
    }

    suspend fun setzeGeaendert(geaendert: Boolean) {
        dataStore.edit { it[KEY_GEAENDERT] = geaendert }
    }

    private companion object {
        val KEY_GERAETE_ID = stringPreferencesKey("geraete_id")
        val KEY_PARTNER_ID = stringPreferencesKey("partner_geraete_id")
        val KEY_GEAENDERT = booleanPreferencesKey("geaendert_seit_abgleich")
    }
}
