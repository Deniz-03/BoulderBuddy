package com.boulderbuddy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Primärer Aktions-Button für Formular-Screens ("Session starten", "Speichern").
// Bewusst auf Surface(onClick) aufgebaut statt M3-Button: passt zur flachen
// Designsprache (kein Schatten) und nutzt dieselbe Farbpaarung wie der gewählte
// SelectableChip — fillStrong als Füllung, onFillStrong als Inhalt.
//
// Das Paar dreht zwischen den Themes: Light dunkel-auf-hell, Dark hell-auf-dunkel. So bleibt
// der Button in beiden Fällen das auffälligste Element. Früher stand hier surfaceInverse mit
// surfaceBackground als Textfarbe — im Dark Mode ergab das 1,42:1, also unlesbar.
//
// Surface setzt die contentColor als LocalContentColor, daher erben Icon und Text
// die Textfarbe automatisch — kein explizites tint/color nötig.
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = BoulderBuddy.colors.fillStrong,
        contentColor = BoulderBuddy.colors.onFillStrong,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Dimens.paddingL,
                vertical = Dimens.paddingM,
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // null: der Button-Text beschreibt die Aktion bereits.
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconS),
                )
                Spacer(Modifier.width(Dimens.paddingS))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6, widthDp = 360)
@Composable
private fun PrimaryButtonPreview() {
    BoulderBuddyTheme {
        Column(
            modifier = Modifier.padding(Dimens.paddingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
        ) {
            PrimaryButton(
                text = "Session starten",
                icon = Icons.Filled.PlayArrow,
                onClick = {},
            )
            // Ohne Icon — wie "Speichern" auf Screen 8.
            PrimaryButton(
                text = "Speichern",
                onClick = {},
            )
        }
    }
}
