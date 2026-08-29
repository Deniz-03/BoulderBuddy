package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TagesVerlaufChart
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.TagesBoulderUi
import com.boulderbuddy.ui.viewmodel.TagesKennzahlenUi
import com.boulderbuddy.ui.viewmodel.TagesstatistikUi

/** Ein Gradsystem für die Umschaltleiste über der Kurve. */
data class TagesSystemUi(val id: Int, val name: String)

/**
 * Der Verlauf eines Klettertages, wie ihn beide Orte zeigen: die Session-Ansicht und der
 * Statistik-Tab.
 *
 * **Die Umschaltleiste erscheint nur bei mehr als einem Gradsystem.** Der Regelfall ist ein
 * System pro Tag; dann wäre ein einzelner Chip eine Bedienung ohne Wahl. Nötig ist sie
 * trotzdem, weil `order` nur innerhalb eines Systems eine Reihenfolge ist — eine Kurve, die
 * V4 mit 6b+ verbindet, behauptete einen Vergleich, den es nicht gibt.
 *
 * @param zeigeKennzahlen In der **Statistik** steht die Zahlenzeile über der Kurve. In der
 *   **Session** nicht: Tops, Versuche und Top-Grad stehen dort schon als Kopfzeile des
 *   Screens, und dieselben drei Zahlen ein zweites Mal wären keine Zusammenfassung mehr,
 *   sondern eine Dopplung.
 */
@Composable
fun TagesstatistikBlock(
    titel: String,
    systeme: List<TagesSystemUi>,
    gewaehltesSystem: Int?,
    statistik: TagesstatistikUi?,
    onSystemWaehlen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    zeigeKennzahlen: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
    ) {
        SectionHeader(text = titel)
        Text(
            text = stringResource(R.string.tag_verlauf_hinweis),
            style = MaterialTheme.typography.bodySmall,
            color = BoulderBuddy.colors.textSecondary,
        )

        if (systeme.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                systeme.forEach { system ->
                    SelectableChip(
                        label = system.name,
                        selected = system.id == gewaehltesSystem,
                        onClick = { onSystemWaehlen(system.id) },
                    )
                }
            }
        }

        if (statistik == null || statistik.boulder.isEmpty()) {
            Text(
                text = stringResource(R.string.tag_leer),
                style = MaterialTheme.typography.bodyMedium,
                color = BoulderBuddy.colors.textSecondary,
            )
            return@Column
        }

        if (zeigeKennzahlen) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
            ) {
                val k = statistik.kennzahlen
                StatCard(
                    value = k.tops,
                    label = stringResource(R.string.tag_tops),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatCard(
                    value = k.versuche,
                    label = stringResource(R.string.tag_versuche),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatCard(
                    value = k.topGrad,
                    label = stringResource(R.string.tag_topgrad),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatCard(
                    value = k.flash,
                    label = stringResource(R.string.tag_flash),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        TagesVerlaufChart(boulder = statistik.boulder)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4, widthDp = 360)
@Composable
private fun TagesstatistikBlockPreview() {
    BoulderBuddyTheme {
        TagesstatistikBlock(
            titel = "Verlauf des Tages",
            systeme = listOf(TagesSystemUi(1, "V-Scale")),
            gewaehltesSystem = 1,
            statistik = TagesstatistikUi(
                boulder = listOf(
                    TagesBoulderUi(1, "V3", 3, getoppt = true, flash = true, versuche = 1),
                    TagesBoulderUi(2, "V4", 4, getoppt = true, flash = false, versuche = 2),
                    TagesBoulderUi(3, "V6", 6, getoppt = true, flash = false, versuche = 5),
                    TagesBoulderUi(4, "V7", 7, getoppt = false, flash = false, versuche = 12),
                    TagesBoulderUi(5, "V5", 5, getoppt = true, flash = false, versuche = 3),
                ),
                kennzahlen = TagesKennzahlenUi("4", "23", "V6", "1/4"),
            ),
            onSystemWaehlen = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
