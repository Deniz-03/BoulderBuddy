package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Schnellaktions-Kachel auf dem Home-Screen ("Session starten", "Boulder hinzufügen").
// Icon oben, Label unten. Zwei Varianten:
//  - primary:   fillStrong als Füllung + onFillStrong als Inhalt (dreht im Dark Mode)
//  - secondary: helle Card (surfaceCard) + dunkler Inhalt + dezenter Rand
// Dieselbe Farblogik wie PrimaryButton/SelectableChip, nur als quadratische Kachel.
// onClick ist Kern-UI: die Kachel IST ein Button.
@Composable
fun QuickActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (primary) BoulderBuddy.colors.fillStrong else BoulderBuddy.colors.surfaceCard,
        contentColor = if (primary) BoulderBuddy.colors.onFillStrong else MaterialTheme.colorScheme.onSurface,
        border = if (primary) null else BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.paddingL),
            // Icon nach oben, Label nach unten.
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                // null: der Label-Text beschreibt die Aktion bereits.
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconL),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6, widthDp = 360)
@Composable
private fun QuickActionButtonPreview() {
    BoulderBuddyTheme {
        Row(
            modifier = Modifier.padding(Dimens.paddingL),
            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
        ) {
            QuickActionButton(
                text = "Session starten",
                icon = Icons.Filled.PlayArrow,
                onClick = {},
                primary = true,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
            QuickActionButton(
                text = "Boulder hinzufügen",
                icon = Icons.Filled.Add,
                onClick = {},
                primary = false,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
        }
    }
}
