package com.boulderbuddy.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import com.boulderbuddy.R
import com.boulderbuddy.ui.Texte
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.sync.Abgleicher
import com.boulderbuddy.sync.Abgleichvorschlag
import com.boulderbuddy.sync.Bilanz
import com.boulderbuddy.sync.GeraeteIdentitaet
import com.boulderbuddy.sync.MedienUmzug
import com.boulderbuddy.sync.AbgleichDateien
import com.boulderbuddy.sync.Seite
import com.boulderbuddy.sync.StandDatei
import com.boulderbuddy.sync.nearby.AbgleichService
import com.boulderbuddy.sync.nearby.AbgleichSitzung
import com.boulderbuddy.sync.nearby.Sitzungsstand
import com.boulderbuddy.widget.refreshBoulderWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Zustand des Abgleich-Screens.
 *
 * Die Sprache gehört in den Screen, nicht hierher — hier stehen nur Tatsachen (Ablauf 6).
 */
data class AbgleichUiState(
    val laeuft: Boolean = false,
    /** Was gerade passiert, für die Fortschrittszeile. */
    val schritt: String? = null,
    /** Offener Vorschlag, der eine Entscheidung braucht; `null` = keine offene Frage. */
    val vorschlag: Abgleichvorschlag? = null,
    /** Bilanz des letzten Abgleichs; `null` = noch keiner gelaufen. */
    val bilanz: Bilanz? = null,
    val meldung: String? = null,
    val kannRueckgaengig: Boolean = false,
    /**
     * Nach einer Erstbegegnung: Room hält noch die alte Datei offen, die App muss neu
     * starten, bevor sie wieder benutzbar ist (E10).
     */
    val neustartNoetig: Boolean = false,
)

/**
 * Führt den Abgleich über den Datei-Weg (Sync-Plan S7/S8).
 *
 * Alles Entscheidende liegt im [Abgleicher]; dieses ViewModel bringt nur die Reihenfolge und
 * den Zustand für den Bildschirm.
 */
