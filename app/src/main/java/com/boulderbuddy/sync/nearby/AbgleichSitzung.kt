package com.boulderbuddy.sync.nearby

import com.boulderbuddy.sync.AbgleichDateien
import com.boulderbuddy.sync.Abgleicher
import com.boulderbuddy.sync.Abgleichvorschlag
import com.boulderbuddy.sync.Bestandszahlen
import com.boulderbuddy.sync.Bilanz
import com.boulderbuddy.sync.GeraeteIdentitaet
import com.boulderbuddy.sync.Konflikt
import com.boulderbuddy.sync.MedienSpeicher
import com.boulderbuddy.sync.MedienUmzug
import com.boulderbuddy.sync.Seite
import com.boulderbuddy.sync.anwenden
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================================
// Nearby-Abgleich, Sitzungsablauf (Sync-Plan S4/S5)
// =============================================================================
//
// Die Datei hat zwei Hälften: oben [Sitzungsstand], die Kurzfassung fürs Auge des Nutzers,
// darunter [AbgleichSitzung] mit dem Ablauf selbst — bewusst linear von oben nach unten
// gelesen statt als Zustandsautomat.
//
// **Das ist der Teil des Abgleichs ohne automatisierte Absicherung.** Alles, was ohne Funk
// prüfbar ist, liegt woanders und ist es auch: der Vergleich in `Abgleich.kt` (reine
// Funktionen, JVM-Tests), der Gesprächsablauf in `Protokoll.kt` (ebenfalls android-frei),
// der Datei-Weg in `StandDatei.kt`. Hier bleibt der Rest — zwei Geräte, ein Raum, von Hand.
// Die Reihenfolge, in der man das prüft, steht im SYNC_PLAN unter „S0 zuerst und allein".
//
// Wer hier etwas ändert, ändert also den einzigen Teil, den kein Test auffängt.

/** Woran der Abgleich gerade ist — das, was der Bildschirm zeigt. */
sealed interface Sitzungsstand {
    data object Untaetig : Sitzungsstand

    /** Werben und suchen. Beide Geräte müssen den Screen offen haben. */
    data object Suche : Sitzungsstand

    /**
     * Beide Geräte zeigen dieselbe vierstellige Zahl. Stimmen sie nicht überein, ist es
     * nicht das richtige Gerät — der einzige Schutz davor, den eigenen Stand versehentlich
     * einem fremden Tablet zu geben.
     */
    data class Bestaetigen(val name: String, val zahl: String) : Sitzungsstand

    data class Laeuft(val was: String, val anteil: Float? = null) : Sitzungsstand

    data class KonfliktFrage(val konflikte: List<Konflikt>) : Sitzungsstand

    data class ErstbegegnungFrage(
        val meine: Bestandszahlen,
        val fremde: Bestandszahlen,
    ) : Sitzungsstand

    data class Fertig(val bilanz: Bilanz, val neustartNoetig: Boolean = false) : Sitzungsstand

    data class Abgebrochen(val grund: String) : Sitzungsstand
}

/**
 * Ein Abgleich über Nearby, von Anfang bis Ende (Sync-Plan S4/S5).
 *
 * Bewusst als **linearer** Ablauf geschrieben und nicht als Zustandsautomat: der Funkweg ist
 * der einzige Teil dieses Features, der sich ohne zwei echte Geräte nicht prüfen lässt. Wenn
 * ich ihn nicht testen kann, soll er wenigstens von oben nach unten lesbar sein.
 *
 * Die Reihenfolge folgt dem Plan: Vorprüfungen → Hash-Listen → fehlende Medien → **zuletzt**
 * der Stand. Zuletzt deshalb, weil ein Abbruch davor folgenlos ist: herumliegende Medien
 * kosten Platz, ein halb angewendeter Stand kostet Daten.
 */
