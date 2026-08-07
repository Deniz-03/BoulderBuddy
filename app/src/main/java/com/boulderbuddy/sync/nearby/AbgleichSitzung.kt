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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Woran der Abgleich gerade ist — das, was der Bildschirm zeigt. */
sealed interface Sitzungsstand {
    data object Untaetig : Sitzungsstand

    /** Werben und suchen. Beide Geräte müssen den Screen offen haben. */
    data object Suche : Sitzungsstand

    /**
     * Beide Geräte zeigen dieselbe vierstellige Zahl. Stimmen sie nicht überein, ist es
     * nicht das richtige Gerät — das ist der einzige Schutz davor, den Stand versehentlich
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
 * Ein Abgleich über Nearby, von Anfang bis Ende (Sync-Plan S5).
 *
 * Bewusst als **linearer** Ablauf geschrieben und nicht als Zustandsautomat: der Funkweg ist
 * der einzige Teil dieses Features, der sich ohne zwei echte Geräte nicht prüfen lässt.
 * Wenn ich ihn nicht testen kann, soll er wenigstens von oben nach unten lesbar sein —
 * `warteAuf(...)` hält den Ablauf an, statt ihn über ein Dutzend Rückrufe zu verstreuen.
 *
 * Die Reihenfolge folgt dem Plan: Vorprüfungen → Hash-Listen → fehlende Medien →
 * **zuletzt** der Stand. Zuletzt deshalb, weil ein Abbruch vor dem Stand folgenlos ist:
 * herumliegende Medien kosten Platz, ein halb angewendeter Stand kostet Daten.
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

    /** Antworten des Nutzers, auf die der Ablauf wartet. */
    private var bestaetigung: CompletableDeferred<Boolean>? = null
    private var konfliktAntwort: CompletableDeferred<Seite>? = null
    private var erstbegegnungAntwort: CompletableDeferred<Boolean>? = null

    private var offenerEndpunkt: String? = null

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
     * @param anzeigename was das andere Gerät sieht.
     */
    suspend fun fuehre(hatGedrueckt: Boolean, anzeigename: String) {
        try {
            // Vor allem anderen: alle Medien gehören der App und heißen nach ihrem Inhalt.
            // Sonst tauschen die Geräte gleich Namenslisten aus, die nichts bedeuten (E5).
            _stand.value = Sitzungsstand.Laeuft("Medien werden vorbereitet …")
            medienUmzug.stelleSicher()

            _stand.value = Sitzungsstand.Suche
            verbindung.starte(anzeigename)

            val endpunkt = verbindeMitBestaetigung()
            offenerEndpunkt = endpunkt

            val meinHallo = baueHallo(hatGedrueckt)
            verbindung.sende(endpunkt, meinHallo)
            _stand.value = Sitzungsstand.Laeuft("Geräte gleichen ab …")
            val fremdesHallo = warteAufNachricht<Nachricht.Hallo>()

            when (val ergebnis = pruefeHandschlag(meinHallo, fremdesHallo)) {
                is Handschlag.Abbruch -> {
                    // Die Gegenseite kommt zum selben Ergebnis, aber die Nachricht spart ihr
                    // das Warten auf einen Abbruch, der nie kommt.
                    verbindung.sende(endpunkt, Nachricht.Abbruch(ergebnis.grund))
                    beende(ergebnis.grund)
                    return
                }

                is Handschlag.Bereit -> tauscheAus(
                    endpunkt = endpunkt,
                    ichRechne = ergebnis.ichRechne,
                    meinHallo = meinHallo,
                    fremdesHallo = fremdesHallo,
                )
            }
        } catch (e: Exception) {
            beende(e.message ?: "Der Abgleich ist fehlgeschlagen.")
        } finally {
            verbindung.beende()
            dateien.raeumeEmpfangenesAuf()
            offenerEndpunkt = null
        }
    }

