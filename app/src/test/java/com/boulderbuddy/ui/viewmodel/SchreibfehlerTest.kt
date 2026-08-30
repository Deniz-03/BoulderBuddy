package com.boulderbuddy.ui.viewmodel

import com.boulderbuddy.R
import com.boulderbuddy.data.db.entity.RouteEntity
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.data.settings.TimerConfig
import com.boulderbuddy.fake.FakeGradeRepository
import com.boulderbuddy.fake.FakeGymRepository
import com.boulderbuddy.fake.FakeHangboardRepository
import com.boulderbuddy.fake.FakeHangboardWorkoutRepository
import com.boulderbuddy.fake.FakeHapticPlayer
import com.boulderbuddy.fake.FakeRouteRepository
import com.boulderbuddy.fake.FakeSessionRepository
import com.boulderbuddy.fake.FakeSettingsRepository
import com.boulderbuddy.fake.FakeWearConnection
import com.boulderbuddy.fake.FakeTexte
import com.boulderbuddy.ui.Fehlerkanal
import com.boulderbuddy.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Was passiert, wenn das Schreiben scheitert.
 *
 * Vorher lief jeder dieser Wege ungesichert in `viewModelScope`: eine Exception aus Room
 * beendete die App. Geprüft wird beides — dass eine Meldung ankommt, **und** dass die App
 * danach nicht so tut, als sei gespeichert worden.
 *
 * Nur die beiden ViewModels, die ohne Android-Context auskommen, stehen hier. Die drei
 * anderen (Boulder-Formular, Session anlegen, Session) brauchen für den Widget-Anstoß einen
 * echten Context und wären damit Instrumented-Tests; ihr Schutz ist derselbe Aufruf von
 * `Fehlerkanal.schreibe`, dessen Vertrag [com.boulderbuddy.ui.FehlerkanalTest] prüft.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchreibfehlerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * Startet einen Sammler auf dem Kanal und wartet, bis er abonniert hat.
     *
     * Der Kanal ist ein heisser Flow ohne Replay: wer erst nach der Tat zuhoert, hoert
     * nichts. Deshalb ausdruecklich vorher starten und `runCurrent()` - sonst schluege der
     * Test aus dem falschen Grund fehl.
     */
    private fun TestScope.sammle(): List<Int> {
        val gesehen = mutableListOf<Int>()
        backgroundScope.launch { fehlerkanal.meldungen.collect { gesehen += it } }
        runCurrent()
        return gesehen
    }

    /**
     * Laesst alles auslaufen, was das ViewModel angestossen hat - und danach den Sammler.
     *
     * Das abschliessende `runCurrent()` ist noetig und nicht bloss Vorsicht: faellt die
     * Meldung in die letzte Aufgabe, die `advanceUntilIdle()` abarbeitet, wird der Sammler
     * erst danach fortgesetzt. Ohne diese Zeile stand die Meldung im Kanal, und der Test
     * sah eine leere Liste - ein Messfehler, kein Befund.
     */
    private fun TestScope.laufenLassen() {
        advanceUntilIdle()
        runCurrent()
    }

    private val routes = FakeRouteRepository()
    private val sessions = FakeSessionRepository()
    private val grades = FakeGradeRepository()
    private val fehlerkanal = Fehlerkanal()

    // --- Boulder-Detail: Versuche hoch-/runterzählen ---------------------------------

    private fun boulderDetailViewModel() = BoulderDetailViewModel(
        boulderId = 1,
        routeRepository = routes,
        fehlerkanal = fehlerkanal,
        texte = FakeTexte(),
        gradeRepository = grades,
        sessionRepository = sessions,
    )

    private val boulder = RouteEntity(
        id = 1,
        sessionId = 1,
        gradeId = null,
        color = "red",
        name = "Dachrinne",
        sektor = "A",
        attempts = 2,
        status = RouteStatus.PROJECT,
        mediaUri = null,
        notes = null,
    )

    @Test
    fun versuche_erhoehen_meldet_den_fehlschlag() = runTest(mainDispatcherRule.dispatcher) {
        routes.all.value = listOf(boulder)
        routes.schreibfehler = true
        val vm = boulderDetailViewModel()

        val gesehen = sammle()
        vm.incrementAttempts()
        laufenLassen()

        assertThat(gesehen).containsExactly(R.string.fehler_versuche_speichern)
    }

    @Test
    fun versuche_erhoehen_laesst_den_wert_stehen() = runTest(mainDispatcherRule.dispatcher) {
        routes.all.value = listOf(boulder)
        routes.schreibfehler = true
        val vm = boulderDetailViewModel()

        vm.incrementAttempts()
        laufenLassen()

        // Nichts geschrieben, und der Bestand ist unverändert: 2 Versuche, nicht 3.
        assertThat(routes.updated).isEmpty()
        assertThat(routes.all.value.single().attempts).isEqualTo(2)
    }

    @Test
    fun versuche_erhoehen_geht_im_normalfall_durch() = runTest(mainDispatcherRule.dispatcher) {
        routes.all.value = listOf(boulder)
        val vm = boulderDetailViewModel()

        val gesehen = sammle()
        vm.incrementAttempts()
        laufenLassen()

        assertThat(gesehen).isEmpty()
        assertThat(routes.all.value.single().attempts).isEqualTo(3)
    }

    // --- Hangboard-Timer: fertiger Durchlauf ------------------------------------------

    private val settings = FakeSettingsRepository(TimerConfig(sets = 2, hangSec = 1, restSec = 0))
    private val hangboardWorkouts = FakeHangboardWorkoutRepository()
    private val hangboard = FakeHangboardRepository()
    private val gyms = FakeGymRepository()

    private fun timerViewModel() = HangboardTimerViewModel(
        settingsRepository = settings,
        sessionRepository = sessions,
        hangboardWorkoutRepository = hangboardWorkouts,
        hangboardRepository = hangboard,
        gymRepository = gyms,
        hapticPlayer = FakeHapticPlayer(),
        fehlerkanal = fehlerkanal,
        texte = FakeTexte(),
        wearConnection = FakeWearConnection(),
    )

    /**
     * Der teuerste Fehlschlag der App: der Durchlauf ist gehangen, und beim Speichern geht
     * etwas schief. Genau hier darf nichts stillschweigend verschwinden.
     */
    @Test
    fun fertiger_durchlauf_meldet_den_fehlschlag() = runTest(mainDispatcherRule.dispatcher) {
        hangboardWorkouts.schreibfehler = true
        val vm = timerViewModel()
        advanceUntilIdle()

        val gesehen = sammle()
        vm.onPlayPause()
        // 2 Sätze à 1 s ohne Pause — großzügig darüber hinaus laufen lassen.
        laufenLassen()

        assertThat(gesehen).containsExactly(R.string.fehler_workout_speichern)
    }

    /**
     * „In Session gespeichert" ist eine Zusicherung. Nach einem Fehlschlag darf sie nicht
     * dastehen — sonst behauptet die App das Gegenteil dessen, was passiert ist.
     */
    @Test
    fun nach_fehlschlag_steht_keine_speicher_zusicherung() =
        runTest(mainDispatcherRule.dispatcher) {
            hangboardWorkouts.schreibfehler = true
            val vm = timerViewModel()
            advanceUntilIdle()

            vm.onPlayPause()
            laufenLassen()

            assertThat(hangboardWorkouts.created).isEmpty()
            assertThat(vm.uiState.value.savedTo).isNull()
        }

    @Test
    fun preset_speichern_meldet_den_fehlschlag() = runTest(mainDispatcherRule.dispatcher) {
        hangboard.schreibfehler = true
        val vm = timerViewModel()
        advanceUntilIdle()

        val gesehen = sammle()
        vm.savePreset(name = "Repeater 7/3", sets = 6, hangSec = 7, restSec = 3)
        laufenLassen()

        assertThat(gesehen).containsExactly(R.string.fehler_preset_speichern)
        assertThat(hangboard.created).isEmpty()
    }
}
