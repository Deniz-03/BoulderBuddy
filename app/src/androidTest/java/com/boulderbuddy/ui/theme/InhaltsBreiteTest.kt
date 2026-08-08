package com.boulderbuddy.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test

/**
 * Der Vertrag von [Modifier.inhaltsBreite] — gemessen an einem gerenderten Frame.
 *
 * **Warum es diesen Test gibt:** Die Tablet-Runde hatte sieben Commits Layout-Arbeit und
 * keinen einzigen Test. Zwei Fehler sind dabei durchgerutscht, beide in dieser Modifier-Kette,
 * beide mit grünem Build und grünen Unit-Tests, beide erst auf einem Screenshot aufgefallen:
 *
 * 1. `fillMaxWidth().widthIn(max = …)` deckelt **nicht** — die Heatmap-Zellen blieben 83 dp
 *    groß, obwohl die Begrenzung im Code stand.
 * 2. Die Korrektur ließ den Diagramm-Balken auf Breite **0** zusammenfallen — die Balken
 *    waren am Gerät komplett verschwunden.
 *
 * Ein Test über Konstanten hätte beides nicht gefunden: die *Werte* waren jedes Mal richtig,
 * nur das Layout hat sie nicht umgesetzt.
 *
 * **Gemessen wird das Kind, nicht der Knoten mit dem Modifier.** `inhaltsBreite` macht seinen
 * eigenen Knoten absichtlich fensterbreit — anders ließe sich der Inhalt darin nicht
 * zentrieren. Gedeckelt ist, was *darin* liegt. Genau so benutzen es die Screens: eine
 * `LazyColumn` mit `inhaltsBreite`, deren Karten `fillMaxWidth()` haben und damit auf die
 * gedeckelte Breite gehen. (Diese Unterscheidung hat der erste Anlauf dieses Tests selbst
 * nicht getroffen — er maß den äußeren Knoten und schlug fehl, obwohl der Modifier stimmte.)
 *
 * `requiredWidth` erzwingt die Fensterbreite unabhängig vom echten Testgerät.
 */
class InhaltsBreiteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fensterBreite = 1280.dp
    private val grenze = 600.dp
    private val kind = "kind"

    /** Rendert [inhalt] in einem Fenster fester Breite. */
    private fun imFenster(breite: Dp = fensterBreite, inhalt: @Composable () -> Unit) {
        composeRule.setContent {
            BoulderBuddyTheme {
                Box(Modifier.requiredWidth(breite)) { inhalt() }
            }
        }
    }

    /** Ein Kind, das sich nimmt, was der Container ihm erlaubt — wie eine Karte im Screen. */
    @Composable
    private fun FuellendesKind() {
        Box(Modifier.fillMaxWidth().height(20.dp).testTag(kind))
    }

    @Test
    fun deckeltDenInhaltAufDenGrenzwert() {
        imFenster {
            Box(Modifier.inhaltsBreite(grenze)) { FuellendesKind() }
        }

        composeRule.onNodeWithTag(kind).assertWidthIsEqualTo(grenze)
    }

    @Test
    fun zentriertDenInhaltImFenster() {
        imFenster {
            Box(Modifier.inhaltsBreite(grenze)) { FuellendesKind() }
        }

        // (1280 − 600) / 2 = 340
        composeRule.onNodeWithTag(kind).assertLeftPositionInRootIsEqualTo(340.dp)
    }

    @Test
    fun linksbuendigWennSoVerlangt() {
        imFenster {
            Box(Modifier.inhaltsBreite(grenze, ausrichtung = Alignment.Start)) { FuellendesKind() }
        }

        composeRule.onNodeWithTag(kind).assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun amTelefonWirkungslos() {
        // Der eigentliche Grund, warum der Modifier ohne Breitenabfrage gesetzt werden darf:
        // ist das Fenster schmaler als die Grenze, nimmt der Inhalt die volle Breite.
        val schmal = 411.dp
        imFenster(breite = schmal) {
            Box(Modifier.inhaltsBreite(grenze)) { FuellendesKind() }
        }

        composeRule.onNodeWithTag(kind).assertWidthIsEqualTo(schmal)
    }

    @Test
    fun leererKastenBehaeltBreite() {
        // Der Fall, an dem die Balken verschwanden: ein Box OHNE Inhalt. Er hat keine
        // Eigenbreite — wer ihn nur nach oben begrenzt, bekommt 0. Deshalb dort erst
        // `widthIn`, dann `fillMaxWidth`.
        imFenster {
            Box(
                Modifier
                    .widthIn(max = 56.dp)
                    .fillMaxWidth()
                    .height(20.dp)
                    .testTag("balken"),
            )
        }

        composeRule.onNodeWithTag("balken").assertWidthIsEqualTo(56.dp)
    }

    /**
     * Der Test, der die Falle festhält, statt nur die Lösung.
     *
     * `fillMaxWidth().widthIn(max = …)` ist die Schreibweise, die man erwartet — und sie tut
     * nichts. `fillMaxWidth` setzt Mindest- **und** Höchstbreite auf die Elternbreite, und
     * gegen eine Mindestbreite kommt ein späteres `widthIn` nicht an. Schlägt dieser Test
     * eines Tages fehl, weil Compose das Verhalten geändert hat, darf [inhaltsBreite]
     * vereinfacht werden — bis dahin ist der Umweg über `wrapContentWidth` nötig.
     */
    @Test
    fun derNaheliegendeWegDeckeltNicht() {
        imFenster {
            Box(Modifier.fillMaxWidth().widthIn(max = grenze)) { FuellendesKind() }
        }

        val gemessen = composeRule.onNodeWithTag(kind).getUnclippedBoundsInRoot().width

        assertWithMessage(
            "fillMaxWidth().widthIn(max) deckelt inzwischen doch — inhaltsBreite kann " +
                "vereinfacht werden (wrapContentWidth entfällt).",
        ).that(gemessen.value).isGreaterThan(grenze.value)
    }

    @Test
    fun inhaltsBreiteDeckeltWoDerNaiveWegVersagt() {
        // Dieselbe Situation, dieselbe Grenze, dasselbe Kind — nur mit dem Modifier.
        // Der Vergleich mit dem Test darüber ist die eigentliche Aussage.
        imFenster {
            Box(Modifier.inhaltsBreite(grenze)) { FuellendesKind() }
        }

        val gemessen = composeRule.onNodeWithTag(kind).getUnclippedBoundsInRoot().width
        assertThat(gemessen.value).isWithin(1f).of(grenze.value)
    }
}
