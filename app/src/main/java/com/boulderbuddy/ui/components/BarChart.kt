package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Höhe der Balken-Zeichenfläche, lokal gehalten.
private val BarChartHeight = 160.dp

// Breiteste zulässige Balkenbreite. Bei wenigen Balken auf einem breiten Fenster wäre ein
// Balken sonst breiter als hoch, und das Verhältnis der Höhen — die eigentliche Aussage des
// Diagramms — geht neben der Fläche unter.
private val BarMaxWidth = 56.dp

// Ein Balken: Beschriftung + Wert + Farbe (i.d.R. die Routenfarbe des Grades).
data class BarChartEntry(
    val label: String,
    val value: Float,
    val color: Color,
)

// Balkendiagramm für die Grade-Verteilung (Statistik-Screen).
// Bewusst aus Compose-Boxen statt Canvas: Balkenhöhe = Anteil am Maximalwert, mit
// gerundeten Ecken und Labels — wartbarer und token-freundlicher als drawRect.
@Composable
fun BarChart(
    entries: List<BarChartEntry>,
    modifier: Modifier = Modifier,
) {
    // Maximalwert als Bezug für die relative Balkenhöhe (mind. 1, um /0 zu vermeiden).
    val maxValue = entries.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f

    Row(
        modifier = modifier.height(BarChartHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
        verticalAlignment = Alignment.Bottom,
    ) {
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Balken-Bereich: füllt die Höhe über dem Label.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            /*
                             * Gedeckelt, nicht gestreckt. Vorher `fillMaxWidth()`: bei zwei
                             * Balken auf dem Tablet wurden daraus zwei Farbflächen von je
                             * 615 dp — eine Flagge, kein Diagramm. Die Spalte behält ihr
                             * `weight(1f)` (die Abstände bleiben gleichmäßig), nur der Balken
                             * darin hört auf zu wachsen. Zentriert wird er vom `BottomCenter`
                             * der umgebenden Box.
                             *
                             * Die Reihenfolge ist hier genau umgekehrt zu `inhaltsBreite`:
                             * erst deckeln, dann füllen. Der Balken ist ein LEERER Kasten —
                             * er hat keine Eigenbreite, die man begrenzen könnte. `widthIn`
                             * allein ließe ihn auf 0 zusammenfallen (und das tat es auch:
                             * die Balken waren schlicht weg). `widthIn` senkt die
                             * Höchstbreite auf 56 dp, `fillMaxWidth` nimmt sich danach genau
                             * diese Höchstbreite.
                             */
                            .widthIn(max = BarMaxWidth)
                            .fillMaxWidth()
                            .fillMaxHeight(entry.value / maxValue)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(entry.color),
                    )
                }
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = BoulderBuddy.colors.textTertiary,
                    modifier = Modifier.padding(top = Dimens.paddingXS),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4, widthDp = 320)
@Composable
private fun BarChartPreview() {
    BoulderBuddyTheme {
        val routes = BoulderBuddy.colors.routes
        BarChart(
            entries = listOf(
                BarChartEntry("5", 3f, routes.green),
                BarChartEntry("6a", 7f, routes.blue),
                BarChartEntry("6b", 5f, routes.orange),
                BarChartEntry("6c", 4f, routes.red),
                BarChartEntry("7a", 2f, routes.purple),
            ),
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}
