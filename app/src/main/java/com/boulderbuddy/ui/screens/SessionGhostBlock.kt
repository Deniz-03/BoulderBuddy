package com.boulderbuddy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.SessionGhostAnalyseUi

/**
 * Der Ghost-Climber-Block einer Session — in der laufenden wie in der abgeschlossenen Ansicht
 * dasselbe, deshalb an einer Stelle.
 *
 * **Anders als der Hangboard-Block steht er auch leer da.** Der Hangboard-Block ist ein reiner
 * Beleg: kein Workout, nichts zu zeigen. Hier ist der Block zugleich der Einstieg — blendete
 * er sich leer aus, gäbe es aus der Session heraus keinen Weg zu einer ersten Analyse.
 *
 * Auch die abgeschlossene Session darf welche bekommen: gefilmt wird in der Halle, analysiert
 * meist abends auf dem Sofa. Wäre das Hinzufügen an „läuft noch" gebunden, ginge genau der
 * übliche Fall nicht.
 */
@Composable
fun SessionGhostBlock(
    analysen: List<SessionGhostAnalyseUi>,
    onOeffnen: (Int) -> Unit,
    onHinzufuegen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
        SectionHeader(text = stringResource(R.string.session_ghost_ueberschrift))
        if (analysen.isEmpty()) {
            Text(
                text = stringResource(R.string.session_ghost_leer),
                style = MaterialTheme.typography.bodyMedium,
                color = BoulderBuddy.colors.textSecondary,
            )
        }
        analysen.forEach { analyse ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOeffnen(analyse.id) }
                    .padding(vertical = Dimens.paddingXS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Compare,
                    // null: die Zeile daneben benennt den Eintrag bereits.
                    contentDescription = null,
                    tint = BoulderBuddy.colors.textSecondary,
                )
                Text(
                    text = stringResource(
                        R.string.session_ghost_zeile,
                        analyse.zeitText,
                        analyse.modusLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        TextButton(onClick = onHinzufuegen) {
            Text(stringResource(R.string.session_ghost_hinzufuegen))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun SessionGhostBlockPreview() {
    BoulderBuddyTheme {
        SessionGhostBlock(
            analysen = listOf(
                SessionGhostAnalyseUi(1, "14:32", "Overlay"),
                SessionGhostAnalyseUi(2, "15:07", "Side-by-Side"),
            ),
            onOeffnen = {},
            onHinzufuegen = {},
        )
    }
}
