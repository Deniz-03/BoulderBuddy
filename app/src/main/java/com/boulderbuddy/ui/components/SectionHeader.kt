package com.boulderbuddy.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Kleines Uppercase-Abschnittslabel ("KLETTERN", "GERÄT", "FARBE").
// Bündelt das Label-Muster, das vorher in jeder Formular-Preview wiederholt wurde.
// Sperrung (letterSpacing) kommt aus labelSmall (Type.kt), die Farbe aus textTertiary.
// uppercase() in der Komponente: der Aufrufer übergibt normalen Text ("Klettern").
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = BoulderBuddy.colors.textTertiary,
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6)
@Composable
private fun SectionHeaderPreview() {
    BoulderBuddyTheme {
        SectionHeader(
            text = "Klettern",
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}
