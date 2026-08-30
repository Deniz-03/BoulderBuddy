package com.boulderbuddy.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der eine Weg, auf dem ein fehlgeschlagener Schreibvorgang beim Nutzer ankommt.
 *
 * **Warum es das gibt.** Die Schreibpfade der Formulare liefen ungesichert in
 * `viewModelScope`: ein Fehler aus Room — volle Platte, verletzte Fremdschlüssel-Bedingung,
 * geschlossene Datenbank während eines Abgleichs — beendete die App, statt etwas zu sagen.
 * Aufgefallen wäre das im Alltag selten und dann im schlechtesten Moment: beim Speichern
 * eines Boulders mitten in der Session, also genau dann, wenn die Eingabe noch nicht
 * woanders steht.
 *
 * **Warum ein gemeinsamer Kanal und nicht fünf Felder in fünf UI-States.** Zwei der
 * betroffenen Screens (Boulder-Formular, Session anlegen) navigieren im Erfolgsfall sofort
 * weg — ein Fehlerfeld in ihrem State hätte also ohnehin nur die Aufgabe, das Wegnavigieren
 * zu verhindern, und für die Meldung bräuchte jeder Screen zusätzlich ein eigenes
 * Snackbar-Gerüst. Fünfmal dasselbe Gerüst für eine Meldung, die immer gleich aussieht.
 * Hier liegt sie einmal, und [com.boulderbuddy.ui.navigation.AppNavigation] zeigt sie an
 * einer Stelle an.
 *
 * **Warum eine String-Ressource und kein fertiger Text.** So bleibt der Wortlaut in
 * `strings.xml`, wo der Rest der Oberfläche steht, und die ViewModels brauchen für die
 * Meldung keinen Context.
 *
 * Der Kanal ist bewusst schmal: er trägt Meldungen, keine Zustände. Was nach einem
 * Fehlschlag mit der Ansicht passiert — auf dem Formular bleiben, nicht weiterspringen —
 * entscheidet der Aufrufer anhand des Rückgabewerts von [schreibe].
 */
@Singleton
class Fehlerkanal @Inject constructor() {

    /*
     * `extraBufferCapacity = 1` mit `DROP_OLDEST`, damit [melde] nie blockiert und ohne
     * Coroutine aufrufbar bleibt. Fehlt gerade ein Sammler (die Composition ist im Aufbau),
     * geht die Meldung verloren — das ist die richtige Wahl: eine Fehlermeldung, die drei
     * Bildschirme später auftaucht, verwirrt mehr, als sie hilft. `replay = 0` aus demselben
     * Grund; sonst zeigte jede Drehung des Geräts den alten Fehler noch einmal.
     */
    private val _meldungen = MutableSharedFlow<Int>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Fehlermeldungen als String-Ressourcen-IDs, in der Reihenfolge ihres Auftretens. */
    val meldungen: SharedFlow<Int> = _meldungen.asSharedFlow()

    /** Meldet einen Fehlschlag. Blockiert nie und darf aus jedem Kontext gerufen werden. */
    fun melde(@StringRes text: Int) {
        _meldungen.tryEmit(text)
    }
}

/**
 * Führt [block] aus und fängt einen Fehlschlag ab, statt die App zu beenden.
 *
 * @return `true`, wenn [block] durchgelaufen ist. Bei `false` hat der Nutzer die Meldung
 *   [fehlertext] bekommen, und der Aufrufer soll **nicht** so weitermachen, als wäre
 *   gespeichert worden — insbesondere nicht wegnavigieren.
 *
 * [CancellationException] wird durchgereicht und nicht gemeldet: sie ist kein Fehler,
 * sondern das Ende des Scopes (Screen verlassen, ViewModel abgeräumt). Würde sie hier
 * hängenbleiben, bräche die Struktur der Coroutinen, und der Nutzer sähe beim Verlassen
 * eines Formulars eine Fehlermeldung für etwas, das er selbst ausgelöst hat.
 */
suspend fun Fehlerkanal.schreibe(
    @StringRes fehlertext: Int,
    protokollMarke: String,
    block: suspend () -> Unit,
): Boolean = try {
    block()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("Fehlerkanal", "$protokollMarke fehlgeschlagen", e)
    melde(fehlertext)
    false
}

/**
 * Reicht den [Fehlerkanal] in die Composition. Ein ViewModel und kein `EntryPoint`, weil die
 * Wurzel der Navigation ein Composable ist und `hiltViewModel()` dort der übliche Weg ist —
 * `EntryPointAccessors` braucht es nur dort, wo es keinen gibt (Receiver, Glance-Widget).
 */
@HiltViewModel
class FehlerkanalViewModel @Inject constructor(
    kanal: Fehlerkanal,
) : ViewModel() {
    val meldungen: SharedFlow<Int> = kanal.meldungen
}
