package com.boulderbuddy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.data.repository.GradeRepository
import com.boulderbuddy.data.repository.HangboardWorkoutRepository
import com.boulderbuddy.data.repository.RouteRepository
import com.boulderbuddy.data.repository.SessionRepository
import com.boulderbuddy.ui.components.BarChartEntry
import com.boulderbuddy.ui.components.LinePoint
import com.boulderbuddy.ui.model.Zeitraum
import com.boulderbuddy.ui.model.formatRelativeDay
import com.boulderbuddy.ui.model.eimerLabel
import com.boulderbuddy.ui.model.eimerReihe
import com.boulderbuddy.ui.model.eimerStart
import com.boulderbuddy.ui.model.formatDayMonth
import com.boulderbuddy.ui.model.formatHangTime
import com.boulderbuddy.ui.model.istGetoppt
import com.boulderbuddy.ui.model.toLocalDate
import com.boulderbuddy.ui.theme.routeColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

// =============================================================================
// Statistik — alles Gerechnete für den Statistik-Tab
// =============================================================================
//
// Aggregiert Sessions, Boulder, Grade und Hangboard-Workouts zu Kennzahlen, Verläufen und
// der Aktivitäts-Heatmap. Der Screen bekommt ausschließlich fertige Werte: formatierte
// Texte und Listen von Balkenhöhen, keine Entities und keine Rechenvorschrift.
//
// Ghost-Analysen gehen hier bewusst NICHT ein. Sie hängen zwar an einer Session, sind aber
// kein Trainingsergebnis, das sich sinnvoll summieren ließe.
//
// Die Regeln, die man beim Ändern kennen muss (jeweils am Ort begründet): Grade werden nur
// innerhalb desselben Gradsystems verglichen, und der Verlauf zeigt den höchsten und nicht
// den durchschnittlichen Grad.

data class StatistikUiState(
    val flashRate: String = "–",
    val totalTops: Int = 0,
    val totalSessions: Int = 0,
    /** Systeme, in denen getoppt wurde — Umschalter über der Grade-Verteilung. */
    val distributionSystems: List<GradeSystemFilterUi> = emptyList(),
    /** Je System die Grade-Verteilung (ein Balken je Grad, in Grad-Reihenfolge). */
    val distributionBySystem: Map<Int, List<BarChartEntry>> = emptyMap(),
    val activity: List<Float> = emptyList(),
    /** Konkreter Zeitraum der Heatmap, z.B. "6. Juli – 2. August"; leer = keine Angabe. */
    val activityRange: String = "",
    // --- Verläufe über die Zeit ---
    /**
     * Routen je Zeitabschnitt, **unkumuliert** — ein Balken je Woche/Monat/Jahr, Abschnitte
     * ohne Aktivität stehen mit 0 drin. Alle drei Körnungen werden vorgehalten, damit der
     * Umschalter im Screen ohne neue Datenbankabfrage auskommt; die Datenmenge ist klein.
     */
    val routenVerlauf: Map<Zeitraum, List<BarChartEntry>> = emptyMap(),
    /**
     * Höchster getoppter Grad je Zeitabschnitt, **je Gradsystem** (Schlüssel = systemId).
     *
     * Die Trennung nach System ist keine Bequemlichkeit: `order` zählt pro System, und ein
     * „V4" ist gegen ein „6b" nicht verrechenbar. Eine systemübergreifende Kurve wäre eine
     * Linie durch zwei verschiedene Maßstäbe.
     */
    val gradVerlauf: Map<Zeitraum, Map<Int, List<LinePoint>>> = emptyMap(),
    // --- Hangboard-Training ---
    /** Anzahl abgeschlossener Hangboard-Workouts (Phone+Uhr, manuell+auto, auch ohne Session). */
    val hangboardWorkouts: Int = 0,
    /** Summe aller absolvierten Sätze (= Segmente) über alle Workouts. */
    val hangboardSets: Int = 0,
    /** Gesamte Hängezeit (Summe der gemessenen Segment-Hängezeiten) als Kurzform. */
    val hangboardHangTime: String = "–",
    // --- Einzelner Tag ---
    /**
     * Die jüngsten Klettertage für die Auswahlleiste, neuester zuerst; leer = noch nie
     * geklettert, dann entfällt der Abschnitt.
     */
    val tage: List<TagUi> = emptyList(),
    /**
     * Verlauf je Tag und Gradsystem. Vorgehalten für alle Tage aus [tage], damit das
     * Umschalten ohne neue Abfrage auskommt — es sind wenige Tage mit wenigen Bouldern.
     */
    val tagesstatistik: Map<LocalDate, Map<Int, TagesstatistikUi>> = emptyMap(),
    /** Namen der Gradsysteme, für die Umschaltleiste über der Tageskurve. */
    val systemNamen: Map<Int, String> = emptyMap(),
)

