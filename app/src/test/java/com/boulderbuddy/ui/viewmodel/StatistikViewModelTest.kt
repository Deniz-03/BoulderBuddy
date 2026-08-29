package com.boulderbuddy.ui.viewmodel

import app.cash.turbine.test
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.HangboardSegmentEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.HangboardWorkoutOrigin
import com.boulderbuddy.data.db.entity.HangboardWorkoutWithSegments
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.fake.FakeGradeRepository
import com.boulderbuddy.fake.FakeHangboardWorkoutRepository
import com.boulderbuddy.fake.FakeRouteRepository
import com.boulderbuddy.fake.FakeSessionRepository
import com.boulderbuddy.ui.model.Zeitraum
import com.boulderbuddy.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aggregations-Logik der Statistik: Flash-Rate, Tops, Hangboard-Summen und Grade-Verteilung.
 * Deterministisch über Fakes; die zeitbasierte Aktivitäts-Heatmap wird bewusst nicht geprüft
 * (hängt an `LocalDate.now()`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatistikViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessions = FakeSessionRepository()
    private val routes = FakeRouteRepository()
    private val grades = FakeGradeRepository()
    private val hangboardWorkouts = FakeHangboardWorkoutRepository()

    @Test
    fun aggregatesTopsFlashRateHangboardAndDistribution() =
        runTest(mainDispatcherRule.dispatcher) {
            grades.systems.value = listOf(GradeSystemEntity(id = 1, gymId = null, name = "Französisch"))
            grades.grades.value = listOf(
                GradeEntity(id = 10, systemId = 1, label = "6a", order = 0),
                GradeEntity(id = 11, systemId = 1, label = "6b", order = 1),
            )
            sessions.all.value = listOf(SessionEntity(id = 1, gymId = 1, date = 0L))
            routes.all.value = listOf(
                // Flash: getoppt mit 1 Versuch.
                RouteEntity(id = 1, sessionId = 1, gradeId = 10, status = RouteStatus.SENT, attempts = 1),
                // Top, aber kein Flash (3 Versuche).
                RouteEntity(id = 2, sessionId = 1, gradeId = 10, status = RouteStatus.SENT, attempts = 3),
                // Offen → zählt nicht als Top.
                RouteEntity(id = 3, sessionId = 1, gradeId = 11, status = RouteStatus.OPEN),
            )
            // 10 Sätze à 7s Hang (Segmente) — Hängezeit kommt aus den gemessenen Dauern.
            hangboardWorkouts.all.value = listOf(
                HangboardWorkoutWithSegments(
                    workout = HangboardWorkoutEntity(
                        id = 1, sessionId = 1,
                        mode = HangboardWorkoutMode.MANUAL,
                        origin = HangboardWorkoutOrigin.PHONE,
                        startedAt = 0L, endedAt = 0L,
                        plannedSets = 10, plannedHangSec = 7, plannedRestSec = 3,
                    ),
                    segments = List(10) { i ->
                        HangboardSegmentEntity(
                            id = i + 1, workoutId = 1, setIndex = i,
                            hangMs = 7_000L, restMs = if (i < 9) 3_000L else 0L,
                        )
                    },
                ),
            )

            val vm = StatistikViewModel(sessions, routes, grades, hangboardWorkouts)

            vm.uiState.test {
                // Auf den berechneten Zustand warten (erstes Item ist der initiale Leerzustand).
                var state = awaitItem()
                while (state.totalSessions == 0) state = awaitItem()

                assertThat(state.totalTops).isEqualTo(2)
                assertThat(state.flashRate).isEqualTo("50%")
                assertThat(state.totalSessions).isEqualTo(1)

                assertThat(state.hangboardWorkouts).isEqualTo(1)
                assertThat(state.hangboardSets).isEqualTo(10)
                // 10 Sätze × 7s = 70s → "1:10min" (Sekunden bleiben sichtbar).
                assertThat(state.hangboardHangTime).isEqualTo("1:10min")

                // Beide Tops sind "6a" im selben System → ein Balken mit Wert 2.
                assertThat(state.distributionSystems.map { it.id }).containsExactly(1)
                val entries = state.distributionBySystem.getValue(1)
                assertThat(entries).hasSize(1)
                assertThat(entries.first().label).isEqualTo("6a")
                assertThat(entries.first().value).isEqualTo(2f)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun noToppedRoutes_yieldsPlaceholderFlashRate() =
        runTest(mainDispatcherRule.dispatcher) {
            sessions.all.value = listOf(SessionEntity(id = 1, gymId = 1, date = 0L))
            routes.all.value = listOf(
                RouteEntity(id = 1, sessionId = 1, gradeId = null, status = RouteStatus.OPEN),
            )

            val vm = StatistikViewModel(sessions, routes, grades, hangboardWorkouts)

            vm.uiState.test {
                var state = awaitItem()
                while (state.totalSessions == 0) state = awaitItem()

                assertThat(state.totalTops).isEqualTo(0)
                assertThat(state.flashRate).isEqualTo("–")
                assertThat(state.hangboardHangTime).isEqualTo("–")
                assertThat(state.distributionBySystem).isEmpty()

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Nur die Verdrahtung: dass beide Verläufe für **alle drei** Körnungen im Zustand liegen.
     *
     * Der Umschalter im Screen wechselt ohne neue Datenbankabfrage — er greift in diese Maps.
     * Fehlte ein Schlüssel, liefe er in einen leeren Zustand. Was die Aggregation *rechnet*,
     * prüft `StatistikVerlaufTest` mit festem Stichtag; hier wird bewusst nichts über Werte
     * behauptet, weil das ViewModel `LocalDate.now()` benutzt.
     */
    @Test
    fun `beide Verlaeufe liegen fuer alle drei Koernungen im Zustand`() =
        runTest(mainDispatcherRule.dispatcher) {
            grades.systems.value = listOf(GradeSystemEntity(id = 1, gymId = null, name = "Französisch"))
            grades.grades.value = listOf(GradeEntity(id = 10, systemId = 1, label = "6a", order = 0))
            sessions.all.value = listOf(
                SessionEntity(id = 1, gymId = 1, date = System.currentTimeMillis()),
            )
            routes.all.value = listOf(
                RouteEntity(id = 1, sessionId = 1, gradeId = 10, status = RouteStatus.SENT, attempts = 1),
            )

            val vm = StatistikViewModel(sessions, routes, grades, hangboardWorkouts)

            vm.uiState.test {
                var state = awaitItem()
                while (state.totalSessions == 0) state = awaitItem()

                assertThat(state.routenVerlauf.keys).containsExactlyElementsIn(Zeitraum.entries)
                assertThat(state.gradVerlauf.keys).containsExactlyElementsIn(Zeitraum.entries)

                Zeitraum.entries.forEach { zeitraum ->
                    // Die Reihe ist vollständig — auch Abschnitte ohne Aktivität stehen drin.
                    assertThat(state.routenVerlauf.getValue(zeitraum)).hasSize(zeitraum.eimer)
                    // Das Top von heute liegt im jüngsten Abschnitt; die x-Achsen beider
                    // Diagramme müssen deckungsgleich sein, sonst liegen sie versetzt
                    // untereinander.
                    val kurve = state.gradVerlauf.getValue(zeitraum).getValue(1)
                    assertThat(kurve.map { it.label })
                        .isEqualTo(state.routenVerlauf.getValue(zeitraum).map { it.label })
                    assertThat(kurve.last().wertLabel).isEqualTo("6a")
                }

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `zwei klettertage ein jahr auseinander bekommen verschiedene chips`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Die Auswahlleiste zeigt die zehn jüngsten Klettertage. Wer selten klettert,
            // hat darin ohne Weiteres zwei Tage aus verschiedenen Jahren — und ohne
            // Jahreszahl trügen beide dieselbe Aufschrift.
            val juenger = LocalDate.now().minusDays(30).let {
                // Der 29. Februar hat kein Gegenstück im Vorjahr; dann einen Tag früher.
                if (it.monthValue == 2 && it.dayOfMonth == 29) it.minusDays(1) else it
            }
            val aelter = juenger.minusYears(1)
            // Sonst prüfte der Test nichts: verschiedene Tage heißen ohnehin verschieden.
            assertThat(aelter.dayOfMonth).isEqualTo(juenger.dayOfMonth)
            assertThat(aelter.month).isEqualTo(juenger.month)

            grades.systems.value = listOf(GradeSystemEntity(id = 1, gymId = null, name = "V-Scale"))
            grades.grades.value = listOf(GradeEntity(id = 10, systemId = 1, label = "V4", order = 4))
            sessions.all.value = listOf(
                SessionEntity(id = 1, gymId = 1, date = tagesBeginn(juenger)),
                SessionEntity(id = 2, gymId = 1, date = tagesBeginn(aelter)),
            )
            // Je ein getoppter Boulder MIT Grad — ein Tag ohne Grad fällt aus der Auswahl.
            routes.all.value = listOf(
                RouteEntity(id = 1, sessionId = 1, gradeId = 10, status = RouteStatus.SENT, attempts = 1),
                RouteEntity(id = 2, sessionId = 2, gradeId = 10, status = RouteStatus.SENT, attempts = 2),
            )

            val vm = StatistikViewModel(sessions, routes, grades, hangboardWorkouts)

            vm.uiState.test {
                var state = awaitItem()
                while (state.tage.size < 2) state = awaitItem()

                val aufschriften = state.tage.map { it.label }
                assertThat(aufschriften).containsNoDuplicates()
                assertThat(aufschriften).contains("${juenger.dayOfMonth}. ${monatsname(juenger)}")
                assertThat(aufschriften)
                    .contains("${aelter.dayOfMonth}. ${monatsname(aelter)} ${aelter.year}")

                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun tagesBeginn(tag: LocalDate): Long =
        tag.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Der deutsche Monatsname, wie ihn die Aufschrift verwendet. */
    private fun monatsname(tag: LocalDate): String =
        java.time.format.DateTimeFormatter.ofPattern("MMMM", java.util.Locale.GERMAN).format(tag)
}
