package com.boulderbuddy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

/**
 * Leerzustand einer Liste oder Auswertung: Icon, Überschrift, erklärender Satz und optional
 * eine Aktion, die direkt aus dem Nichts herausführt.
 *
 * Bewusst eine gemeinsame Komponente, damit alle leeren Ansichten gleich aussehen und
 * gleich klingen — statt jeweils einer weißen Fläche, die wie ein Ladefehler wirkt.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Landscape,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.paddingXL, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
    ) {
        Icon(
            imageVector = icon,
            // null: Überschrift und Text tragen die Information bereits.
            contentDescription = null,
            tint = BoulderBuddy.colors.textTertiary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = BoulderBuddy.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            PrimaryButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.padding(top = Dimens.paddingS),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3, widthDp = 360)
@Composable
private fun EmptyStatePreview() {
    BoulderBuddyTheme {
        EmptyState(
            title = "Noch keine Sessions",
            description = "Starte deine erste Session, dann tauchen hier alle deine " +
                "Hallenbesuche auf.",
            actionText = "Session starten",
            onAction = {},
        )
    }
}
