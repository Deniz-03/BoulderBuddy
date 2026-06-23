package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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

@Composable
fun RouteCard(
    grade: String,
    name: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    meta: String? = null,
    statusIcon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = BoulderBuddy.colors.surfaceCard,
        border = BorderStroke(Dimens.borderAccent, accentColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.paddingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = grade,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )
                if (statusIcon != null) {
                    statusIcon()
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.textTertiary
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun RouteCardPreview() {
    BoulderBuddyTheme {
        RouteCard(
            grade = "6a+",
            name = "Cooler Boulder",
            meta = "Sektor A",
            accentColor = BoulderBuddy.colors.routes.blue,
            statusIcon = {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.routes.green
                )
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun RouteCardWithStatusPreview() {
    BoulderBuddyTheme {
        RouteCard(
            grade = "5c",
            name = "Grüner Weg",
            meta = "Sektor X",
            accentColor = BoulderBuddy.colors.routes.green,
            statusIcon = {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.routes.green
                )
            },
        )
    }
}
