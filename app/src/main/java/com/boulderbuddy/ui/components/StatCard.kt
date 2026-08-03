package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyColors
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.LocalBoulderBuddyColors

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = BoulderBuddy.colors.surfaceCard,
        border = BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.paddingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = BoulderBuddy.colors.textSecondary
            )
        }
    }
}



@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6)
@Composable
private fun StatCardPreview() {
    BoulderBuddyTheme {
        StatCard(
            label = "Flash Rate",
            value = "42%",
        )
    }
}
