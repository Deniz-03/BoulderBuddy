package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Texteingabe für Formular-Screens (Session erstellen, Route hinzufügen).
// Bewusst auf BasicTextField aufgebaut statt M3-OutlinedTextField, damit der
// gesamte Look (Card-Hintergrund, dezenter Rand, abgerundete Form) ausschließlich
// über unsere Design Tokens läuft und zum Rest der App passt.
//
// State Hoisting: das Feld besitzt keinen eigenen Text — der Aufrufer hält `value`
// und bekommt Änderungen über `onChange` zurück (Single Source of Truth).
@Composable
fun TextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        // Optionales Uppercase-Label über dem Feld (wie "HALLE / ORT" im Wireframe).
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(BoulderBuddy.colors.textSecondary),
            // decorationBox umrahmt das eigentliche Eingabefeld (innerTextField) mit
            // unserem Card-Look und blendet den Placeholder ein, solange value leer ist.
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(BoulderBuddy.colors.surfaceCard)
                        .border(
                            BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
                            MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingM),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BoulderBuddy.colors.textTertiary,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun TextFieldEmptyPreview() {
    BoulderBuddyTheme {
        var text by remember { mutableStateOf("") }
        TextField(
            value = text,
            onChange = { text = it },
            label = "HALLE / ORT",
            placeholder = "z.B. Boulderhalle Nord",
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun TextFieldNotePreview() {
    BoulderBuddyTheme {
        var note by remember { mutableStateOf("") }
        TextField(
            value = note,
            onChange = { note = it },
            label = "NOTIZ (OPTIONAL)",
            placeholder = "Ziel für heute…",
            singleLine = false,
            minLines = 3,
            modifier = Modifier.padding(Dimens.paddingL),
        )
    }
}
