package com.boulderbuddy.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.viewmodel.GhostClimberUiState
import com.boulderbuddy.ui.viewmodel.GhostStep
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Der Weg „Zurück → Speichern → verlassen" und was passiert, wenn das Speichern scheitert.
 *
 * Verlassen darf erst NACH dem bestätigten Speichern passieren: navigiert der Bildschirm
 * sofort weg, räumt das den ViewModel-Scope ab, während Room noch schreibt — genau der
 * Datenverlust, gegen den der Dialog gebaut ist. Der Bildschirm wartet deshalb auf den
 * Zustand.
 *
 * Das Warten braucht aber einen Abbruch, und der fehlte: nach einem gescheiterten Versuch
 * blieb die Absicht stehen und ließ den Bildschirm beim nächsten erfolgreichen Speichern von
 * selbst zugehen — Stunden später, ohne Zusammenhang.
 *
 * Geprüft wird gegen den Zustand, nicht gegen ein ViewModel: der Bildschirm ist zustandslos,
 * und die Vorschau muss dafür nichts zeichnen. Der Dialog hängt allein an `step` und
 * `analysisSaved`.
 */
class GhostVerwerfenDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Vorschau erreicht, noch nicht gespeichert — der einzige Zustand mit „Speichern" im Dialog. */
    private val vorschau = GhostClimberUiState(
        step = GhostStep.PREVIEW,
        analysisSaved = false,
    )

    @Test
    fun speichern_verlaesst_erst_wenn_der_zustand_es_bestaetigt() {
        var state by mutableStateOf(vorschau)
        var gespeichert = 0
        var verlassen = 0

        composeRule.setContent {
            BoulderBuddyTheme {
                GhostClimberScreen(
                    state = state,
                    onSaveAnalysis = { gespeichert++ },
                    onBack = { verlassen++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithText("Speichern").performClick()

        assertThat(gespeichert).isEqualTo(1)
        // Noch nicht gegangen — der Zustand hat das Speichern nicht bestätigt.
        assertThat(verlassen).isEqualTo(0)

        state = state.copy(analysisSaved = true)
        composeRule.waitForIdle()

        assertThat(verlassen).isEqualTo(1)
    }

    @Test
    fun ein_fehlgeschlagenes_speichern_schliesst_den_bildschirm_nicht_nachtraeglich() {
        var state by mutableStateOf(vorschau)
        var verlassen = 0

        composeRule.setContent {
            BoulderBuddyTheme {
                GhostClimberScreen(
                    state = state,
                    onSaveAnalysis = {},
                    onBack = { verlassen++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Zurück").performClick()
        composeRule.onNodeWithText("Speichern").performClick()

        // Der Versuch scheitert: Meldung statt Bestätigung.
        state = state.copy(error = "Speichern fehlgeschlagen")
        composeRule.waitForIdle()
        assertThat(verlassen).isEqualTo(0)

        // Später greift der Nutzer zum regulären Speichern-Knopf, diesmal erfolgreich. Der
        // Bildschirm darf sich davon NICHT schließen lassen — er wollte nur damals gehen.
        state = state.copy(analysisSaved = true, error = null)
        composeRule.waitForIdle()

        assertThat(verlassen).isEqualTo(0)
    }
}
