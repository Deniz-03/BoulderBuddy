package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.RouteCard
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.viewmodel.HangboardWorkoutUi
import com.boulderbuddy.ui.viewmodel.SessionBoulderUi
import kotlinx.coroutines.delay

// Status → Farbe fürs Status-Symbol der RouteCard. Top grün, Flash orange, Projekt dezent.
@Composable
private fun statusColorFor(status: BoulderStatus): Color = when (status) {
    BoulderStatus.TOP -> BoulderBuddy.colors.routes.green
    BoulderStatus.FLASH -> BoulderBuddy.colors.routes.orange
    BoulderStatus.PROJEKT -> BoulderBuddy.colors.textTertiary
}

@Composable
fun SessionDetailScreen(
    // Anzeige-Daten der aktiven Session (Phase 6.4, aus SessionViewModel).
    gym: String = "Boulderhalle Nord",
    startMillis: Long = System.currentTimeMillis(),
    topGrade: String = "–",
    boulders: List<SessionBoulderUi> = emptyList(),
    // Getrackte Hangboard-Workouts dieser Session; leer = Block wird ausgeblendet.
    hangboardWorkouts: List<HangboardWorkoutUi> = emptyList(),
    // Navigations-Callbacks (Phase 2). onAddRoute ist von SessionRoute bereits an die
    // sessionId dieser Session gebunden.
    onBack: () -> Unit = {},
    onOpenBoulder: (Int) -> Unit = {},
    onAddRoute: () -> Unit = {},
    onEndSession: () -> Unit = {},
) {
    // Verstrichene Session-Dauer als "HH:mm" für den Header ("● Läuft · 01:12 h"),
    // gerechnet ab dem echten Startzeitpunkt der Session.
    var elapsed by remember { mutableStateOf("00:00") }
    LaunchedEffect(startMillis) {
        while (true) {
            val totalSeconds = ((System.currentTimeMillis() - startMillis) / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            elapsed = "%02d:%02d".format(hours, minutes)
            delay(1000)
        }
    }

    // Stats aus den Bouldern ableiten: Tops = geschaffte, Versuche = Summe.
    val tops = boulders.count { it.status.getoppt }
    val versuche = boulders.sumOf { it.versuche }

    // Steuert den Bestätigungsdialog des zentralen "Session beenden"-Buttons.
    var showEndDialog by remember { mutableStateOf(false) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = gym,
                subtitle = "● Läuft · $elapsed h",
                navIcon = {
                    IconButton(onClick = onBack) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        StatCard(
                            value = tops.toString(),
                            label = "Tops",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            value = versuche.toString(),
                            label = "Versuche",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            value = topGrade,
                            label = "Top-Grade",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                // --- Boulder-Grid (2 Spalten) ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = "Boulder dieser Session")

                        // null = Platzhalter für die AddRouteCard, immer als letzte Kachel.
                        val cells: List<SessionBoulderUi?> = boulders + null
                        cells.chunked(2).forEach { rowCells ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                            ) {
                                rowCells.forEach { cell ->
                                    if (cell == null) {
                                        AddRouteCard(
                                            onClick = onAddRoute,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        RouteCard(
                                            grade = cell.grade,
                                            name = cell.name,
                                            meta = "${cell.versuche} Vers.",
                                            accentColor = cell.accentColor,
                                            statusIcon = {
                                                Text(
                                                    text = cell.status.symbol,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = statusColorFor(cell.status),
                                                )
                                            },
                                            onClick = { onOpenBoulder(cell.id) },
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

                // --- Hangboard-Training (nur wenn damit trainiert wurde) ---
                if (hangboardWorkouts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                            SectionHeader(text = "Hangboard-Training")
                            hangboardWorkouts.forEach { hb ->
                                Text(
                                    text = "• ${hb.summary}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // --- Zentraler "Session beenden"-Button ---
                // Setzt (nach Bestätigung) endedAt (SessionViewModel.endSession); danach kippt
                // der Dispatcher automatisch in die abgeschlossene Ansicht.
                item {
                    PrimaryButton(
                        text = "Session beenden",
                        icon = Icons.Filled.Flag,
                        onClick = { showEndDialog = true },
                    )
                }
            }
        },
    )

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("Session beenden?") },
            text = {
                Text("Die Session wird abgeschlossen und lässt sich danach nicht mehr bearbeiten.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndDialog = false
                        onEndSession()
                    },
                ) { Text("Beenden") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionDetailScreenPreview() {
    BoulderBuddyTheme {
        val routes = BoulderBuddy.colors.routes
        SessionDetailScreen(
            topGrade = "6b",
            boulders = listOf(
                SessionBoulderUi(0, "5c", "Dachrinne", routes.red, BoulderStatus.FLASH, 1),
                SessionBoulderUi(1, "6a", "Slab Talk", routes.blue, BoulderStatus.PROJEKT, 3),
                SessionBoulderUi(2, "6b", "Überhang", routes.green, BoulderStatus.TOP, 2),
            ),
        )
    }
}
