package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.BoulderListRow
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary

// Status eines Boulders. getoppt = als geschafft gewertet (Top oder Flash zählen beide
// als Top; Projekt nicht). Das Symbol steckt am Status, damit es nur eine Quelle gibt.
// TODO: Vor dem Room-Anschluss mit Route.status (OPEN/SENT/PROJECT/SKIP) abgleichen
//  (z.B. ist Flash = SENT mit attempts == 1?) und Status-Modell screen-übergreifend teilen.
private enum class BoulderStatus(val symbol: String) {
    TOP("✓"),
    FLASH("🔥"),
    PROJEKT("⏳");

    val getoppt: Boolean get() = this == TOP || this == FLASH
}

// Read-only-Daten eines gekletterten Boulders. accentColor = Routenfarbe (Identifikation).
private data class BoulderRowData(
    val grade: String,
    val name: String,
    val accentColor: Color,
    val status: BoulderStatus,
)

@Composable
fun AlteSessionScreen() {
    // TODO: Diese Daten kommen später aus dem ViewModel (abgeschlossene Session aus Room).
    val boulders = listOf(
        BoulderRowData("6b", "Überhang", BoulderBuddy.colors.routes.green, BoulderStatus.TOP),
        BoulderRowData("5c", "Dachrinne", BoulderBuddy.colors.routes.red, BoulderStatus.TOP),
        BoulderRowData("5a", "Warmup", BoulderBuddy.colors.routes.yellow, BoulderStatus.FLASH),
        BoulderRowData("6a", "Slab Talk", BoulderBuddy.colors.routes.blue, BoulderStatus.PROJEKT),
        BoulderRowData("6c", "Dynamo", BoulderBuddy.colors.routes.purple, BoulderStatus.PROJEKT),
    )
    // TODO: Dauer aus Start-/Endzeitpunkt der Session berechnen und formatieren.
    val dauer = "1.5h"
    // Editierbare Session-Notiz. Startet leer; der Beispieltext ist nur Placeholder.
    // TODO: bestehende Notiz aus Room laden und Änderungen zurückschreiben (ViewModel).
    var notiz by remember { mutableStateOf("") }

    // Stat-Werte werden aus den Daten ABGELEITET, nicht separat hartkodiert:
    //  Boulder = Anzahl gekletterter Boulder, Tops = davon geschaffte.
    val boulderAnzahl = boulders.size
    val topAnzahl = boulders.count { it.status.getoppt }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                // TODO: Halle + Datum kommen aus der abgeschlossenen Session.
                title = "Boulderhalle Nord",
                subtitle = "12. Juni · abgeschlossen",
                navIcon = {
                    IconButton(onClick = { /* TODO: Navigation zurück zur Session-Übersicht */ }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = M3OnPrimary,
                        )
                    }
                },
            )
        },
        content = { _ ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingL,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
            ) {
                // --- Stats ---
                item {
                    // height(IntrinsicSize.Min) + fillMaxHeight() → alle drei Karten gleich hoch,
                    // auch wenn ein Label umbricht.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        StatCard(
                            value = boulderAnzahl.toString(),
                            label = "Boulder",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatCard(
                            value = topAnzahl.toString(),
                            label = "Tops",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatCard(
                            value = dauer,
                            label = "Dauer",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }

                // --- Notiz (read-only über unsere TextField-Komponente) ---
                item {
                    TextField(
                        value = notiz,
                        onChange = { notiz = it },
                        label = "NOTIZ",
                        placeholder = "Überhang endlich geschafft. Linke Schulter zwickt - " +
                                "nächstes Mal mehr aufwärmen.",
                        singleLine = false,
                        minLines = 3,
                    )
                }

                // --- Gekletterte Boulder (read-only) ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = "Gekletterte Boulder")
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                            boulders.forEach { boulder ->
                                BoulderListRow(
                                    grade = boulder.grade,
                                    name = boulder.name,
                                    accentColor = boulder.accentColor,
                                    statusIcon = {
                                        Text(
                                            text = boulder.status.symbol,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = statusColorFor(boulder.status),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

// Status → Farbe fürs Status-Symbol. Top grün, Flash orange, Projekt dezent.
@Composable
private fun statusColorFor(status: BoulderStatus): Color = when (status) {
    BoulderStatus.TOP -> BoulderBuddy.colors.routes.green
    BoulderStatus.FLASH -> BoulderBuddy.colors.routes.orange
    BoulderStatus.PROJEKT -> BoulderBuddy.colors.textTertiary
}

@Preview(showBackground = true)
@Composable
private fun AlteSessionScreenPreview() {
    BoulderBuddyTheme {
        AlteSessionScreen()
    }
}
