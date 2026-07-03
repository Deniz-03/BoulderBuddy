package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.QuickActionButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SessionListItem
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.viewmodel.HomeUiState
import com.boulderbuddy.ui.viewmodel.LastSessionUi
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    // Anzeige-Zustand aus dem HomeViewModel (Phase 6.1). Default = leerer State hält
    // Preview & Tests lauffähig.
    state: HomeUiState = HomeUiState(),
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onOpenSettings: () -> Unit = {},
    onStartSession: () -> Unit = {},
    onAddBoulderToActiveSession: () -> Unit = {},
    onOpenAllBoulders: () -> Unit = {},
    onOpenLastSession: () -> Unit = {},
) {
    val userName = state.userName
    val hasActiveSession = state.hasActiveSession

    // Ermittlung des aktuellen Datums
    // Remember spart Leistung und Akku, da es dafür sorgt, dass das Datum nicht bei jeder Änderung am Bildschirm neu geladen werden muss.
    val dateText = remember {
        LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
        )
    }

    // Scaffold teilt den Bildschirm in feste Bereiche. oben die Menüleiste, unten die NavBar und in der Mitte der Hauptinhalt.
    BoulderBuddyScaffold(
        // Obere Leiste
        topBar = {
            TopBar(
                // TODO: "$userName" durch echten Nutzernamen aus der Datenbank ersetzen
                title = "Hallo, $userName 👋",
                subtitle = dateText,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Einstellungen",
                            tint = M3OnPrimary,
                        )
                    }
                },
            )
        },
        // BottomNav wird ab Phase 1.3 zentral vom Navigations-Gerüst (AppNavigation)
        // gestellt — dieser Screen rendert sie nicht mehr selbst.
        // Mittlerer Hauptbereich
        content = { _ ->
            // Scrollbare Leiste die nur die Elemente Lädt die gerade auf dem Bildschirm sind -> Spart Leistung
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
                    // Werte aus dem HomeViewModel (Room): Sessions der Woche, Tops gesamt,
                    // höchster getoppter Grad.
                    // height(IntrinsicSize.Min) + fillMaxHeight() → alle drei Karten gleich hoch,
                    // auch wenn ein Label umbricht (z.B. "Sessions / Woche").
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        StatCard(
                            value = state.sessionsPerWeek.toString(),
                            label = "Sessions / Woche",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatCard(
                            value = state.totalTops.toString(),
                            label = "Tops gesamt",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatCard(
                            value = state.topGrade,
                            label = "Top Grade",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }

                // --- Schnellaktionen ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = "Schnellaktionen")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                        ) {
                            QuickActionButton(
                                text = "Session starten",
                                icon = Icons.Filled.PlayArrow,
                                onClick = onStartSession,
                                primary = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                            // Nur bei aktiver Session: Boulder direkt in diese Session anlegen.
                            if (hasActiveSession) {
                                QuickActionButton(
                                    text = "Boulder hinzufügen",
                                    icon = Icons.Filled.Add,
                                    // Navigation zu "Route hinzufügen" für die AKTIVE Session
                                    // (sessionId = activeSessionId); der neue Boulder wird mit
                                    // dieser sessionId in Room gespeichert (Ziel-ID liefert der
                                    // NavHost, solange kein ViewModel existiert).
                                    onClick = onAddBoulderToActiveSession,
                                    primary = false,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                )
                            }
                            QuickActionButton(
                                text = "Alle Boulder",
                                icon = Icons.Filled.GridView,
                                onClick = onOpenAllBoulders,
                                primary = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }

                // --- Letzte Session ---
                // Nur anzeigen, wenn es überhaupt schon eine Session gibt.
                state.lastSession?.let { last ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                            SectionHeader(text = "Letzte Session")
                            SessionListItem(
                                gym = last.gym,
                                date = last.subtitle,
                                accentColor = last.accentColor,
                                badges = emptyList(),
                                isActive = last.isActive,
                                // Navigation zur letzten Session (SessionRoute mit deren sessionId).
                                onClick = onOpenLastSession,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    BoulderBuddyTheme {
        HomeScreen(
            state = HomeUiState(
                sessionsPerWeek = 4,
                totalTops = 23,
                topGrade = "6c",
                hasActiveSession = true,
                activeSessionId = 1,
                lastSession = LastSessionUi(
                    sessionId = 1,
                    gym = "Boulderhalle Nord",
                    subtitle = "12. Juni · 8 Boulder",
                    accentColor = Color(0xFF2E9E52),
                    isActive = false,
                ),
            ),
        )
    }
}