@Singleton
class AbgleichSitzung @Inject constructor(
    private val verbindung: NearbyVerbindung,
    private val abgleicher: Abgleicher,
    private val dateien: AbgleichDateien,
    private val identitaet: GeraeteIdentitaet,
    private val medien: MedienSpeicher,
    private val medienUmzug: MedienUmzug,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _stand = MutableStateFlow<Sitzungsstand>(Sitzungsstand.Untaetig)
    val stand: StateFlow<Sitzungsstand> = _stand.asStateFlow()

    private var bestaetigung: CompletableDeferred<Boolean>? = null
    private var konfliktAntwort: CompletableDeferred<Seite>? = null
    private var erstbegegnungAntwort: CompletableDeferred<Boolean>? = null

    /**
     * Buchführung über Dateien. Ankündigung (BYTES) und Datei (FILE) kommen als getrennte
     * Payloads und in beliebiger Reihenfolge an — erst beides zusammen ergibt „Datei X der
     * Art Y ist da".
     */
    private val ankuendigungen = mutableMapOf<Long, Nachricht.DateiFolgt>()
    private val angekommene = mutableMapOf<Long, File>()

    fun bestaetigeVerbindung(ja: Boolean) {
        bestaetigung?.complete(ja)
    }

    fun beantworteKonflikt(wahl: Seite) {
        konfliktAntwort?.complete(wahl)
    }

    /** @param fremderGewinnt true = der Stand des anderen Geräts wird übernommen. */
    fun beantworteErstbegegnung(fremderGewinnt: Boolean) {
        erstbegegnungAntwort?.complete(fremderGewinnt)
    }

    fun brichAb() {
        beende("Abgebrochen.")
    }

    /**
     * Führt einen kompletten Abgleich durch.
     *
     * @param hatGedrueckt ob der Nutzer auf **diesem** Gerät gestartet hat. Bestimmt
     *   zusammen mit der Geräte-ID, wer rechnet (E12).
     */
    suspend fun fuehre(hatGedrueckt: Boolean, anzeigename: String) {
        // Zustand zurücksetzen, BEVOR irgendjemand mitliest: das Ergebnis des letzten Laufs
        // steht hier noch, und der Foreground Service liest den Zustand mit, um sich am Ende
        // zu beenden. Bliebe „fertig" stehen, hörte er sofort wieder auf — der zweite
        // Abgleich käme nie zustande.
        _stand.value = Sitzungsstand.Untaetig
        ankuendigungen.clear()
        angekommene.clear()

        try {
            // Vor allem anderen: alle Medien gehören der App und heißen nach ihrem Inhalt.
            // Sonst tauschen die Geräte Namenslisten aus, die nichts bedeuten (E5).
            _stand.value = Sitzungsstand.Laeuft("Medien werden vorbereitet …")
            medienUmzug.stelleSicher()

            _stand.value = Sitzungsstand.Suche
            verbindung.starte(anzeigename)

            val endpunkt = verbindeMitBestaetigung()

            val meinHallo = abgleicher.beschreibeMich(hatGedrueckt, identitaet.eigeneId())
            verbindung.sende(endpunkt, meinHallo)
            _stand.value = Sitzungsstand.Laeuft("Geräte gleichen ab …")
            val fremdesHallo = warteAufNachricht<Nachricht.Hallo>()

            when (val ergebnis = pruefeHandschlag(meinHallo, fremdesHallo)) {
                is Handschlag.Abbruch -> {
                    // Die Gegenseite kommt zum selben Ergebnis, aber die Nachricht spart ihr
                    // das Warten auf einen Abbruch, der nie kommt.
                    verbindung.sende(endpunkt, Nachricht.Abbruch(ergebnis.grund))
                    beende(ergebnis.grund)
                }

                is Handschlag.Bereit -> tauscheAus(endpunkt, ergebnis.ichRechne, fremdesHallo)
            }
        } catch (e: CancellationException) {
            // Der Abbrecher hat den Grund schon gesetzt ([brichAb] schreibt „Abgebrochen.").
            // Ihn hier mit `e.message` zu überschreiben hieße, dem Nutzer „Job was cancelled"
            // hinzuschreiben — am Gerät genau so gemessen (09.08.2026). Weiterreichen statt
            // behandeln: eine Absage ist keine Störung, und strukturell bleibt sie damit eine.
            throw e
        } catch (e: Exception) {
            beende(e.message ?: "Der Abgleich ist fehlgeschlagen.")
        } finally {
            verbindung.beende()
            dateien.raeumeEmpfangenesAuf()
        }
    }

    /** Warten, bis der Nutzer die vierstellige Zahl bestätigt hat und die Verbindung steht. */
    private suspend fun verbindeMitBestaetigung(): String {
        while (true) {
            val ereignis = warteBis(SUCHE_ZEITLIMIT) {
                it is Funkereignis.BestaetigungNoetig || it is Funkereignis.Verbunden
            }
            when (ereignis) {
                is Funkereignis.BestaetigungNoetig -> {
                    val antwort = CompletableDeferred<Boolean>()
                    bestaetigung = antwort
                    _stand.value = Sitzungsstand.Bestaetigen(ereignis.name, ereignis.zahl)
                    if (antwort.await()) {
                        verbindung.bestaetige(ereignis.endpunkt)
                        _stand.value = Sitzungsstand.Laeuft("Verbindung wird aufgebaut …")
                    } else {
                        verbindung.lehneAb(ereignis.endpunkt)
                        error("Die Verbindung wurde abgelehnt.")
                    }
                }

                is Funkereignis.Verbunden -> return ereignis.endpunkt
                else -> Unit
            }
        }
    }

    /**
     * Der eigentliche Austausch. Beide Seiten laufen hier durch, tun aber Verschiedenes —
     * **nur ein Gerät rechnet** und schickt der Gegenseite das Ergebnis, nicht die Aufgabe
     * (E12, Ablauf 17).
     */
    private suspend fun tauscheAus(
        endpunkt: String,
        ichRechne: Boolean,
        fremdesHallo: Nachricht.Hallo,
    ) {
        // 1. Hash-Listen tauschen. Beide schicken, beide warten.
        val meineMedien = medien.vorhandeneNamen()
        verbindung.sende(endpunkt, Nachricht.Medienliste(meineMedien.toList()))
        val fremdeMedien = warteAufNachricht<Nachricht.Medienliste>().namen

        // 2. Fehlende Medien schicken, dann „fertig" — auch wenn keins zu schicken war.
        val zuSchicken = fehlendeMedien(meineMedien, fremdeMedien)
        zuSchicken.forEachIndexed { i, name ->
            _stand.value = Sitzungsstand.Laeuft(
                "Videos und Fotos werden übertragen …",
                anteil = (i + 1f) / zuSchicken.size,
            )
            val datei = medien.datei(name)
            // Eine inzwischen gelöschte Datei lässt den Abgleich durchlaufen, statt ihn
            // anzuhalten: der Empfänger zählt nichts mit, er wartet auf das „fertig".
            if (datei.exists()) verbindung.sendeDatei(endpunkt, datei, DateiArt.MEDIUM, name)
        }
        verbindung.sende(endpunkt, Nachricht.Fertig)

        // 3. Auf das „fertig" der Gegenseite warten. Was bis dahin an Medien eintrifft,
        //    landet nebenbei in der Buchführung — gezählt wird bewusst nichts.
        _stand.value = Sitzungsstand.Laeuft("Videos und Fotos werden empfangen …")
        warteAufNachricht<Nachricht.Fertig>()

        // 4. Zuletzt der Stand.
        _stand.value = Sitzungsstand.Laeuft("Stand wird abgeglichen …")
        if (ichRechne) {
            val fremdeDatei = holeDatei(DateiArt.STAND)
            rechneUndVerteile(endpunkt, fremdeDatei, fremdesHallo)
        } else {
            val eigene = dateien.kopiereStand(File(dateien.empfangsOrdner(), "abgabe.db"))
            verbindung.sendeDatei(endpunkt, eigene, DateiArt.STAND)
            wendeFremdesErgebnisAn(endpunkt, fremdesHallo)
        }
    }

    /**
     * Das rechnende Gerät: vergleichen, gegebenenfalls fragen, anwenden — und der Gegenseite
     * das Ergebnis schicken.
     */
    private suspend fun rechneUndVerteile(
        endpunkt: String,
        fremdeDatei: File,
        fremdesHallo: Nachricht.Hallo,
    ) {
        when (val vorschlag = abgleicher.pruefe(fremdeDatei)) {
            is Abgleichvorschlag.Abgelehnt -> {
                verbindung.sende(endpunkt, Nachricht.Abbruch(vorschlag.grund))
                beende(vorschlag.grund)
            }

            is Abgleichvorschlag.NichtsZuTun -> {
                verbindung.sende(endpunkt, Nachricht.Fertig)
                _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS)
            }

            is Abgleichvorschlag.Erstbegegnung -> {
                val antwort = CompletableDeferred<Boolean>()
                erstbegegnungAntwort = antwort
                _stand.value = Sitzungsstand.ErstbegegnungFrage(vorschlag.meine, vorschlag.fremde)
                val fremderGewinnt = antwort.await()

                verbindung.sende(
                    endpunkt,
                    Nachricht.ErstbegegnungEntschieden(meinStandGewinnt = !fremderGewinnt),
                )
                if (fremderGewinnt) {
                    // Der fremde Stand gilt. Die Herkunft trägt die ID der GEGENSEITE — nur
                    // so bekommen beide Geräte verschiedene Nummernfenster (E8, korrigiert).
                    abgleicher.uebernimmGanz(vorschlag, herkunftVon = fremdesHallo.geraeteId)
                    _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS, neustartNoetig = true)
                } else {
                    val eigene = dateien.kopiereStand(
                        File(dateien.empfangsOrdner(), "abgabe.db"),
                    )
                    verbindung.sendeDatei(endpunkt, eigene, DateiArt.STAND)
                    abgleicher.merkeErstbegegnungGewonnen(fremdesHallo.geraeteId)
                    // Warten, bis drüben angewendet ist — sonst reißt der Abbau der
                    // Verbindung die Übertragung mittendrin ab.
                    warteAufNachricht<Nachricht.Fertig>()
                    _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS)
                }
            }

            is Abgleichvorschlag.Zusammenfuehren -> {
                val wahl = if (vorschlag.konflikte.isEmpty()) {
                    Seite.MEINS
                } else {
                    val antwort = CompletableDeferred<Seite>()
                    konfliktAntwort = antwort
                    _stand.value = Sitzungsstand.KonfliktFrage(vorschlag.konflikte)
                    antwort.await()
                }

                _stand.value = Sitzungsstand.Laeuft("Wird angewendet …")
                val anweisungen = anwenden(vorschlag.plan, wahl)
                val neueMeta = vorschlag.neueMeta

                // Erst der Gegenseite ihr Ergebnis schicken, dann selbst anwenden: bricht es
                // dazwischen ab, hat die Gegenseite alles und dieses Gerät merkt es beim
                // nächsten Mal über die Basis. Andersherum stünde sie ohne da.
                val paket = Anweisungspaket(
                    operationen = anweisungen.fuerDieGegenseite,
                    generation = neueMeta.generation,
                    erzeugtVon = neueMeta.erzeugtVon,
                    basiertAuf = neueMeta.basiertAuf,
                )
                val datei = File(dateien.empfangsOrdner(), "anweisungen.json")
                datei.writeText(json.encodeToString(paket))
                verbindung.sendeDatei(endpunkt, datei, DateiArt.ANWEISUNGEN)

                val bilanz = abgleicher.fuehreZusammen(vorschlag, wahl)

                // Erst abbauen, wenn drüben bestätigt ist. `beende()` ruft stopAllEndpoints
                // und löscht den Empfangsordner — beides würde eine noch laufende
                // Übertragung abschneiden.
                warteAufNachricht<Nachricht.Fertig>()
                _stand.value = Sitzungsstand.Fertig(bilanz)
            }
        }
    }

    /** Das andere Gerät: das fertige Ergebnis entgegennehmen und anwenden. */
    private suspend fun wendeFremdesErgebnisAn(endpunkt: String, fremdesHallo: Nachricht.Hallo) {
        // Bewusst über die Buchführung geprüft und nicht über das gerade eingetroffene
        // Ereignis: Ankündigung und Datei kommen als getrennte Payloads und in beliebiger
        // Reihenfolge. Käme die Datei zuerst, wüsste man bei ihrem Eintreffen noch nicht,
        // was sie ist — und bei der späteren Ankündigung läge sie schon zu lange zurück.
        val ereignis = warteBis(UEBERTRAGUNG_ZEITLIMIT) { e ->
            (e is Funkereignis.NachrichtDa &&
                (e.nachricht is Nachricht.Fertig ||
                    e.nachricht is Nachricht.ErstbegegnungEntschieden)) ||
                fertigeDatei(DateiArt.ANWEISUNGEN) != null
        }
        val anweisungsDatei = fertigeDatei(DateiArt.ANWEISUNGEN)

        when {
            ereignis is Funkereignis.NachrichtDa && ereignis.nachricht is Nachricht.Fertig ->
                _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS)

            ereignis is Funkereignis.NachrichtDa &&
                ereignis.nachricht is Nachricht.ErstbegegnungEntschieden -> {
                val entscheidung = ereignis.nachricht as Nachricht.ErstbegegnungEntschieden
                if (entscheidung.meinStandGewinnt) {
                    // Der Stand der Gegenseite gilt — ihre Datei kommt gleich.
                    val datei = holeDatei(DateiArt.STAND)
                    val vorschlag = abgleicher.pruefe(datei)
                    if (vorschlag is Abgleichvorschlag.Erstbegegnung) {
                        abgleicher.uebernimmGanz(
                            vorschlag,
                            herkunftVon = fremdesHallo.geraeteId,
                        )
                        verbindung.sende(endpunkt, Nachricht.Fertig)
                        _stand.value =
                            Sitzungsstand.Fertig(Bilanz.NICHTS, neustartNoetig = true)
                    } else {
                        verbindung.sende(
                            endpunkt,
                            Nachricht.Abbruch("Der empfangene Stand passt nicht."),
                        )
                        beende("Der empfangene Stand passt nicht zur Erstbegegnung.")
                    }
                } else {
                    // Der eigene Stand gilt. Auch dann braucht dieses Gerät eine Herkunft,
                    // sonst wäre der nächste Abgleich wieder eine Erstbegegnung und beide
                    // Geräte fielen aufs selbe Nummernfenster zurück.
                    abgleicher.merkeErstbegegnungGewonnen(fremdesHallo.geraeteId)
                    verbindung.sende(endpunkt, Nachricht.Fertig)
                    _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS)
                }
            }

            anweisungsDatei != null -> {
                _stand.value = Sitzungsstand.Laeuft("Wird angewendet …")
                val paket = json.decodeFromString<Anweisungspaket>(anweisungsDatei.readText())
                val bilanz = abgleicher.wendeFremdesPaketAn(paket)
                // Die Bestätigung ist keine Höflichkeit: die Gegenseite baut die Verbindung
                // erst danach ab. Ohne sie schnitte sie eine noch laufende Übertragung ab.
                verbindung.sende(endpunkt, Nachricht.Fertig)
                _stand.value = Sitzungsstand.Fertig(bilanz)
            }
        }
    }

    /** Eine Datei der gewünschten Art — egal, ob sie vor oder nach ihrer Ankündigung ankam. */
    private suspend fun holeDatei(art: DateiArt): File {
        fertigeDatei(art)?.let { return it }
        warteBis(UEBERTRAGUNG_ZEITLIMIT) { fertigeDatei(art) != null }
        return fertigeDatei(art) ?: error("Die erwartete Datei ist nicht angekommen.")
    }

    private fun fertigeDatei(art: DateiArt): File? = ankuendigungen.values
        .firstOrNull { it.art == art && angekommene.containsKey(it.payloadId) }
        ?.let { angekommene[it.payloadId] }

    private suspend inline fun <reified T : Nachricht> warteAufNachricht(): T {
        val ereignis = warteBis(UEBERTRAGUNG_ZEITLIMIT) {
            it is Funkereignis.NachrichtDa && it.nachricht is T
        }
        return (ereignis as Funkereignis.NachrichtDa).nachricht as T
    }

    /**
     * Zieht Ereignisse aus der Warteschlange, bis [passt] zutrifft.
     *
     * Die Buchführung über Dateien läuft dabei nebenher — deshalb ist es egal, in welcher
     * Reihenfolge Ankündigung und Datei eintreffen, und es geht nichts verloren, während
     * gerade auf etwas anderes gewartet wird. Abbruch, Verbindungsverlust und Fehler brechen
     * hier zentral ab, statt an jeder Wartestelle einzeln behandelt zu werden.
     */
    private suspend fun warteBis(
        zeitlimit: Long,
        passt: (Funkereignis) -> Boolean,
    ): Funkereignis = withTimeout(zeitlimit) {
        while (true) {
            val ereignis = verbindung.naechstes()

            when {
                ereignis is Funkereignis.NachrichtDa &&
                    ereignis.nachricht is Nachricht.Abbruch ->
                    error((ereignis.nachricht as Nachricht.Abbruch).grund)

                ereignis is Funkereignis.Fehlgeschlagen -> error(ereignis.grund)

                ereignis is Funkereignis.Getrennt ->
                    error("Die Verbindung zum anderen Gerät ist abgerissen.")

                ereignis is Funkereignis.NachrichtDa &&
                    ereignis.nachricht is Nachricht.DateiFolgt -> {
                    val a = ereignis.nachricht as Nachricht.DateiFolgt
                    ankuendigungen[a.payloadId] = a
                }

                ereignis is Funkereignis.DateiDa ->
                    angekommene[ereignis.payloadId] = ereignis.datei

                else -> Unit
            }

            if (passt(ereignis)) return@withTimeout ereignis
        }
        @Suppress("UNREACHABLE_CODE")
        error("unerreichbar")
    }

    private fun beende(grund: String) {
        _stand.value = Sitzungsstand.Abgebrochen(grund)
        verbindung.beende()
    }

    private companion object {
        /** Findet sich in zwei Minuten nichts, findet sich nichts mehr. */
        const val SUCHE_ZEITLIMIT = 120_000L

        /** Großzügig: die erste Übertragung kann Gigabyte umfassen. */
        const val UEBERTRAGUNG_ZEITLIMIT = 30 * 60_000L
    }
}
