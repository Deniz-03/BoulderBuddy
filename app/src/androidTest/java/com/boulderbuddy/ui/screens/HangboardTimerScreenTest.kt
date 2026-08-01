package com.boulderbuddy.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Compose-UI-Test des (stateless) [HangboardTimerScreen]: rendert er den übergebenen State,
 * und löst ein Tap auf den Start-Button die `onPlayPause`-Callback aus?
 */
class HangboardTimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun state(isRunning: Boolean = false) = HangboardTimerUiState(
        progress = 1f,
        time = "00:07",
        restTime = "00:03",
        phase = TimerPhase.HANG,
        currentSet = 1,
        totalSets = 6,
        hangSec = 7,
        restSec = 3,
        isRunning = isRunning,
    )

    @Test
    fun rendersTimerStateFromUiState() {
        composeRule.setContent {
            BoulderBuddyTheme {
                HangboardTimerScreen(
                    state = state(),
                    onPlayPause = {},
                    onReset = {},
                    onUpdateConfig = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("00:07").assertIsDisplayed()
        composeRule.onNodeWithText("Satz 1 / 6 · Rest 00:03").assertIsDisplayed()
        // Nicht laufend → Start-Button sichtbar.
        composeRule.onNodeWithContentDescription("Start").assertIsDisplayed()
    }

    @Test
    fun tappingStart_invokesOnPlayPause() {
        var playPauseClicks = 0
        composeRule.setContent {
            BoulderBuddyTheme {
                HangboardTimerScreen(
                    state = state(isRunning = false),
                    onPlayPause = { playPauseClicks++ },
                    onReset = {},
                    onUpdateConfig = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Start").performClick()

        assertThat(playPauseClicks).isEqualTo(1)
    }
}
