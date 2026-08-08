package com.boulderbuddy.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.GymVisitEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.GymRepository
import com.boulderbuddy.data.repository.GymVisitRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.data.db.entity.hallenName
import com.boulderbuddy.ui.model.formatSeit
import com.boulderbuddy.ui.navigation.SessionErstellen
import com.boulderbuddy.widget.refreshBoulderWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Eine bestehende Halle in der Auswahl — bewusst nur ID und Name.
 *
 * Nicht [GymUi] aus der Gym-Verwaltung: die trägt Adresse, Koordinaten-Status und
 * Erinnerungs-Schalter, und nichts davon gehört in eine Auswahlliste beim Session-Start.
 */
data class HalleUi(
    val id: Int,
    val name: String,
    /** Standard-Gradsystem dieser Halle; `null` = keins gesetzt. */
    val defaultGradeSystemId: Int? = null,
)

/**
 * Hinweis auf bereits laufende Sessions.
 *
 * Mehrere gleichzeitig sind **erlaubt** — man kann vormittags am Hangboard und abends in der
 * Halle sein, ohne dazwischen aufzuräumen. Nur überraschen soll es niemanden: vorher entstand
 * die zweite Session kommentarlos, und die erste lief unbemerkt weiter, bis sie irgendwann in
 * der Liste auffiel.
 */
data class LaufendeSessionUi(
    /** Wie viele Sessions gerade laufen (mindestens 1, sonst gibt es diesen Hinweis nicht). */
    val anzahl: Int,
    /** Halle der zuletzt gestarteten; leer, wenn sie sich nicht auflösen lässt. */
    val gymName: String,
    /** Startzeitpunkt der zuletzt gestarteten, z.B. "seit 14:20" (siehe `formatSeit`). */
    val seit: String,
)

/** Anzeige-Zustand des "Neue Session"-Screens: wählbare Hallen + Grading-Systeme. */
data class SessionErstellenUiState(
    val systems: List<GradeSystemUi> = emptyList(),
    /**
     * Bestehende Hallen, **zuletzt benutzte zuerst**. Die Reihenfolge ist die eigentliche
     * Empfehlung: wer klettert, geht meist in dieselbe Halle wie beim letzten Mal.
     */
    val gyms: List<HalleUi> = emptyList(),
    /**
     * Vorausgewählte Halle: die aus dem Näherungs-Notification-Deep-Link
     * (`SessionErstellen(gymId)`), sonst die zuletzt benutzte. `null` = es gibt noch keine
     * Halle, der Screen zeigt dann direkt das Namensfeld.
     */
    val vorauswahlGymId: Int? = null,
    /** Bereits laufende Sessions; `null` = keine, dann erscheint kein Hinweis. */
    val laufende: LaufendeSessionUi? = null,
)

/**
 * Baut den Hinweis auf bereits laufende Sessions — `null`, wenn keine läuft.
 *
 * Genannt wird die **zuletzt gestartete**: sie ist die, an die man gerade denkt. Die Anzahl steht
 * daneben, weil „eine läuft bereits" schlicht falsch wäre, wenn es drei sind.
 *
 * Als eigene Funktion, damit die Regel ohne Android testbar bleibt.
 */
fun laufendeSessions(
    sessions: List<SessionEntity>,
    gyms: List<GymEntity>,
): LaufendeSessionUi? {
    val laufend = sessions.filter { it.endedAt == null }
    val juengste = laufend.maxByOrNull { it.date } ?: return null
    return LaufendeSessionUi(
        anzahl = laufend.size,
        gymName = juengste.hallenName { id -> gyms.firstOrNull { it.id == id }?.name }.orEmpty(),
        seit = formatSeit(juengste.date),
    )
}

/**
 * Sortiert die Hallen nach ihrer letzten Session, neueste zuerst.
 *
 * Als eigene Funktion, weil hier die einzige Entscheidung dieses Screens steckt: welche Halle
 * ganz vorn steht, ist zugleich die Vorauswahl. Ohne Session behält eine Halle die Reihenfolge,
 * in der sie hereinkommt (alphabetisch aus dem Repository), und landet hinter allen benutzten —
 * `sortedByDescending` ist stabil, das trägt die Sortierung mit.
 */
fun sortiereNachLetzterNutzung(
    gyms: List<GymEntity>,
    sessions: List<SessionEntity>,
): List<GymEntity> {
    val letzteNutzung = sessions.groupBy { it.gymId }
        .mapValues { (_, eintraege) -> eintraege.maxOf { it.date } }
    return gyms.sortedByDescending { letzteNutzung[it.id] ?: Long.MIN_VALUE }
}

