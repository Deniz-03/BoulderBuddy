package com.boulderbuddy.ui

import com.boulderbuddy.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Der Vertrag des [Fehlerkanal]s — die eine Stelle, an der die App entscheidet, was ein
 * fehlgeschlagener Schreibvorgang bedeutet.
 *
 * Geprüft wird nicht, dass eine Snackbar erscheint (das macht Compose), sondern die vier
 * Zusagen, auf die sich jeder Aufrufer verlässt: es kommt eine Meldung, der Aufrufer erfährt
 * den Fehlschlag, ein Abbruch ist kein Fehler, und ohne Zuhörer staut sich nichts an.
 *
 * **Zur Testform:** der Sammler wird ausdrücklich vor der Tat gestartet und mit `runCurrent()`
 * zum Abonnieren gebracht. Der Kanal ist ein heißer Flow ohne Replay — wer erst nach dem
 * Auslösen zuhört, hört nichts, und der Test schlüge aus dem falschen Grund fehl.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FehlerkanalTest {

    /** Startet einen Sammler und wartet, bis er wirklich abonniert hat. */
    private fun TestScope.sammle(kanal: Fehlerkanal): List<Int> {
        val gesehen = mutableListOf<Int>()
        backgroundScope.launch { kanal.meldungen.collect { gesehen += it } }
        runCurrent()
        return gesehen
    }

    @Test
    fun erfolg_meldet_nichts_und_sagt_true() = runTest {
        val kanal = Fehlerkanal()
        val gesehen = sammle(kanal)

        val ergebnis = kanal.schreibe(R.string.fehler_boulder_speichern, "Test") { }

        assertThat(ergebnis).isTrue()
        assertThat(gesehen).isEmpty()
    }

    @Test
    fun fehlschlag_meldet_den_text_und_sagt_false() = runTest {
        val kanal = Fehlerkanal()
        val gesehen = sammle(kanal)

        val ergebnis = kanal.schreibe(R.string.fehler_boulder_speichern, "Test") {
            error("Datenbank kaputt")
        }
        runCurrent()

        assertThat(ergebnis).isFalse()
        assertThat(gesehen).containsExactly(R.string.fehler_boulder_speichern)
    }

    /**
     * Ein Abbruch ist kein Fehler, sondern das Ende des Scopes — der Nutzer hat den Screen
     * verlassen. Bliebe die [CancellationException] hier hängen, bekäme er für sein eigenes
     * Zurücktippen eine Fehlermeldung, und die Struktur der Coroutinen wäre gebrochen.
     */
    @Test
    fun abbruch_wird_durchgereicht_und_nicht_gemeldet() = runTest {
        val kanal = Fehlerkanal()
        val gesehen = sammle(kanal)

        var durchgereicht = false
        try {
            kanal.schreibe(R.string.fehler_boulder_speichern, "Test") {
                throw CancellationException("Screen verlassen")
            }
        } catch (e: CancellationException) {
            durchgereicht = true
        }
        runCurrent()

        assertThat(durchgereicht).isTrue()
        assertThat(gesehen).isEmpty()
    }

    /**
     * Ohne Zuhörer geht eine Meldung verloren, statt sich anzustauen.
     *
     * Das ist Absicht und der Grund für `replay = 0`: eine Fehlermeldung, die erst drei
     * Bildschirme später auftaucht, verwirrt mehr, als sie hilft — und nach einer Drehung
     * des Geräts würde ein gepufferter Wert den alten Fehler erneut zeigen.
     */
    @Test
    fun ohne_zuhoerer_staut_sich_nichts_an() = runTest {
        val kanal = Fehlerkanal()

        kanal.melde(R.string.fehler_notiz_speichern)
        kanal.melde(R.string.fehler_session_beenden)

        val gesehen = sammle(kanal)
        runCurrent()
        assertThat(gesehen).isEmpty()
    }
}
