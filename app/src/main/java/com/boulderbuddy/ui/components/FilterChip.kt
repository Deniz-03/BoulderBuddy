package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) selectedColor else Color.Transparent,
        border = if (selected) null else BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else BoulderBuddy.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Dimens.paddingM, vertical = Dimens.paddingXS),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun FilterChipPreview() {
    BoulderBuddyTheme {
        var selected by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
            FilterChip(label = "Alle", selected = true, onClick = {})
            FilterChip(label = "7a+", selected = selected, onClick = { selected = !selected })
            FilterChip(
                label = "Blau",
                selected = false,
                onClick = {},
                selectedColor = BoulderBuddy.colors.routes.blue,
            )
        }
    }
}