@HiltViewModel
class SessionErstellenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val gymRepository: GymRepository,
    private val gymVisitRepository: GymVisitRepository,
    gradeRepository: GradeRepository,
    // Nur fürs Homescreen-Widget: nach dem Anlegen soll es sofort die neue Session zeigen.
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // Halle aus dem Näherungs-Notification-Deep-Link (M4); null = normaler Aufruf.
    private val argGymId: Int? = savedStateHandle.toRoute<SessionErstellen>().gymId

    // Reale Hallen + Grading-Systeme (Standards + Custom) für die Auswahl beim Anlegen.
    val uiState: StateFlow<SessionErstellenUiState> = combine(
        gradeRepository.observeAllSystems(),
        gradeRepository.observeAllGrades(),
        gymRepository.observeAll(),
        sessionRepository.observeAll(),
    ) { systems, grades, gyms, sessions ->
        val countBySystem = grades.groupingBy { it.systemId }.eachCount()
        val sortiert = sortiereNachLetzterNutzung(gyms, sessions)

        SessionErstellenUiState(
            systems = systems.map {
                GradeSystemUi(id = it.id, name = it.name, gradeCount = countBySystem[it.id] ?: 0)
            },
            gyms = sortiert.map {
                HalleUi(
                    id = it.id,
                    name = it.name,
                    // Eine tote ID (System gelöscht) wird hier zu `null` — der Screen fiele
                    // sonst auf einen Chip zurück, den es nicht mehr gibt.
                    defaultGradeSystemId = it.defaultGradeSystemId
                        ?.takeIf { id -> systems.any { s -> s.id == id } },
                )
            },
            // Der Deep-Link gewinnt; sonst die zuletzt benutzte Halle. `takeIf` fängt eine
            // Gym-ID ab, die es nicht (mehr) gibt — sonst stünde eine Auswahl da, zu der kein
            // Chip gehört.
            vorauswahlGymId = argGymId?.takeIf { id -> gyms.any { it.id == id } }
                ?: sortiert.firstOrNull()?.id,
            laufende = laufendeSessions(sessions, gyms),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionErstellenUiState(),
    )

    /**
     * Legt eine neue, aktive Session an (`endedAt = null`) und meldet die neue ID zurück.
     *
     * Die Halle kommt als **ID** herein, nicht als Text. Früher trug jede Session nur einen
     * freien Ort-String, aus dem hier per "find-or-create" eine Halle wurde; ein abgewandelter
     * Name legte stillschweigend eine zweite an, die Session hing danach woanders als der
     * Geofence, und die Näherungs-Politik sah für die eigentliche Halle nie eine Session
     * ([com.boulderbuddy.proximity.ProximityNotificationPolicy]). Neue Hallen entstehen jetzt
     * ausschließlich im Gym-Editor — mit Koordinaten, Radius und Standard-Grading.
     *
     * [gradeSystemId] wird auf der Session gespeichert und steuert später die Grade-Auswahl beim
     * Boulder-Anlegen. Vorbelegt ist das Standard-Grading der Halle, überschreibbar bleibt es.
     *
     * `gymName` wird **mitgeschrieben**, nicht nur `gymId`. Ohne diese Zeile blieb die Spalte auf
     * ihrem Default `""`, und der Beleg, der eine gelöschte Halle überdauern soll (DB v10), war
     * für jede real angelegte Session leer — die Session hieß danach „Unbekannte Halle", obwohl
     * die Rückfrage vor dem Löschen ausdrücklich zusichert, der Hallenname bleibe erhalten.
     * Aufgefallen ist das erst am Gerät, weil die Seed-Session ihren Namen mitbringt und der
     * Fehler damit ausgerechnet im Beispieldatensatz nicht auftritt.
     */
    /**
     * Verhindert, dass ein zweiter Tap eine zweite Session anlegt.
     *
     * Zwischen Tap und Navigation liegen ein Room-Insert und ein Besuchs-Eintrag; in dieser
     * Zeitspanne bleibt der Knopf sichtbar und bedienbar. Am Gerät reichten zwei schnelle Taps
     * für zwei Sessions mit identischem Zeitstempel — die App sprang in eine davon, die andere
     * blieb unsichtbar im Bestand liegen.
     */
    private var legtAn = false

    fun createSession(
        gymId: Int,
        gradeSystemId: Int?,
        notiz: String,
        onCreated: (Int) -> Unit,
    ) {
        if (legtAn) return
        legtAn = true
        viewModelScope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                val newId = sessionRepository.create(
                    SessionEntity(
                        gymId = gymId,
                        // Leer nur, wenn die Halle zwischen Auswahl und Tippen verschwunden ist —
                        // dann greift ohnehin gleich der Fremdschlüssel.
                        gymName = gymRepository.getById(gymId)?.name.orEmpty(),
                        gradeSystemId = gradeSystemId,
                        date = startedAt,
                        notes = notiz.trim().ifBlank { null },
                        endedAt = null,
                    )
                )
                // Gym-Näherungs-Push (M3): Session-Start zählt als Besuch fürs Besuchsmuster
                // (Tages-Dedupe im Repository — war heute schon ein Geofence-Besuch da, passiert nichts).
                gymVisitRepository.logVisit(
                    gymId = gymId,
                    timestamp = startedAt,
                    source = GymVisitEntity.SOURCE_SESSION,
                )
                // Widget-Snapshot nachziehen, damit dort sofort „Session öffnen" statt
                // „Session starten" steht (7.4c: sonst erst nach bis zu 30 min).
                refreshBoulderWidget(appContext)
                onCreated(newId)
            } finally {
                // Nach dem Erfolg ist der Screen ohnehin vom Stapel; wichtig ist der Fehlerfall:
                // eine Sperre, die nie aufgeht, wäre schlimmer als der doppelte Tap.
                legtAn = false
            }
        }
    }
}