    /** Warten, bis der Nutzer die vierstellige Zahl bestätigt hat und die Verbindung steht. */
    private suspend fun verbindeMitBestaetigung(): String {
        while (true) {
            val ereignis = withTimeout(SUCHE_ZEITLIMIT) {
                verbindung.ereignisse.first {
                    it is Funkereignis.BestaetigungNoetig ||
                        it is Funkereignis.Verbunden ||
                        it is Funkereignis.Fehlgeschlagen
                }
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
                is Funkereignis.Fehlgeschlagen -> error(ereignis.grund)
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
        meinHallo: Nachricht.Hallo,
        fremdesHallo: Nachricht.Hallo,
    ) {
        // 1. Hash-Listen tauschen. Beide schicken, beide warten — die Reihenfolge ist egal,
        //    weil niemand auf die Antwort des anderen wartet, um zu senden.
        val meineMedien = medien.vorhandeneNamen()
        verbindung.sende(endpunkt, Nachricht.Medienliste(meineMedien.toList()))
        val fremdeMedien = warteAufNachricht<Nachricht.Medienliste>().namen

        // 2. Fehlende Medien schicken. Vor dem Stand: bricht es hier ab, liegen ein paar
        //    Dateien zu viel herum — mehr nicht.
        val zuSchicken = fehlendeMedien(meineMedien, fremdeMedien)
        val zuEmpfangen = fehlendeMedien(fremdeMedien, meineMedien)
        zuSchicken.forEachIndexed { i, name ->
            _stand.value = Sitzungsstand.Laeuft(
                "Videos und Fotos werden übertragen …",
                anteil = (i + 1f) / zuSchicken.size,
            )
            val datei = medien.datei(name)
            if (datei.exists()) verbindung.sendeDatei(endpunkt, datei, DateiArt.MEDIUM, name)
        }
        verbindung.sende(endpunkt, Nachricht.Fertig)

        // 3. Die fehlenden Medien der Gegenseite entgegennehmen.
        empfangeMedien(zuEmpfangen.size)

        // 4. Zuletzt der Stand.
        _stand.value = Sitzungsstand.Laeuft("Stand wird abgeglichen …")
        if (ichRechne) {
            val fremdeDatei = empfangeDatei(DateiArt.STAND)
            rechneUndVerteile(endpunkt, fremdeDatei, meinHallo, fremdesHallo)
        } else {
            val eigene = dateien.kopiereStand(
                File(dateien.empfangsOrdner(), "abgabe.db"),
            )
            verbindung.sendeDatei(endpunkt, eigene, DateiArt.STAND)
            wendeFremdesErgebnisAn(endpunkt)
        }
    }

    /**
     * Das rechnende Gerät: vergleichen, gegebenenfalls fragen, anwenden — und der
     * Gegenseite das Ergebnis schicken.
     */
    private suspend fun rechneUndVerteile(
        endpunkt: String,
        fremdeDatei: File,
        meinHallo: Nachricht.Hallo,
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
                    // Hier wird die Datei ersetzt — danach ist die App bis zum Neustart
                    // nicht benutzbar, weil Room noch die alte Datei hält (E10).
                    abgleicher.uebernimmGanz(vorschlag)
                    _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS, neustartNoetig = true)
                } else {
                    val eigene = dateien.kopiereStand(
                        File(dateien.empfangsOrdner(), "abgabe.db"),
                    )
                    verbindung.sendeDatei(endpunkt, eigene, DateiArt.STAND)
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
                _stand.value = Sitzungsstand.Fertig(bilanz)
            }
        }
    }

    /** Das andere Gerät: das fertige Ergebnis entgegennehmen und anwenden. */
    private suspend fun wendeFremdesErgebnisAn(endpunkt: String) {
        val ereignis = withTimeout(UEBERTRAGUNG_ZEITLIMIT) {
            verbindung.ereignisse.first {
                (it is Funkereignis.NachrichtDa &&
                    (it.nachricht is Nachricht.Abbruch ||
                        it.nachricht is Nachricht.Fertig ||
                        it.nachricht is Nachricht.ErstbegegnungEntschieden)) ||
                    it is Funkereignis.DateiDa
            }
        }

        when {
            ereignis is Funkereignis.NachrichtDa &&
                ereignis.nachricht is Nachricht.Abbruch -> {
                beende((ereignis.nachricht as Nachricht.Abbruch).grund)
            }

            ereignis is Funkereignis.NachrichtDa &&
                ereignis.nachricht is Nachricht.Fertig -> {
                _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS)
            }

            ereignis is Funkereignis.NachrichtDa &&
                ereignis.nachricht is Nachricht.ErstbegegnungEntschieden -> {
                val entscheidung = ereignis.nachricht as Nachricht.ErstbegegnungEntschieden
                if (entscheidung.meinStandGewinnt) {
                    // Der Stand des anderen Geräts gilt — seine Datei kommt gleich.
                    val datei = empfangeDatei(DateiArt.STAND)
                    val vorschlag = abgleicher.pruefe(datei)
                    if (vorschlag is Abgleichvorschlag.Erstbegegnung) {
                        abgleicher.uebernimmGanz(vorschlag)
                        _stand.value =
                            Sitzungsstand.Fertig(Bilanz.NICHTS, neustartNoetig = true)
                    } else {
                        beende("Der empfangene Stand passt nicht zur Erstbegegnung.")
                    }
                } else {
                    // Der eigene Stand gilt; die Gegenseite übernimmt ihn.
                    _stand.value = Sitzungsstand.Fertig(Bilanz.NICHTS)
                }
            }

            ereignis is Funkereignis.DateiDa -> {
                val paket = json.decodeFromString<Anweisungspaket>(ereignis.datei.readText())
                _stand.value = Sitzungsstand.Laeuft("Wird angewendet …")
                val bilanz = abgleicher.wendeFremdesPaketAn(paket)
                _stand.value = Sitzungsstand.Fertig(bilanz)
            }
        }
    }

    private suspend fun empfangeMedien(anzahl: Int) {
        if (anzahl == 0) {
            // Trotzdem auf das „fertig" der Gegenseite warten — sonst überholt der Stand
            // die Medien und die Zuordnung gerät durcheinander.
            warteAufNachricht<Nachricht.Fertig>()
            return
        }
        var empfangen = 0
        while (empfangen < anzahl) {
            val ereignis = withTimeout(UEBERTRAGUNG_ZEITLIMIT) {
                verbindung.ereignisse.first {
                    it is Funkereignis.DateiDa ||
                        (it is Funkereignis.NachrichtDa && it.nachricht is Nachricht.Abbruch)
                }
            }
            if (ereignis is Funkereignis.NachrichtDa) {
                error((ereignis.nachricht as Nachricht.Abbruch).grund)
            }
            empfangen++
            _stand.value = Sitzungsstand.Laeuft(
                "Videos und Fotos werden empfangen …",
                anteil = empfangen.toFloat() / anzahl,
            )
        }
        warteAufNachricht<Nachricht.Fertig>()
    }

    private suspend fun empfangeDatei(art: DateiArt): File {
        // Die Ankündigung nennt die Payload-Nummer; die Datei kommt getrennt davon an.
        val ankuendigung = withTimeout(UEBERTRAGUNG_ZEITLIMIT) {
            verbindung.ereignisse
                .filterIsInstance<Funkereignis.NachrichtDa>()
                .first { it.nachricht is Nachricht.DateiFolgt &&
                    (it.nachricht as Nachricht.DateiFolgt).art == art }
        }
        val erwartet = (ankuendigung.nachricht as Nachricht.DateiFolgt).payloadId
        val datei = withTimeout(UEBERTRAGUNG_ZEITLIMIT) {
            verbindung.ereignisse
                .filterIsInstance<Funkereignis.DateiDa>()
                .first { it.payloadId == erwartet }
        }
        return datei.datei
    }

    private suspend inline fun <reified T : Nachricht> warteAufNachricht(): T =
        withTimeout(UEBERTRAGUNG_ZEITLIMIT) {
            verbindung.ereignisse
                .filterIsInstance<Funkereignis.NachrichtDa>()
                .first { it.nachricht is T }
                .nachricht as T
        }

    private suspend fun baueHallo(hatGedrueckt: Boolean): Nachricht.Hallo =
        abgleicher.beschreibeMich(hatGedrueckt, identitaet.eigeneId())

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
