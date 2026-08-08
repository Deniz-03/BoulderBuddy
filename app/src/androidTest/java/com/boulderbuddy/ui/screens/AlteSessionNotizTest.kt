package com.boulderbuddy.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Die Notiz der abgeschlossenen Session muss **beim Tippen** gespeichert werden.
 *
 * Vorher hing das Speichern am Fokusverlust des Feldes. Am Gerät ließ sich kein Weg finden, der
 * ihn auslöst: den Screen verlässt man über Zurück (System-Geste wie Titelleisten-Pfeil), und
 * Tippen an eine andere Stelle nimmt dem Feld den Fokus nicht. Die Notiz war damit jedes Mal
 * verloren — ausgerechnet die Reflexion nach der Session, wofür das Feld überhaupt da ist.
 *
 * Deshalb prüft dieser Test nicht das Ergebnis irgendeines Verlassens, sondern die Zusage
 * selbst: **jede Eingabe meldet sich sofort nach oben.**
 */
class AlteSessionNotizTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tippen_meldet_die_notiz_sofort_nach_oben() {
        val gemeldet = mutableListOf<String>()

        composeRule.setContent {
            BoulderBuddyTheme {
                AlteSessionScreen(
                    gym = "Boulder World München",
                    dateSubtitle = "8. August · abgeschlossen",
                    durationText = "1.5h",
                    notes = "",
                    onNotesChange = { gemeldet += it },
                )
            }
        }

        composeRule.onNodeWithText("Notiz zu dieser Session…").performTextInput("Schulter zwickt")

        // Ohne jedes Verlassen des Feldes muss der Text bereits oben angekommen sein.
        assertThat(gemeldet).isNotEmpty()
        assertThat(gemeldet.last()).isEqualTo("Schulter zwickt")
    }

    @Test
    fun bestehende_notiz_wird_angezeigt() {
        composeRule.setContent {
            BoulderBuddyTheme {
                AlteSessionScreen(
                    gym = "Boulder World München",
                    dateSubtitle = "8. August · abgeschlossen",
                    durationText = "1.5h",
                    notes = "Schulter zwickt beim Mantle",
                )
            }
        }

        composeRule.onNodeWithText("Schulter zwickt beim Mantle").assertExists()
    }
}
