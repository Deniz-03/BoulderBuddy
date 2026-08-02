package com.boulderbuddy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Eine Zeile im Einstellungen-Screen: führendes Icon, Label (optional mit erklärender
// Unterzeile), und rechts entweder ein Wert-Text (z.B. "Französisch", "v0.1") ODER ein
// eigenes trailing-Element (ToggleSwitch, Chevron). Die Unterzeile trägt die Erklärung bei
// Toggle-Zeilen, wo der Platz rechts schon vom Schalter belegt ist.
// onClick optional — gesetzt, wenn die ganze Zeile navigiert.
@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
    ) {
        Icon(
            imageVector = icon,
            // null: das Label beschreibt die Zeile bereits.
            contentDescription = null,
            tint = BoulderBuddy.colors.textSecondary,
            modifier = Modifier.size(Dimens.iconS),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = BoulderBuddy.colors.textTertiary,
                )
            }
        }
        // Trailing: eigenes Element hat Vorrang, sonst der Wert-Text.
        when {
            trailing != null -> trailing()
            value != null -> Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3, widthDp = 360)
@Composable
private fun SettingsRowPreview() {
    BoulderBuddyTheme {
        var smartwatch by remember { mutableStateOf(true) }
        var darkMode by remember { mutableStateOf(false) }
        Column {
            SectionHeader(
                text = "Gerät",
                modifier = Modifier.padding(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingS,
                ),
            )
            // Wert-Variante (navigierbar)
            SettingsRow(
                icon = Icons.Outlined.Tune,
                label = "Standard-Grading",
                value = "Französisch",
                onClick = {},
            )
            // Trailing-Variante mit Toggle
            SettingsRow(
                icon = Icons.Outlined.Watch,
                label = "Smartwatch verbunden",
                trailing = {
                    ToggleSwitch(checked = smartwatch, onCheckedChange = { smartwatch = it })
                },
            )
            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                label = "Dark Mode",
                trailing = {
                    ToggleSwitch(checked = darkMode, onCheckedChange = { darkMode = it })
                },
            )
        }
    }
}
