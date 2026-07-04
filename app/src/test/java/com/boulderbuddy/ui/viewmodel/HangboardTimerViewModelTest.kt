package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.settings.TimerConfig
import com.boulderbuddy.fake.FakeHangboardRepository
import com.boulderbuddy.fake.FakeHangboardSessionRepository
import com.boulderbuddy.fake.FakeSessionRepository
import com.boulderbuddy.fake.FakeSettingsRepository
import com.boulderbuddy.ui.screens.TimerPhase
import com.boulderbuddy.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Zustandsmaschine des Hangboard-Timers (HANG → REST → HANG … → DONE) mit virtueller Zeit.
 * Konfiguration bewusst klein (2 Sätze, 3s Hang, 1s Pause) für kurze, deterministische Abläufe.
 *
 * Hinweis zur Zeitsteuerung: `advanceTimeBy(n)` führt Tasks *vor* dem neuen Zeitpunkt aus, ein
 * bei genau `n` fälliger `delay` erst mit anschließendem `runCurrent()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HangboardTimerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository(TimerConfig(sets = 2, hangSec = 3, restSec = 1))
    private val sessions = FakeSessionRepository()
    private val hangboardSessions = FakeHangboardSessionRepository()
    private val hangboard = FakeHangboardRepository()

    private fun createViewModel() = HangboardTimerViewModel(
        settingsRepository = settings,
        sessionRepository = sessions,
        hangboardSessionRepository = hangboardSessions,
        hangboardRepository = hangboard,
    )

    @Test
    fun initialState_reflectsPersistedConfig() = runTest(mainDispatcherRule.dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.totalSets).isEqualTo(2)
        assertThat(state.hangSec).isEqualTo(3)
        assertThat(state.restSec).isEqualTo(1)
        assertThat(state.phase).isEqualTo(TimerPhase.HANG)
        assertThat(state.currentSet).isEqualTo(1)
        assertThat(state.isRunning).isFalse()
    }

    @Test
    fun timer_advancesHangToRestToNextHang() = runTest(mainDispatcherRule.dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onPlayPause()
        assertThat(vm.uiState.value.isRunning).isTrue()

        // Hang-Phase (3s) abgelaufen → Pause, gleicher Satz.
        advanceTimeBy(3_000)
        runCurrent()
        assertThat(vm.uiState.value.phase).isEqualTo(TimerPhase.REST)
        assertThat(vm.uiState.value.currentSet).isEqualTo(1)

        // Pause (1s) abgelaufen → nächster Satz, wieder Hang.
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(vm.uiState.value.phase).isEqualTo(TimerPhase.HANG)
        assertThat(vm.uiState.value.currentSet).isEqualTo(2)
    }

    @Test
    fun completingAllSets_reachesDoneAndRecordsWorkoutInActiveSession() =
        runTest(mainDispatcherRule.dispatcher) {
            sessions.active.value = SessionEntity(id = 5, gymId = 1, date = 0L)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onPlayPause()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.phase).isEqualTo(TimerPhase.DONE)
            assertThat(state.isRunning).isFalse()

            assertThat(hangboardSessions.created).hasSize(1)
            val workout = hangboardSessions.created.first()
            assertThat(workout.sessionId).isEqualTo(5)
            assertThat(workout.totalSets).isEqualTo(2)
            assertThat(workout.completedSets).isEqualTo(2)
            assertThat(workout.hangSec).isEqualTo(3)
        }

    @Test
    fun completingWithoutActiveSession_recordsNothing() =
        runTest(mainDispatcherRule.dispatcher) {
            // Keine aktive Session (active bleibt null).
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onPlayPause()
            advanceUntilIdle()

            assertThat(vm.uiState.value.phase).isEqualTo(TimerPhase.DONE)
            assertThat(hangboardSessions.created).isEmpty()
        }

    @Test
    fun reset_returnsToFirstHangAndStops() = runTest(mainDispatcherRule.dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onPlayPause()
        advanceTimeBy(3_000)
        runCurrent()
        // Mitten in der Pause zurücksetzen.
        vm.onReset()

        val state = vm.uiState.value
        assertThat(state.phase).isEqualTo(TimerPhase.HANG)
        assertThat(state.currentSet).isEqualTo(1)
        assertThat(state.isRunning).isFalse()
    }

    @Test
    fun applyPreset_loadsPresetValuesAndPersistsAsLastUsed() =
        runTest(mainDispatcherRule.dispatcher) {
            hangboard.all.value = listOf(
                HangboardTemplateEntity(
                    id = 1, name = "Max Hangs", sets = 4, hangSec = 10, restSec = 5, repRestSec = 5,
                ),
            )
            val vm = createViewModel()
            advanceUntilIdle()

            vm.applyPreset(1)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.totalSets).isEqualTo(4)
            assertThat(state.hangSec).isEqualTo(10)
            assertThat(state.restSec).isEqualTo(5)
            // Config wird als "zuletzt genutzt" persistiert.
            assertThat(settings.timerConfigState.value.sets).isEqualTo(4)
        }
}
