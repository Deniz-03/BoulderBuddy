package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary


@Composable
fun SessionErstellenScreen(
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    onSessionCreated: (Int) -> Unit = {},
) {
    var ort by remember { mutableStateOf("") }
    var gradingIndex by remember { mutableIntStateOf(0) }
    var notiz by remember { mutableStateOf("") }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Neue Session",
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = M3OnPrimary,
                        )
                    }
                },
            )
        },
        content = { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = Dimens.paddingL,   // 16dp – Abstand zum linken/rechten Rand
                        vertical = Dimens.paddingL,      // 16dp – Abstand oben/unten
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),   // 16dp zwischen den Blöcken
            ) {
                TextField(
                    value = ort,
                    onChange = {ort = it},
                    placeholder = "z.B. Boulderhalle Nord",
                    label = "HALLE/ORT",
                )

                // Grading-System: eigenes Label + festes 2×2-Raster. Bewusst nicht die
                // SelectableChipGroup (FlowRow bricht je nach Breite 3+1 um) — hier zwei
                // Rows mit je zwei Chips à weight(1f), damit immer 2 oben / 2 unten und
                // alle gleich breit sind.
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = "GRADING-SYSTEM",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    // TODO: "Eigenes…" lädt später die Custom-Farbsysteme aus der DB (Room).
                    val gradingOptions = listOf("Französisch", "V-Scale", "Farbsystem", "Eigenes…")
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                        // chunked(2) teilt die 4 Optionen in 2er-Reihen → 2×2-Raster.
                        gradingOptions.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                            ) {
                                rowOptions.forEachIndexed { colIndex, label ->
                                    val index = rowIndex * 2 + colIndex
                                    SelectableChip(
                                        label = label,
                                        selected = gradingIndex == index,
                                        onClick = { gradingIndex = index },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                TextField(
                    value = notiz,
                    singleLine = false,
                    minLines = 3,
                    label = "NOTIZ (OPTIONAL)",
                    placeholder = "Ziel für heute...",
                    onChange = {notiz = it},
                )

                // Schiebt den Button ans untere Ende: der Spacer schluckt den restlichen
                // freien Platz der Column, sodass "Session starten" unten klebt.
                Spacer(Modifier.weight(1f))

                PrimaryButton(
                    text = "Session starten",
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        // TODO: Session aus ort/gradingIndex/notiz in Room speichern und die
                        //  echte, neu vergebene sessionId liefern (Phase 6.3). Bis dahin liefert
                        //  der NavHost eine Platzhalter-ID für die aktive Session.
                        onSessionCreated(0)
                    },
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionErstellenScreenPreview() {
    BoulderBuddyTheme {
        SessionErstellenScreen()
    }
}
