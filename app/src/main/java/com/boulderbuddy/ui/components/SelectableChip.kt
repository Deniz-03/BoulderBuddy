package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Einzelner Chip einer Single-Select-Gruppe. Im Gegensatz zum FilterChip kennzeichnet
// die Selektion eine *gewählte* Option (nicht einen aktiven Filter): gefüllt-invers
// statt akzentfarbig.
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) BoulderBuddy.colors.fillStrong else BoulderBuddy.colors.surfaceCard,
        border = if (selected) null else BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) BoulderBuddy.colors.onFillStrong else BoulderBuddy.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingS),
        )
    }
}

// Single-Select-Gruppe (Radio-Verhalten): genau eine Option ist gewählt.
// Die Gruppe besitzt den ausgewählten Index und meldet Änderungen über onSelect.
// FlowRow statt Row: bei mehreren Optionen (z.B. 4 Grading-Systeme) brechen die
// Chips ins 2×2-Raster um, statt über den Bildschirmrand zu laufen.
//
// @OptIn(ExperimentalLayoutApi::class): FlowRow ist in Compose noch als
// "experimentell" markiert (funktioniert stabil, aber die Signatur könnte sich
// in künftigen Compose-Versionen noch ändern). Der Compiler verlangt deshalb,
// dass wir die Nutzung bewusst bestätigen — genau das macht diese Annotation.
// Sie gilt nur für diese eine Funktion, nicht für die ganze Datei.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectableChipGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        options.forEachIndexed { index, label ->
            SelectableChip(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

// Preview im Kontext von Screen 3 "Neue Session": Abschnitts-Label über dem
// 2×2-Grading-Raster, auf einer Formular-Card. Feste Breite, damit FlowRow
// wie auf dem Handy umbricht.
// widthDp simuliert die Handy-Breite, damit FlowRow in der Preview wie auf dem
// Gerät ins 2×2-Raster umbricht (in Annotationen sind Konstanten unvermeidbar).
@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6, widthDp = 300)
@Composable
private fun SelectableChipGroupPreview() {
    BoulderBuddyTheme {
        var selectedIndex by remember { mutableIntStateOf(0) }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = BoulderBuddy.colors.surfaceCard,
            border = BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
            ) {
                Text(
                    text = "GRADING-SYSTEM",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoulderBuddy.colors.textTertiary,
                )
                SelectableChipGroup(
                    options = listOf("Französisch", "V-Scale", "Farbsystem", "Eigenes…"),
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                )
            }
        }
    }
}