// Anzahl der Tage in der Aktivitäts-Heatmap (4 Wochen à 7 Tage).
private const val ACTIVITY_DAYS = 28

// Wie viele Klettertage die Auswahlleiste der Tagesstatistik anbietet. Zehn reichen: weiter
// zurück erinnert man den einzelnen Tag ohnehin nicht, und die Leiste soll eine Leiste
// bleiben statt zu einem Kalender zu werden.
//
// In eine Bildschirmzeile passen zehn Tage nicht — die Leiste scrollt deshalb waagerecht
// (siehe StatistikScreen).
private const val TAGE_ZUR_AUSWAHL = 10

// Einheitliche Balkenfarbe der Verlaufs-Diagramme. Index in die Route-Palette, damit sie aus
// demselben Vorrat stammt wie alles andere und nicht als Sonderfarbe danebensteht.
private const val VERLAUF_FARBE_INDEX = 4

@HiltViewModel
class StatistikViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    routeRepository: RouteRepository,
    gradeRepository: GradeRepository,
    hangboardWorkoutRepository: HangboardWorkoutRepository,
) : ViewModel() {

    val uiState: StateFlow<StatistikUiState> = combine(
        sessionRepository.observeAll(),
        routeRepository.observeAll(),
        gradeRepository.observeAllGrades(),
        gradeRepository.observeAllSystems(),
        hangboardWorkoutRepository.observeAll(),
    ) { sessions, routes, grades, systems, hangboardWorkouts ->
        val gradesById = grades.associateBy { it.id }
        val sessionsById = sessions.associateBy { it.id }
        val heute = LocalDate.now()

        val tagesstatistik = tagesstatistiken(
            routen = routes,
            sessionsById = sessionsById,
            gradesById = gradesById,
            maxTage = TAGE_ZUR_AUSWAHL,
        )
        val topped = routes.filter { it.status.istGetoppt }
        val flashes = topped.count { it.attempts <= 1 }
        val flashRate = if (topped.isEmpty()) "–" else "${flashes * 100 / topped.size}%"

        // Hängezeit = Summe der gemessenen Segment-Dauern (bei AUTO korrekt, bei MANUAL
        // identisch zur früheren Plan-Rechnung completedSets × hangSec).
        val hangboardSets = hangboardWorkouts.sumOf { it.segments.size }
        val hangboardHangMs = hangboardWorkouts.sumOf { it.totalHangMs }

        // Grade-Verteilung pro System (systemübergreifende Sortierung wäre bedeutungslos, da
        // `order` pro System zählt und Labels verschiedener Systeme nicht vergleichbar sind).
        val distributionBySystem = distributionBySystem(topped, gradesById)
        val distributionSystems = systems
            .filter { it.id in distributionBySystem.keys }
            .map { GradeSystemFilterUi(it.id, it.name) }

        StatistikUiState(
            flashRate = flashRate,
            totalTops = topped.size,
            totalSessions = sessions.size,
            distributionSystems = distributionSystems,
            distributionBySystem = distributionBySystem,
            activity = activity(routes, sessionsById, heute),
            activityRange = activityRange(heute),
            // `heute` wird hier EINMAL bestimmt und in beide Verläufe gereicht, statt dass
            // jede Funktion für sich `LocalDate.now()` fragt: sonst könnten die zwei
            // Diagramme über einen Mitternachtswechsel hinweg verschiedene Eimer-Reihen
            // zeichnen und wären untereinander verschoben.
            routenVerlauf = Zeitraum.entries.associateWith { zeitraum ->
                routenVerlauf(routes, sessionsById, zeitraum, heute)
            },
            gradVerlauf = Zeitraum.entries.associateWith { zeitraum ->
                gradVerlauf(topped, sessionsById, gradesById, zeitraum, heute)
            },
            hangboardWorkouts = hangboardWorkouts.size,
            hangboardSets = hangboardSets,
            // formatHangTime statt formatDurationShort: ein 30-Sekunden-Durchlauf würde beim
            // Abrunden auf ganze Minuten sonst als "0min" erscheinen.
            hangboardHangTime = if (hangboardWorkouts.isEmpty()) "–"
                else formatHangTime(hangboardHangMs),
            tage = tagesstatistik.keys.sortedDescending().map { tag ->
                // „Heute", „Gestern", sonst das Datum — dieselbe Sprache wie in der
                // Sessions-Liste, damit derselbe Tag nicht zweimal anders heißt.
                TagUi(datum = tag, label = formatRelativeDay(tag, heute))
            },
            tagesstatistik = tagesstatistik,
            systemNamen = systems.associate { it.id to it.name },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatistikUiState(),
    )

    // Getoppte Boulder je System nach Grad gruppiert; ein Balken je Grad, in Grad-Reihenfolge.
    // Die Farbe ist rein dekorativ (zyklisch aus der Route-Palette) — Grade sind bewusst nicht
    // farbcodiert. Nur Systeme mit getoppten Bouldern erscheinen als Schlüssel.
    private fun distributionBySystem(
        topped: List<RouteEntity>,
        gradesById: Map<Int, GradeEntity>,
    ): Map<Int, List<BarChartEntry>> =
        topped
            .mapNotNull { it.gradeId?.let(gradesById::get) }
            .groupBy { it.systemId }
            .mapValues { (_, systemGrades) ->
                systemGrades
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedBy { it.key.order }
                    .mapIndexed { index, (grade, count) ->
                        BarChartEntry(
                            label = grade.label,
                            value = count.toFloat(),
                            color = routeColorPalette[index % routeColorPalette.size].second,
                        )
                    }
            }

    // Boulder pro Tag über die letzten [ACTIVITY_DAYS] Tage, auf 0f..1f normalisiert.
    // Jede Route wird über das Datum ihrer Session einem Tag zugeordnet.
    private fun activity(
        routes: List<RouteEntity>,
        sessionsById: Map<Int, SessionEntity>,
        today: LocalDate,
    ): List<Float> {
        val startDay = today.minusDays((ACTIVITY_DAYS - 1).toLong())

        val countsByDay = HashMap<LocalDate, Int>()
        routes.forEach { route ->
            val session = sessionsById[route.sessionId] ?: return@forEach
            val day = session.date.toLocalDate()
            if (!day.isBefore(startDay) && !day.isAfter(today)) {
                countsByDay[day] = (countsByDay[day] ?: 0) + 1
            }
        }
        val max = (countsByDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
        return (0 until ACTIVITY_DAYS).map { offset ->
            val day = startDay.plusDays(offset.toLong())
            (countsByDay[day] ?: 0).toFloat() / max
        }
    }

    // Konkrete Datumsspanne der Heatmap, passend zu [activity]. Statt der vagen Angabe
    // "letzte 4 Wochen" sieht man, welcher Zeitraum tatsächlich dargestellt ist.
    private fun activityRange(today: LocalDate): String {
        val startDay = today.minusDays((ACTIVITY_DAYS - 1).toLong())
        return "${formatDayMonth(startDay)} – ${formatDayMonth(today)}"
    }
}

/*
 * Die beiden Verlaufs-Aggregationen stehen bewusst AUSSERHALB der Klasse.
 *
 * Sie waren nie an den ViewModel-Zustand gebunden — alles, was sie brauchen, steht in ihren
 * Parametern. Als private Methoden waren sie deshalb nur eines: unprüfbar. Auf Dateiebene und
 * mit `heute` als Pflichtparameter sind es reine Funktionen, die ein Test mit einem festen
 * Datum aufrufen kann, ohne eine Uhr zu injizieren (siehe `StatistikVerlaufTest`).
 */

/**
 * Routen je Zeitabschnitt — **unkumuliert**: jeder Balken zählt nur, was in *diesem*
 * Abschnitt geklettert wurde.
 *
 * Abschnitte ohne Aktivität erscheinen mit dem Wert 0 und nicht etwa gar nicht. Das ist
 * der Punkt der Darstellung: eine Lücke im Training soll als Lücke sichtbar sein und
 * nicht dadurch verschwinden, dass zwei aktive Wochen nebeneinander rutschen.
 *
 * Die Farbe ist einheitlich, nicht zyklisch wie bei der Grade-Verteilung: dort steht jede
 * Farbe für einen anderen Grad, hier wäre sie bedeutungslos und würde Unterschiede
 * behaupten, wo nur Zeit vergeht.
 */
internal fun routenVerlauf(
    routes: List<RouteEntity>,
    sessionsById: Map<Int, SessionEntity>,
    zeitraum: Zeitraum,
    heute: LocalDate,
): List<BarChartEntry> {
    val proEimer = routes
        .mapNotNull { route -> sessionsById[route.sessionId]?.date?.toLocalDate() }
        .groupingBy { tag -> eimerStart(tag, zeitraum) }
        .eachCount()

    return eimerReihe(heute, zeitraum).map { start ->
        BarChartEntry(
            label = eimerLabel(start, zeitraum),
            value = (proEimer[start] ?: 0).toFloat(),
            color = routeColorPalette[VERLAUF_FARBE_INDEX].second,
        )
    }
}

/**
 * Höchster getoppter Grad je Zeitabschnitt und System.
 *
 * **Höchster, nicht durchschnittlicher.** Ein Mittelwert sinkt, sobald man an einem Tag
 * viel Leichtes zum Aufwärmen klettert — er misst dann die Zusammensetzung der Session
 * statt des Könnens. Das Maximum beantwortet die Frage, um die es hier geht: „Was habe
 * ich in dieser Zeit geschafft?"
 *
 * Der Kurvenwert ist die Ordnungszahl des Grades (`order`), weil nur die eine Reihenfolge
 * kennt; angezeigt wird das Label. Abschnitte ohne Top ergeben `null` — keinen Nullpunkt,
 * siehe [LinePoint].
 */
internal fun gradVerlauf(
    topped: List<RouteEntity>,
    sessionsById: Map<Int, SessionEntity>,
    gradesById: Map<Int, GradeEntity>,
    zeitraum: Zeitraum,
    heute: LocalDate,
): Map<Int, List<LinePoint>> {
    // (systemId, Eimerstart) → bester Grad dieses Abschnitts
    val bester = HashMap<Pair<Int, LocalDate>, GradeEntity>()
    topped.forEach { route ->
        val grade = route.gradeId?.let(gradesById::get) ?: return@forEach
        val tag = sessionsById[route.sessionId]?.date?.toLocalDate() ?: return@forEach
        val schluessel = grade.systemId to eimerStart(tag, zeitraum)
        val bisher = bester[schluessel]
        if (bisher == null || grade.order > bisher.order) bester[schluessel] = grade
    }

    val reihe = eimerReihe(heute, zeitraum)
    return bester.keys.map { it.first }.distinct().associateWith { systemId ->
        reihe.map { start ->
            val grade = bester[systemId to start]
            LinePoint(
                label = eimerLabel(start, zeitraum),
                wert = grade?.order?.toFloat(),
                wertLabel = grade?.label,
            )
        }
    }
}
