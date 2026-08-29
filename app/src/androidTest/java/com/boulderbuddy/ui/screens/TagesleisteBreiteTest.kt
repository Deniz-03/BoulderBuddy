package com.boulderbuddy.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.viewmodel.StatistikUiState
import com.boulderbuddy.ui.viewmodel.TagUi
import com.boulderbuddy.ui.viewmodel.TagesBoulderUi
import com.boulderbuddy.ui.viewmodel.TagesKennzahlenUi
import com.boulderbuddy.ui.viewmodel.TagesstatistikUi
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Die Auswahlleiste bietet zehn Klettertage an — die passen in keine Telefonzeile.
 *
 * Der Anlass ist der Jahres-Befund: seit ein Tag aus einem anderen Jahr sein Jahr nennt
 * („27. August 2025"), sind die Chips noch breiter. Ohne waagerechtes Scrollen liegen die
 * älteren schlicht außerhalb des Bildschirms — sie sind zwar komponiert, aber niemand kommt
 * an sie heran, und die Leiste verspricht zehn Tage, von denen man drei erreicht.
 *
 * Geprüft wird deshalb der letzte Chip: erreichbar und antippbar.
 */
class TagesleisteBreiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val heute = LocalDate.of(2026, 8, 29)

    private fun tag(versuche: String) = TagesstatistikUi(
        boulder = listOf(
            TagesBoulderUi(1, "V4", 4, getoppt = true, flash = true, versuche = 1),
        ),
        kennzahlen = TagesKennzahlenUi(tops = "1", versuche = versuche, topGrad = "V4", flash = "1/1"),
    )

    /**
     * Zehn Tage wie im Betrieb, der älteste aus dem Vorjahr — also mit der längsten
     * Aufschrift, die überhaupt vorkommen kann.
     */
    private val zehnTage = buildList {
        repeat(9) { i -> add(TagUi(heute.minusDays(i.toLong()), "${20 + i}. August")) }
        add(TagUi(heute.minusYears(1), "29. August 2025"))
    }

    private val state = StatistikUiState(
        totalSessions = 10,
        tage = zehnTage,
        tagesstatistik = zehnTage.associate { it.datum to mapOf(1 to tag("7")) } +
            (zehnTage.last().datum to mapOf(1 to tag("42"))),
        systemNamen = mapOf(1 to "V-Scale"),
    )

    @Test
    fun auch_der_aelteste_tag_ist_erreichbar() {
        composeRule.setContent {
            BoulderBuddyTheme { StatistikScreen(state = state) }
        }

        // Erst senkrecht zum Abschnitt — die Liste ist lazy.
        composeRule.onNodeWithTag(STATISTIK_LISTE_TEST_TAG)
            .performScrollToNode(hasText("20. August"))

        // Dann waagerecht bis zum letzten Chip. Ohne scrollbare Leiste bleibt er außerhalb
        // des Bildschirms liegen, und `performScrollTo` findet nichts, was ihn hereinholt.
        composeRule.onNodeWithText("29. August 2025")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        // Und er ist wirklich gewählt worden — erkennbar an seinen 42 Versuchen.
        composeRule.onNodeWithText("42").assertIsDisplayed()
    }
}
