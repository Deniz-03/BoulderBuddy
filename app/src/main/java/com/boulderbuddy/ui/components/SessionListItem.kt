package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.PillShape

@Composable
fun SessionListItem(
    gym: String,
    date: String,
    accentColor: Color,
    badges: List<String>,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = BoulderBuddy.colors.surfaceCard,
        border = BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
    ) {
        // IntrinsicSize.Min: gibt dem Akzent-Balken dieselbe Höhe wie der Inhalt,
        // ohne die Höhe der Row fest definieren zu müssen.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Linker Akzent-Balken — kennzeichnet die Session mit ihrer Farbe.
            // 4dp Breite: sichtbar genug um als Farbmarkierung zu wirken, schmal genug um nicht zu stören.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
            ) {
                Text(
                    text = gym,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )
                Row(
                    modifier = Modifier.padding(top = Dimens.paddingXS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                ) {
                    // "● Aktiv"-Badge immer zuerst, in der Akzentfarbe der Session
                    if (isActive) {
                        SessionBadge(text = "● Aktiv", color = accentColor)
                    }
                    badges.forEach { badge ->
                        SessionBadge(text = badge, color = BoulderBuddy.colors.borderSubtle)
                    }
                }
            }
        }
    }
}

// Pill-förmiger Info-Chip innerhalb eines SessionListItem.
// Kein eigenes Composable auf top-level, da er nur in diesem Kontext vorkommt.
@Composable
private fun SessionBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .border(BorderStroke(Dimens.borderSubtle, color), PillShape)
            .padding(horizontal = Dimens.paddingM, vertical = Dimens.paddingXS),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun SessionListItemActivePreview() {
    BoulderBuddyTheme {
        SessionListItem(
            gym = "Boulderhalle Nord",
            date = "Heute · läuft gerade",
            accentColor = BoulderBuddy.colors.routes.blue,
            badges = listOf("5 Boulder"),
            isActive = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun SessionListItemPastPreview() {
    BoulderBuddyTheme {
        SessionListItem(
            gym = "Kletterzentrum Süd",
            date = "9. Juni · V-Scale",
            accentColor = BoulderBuddy.colors.routes.pink,
            badges = listOf("6 Boulder", "3 Tops"),
        )
    }
}