@HiltViewModel
class AbgleichViewModel @Inject constructor(
    application: Application,
    private val abgleicher: Abgleicher,
    private val standDatei: StandDatei,
    private val dateien: AbgleichDateien,
    private val medienUmzug: MedienUmzug,
    private val identitaet: GeraeteIdentitaet,
    private val sitzung: AbgleichSitzung,
    // Loest die Anzeigetexte aus strings.xml auf (siehe ui/Texte.kt).
    private val texte: Texte,
) : AndroidViewModel(application) {

    /**
     * Der Funkweg läuft im Foreground Service, nicht hier — dieses ViewModel stirbt mit dem
     * Screen, die Übertragung darf das nicht. Der Zustand kommt deshalb aus der Sitzung
     * selbst, die ein Singleton ist.
     */
    val funkStand: StateFlow<Sitzungsstand> = sitzung.stand

    private val _uiState = MutableStateFlow(AbgleichUiState())
    val uiState: StateFlow<AbgleichUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(kannRueckgaengig = dateien.kannRueckgaengig()) }
    }

    fun abgabeName(): String = standDatei.abgabeName(System.currentTimeMillis())

    /** Startet den Abgleich über Nearby. Die Berechtigungen holt der Screen vorher ein. */
    fun starteFunkAbgleich() {
        AbgleichService.starte(
            context = getApplication(),
            hatGedrueckt = true,
            anzeigename = android.os.Build.MODEL,
        )
    }

    fun bestaetigeVerbindung(ja: Boolean) {
        sitzung.bestaetigeVerbindung(ja)
        if (!ja) AbgleichService.stoppe(getApplication())
    }

    fun beantworteFunkKonflikt(wahl: Seite) = sitzung.beantworteKonflikt(wahl)

    fun beantworteFunkErstbegegnung(fremderGewinnt: Boolean) =
        sitzung.beantworteErstbegegnung(fremderGewinnt)

    fun brichFunkAbgleichAb() {
        sitzung.brichAb()
        AbgleichService.stoppe(getApplication())
    }

    /**
     * Nach einem Funk-Abgleich: Widget auffrischen und den Rückgängig-Knopf freischalten.
     * Läuft hier statt in der Sitzung, weil beides zum Bildschirm gehört, nicht zum Abgleich.
     */
    fun funkAbgleichAbgeschlossen() {
        viewModelScope.launch {
            refreshBoulderWidget(getApplication())
            identitaet.setzeGeaendert(false)
            _uiState.update { it.copy(kannRueckgaengig = dateien.kannRueckgaengig()) }
        }
    }

    /**
     * Gibt den eigenen Stand als Datei ab.
     *
     * Vorher läuft der einmalige Medien-Umzug (S3): danach gehören alle Medien der App und
     * heißen nach ihrem Inhalt. Ohne das wären Galerie-Verweise in der abgegebenen Datei auf
     * dem anderen Gerät tote Links.
     */
    fun gibAb(ziel: Uri) {
        starte(R.string.abgleich_schritt_vorbereiten) {
            medienUmzug.stelleSicher()
            val bytes = standDatei.exportiere(ziel)
            _uiState.update {
                it.copy(meldung = texte.hole(R.string.abgleich_meldung_abgegeben, bytes / 1024))
            }
        }
    }

    /** Liest einen abgegebenen Stand ein und schlägt vor, was damit zu tun ist. */
    fun lieseEin(quelle: Uri) {
        starte(R.string.abgleich_schritt_pruefen) {
            medienUmzug.stelleSicher()
            val datei = standDatei.importiere(quelle)
            when (val vorschlag = abgleicher.pruefe(datei)) {
                is Abgleichvorschlag.Abgelehnt ->
                    _uiState.update { it.copy(meldung = vorschlag.grund) }

                is Abgleichvorschlag.NichtsZuTun ->
                    _uiState.update {
                        it.copy(meldung = texte.hole(R.string.abgleich_meldung_gleichstand))
                    }

                // Ohne Konflikte gibt es nichts zu fragen — dann einfach zusammenführen.
                is Abgleichvorschlag.Zusammenfuehren ->
                    if (vorschlag.konflikte.isEmpty()) {
                        fuehreZusammen(vorschlag, Seite.MEINS)
                    } else {
                        _uiState.update { it.copy(vorschlag = vorschlag) }
                    }

                is Abgleichvorschlag.Erstbegegnung ->
                    _uiState.update { it.copy(vorschlag = vorschlag) }
            }
        }
    }

    /** Antwort auf die Konfliktfrage — gilt **nur** den Konflikten (E12, Ablauf 23). */
    fun entscheideKonflikt(wahl: Seite) {
        val offen = _uiState.value.vorschlag as? Abgleichvorschlag.Zusammenfuehren ?: return
        starte(R.string.abgleich_schritt_zusammenfuehren) { fuehreZusammen(offen, wahl) }
    }

    /** Antwort auf die Erstbegegnungs-Frage: den fremden Stand ganz übernehmen (E10). */
    fun uebernimmFremdenStand() {
        val offen = _uiState.value.vorschlag as? Abgleichvorschlag.Erstbegegnung ?: return
        starte(R.string.abgleich_schritt_uebernehmen) {
            abgleicher.uebernimmGanz(offen)
            _uiState.update {
                it.copy(
                    vorschlag = null,
                    neustartNoetig = true,
                    kannRueckgaengig = true,
                    meldung = texte.hole(R.string.abgleich_meldung_uebernommen),
                )
            }
        }
    }

    /** Erstbegegnung abgelehnt: der eigene Stand bleibt, nichts ist passiert. */
    fun behalteEigenenStand() {
        _uiState.update {
            it.copy(
                vorschlag = null,
                meldung = texte.hole(R.string.abgleich_meldung_nichts_geaendert),
            )
        }
        dateien.raeumeEmpfangenesAuf()
    }

    fun brichAb() {
        _uiState.update { it.copy(vorschlag = null, schritt = null) }
        dateien.raeumeEmpfangenesAuf()
    }

    /**
     * Nimmt zurück, was der letzte Abgleich auf **diesem** Gerät geändert hat (E13).
     *
     * Das UI verspricht genau das und nichts mehr: eine eigene Löschung von davor steht auch
     * in `vorher.db` nicht mehr drin.
     */
    fun machRueckgaengig() {
        starte(R.string.abgleich_schritt_zuruecknehmen) {
            val ging = abgleicher.machRueckgaengig()
            refreshBoulderWidget(getApplication())
            _uiState.update {
                it.copy(
                    bilanz = null,
                    kannRueckgaengig = false,
                    meldung = texte.hole(
                        if (ging) {
                            R.string.abgleich_meldung_zurueckgenommen
                        } else {
                            R.string.abgleich_meldung_nichts_zurueckzunehmen
                        },
                    ),
                )
            }
        }
    }

    private suspend fun fuehreZusammen(
        vorschlag: Abgleichvorschlag.Zusammenfuehren,
        wahl: Seite,
    ) {
        val bilanz = abgleicher.fuehreZusammen(vorschlag, wahl)
        // Nach dem Abgleich zeigt das Widget sonst den Stand von vorhin (Ablauf 28).
        refreshBoulderWidget(getApplication())
        identitaet.setzeGeaendert(false)
        dateien.raeumeEmpfangenesAuf()
        _uiState.update {
            it.copy(vorschlag = null, bilanz = bilanz, kannRueckgaengig = true)
        }
    }

    /**
     * Gemeinsamer Rahmen: Fortschritt an, Fehler abfangen, Fortschritt aus.
     *
     * Ein Abbruch darf folgenlos bleiben (Ablauf 5) — deshalb wird der Empfangsordner in
     * jedem Fall aufgeräumt, auch wenn etwas schiefgeht (Ablauf 27).
     */
    private fun starte(@StringRes schritt: Int, block: suspend () -> Unit) {
        if (_uiState.value.laeuft) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(laeuft = true, schritt = texte.hole(schritt), meldung = null)
            }
            try {
                block()
            } catch (e: Exception) {
                dateien.raeumeEmpfangenesAuf()
                _uiState.update {
                    it.copy(
                        vorschlag = null,
                        meldung = e.message ?: texte.hole(R.string.abgleich_meldung_fehlgeschlagen),
                    )
                }
            } finally {
                _uiState.update { it.copy(laeuft = false, schritt = null) }
            }
        }
    }
}
