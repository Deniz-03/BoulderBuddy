package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.data.db.entity.GymEntity
import com.boulderbuddy.data.db.entity.HangboardTemplateEntity
import com.boulderbuddy.data.db.entity.HangboardWorkoutMode
import com.boulderbuddy.data.db.entity.HangboardWorkoutOrigin
import com.boulderbuddy.data.db.entity.SessionEntity
import com.boulderbuddy.data.settings.TimerConfig
import com.boulderbuddy.fake.FakeGymRepository
import com.boulderbuddy.fake.FakeHangboardRepository
import com.boulderbuddy.fake.FakeHangboardWorkoutRepository
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
    private val hangboardWorkouts = FakeHangboardWorkoutRepository()
    private val hangboard = FakeHangboardRepository()
    private val gyms = FakeGymRepository()

    private fun createViewModel() = HangboardTimerViewModel(
        settingsRepository = settings,
        sessionRepository = sessions,
        hangboardWorkoutRepository = hangboardWorkouts,
        hangboardRepository = hangboard,
        gymRepository = gyms,
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
    fun completingAllSets_reachesDoneAndAttachesWorkoutToActiveSession() =
        runTest(mainDispatcherRule.dispatcher) {
            gyms.all.value = listOf(GymEntity(id = 1, name = "Halle Nord"))
            sessions.active.value = SessionEntity(id = 5, gymId = 1, date = 0L)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onPlayPause()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.phase).isEqualTo(TimerPhase.DONE)
            assertThat(state.isRunning).isFalse()

            assertThat(hangboardWorkouts.created).hasSize(1)
            val created = hangboardWorkouts.created.first()
            assertThat(created.workout.sessionId).isEqualTo(5)
            assertThat(created.workout.mode).isEqualTo(HangboardWorkoutMode.MANUAL)
            assertThat(created.workout.origin).isEqualTo(HangboardWorkoutOrigin.PHONE)
            assertThat(created.workout.plannedSets).isEqualTo(2)
            assertThat(created.workout.plannedHangSec).isEqualTo(3)
            // Segmente aus der Vorgabe: 2 Sätze à 3s Hang, letzter Satz ohne Pause.
            assertThat(created.segments).hasSize(2)
            assertThat(created.segments.map { it.hangMs }).containsExactly(3_000L, 3_000L)
            assertThat(created.segments.last().restMs).isEqualTo(0L)
            // Speicherort-Feedback (§0 Säule 3) nennt die Session-Halle.
            assertThat(state.savedTo).contains("Halle Nord")
        }

    @Test
    fun completingWithoutActiveSession_recordsStandaloneWorkout() =
        runTest(mainDispatcherRule.dispatcher) {
            // Keine aktive Session (active bleibt null) → trotzdem speichern, ohne sessionId.
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onPlayPause()
            advanceUntilIdle()

            assertThat(vm.uiState.value.phase).isEqualTo(TimerPhase.DONE)
            assertThat(hangboardWorkouts.created).hasSize(1)
            assertThat(hangboardWorkouts.created.first().workout.sessionId).isNull()
            assertThat(vm.uiState.value.savedTo).contains("eigenständiges")
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
