package com.boulderbuddy.ghost

import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.pose.PoseSpurQuelle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================================
// Ghost Climber — der laufende Analyse-Vorgang, unabhängig vom Bildschirm (7.5h)
// =============================================================================
//
// Hier liegt der Zustand EINES Laufs: welche zwei Videos, wie weit, Ergebnis oder Fehler.
// Der Runner ist ein Singleton und gehört bewusst weder dem ViewModel noch dem Dienst:
//
// * Das ViewModel stirbt mit dem Bildschirm, die sieben Minuten Rechnung dürfen das nicht.
// * Der Dienst hält den Prozess am Leben und zeigt den Fortschritt an — er rechnet aber
//   nicht selbst und weiß vom Ergebnis nur, dass es eines gibt.
//
// Daraus folgt die Arbeitsteilung: der Bildschirm stößt an und liest [GhostAnalyseRunner.stand]
// mit, der Dienst hängt sich an denselben Stand. Ein neu aufgebauter Bildschirm findet über
// diesen Stand in einen Lauf zurück, von dessen Start er nichts mitbekommen hat.

/** Frame-Zähler eines Videos; `gesamt == 0` heißt „noch nicht angefangen". */
data class Fortschritt(val fertig: Int = 0, val gesamt: Int = 0)

/**
 * Zustand der Pose-Extraktion beider Videos. Lebt im [GhostAnalyseRunner] und damit
 * außerhalb jedes Bildschirms — genau deshalb kann die Analyse das Verlassen überstehen.
 */
sealed interface GhostAnalyseStand {

    /** Nichts läuft. Auch der Zustand nach Abbruch und nach dem Abholen eines Ergebnisses. */
    data object Untaetig : GhostAnalyseStand

    data class Laeuft(
        val refUri: String,
        val cmpUri: String,
        val ref: Fortschritt = Fortschritt(),
        val cmp: Fortschritt = Fortschritt(),
    ) : GhostAnalyseStand {
        /** Welches der beiden Videos gerade an der Reihe ist (sie laufen nacheinander). */
        val beiVergleich: Boolean get() = cmp.gesamt > 0

        /** Anteil des laufenden Videos, 0..1; null, solange die Frame-Zahl unbekannt ist. */
        val anteil: Float?
            get() = (if (beiVergleich) cmp else ref)
                .takeIf { it.gesamt > 0 }
                ?.let { it.fertig.toFloat() / it.gesamt }
    }

    data class Fertig(
        val refUri: String,
        val cmpUri: String,
        val refTrack: GhostPoseTrack,
        val cmpTrack: GhostPoseTrack,
    ) : GhostAnalyseStand

    data class Fehler(val meldung: String) : GhostAnalyseStand
}

/**
 * Führt die Pose-Extraktion beider Ghost-Videos aus — als Singleton und in einem eigenen
 * Scope, nicht im `viewModelScope`.
 *
 * Der Grund ist gemessen: ein Paar aus 289 + 315 Frames braucht auf dem Pixel rund sieben
 * Minuten. Lief die Arbeit im ViewModel, hielt sie den Nutzer auf dem Bildschirm fest —
 * ein `popBackStack` räumt das ViewModel ab, der Scope stirbt, und sieben Minuten Rechnung
 * sind weg. Hier überlebt sie, weil niemand sie besitzt außer dem Prozess selbst.
 *
 * Der Runner rechnet, meldet und hört auf Abbruch. Sichtbar (Notification) wird das im
 * [com.boulderbuddy.ghost.service.GhostAnalyseService], der ihn startet und mitliest; das
 * ViewModel liest denselben [stand] und findet beim Zurückkommen einfach wieder an.
 *
 * Der Cache bleibt unberührt: die Spuren kommen weiterhin über die [PoseSpurQuelle] aus
 * `files/ghost/` bzw. landen dort, ein zweiter Lauf über dieselbe URI rechnet nicht noch
 * einmal. Der Schlüssel dazu steht unverändert im [GhostArtifactStore].
 */
