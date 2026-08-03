package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Farbwähler für "Route hinzufügen" — die 7 Routenfarben als klickbare Kreise.
// Zentrales Element des Formulars: die gewählte Farbe färbt später alle Akzente
// des Boulders.
//
// State Hoisting: der Picker besitzt keine Auswahl — der Aufrufer hält `selected`
// und bekommt Änderungen über `onSelect` zurück.
@Composable
fun ColorPicker(
    colors: List<Color>,
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        colors.forEach { color ->
            ColorSwatch(
                color = color,
                selected = color == selected,
                onClick = { onSelect(color) },
            )
        }
    }
}

// Einzelner Farbkreis. Im gewählten Zustand bekommt er einen Ring in seiner eigenen
// Farbe mit einer Lücke (paddingXS) zur Füllung — der klassische "ausgewählt"-Look.
// Die Klickfläche bleibt für alle Kreise gleich groß (swatchSize), nur die Füllung
// schrumpft bei Auswahl um Ring + Lücke.
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Dimens.swatchSize)
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier
                        .border(Dimens.borderAccent, color, CircleShape)
                        .padding(Dimens.paddingXS)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(color)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6)
@Composable
private fun ColorPickerPreview() {
    BoulderBuddyTheme {
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
        // Start auf Grün — wie im Wireframe "Route hinzufügen".
        var selected by remember { mutableStateOf(routes.green) }

        Column(
            modifier = Modifier.padding(Dimens.paddingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
        ) {
            Text(
                text = "FARBE",
                style = MaterialTheme.typography.labelSmall,
                color = BoulderBuddy.colors.textTertiary,
            )
            ColorPicker(
                colors = palette,
                selected = selected,
                onSelect = { selected = it },
            )
        }
    }
}
