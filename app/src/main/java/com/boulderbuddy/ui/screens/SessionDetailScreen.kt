package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.AddRouteCard
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.RouteCard
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import kotlinx.coroutines.delay

// Platzhalter-Datenklasse bis das echte Datenmodell aus Room kommt.
// status: "top" | "flash" | "projekt" — steuert das Status-Icon der RouteCard.
private data class BoulderPreviewData(
    val grade: String,
    val name: String,
    val accentColorKey: String,
    val versuche: Int,
    val status: String,
)

// TODO: Diese Liste kommt später aus dem ViewModel (Boulder dieser Session via Room).
private val placeholderBoulders = listOf(
    BoulderPreviewData(grade = "5c", name = "Dachrinne", accentColorKey = "red", versuche = 1, status = "top"),
    BoulderPreviewData(grade = "6a", name = "Slab Talk", accentColorKey = "blue", versuche = 1, status = "projekt"),
    BoulderPreviewData(grade = "6b", name = "Überhang", accentColorKey = "green", versuche = 1, status = "top"),
)

// Übersetzt den gespeicherten Farb-Key in die zugehörige Routenfarbe.
// Ausgelagert für bessere Lesbarkeit
@Composable
private fun routeColorFor(key: String): Color = when (key) {
    "red" -> BoulderBuddy.colors.routes.red
    "orange" -> BoulderBuddy.colors.routes.orange
    "yellow" -> BoulderBuddy.colors.routes.yellow
    "green" -> BoulderBuddy.colors.routes.green
    "blue" -> BoulderBuddy.colors.routes.blue
    "purple" -> BoulderBuddy.colors.routes.purple
    "pink" -> BoulderBuddy.colors.routes.pink
    else -> BoulderBuddy.colors.borderSubtle
}

// Status → Symbol fürs statusIcon-Slot der RouteCard.
private fun statusSymbol(status: String): String = when (status) {
    "top" -> "✓"
    "flash" -> "🔥"
    "projekt" -> "⏳"
    else -> ""
}

// Ausgelagert für bessere Lesbarkeit
@Composable
private fun statusColorFor(status: String): Color = when (status) {
    "top" -> BoulderBuddy.colors.routes.green
    "flash" -> BoulderBuddy.colors.routes.orange
    else -> BoulderBuddy.colors.textTertiary
}

@Composable
fun SessionDetailScreen() {
    // Verstrichene Session-Dauer als "HH:mm" für den Header ("● Läuft · 01:12 h").
    // TODO: sessionStartMillis kommt später aus der DB (Startzeitpunkt der aktiven Session).
    val sessionStartMillis = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableStateOf("00:00") }

    // Aktualisiert die Anzeige jede Sekunde: Differenz zwischen jetzt und dem Start.
    LaunchedEffect(Unit) {
        while (true) {
            val totalSeconds = (System.currentTimeMillis() - sessionStartMillis) / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            elapsed = "%02d:%02d".format(hours, minutes)
            delay(1000)
        }
    }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                // TODO: Halle kommt aus der aktiven Session (ViewModel/Room).
                title = "Boulderhalle Nord",
                subtitle = "● Läuft · $elapsed h",
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
                    // TODO: Werte kommen aus der aktiven Session (Tops, Versuche, höchster Grad).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        StatCard(value = "5", label = "Tops", modifier = Modifier.weight(1f))
                        StatCard(value = "8", label = "Versuche", modifier = Modifier.weight(1f))
                        StatCard(value = "6b", label = "Top-Grade", modifier = Modifier.weight(1f))
                    }
                }

                // --- Boulder-Grid (2 Spalten) ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = "Boulder dieser Session")

                        // null = Platzhalter für die AddRouteCard, die immer als letzte
                        // Kachel ans Ende des Grids gehängt wird.
                        val cells: List<BoulderPreviewData?> = placeholderBoulders + null
                        cells.chunked(2).forEach { rowCells ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                            ) {
                                rowCells.forEach { cell ->
                                    if (cell == null) {
                                        AddRouteCard(
                                            onClick = { /* TODO: Navigation zu "Route hinzufügen" */ },
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        RouteCard(
                                            grade = cell.grade,
                                            name = cell.name,
                                            meta = "${cell.versuche} Vers.",
                                            accentColor = routeColorFor(cell.accentColorKey),
                                            statusIcon = {
                                                Text(
                                                    text = statusSymbol(cell.status),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = statusColorFor(cell.status),
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                // Ungerade Reihe: Spacer hält die einzelne Kachel auf halber Breite.
                                if (rowCells.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionDetailScreenPreview() {
    BoulderBuddyTheme {
        SessionDetailScreen()
    }
}
