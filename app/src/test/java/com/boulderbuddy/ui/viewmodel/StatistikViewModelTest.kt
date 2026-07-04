package com.boulderbuddy.ui.viewmodel

import app.cash.turbine.test
import com.boulderbuddy.data.db.entity.GradeEntity
import com.boulderbuddy.data.db.entity.GradeSystemEntity
import com.boulderbuddy.data.db.entity.HangboardSessionEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.fake.FakeGradeRepository
import com.boulderbuddy.fake.FakeHangboardSessionRepository
import com.boulderbuddy.fake.FakeRouteRepository
import com.boulderbuddy.fake.FakeSessionRepository
import com.boulderbuddy.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

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
    private val hangboardSessions = FakeHangboardSessionRepository()

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
            hangboardSessions.all.value = listOf(
                HangboardSessionEntity(
                    id = 1, sessionId = 1, completedSets = 10, totalSets = 10,
                    hangSec = 7, restSec = 3, date = 0L,
                ),
            )

            val vm = StatistikViewModel(sessions, routes, grades, hangboardSessions)

            vm.uiState.test {
                // Auf den berechneten Zustand warten (erstes Item ist der initiale Leerzustand).
                var state = awaitItem()
                while (state.totalSessions == 0) state = awaitItem()

                assertThat(state.totalTops).isEqualTo(2)
                assertThat(state.flashRate).isEqualTo("50%")
                assertThat(state.totalSessions).isEqualTo(1)

                assertThat(state.hangboardWorkouts).isEqualTo(1)
                assertThat(state.hangboardSets).isEqualTo(10)
                // 10 Sätze × 7s = 70s → "1min".
                assertThat(state.hangboardHangTime).isEqualTo("1min")

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

            val vm = StatistikViewModel(sessions, routes, grades, hangboardSessions)

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
}
