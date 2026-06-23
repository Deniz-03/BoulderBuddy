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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.ColorPicker
import com.boulderbuddy.ui.components.PhotoPicker
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary

@Composable
fun RouteHinzufuegenScreen() {
    // TODO: Edit-Modus — bei übergebener boulderId die Felder aus Room vorbefüllen
    //  und den Titel auf "Boulder bearbeiten" setzen (Wireframe #8 = hinzufügen/bearbeiten).
    // Speichern der Textfeldeingaben
    var sektor by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    // Die 7 Routenfarben in Wireframe-Reihenfolge.
    val routes = BoulderBuddy.colors.routes
    val palette = listOf(
        routes.red,
        routes.orange,
        routes.yellow,
        routes.green,
        routes.blue,
        routes.purple,
        routes.pink,
    )
    // Aktuell gewählte Routenfarbe (Start: Grün, wie im Wireframe).
    var selected by remember { mutableStateOf(routes.green) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Boulder hinzufügen",
                navIcon = {
                    IconButton(onClick = { /* TODO: Navigation zurück zur Session-Detail */ }) {
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
                // 16dp zwischen den Blöcken
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                PhotoPicker(
                    onClick = { /* TODO: Navigation zur Galerie */ }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                ) {
                    TextField(
                        value = grade,
                        placeholder = "6b",
                        label = "Grade",
                        onChange = { grade = it },
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = sektor,
                        placeholder = "A",
                        label = "Sektor",
                        onChange = { sektor = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Farbe",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoulderBuddy.colors.textTertiary,
                )
                ColorPicker(
                    colors = palette,
                    selected = selected,
                    onSelect = { selected = it },
                )

                TextField(
                    value = name,
                    placeholder = "z.B. Überhang",
                    label = "Name",
                    onChange = { name = it },
                )

                // Drückt den Button ans untere Ende des Screens.
                Spacer(Modifier.weight(1f))

                PrimaryButton(
                    text = "Speichern",
                    icon = Icons.Filled.Check,
                    onClick = { /* TODO: Speichern des Boulders und Navigation zur Boulder-Detailübersicht */ },
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun RouteHinzufuegenScreenPreview() {
    BoulderBuddyTheme {
        RouteHinzufuegenScreen()
    }
}