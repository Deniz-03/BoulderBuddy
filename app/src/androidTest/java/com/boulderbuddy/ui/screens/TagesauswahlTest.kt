package com.boulderbuddy.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
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
 * Die Tagesauswahl im Statistik-Tab muss an einem Tag festhalten, nicht an einer Position.
 *
 * `state.tage` sind „die zehn jüngsten Klettertage". Diese Liste rutscht, sobald ein neuerer
 * Tag dazukommt — und das passiert im Hintergrund, während der Nutzer hinsieht: es genügt,
 * in einer laufenden Session einen Boulder mit Grad anzulegen. Merkt sich der Screen die
 * Position statt des Datums, zeigt er danach einen anderen Tag an, ohne dass jemand etwas
 * angetippt hat.
 *
 * Unterschieden werden die Tage über ihre Versuchssumme: die steht als Karte über der Kurve
 * und kommt im Bild genau einmal vor.
 */
class TagesauswahlTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val gestern = LocalDate.of(2026, 8, 28)
    private val vorgestern = LocalDate.of(2026, 8, 27)
    private val heute = LocalDate.of(2026, 8, 29)

    /**
     * Ein Tag, erkennbar an seiner Versuchszahl.
     *
     * Bewusst nicht am Grad: der steht sowohl auf der Kennzahl-Karte als auch an der Achse
     * der Kurve, und dann findet der Test mehrere Knoten mit demselben Text. Die
     * Versuchssumme kommt genau einmal vor.
     */
    private fun tag(versuche: String) = TagesstatistikUi(
        boulder = listOf(
            TagesBoulderUi(1, "V4", 4, getoppt = true, flash = true, versuche = 1),
            TagesBoulderUi(2, "V5", 5, getoppt = true, flash = false, versuche = 3),
        ),
        kennzahlen = TagesKennzahlenUi(tops = "2", versuche = versuche, topGrad = "V5", flash = "1/2"),
    )

    /** Zwei Klettertage, neuester zuerst — so liefert es der ViewModel. */
    private val zweiTage = StatistikUiState(
        // Ohne Session zeigt der Screen den Leerzustand und ueberspringt alles Weitere.
        totalSessions = 2,
        tage = listOf(TagUi(gestern, "Gestern"), TagUi(vorgestern, "27. August")),
        tagesstatistik = mapOf(gestern to mapOf(1 to tag("61")), vorgestern to mapOf(1 to tag("42"))),
        systemNamen = mapOf(1 to "V-Scale"),
    )

    @Test
    fun ein_neuer_klettertag_verschiebt_die_auswahl_nicht() {
        var state by mutableStateOf(zweiTage)

        composeRule.setContent {
            BoulderBuddyTheme { StatistikScreen(state = state) }
        }

        // Der Abschnitt liegt weiter unten in einer LazyColumn — was nicht sichtbar ist, ist
        // nicht komponiert und für den Test nicht vorhanden. Also erst hinscrollen.
        composeRule.onNodeWithTag(STATISTIK_LISTE_TEST_TAG)
            .performScrollToNode(hasText("27. August"))

        // Den zweiten Chip wählen — vorgestern, erkennbar an seinen 42 Versuchen.
        composeRule.onNodeWithText("27. August").performClick()
        composeRule.onNodeWithText("42").assertIsDisplayed()

        // Jetzt kommt „Heute" dazu und schiebt alles um eine Position nach hinten.
        state = state.copy(
            tage = listOf(TagUi(heute, "Heute")) + state.tage,
            tagesstatistik = state.tagesstatistik + (heute to mapOf(1 to tag("99"))),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STATISTIK_LISTE_TEST_TAG)
            // Zum Chip und nicht zur Ueberschrift: SectionHeader setzt in Versalien, und
            // die Textsuche ist buchstabengetreu.
            .performScrollToNode(hasText("27. August"))

        // Der Nutzer hat nichts angetippt: es muss weiterhin vorgestern zu sehen sein.
        composeRule.onNodeWithText("42").assertIsDisplayed()
    }
}
