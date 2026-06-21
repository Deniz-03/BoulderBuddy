package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Vollbreite horizontale Variante von RouteCard für den Home-Screen.
// RouteCard ist ein kompaktes Grid-Element (halbe Breite, vertikal).
// FeaturedCard ist breit und horizontal — Grade links prominent, Info daneben, Status rechts.
@Composable
fun FeaturedCard(
    grade: String,
    name: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    meta: String? = null,
    statusIcon: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = BoulderBuddy.colors.surfaceCard,
        border = BorderStroke(Dimens.borderAccent, accentColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.paddingL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Grade größer als in RouteCard (headlineMedium statt titleMedium) —
                // der breite Zuschnitt gibt Platz für mehr visuelle Dominanz.
                Text(
                    text = grade,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColor,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (meta != null) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = BoulderBuddy.colors.textSecondary,
                        )
                    }
                }
            }
            if (statusIcon != null) {
                statusIcon()
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun FeaturedCardPreview() {
    BoulderBuddyTheme {
        FeaturedCard(
            grade = "6a+",
            name = "Slab Talk",
            meta = "Sektor C · 2 Versuche",
            accentColor = BoulderBuddy.colors.routes.blue,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun FeaturedCardWithStatusPreview() {
    BoulderBuddyTheme {
        FeaturedCard(
            grade = "5c",
            name = "Dachinne",
            accentColor = BoulderBuddy.colors.routes.red,
            statusIcon = {
                Text(
                    text = "🔥",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
    }
}
