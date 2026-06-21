package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Pill-förmiges Status-Label ("● Aktiv", "✓ Top", "🔥 Flash").
// Umrandet in der übergebenen Farbe; Text und Rand teilen sich die Farbe.
// Verwendet auf Session-Übersicht und Boulder-Detail. Der Punkt/das Icon ist Teil
// des Texts, damit der Aufrufer Symbol und Beschriftung frei kombinieren kann.
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .border(BorderStroke(Dimens.borderSubtle, color), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = Dimens.paddingM, vertical = Dimens.paddingXS),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun StatusBadgePreview() {
    BoulderBuddyTheme {
        Row(
            modifier = Modifier.padding(Dimens.paddingL),
            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
        ) {
            StatusBadge(text = "● Aktiv", color = BoulderBuddy.colors.routes.blue)
            StatusBadge(text = "✓ Top", color = BoulderBuddy.colors.routes.green)
            StatusBadge(text = "🔥 Flash", color = BoulderBuddy.colors.routes.orange)
        }
    }
}
