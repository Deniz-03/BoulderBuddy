package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Kompakte Read-only-Zeile für gekletterte Boulder in der "Alte Session"-Ansicht.
// Nicht editierbar — kein onClick. Gleiche Akzent-Balken-Logik wie SessionListItem,
// aber mit shapes.small für ein schlankeres Erscheinungsbild.
@Composable
fun BoulderListRow(
    grade: String,
    name: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    statusIcon: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = BoulderBuddy.colors.surfaceCard,
        border = BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 3dp statt 4dp (SessionListItem): etwas schlanker passend zur kompakten Zeile
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = grade,
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (statusIcon != null) {
                    statusIcon()
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun BoulderListRowPreview() {
    BoulderBuddyTheme {
        BoulderListRow(
            grade = "6b",
            name = "Überhang",
            accentColor = BoulderBuddy.colors.routes.green,
            statusIcon = {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.routes.green,
                )
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun BoulderListRowNoStatusPreview() {
    BoulderBuddyTheme {
        BoulderListRow(
            grade = "5c",
            name = "Dachinne",
            accentColor = BoulderBuddy.colors.routes.red,
        )
    }
}