@Singleton
class GhostAnalyseRunner(
    private val quelle: PoseSpurQuelle,
    dispatcher: CoroutineDispatcher,
) {

    // Der Dispatcher hängt am Konstruktor und nicht an einem Default-Wert: Dagger sieht
    // Kotlins Vorbelegungen nicht und verlangte prompt eine Bindung für CoroutineDispatcher.
    @Inject
    constructor(quelle: PoseSpurQuelle) : this(quelle, Dispatchers.Default)

    private val _stand = MutableStateFlow<GhostAnalyseStand>(GhostAnalyseStand.Untaetig)
    val stand: StateFlow<GhostAnalyseStand> = _stand.asStateFlow()

    private val bereich = CoroutineScope(SupervisorJob() + dispatcher)
    private var arbeit: Job? = null

    /**
     * Startet die Extraktion beider Videos nacheinander (ein Landmarker zur Zeit — sie
     * teilen sich Speicher und Rechenwerk). Ein zweiter Aufruf während eines laufenden
     * Laufs wird ignoriert.
     */
    fun starte(refUri: String, cmpUri: String) {
        if (arbeit?.isActive == true) return
        // Synchron, noch vor dem Start: wer gleich danach mitzulesen beginnt (der Service),
        // sieht garantiert „läuft" und nicht das Ergebnis des vorherigen Laufs.
        _stand.value = GhostAnalyseStand.Laeuft(refUri = refUri, cmpUri = cmpUri)
        arbeit = bereich.launch {
            try {
                val refTrack = quelle.spur(refUri) { fertig, gesamt ->
                    melde { it.copy(ref = Fortschritt(fertig, gesamt)) }
                }
                val cmpTrack = quelle.spur(cmpUri) { fertig, gesamt ->
                    melde { it.copy(cmp = Fortschritt(fertig, gesamt)) }
                }
                _stand.value = GhostAnalyseStand.Fertig(
                    refUri = refUri,
                    cmpUri = cmpUri,
                    refTrack = refTrack,
                    cmpTrack = cmpTrack,
                )
            } catch (e: CancellationException) {
                // Der Endzustand wird HIER gesetzt, nicht in `brichAb`: `cancel()` wirkt
                // erst beim nächsten Aufsetzpunkt der Coroutine. Setzte der Abbrecher den
                // Zustand selbst, könnte die noch laufende Coroutine ihn danach mit
                // „fertig" überschreiben — der Dienst bliebe stehen, der Bildschirm zeigte
                // ein Ergebnis, das niemand mehr angefordert hat.
                _stand.value = GhostAnalyseStand.Untaetig
                throw e
            } catch (e: Exception) {
                _stand.value = GhostAnalyseStand.Fehler(e.message ?: FEHLER_UNBEKANNT)
            }
        }
    }

    /** Bricht einen laufenden Lauf ab; der Zustand fällt auf [GhostAnalyseStand.Untaetig]. */
    fun brichAb() {
        arbeit?.cancel()
        arbeit = null
    }

    /** Wartet, bis der laufende Lauf zu Ende ist (auch durch Abbruch oder Fehler). */
    suspend fun warteAufEnde() {
        arbeit?.join()
    }

    /**
     * Endzustand abgeholt — der Bildschirm hat Spuren bzw. Fehlermeldung übernommen.
     *
     * Ohne das bliebe das Ergebnis liegen und der nächste Bildschirm-Aufbau spränge erneut
     * in die Anker-Ansicht, obwohl der Nutzer längst weitergearbeitet hat.
     */
    fun quittiere() {
        _stand.update { stand ->
            if (stand is GhostAnalyseStand.Fertig || stand is GhostAnalyseStand.Fehler) {
                GhostAnalyseStand.Untaetig
            } else {
                stand
            }
        }
    }

    // Fortschritt kommt aus dem Extraktor je Frame; nach dem Abbruch trifft er auf einen
    // Zustand, der nicht mehr „läuft" ist — dann verfällt die Meldung.
    private fun melde(transform: (GhostAnalyseStand.Laeuft) -> GhostAnalyseStand.Laeuft) {
        _stand.update { if (it is GhostAnalyseStand.Laeuft) transform(it) else it }
    }

    private companion object {
        const val FEHLER_UNBEKANNT = "Analyse fehlgeschlagen"
    }
}
