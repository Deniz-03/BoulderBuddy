package com.boulderbuddy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.boulderbuddy.R
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

/**
 * Dialog, in dem etwas eingetippt wird — Gegenstück zum reinen Auswahl-Dialog.
 *
 * Es gibt ihn als eigenen Baustein und nicht als Konvention („denk an die zwei
 * Einstellungen"), weil beide Fehler unten genau dadurch entstanden sind, dass ein
 * `AlertDialog` in seiner Grundeinstellung genommen wurde. Wer hier durchgeht, kann sie
 * nicht mehr vergessen.
 *
 * **Danebentippen schließt nicht.** Ein Dialog mit Eingabefeldern ist so hoch, dass rund um
 * ihn wenig Fläche bleibt, und mit aufgeklappter Tastatur wird sie noch schmaler. Ein
 * danebengegangener Tipp hat die Eingabe dann kommentarlos verworfen — beim Anlegen eines
 * Gradsystems mit acht Graden war das die Regel und nicht die Ausnahme. Die Zurück-Geste
 * schließt weiterhin: sie ist eine bewusste Handlung, kein Fehlgriff, und ein Dialog, aus
 * dem Zurück nicht herausführt, wirkt unter Android kaputt.
 *
 * **Die Tastatur schiebt den Inhalt nicht mehr weg.** `inhaltsAbstandMitTastatur()` hilft
 * hier nicht: ein Dialog hat sein eigenes Fenster, und solange das die System-Ränder selbst
 * verrechnet (`decorFitsSystemWindows = true`, die Vorgabe), meldet `WindowInsets.ime` darin
 * nichts — stattdessen schiebt das System das ganze Fenster hoch, bis das *fokussierte* Feld
 * sichtbar ist. Bei acht Graden lagen danach Titel und die Knöpfe „Anlegen"/„Abbrechen"
 * außerhalb des Bildschirms, und scrollen half nicht, weil der Dialog von der Tastatur
 * nichts wusste.
 *
 * Mit `decorFitsSystemWindows = false` reicht das Fenster die Ränder als Insets herein, und
 * `safeDrawingPadding()` begrenzt den Inhalt auf das, was neben Systemleisten und Tastatur
 * übrig ist. Der Inhalt scrollt dann *innerhalb* des Dialogs, während Titel und Knöpfe
 * stehen bleiben — deshalb sitzt der `verticalScroll` hier und nicht in den Aufrufern.
 *
 * @param bestaetigenAktiv sperrt den Bestätigen-Knopf, solange die Eingabe unvollständig ist.
 */
@Composable
fun EingabeDialog(
    titel: String,
    bestaetigenText: String,
    onBestaetigen: () -> Unit,
    onAbbrechen: () -> Unit,
    bestaetigenAktiv: Boolean = true,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        // Wird nur noch von der Zurück-Geste ausgelöst — Danebentippen ist abgeschaltet.
        onDismissRequest = onAbbrechen,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
        modifier = Modifier.safeDrawingPadding(),
        title = { Text(titel) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                content = inhalt,
            )
        },
        confirmButton = {
            TextButton(enabled = bestaetigenAktiv, onClick = onBestaetigen) {
                Text(bestaetigenText)
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.aktion_abbrechen)) }
        },
    )
}

@Preview
@Composable
private fun EingabeDialogPreview() {
    BoulderBuddyTheme {
        EingabeDialog(
            titel = "Gradsystem anlegen",
            bestaetigenText = "Anlegen",
            onBestaetigen = {},
            onAbbrechen = {},
        ) {
            TextField(value = "Halle Nord", onChange = {}, label = "Name")
            TextField(value = "gelb", onChange = {})
        }
    }
}
