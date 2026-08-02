package com.boulderbuddy.ui.viewmodel

import app.cash.turbine.test
import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.fake.FakeGradeRepository
import com.boulderbuddy.fake.FakeGymRepository
import com.boulderbuddy.fake.FakeRouteRepository
import com.boulderbuddy.fake.FakeSessionRepository
import com.boulderbuddy.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Sortierung der Session-Übersicht. Kern-Zusage: die aktive Session steht in *jeder*
 * Sortierung oben, alles darunter folgt dem gewählten Kriterium und der Richtung.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessions = FakeSessionRepository()
    private val routes = FakeRouteRepository()
    private val gyms = FakeGymRepository()
    private val grades = FakeGradeRepository()

    // Zwei Hallen, drei abgeschlossene Sessions + eine aktive (id 4).
    // Boulder-Anzahl: Session 1 → 3, Session 3 → 1, Sessions 2 und 4 → 0.
    private fun seed() {
        gyms.all.value = listOf(
            GymEntity(id = 1, name = "Zenit"),
            GymEntity(id = 2, name = "Alpenblick"),
        )
        sessions.all.value = listOf(
            SessionEntity(id = 1, gymId = 1, date = 300L, endedAt = 400L),
            SessionEntity(id = 2, gymId = 2, date = 100L, endedAt = 200L),
            SessionEntity(id = 3, gymId = 1, date = 200L, endedAt = 300L),
            SessionEntity(id = 4, gymId = 2, date = 500L, endedAt = null),
        )
        routes.all.value = listOf(
            RouteEntity(id = 1, sessionId = 1),
            RouteEntity(id = 2, sessionId = 1),
            RouteEntity(id = 3, sessionId = 1),
            RouteEntity(id = 4, sessionId = 3),
        )
    }

    private fun createViewModel() = SessionListViewModel(
        sessionRepository = sessions,
        routeRepository = routes,
        gymRepository = gyms,
        gradeRepository = grades,
    )

    @Test
    fun defaultSort_isNewestFirstWithActiveSessionOnTop() =
        runTest(mainDispatcherRule.dispatcher) {
            seed()
            val vm = createViewModel()

            vm.uiState.test {
                // Erstes Item ist der initiale Leerzustand.
                var state = awaitItem()
                while (state.sessions.isEmpty()) state = awaitItem()

                assertThat(state.sortMode).isEqualTo(SessionSortMode.DATUM)
                assertThat(state.sortDescending).isTrue()
                assertThat(state.sessions.map { it.id }).containsExactly(4, 1, 3, 2).inOrder()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun selectingSameMode_flipsDirection() = runTest(mainDispatcherRule.dispatcher) {
        seed()
        val vm = createViewModel()

        vm.uiState.test {
            var state = awaitItem()
            while (state.sessions.isEmpty()) state = awaitItem()

            vm.setSortMode(SessionSortMode.DATUM)
            state = awaitItem()

            assertThat(state.sortDescending).isFalse()
            // Aufsteigend nach Datum — die aktive Session bleibt trotzdem oben.
            assertThat(state.sessions.map { it.id }).containsExactly(4, 2, 3, 1).inOrder()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sortByGym_ordersAlphabeticallyAndKeepsActiveOnTop() =
        runTest(mainDispatcherRule.dispatcher) {
            seed()
            val vm = createViewModel()

            vm.uiState.test {
                var state = awaitItem()
                while (state.sessions.isEmpty()) state = awaitItem()

                // Erste Wahl eines neuen Kriteriums startet absteigend: Zenit vor Alpenblick,
                // die aktive Session (Alpenblick) steht davor.
                vm.setSortMode(SessionSortMode.HALLE)
                state = awaitItem()
                assertThat(state.sessions.map { it.gym })
                    .containsExactly("Alpenblick", "Zenit", "Zenit", "Alpenblick").inOrder()

                // Erneut dasselbe Kriterium → aufsteigend.
                vm.setSortMode(SessionSortMode.HALLE)
                state = awaitItem()
                assertThat(state.sessions.map { it.gym })
                    .containsExactly("Alpenblick", "Alpenblick", "Zenit", "Zenit").inOrder()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun sortByBoulderCount_ordersByNumberOfRoutes() = runTest(mainDispatcherRule.dispatcher) {
        seed()
        val vm = createViewModel()

        vm.uiState.test {
            var state = awaitItem()
            while (state.sessions.isEmpty()) state = awaitItem()

            vm.setSortMode(SessionSortMode.BOULDER)
            state = awaitItem()

            // Aktive Session (4) oben, danach absteigend: 3 Boulder → 1 → 0.
            assertThat(state.sessions.map { it.id }).containsExactly(4, 1, 3, 2).inOrder()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
