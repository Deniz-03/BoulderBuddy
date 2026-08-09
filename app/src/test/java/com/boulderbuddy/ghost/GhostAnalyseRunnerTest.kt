package com.boulderbuddy.ghost

import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ghost.pose.PoseSpurQuelle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Der Lebenslauf einer Hintergrund-Analyse (7.5h).
 *
 * Geprüft wird genau das, was der Bildschirm nicht mehr selbst in der Hand hat: dass ein Lauf
 * ohne ihn zu Ende kommt, dass ein Abbruch wirklich abbricht, und dass ein Ergebnis genau
 * einmal abgeholt wird. Die Extraktion selbst braucht Video, MediaPipe und Minuten — sie
 * steckt hinter [PoseSpurQuelle] und ist hier eine Attrappe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GhostAnalyseRunnerTest {

    private val refUri = "content://videos/1"
    private val cmpUri = "content://videos/2"

    private fun fertigeSpur(uri: String) = GhostPoseTrack(
        videoUri = uri,
        frameWidth = 720,
        frameHeight = 1280,
        durationMs = 24_000,
        sampleFps = 12.0,
        frames = emptyList(),
    )

    /**
     * Zählt die Aufrufe und meldet auf Wunsch Fortschritt. [haltAn] pausiert die Extraktion
     * an einer bekannten Stelle — nur so lässt sich ein „läuft gerade" überhaupt beobachten.
     */
    private inner class FakeQuelle(
        private val fortschrittBis: Int = 0,
        private val haltAn: Boolean = false,
        private val fehler: Exception? = null,
    ) : PoseSpurQuelle {
        val angefragt = mutableListOf<String>()

        override suspend fun spur(
            videoUri: String,
            onFortschritt: (fertig: Int, gesamt: Int) -> Unit,
        ): GhostPoseTrack {
            angefragt += videoUri
            fehler?.let { throw it }
            repeat(fortschrittBis) { onFortschritt(it + 1, fortschrittBis) }
            // Aufsetzpunkt: hier wirkt ein `cancel`, und hier steht der Lauf still, solange
            // der Test den laufenden Zustand ansehen will. Bewusst ein Warten, das nie
            // wieder eingeplant wird — eine `yield`-Schleife käme aus `advanceUntilIdle`
            // nie heraus.
            if (haltAn) CompletableDeferred<Unit>().await()
            return fertigeSpur(videoUri)
        }
    }

    @Test
    fun `beide Videos werden nacheinander extrahiert und als fertig gemeldet`() = runTest {
        val quelle = FakeQuelle()
        val runner = GhostAnalyseRunner(quelle, StandardTestDispatcher(testScheduler))

        runner.starte(refUri, cmpUri)
        advanceUntilIdle()

        assertThat(quelle.angefragt).containsExactly(refUri, cmpUri).inOrder()
        val stand = runner.stand.value
        assertThat(stand).isInstanceOf(GhostAnalyseStand.Fertig::class.java)
        with(stand as GhostAnalyseStand.Fertig) {
            assertThat(refTrack.videoUri).isEqualTo(refUri)
            assertThat(cmpTrack.videoUri).isEqualTo(cmpUri)
        }
    }

    @Test
    fun `Fortschritt zeigt das Video an der Reihe`() = runTest {
        val runner = GhostAnalyseRunner(
            FakeQuelle(fortschrittBis = 289, haltAn = true),
            StandardTestDispatcher(testScheduler),
        )

        runner.starte(refUri, cmpUri)
        advanceUntilIdle()

        val stand = runner.stand.value as GhostAnalyseStand.Laeuft
        assertThat(stand.ref).isEqualTo(Fortschritt(289, 289))
        // Solange die Referenz läuft, ist der Vergleich unangetastet — daran (und nur daran)
        // erkennt die Notification, welches der beiden Videos sie gerade benennt.
        assertThat(stand.cmp).isEqualTo(Fortschritt())
        assertThat(stand.beiVergleich).isFalse()
        assertThat(stand.anteil).isEqualTo(1f)
        runner.brichAb()
    }

    @Test
    fun `Abbruch beendet den Lauf und raeumt den Zustand`() = runTest {
        val runner = GhostAnalyseRunner(
            FakeQuelle(haltAn = true),
            StandardTestDispatcher(testScheduler),
        )

        runner.starte(refUri, cmpUri)
        advanceUntilIdle()
        assertThat(runner.stand.value).isInstanceOf(GhostAnalyseStand.Laeuft::class.java)

        runner.brichAb()
        advanceUntilIdle()

        // Nicht „fertig", nicht „Fehler": ein abgebrochener Lauf hinterlässt nichts.
        assertThat(runner.stand.value).isEqualTo(GhostAnalyseStand.Untaetig)
    }

    @Test
    fun `ein zweiter Start waehrend eines Laufs wird ignoriert`() = runTest {
        val quelle = FakeQuelle(haltAn = true)
        val runner = GhostAnalyseRunner(quelle, StandardTestDispatcher(testScheduler))

        runner.starte(refUri, cmpUri)
        advanceUntilIdle()
        runner.starte("content://videos/9", "content://videos/10")
        advanceUntilIdle()

        // Sonst liefen zwei MediaPipe-Landmarker gleichzeitig auf demselben Gerät.
        assertThat(quelle.angefragt).containsExactly(refUri)
        runner.brichAb()
    }

    @Test
    fun `eine gescheiterte Extraktion wird als Fehler gemeldet`() = runTest {
        val runner = GhostAnalyseRunner(
            FakeQuelle(fehler = IllegalStateException("Video zu kurz für eine Analyse")),
            StandardTestDispatcher(testScheduler),
        )

        runner.starte(refUri, cmpUri)
        advanceUntilIdle()

        val stand = runner.stand.value as GhostAnalyseStand.Fehler
        assertThat(stand.meldung).isEqualTo("Video zu kurz für eine Analyse")
    }

    @Test
    fun `quittieren macht den Weg fuer den naechsten Lauf frei`() = runTest {
        val runner = GhostAnalyseRunner(FakeQuelle(), StandardTestDispatcher(testScheduler))

        runner.starte(refUri, cmpUri)
        advanceUntilIdle()
        runner.quittiere()

        // Ohne das spränge jeder neu aufgebaute Bildschirm wieder in die Anker-Ansicht.
        assertThat(runner.stand.value).isEqualTo(GhostAnalyseStand.Untaetig)
    }

    @Test
    fun `quittieren beruehrt einen laufenden Lauf nicht`() = runTest {
        val runner = GhostAnalyseRunner(
            FakeQuelle(haltAn = true),
            StandardTestDispatcher(testScheduler),
        )

        runner.starte(refUri, cmpUri)
        advanceUntilIdle()
        runner.quittiere()

        assertThat(runner.stand.value).isInstanceOf(GhostAnalyseStand.Laeuft::class.java)
        runner.brichAb()
    }

    @Test
    fun `warteAufEnde kehrt erst nach dem Lauf zurueck`() = runTest {
        val runner = GhostAnalyseRunner(FakeQuelle(), StandardTestDispatcher(testScheduler))

        runner.starte(refUri, cmpUri)
        // Genau der Weg, auf dem der Dienst sein Ende erkennt — nicht über den Zustand,
        // den der Bildschirm ihm wegquittieren könnte.
        runner.warteAufEnde()

        assertThat(runner.stand.value).isInstanceOf(GhostAnalyseStand.Fertig::class.java)
    }
}
