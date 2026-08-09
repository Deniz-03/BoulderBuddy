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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.QuickActionButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SessionListItem
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Breite
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.aktuelleBreite
import com.boulderbuddy.ui.theme.inhaltsBreite
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
    // Name kommt aus den Einstellungen (DataStore). Ist keiner gesetzt, grüßt die App neutral,
    // statt einen Platzhalter-Namen zu erfinden.
    val greeting = state.userName
        .takeIf { it.isNotBlank() }
        ?.let { "Hallo, $it 👋" }
        ?: "Hallo 👋"
    val hasActiveSession = state.hasActiveSession

    // Ermittlung des aktuellen Datums
    // Remember spart Leistung und Akku, da es dafür sorgt, dass das Datum nicht bei jeder Änderung am Bildschirm neu geladen werden muss.
    val dateText = remember {
        LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
        )
    }

    /*
     * Die Form der Schnellaktions-Kacheln.
     *
     * Hier stand `aspectRatio(1f)` — quadratisch, weil sich die drei Kacheln die Breite teilen.
     * Am Telefon (~110 dp je Kachel) ergibt das eine handliche Fläche. Die Höhe wächst dabei
     * aber mit der Fensterbreite mit: am Tablet quer wurden daraus 410-dp-Quadrate, die 60 %
     * der Bildhöhe füllten und „Letzte Session" aus dem Bild schoben.
     *
     * Das Quadrat war nie die Absicht, sondern das Ergebnis der Aufteilung. Sobald genug
     * Breite da ist, wird die Höhe deshalb gedeckelt — die Kachel wird dann breit statt hoch,
     * und Icon oben / Label unten funktioniert weiter.
     */
    val kachelHoehe = if (aktuelleBreite() == Breite.Kompakt) {
        Modifier.aspectRatio(1f)
    } else {
        Modifier.height(Dimens.kachelMaxHoehe)
    }

    // Scaffold teilt den Bildschirm in feste Bereiche. oben die Menüleiste, unten die NavBar und in der Mitte der Hauptinhalt.
    BoulderBuddyScaffold(
        // Obere Leiste
        topBar = {
            TopBar(
                title = greeting,
                subtitle = dateText,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.aktion_einstellungen),
                            tint = BoulderBuddy.colors.onChrome,
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
                // Begrenzt und zentriert statt über die volle Fensterbreite gezogen. Am
                // Telefon wirkungslos (das Fenster ist schmaler als die Grenze), am Tablet
                // quer der Unterschied zwischen einem Dashboard und drei auseinandergerissenen
                // Karten.
                modifier = Modifier
                    .fillMaxSize()
                    .inhaltsBreite(Dimens.spaltenBreiteWeit),
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
                            label = stringResource(R.string.home_sessions_pro_woche),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatCard(
                            value = state.totalTops.toString(),
                            label = stringResource(R.string.statistik_tops_gesamt),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatCard(
                            value = state.topGrade,
                            // Label zeigt das System, aus dem der Top-Grade stammt (pro System).
                            label = state.topGradeSystemName
                                .takeIf { it.isNotBlank() }
                                ?.let { "Top · $it" }
                                ?: "Top Grade",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }

                // --- Schnellaktionen ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                        SectionHeader(text = stringResource(R.string.home_schnellaktionen))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                        ) {
                            QuickActionButton(
                                text = stringResource(R.string.session_starten),
                                icon = Icons.Filled.PlayArrow,
                                onClick = onStartSession,
                                primary = true,
                                modifier = Modifier.weight(1f).then(kachelHoehe),
                            )
                            // Nur bei aktiver Session: Boulder direkt in diese Session anlegen.
                            if (hasActiveSession) {
                                QuickActionButton(
                                    text = stringResource(R.string.boulder_hinzufuegen),
                                    icon = Icons.Filled.Add,
                                    // Navigation zu "Route hinzufügen" für die AKTIVE Session
                                    // (sessionId = activeSessionId); der neue Boulder wird mit
                                    // dieser sessionId in Room gespeichert (Ziel-ID liefert der
                                    // NavHost, solange kein ViewModel existiert).
                                    onClick = onAddBoulderToActiveSession,
                                    primary = false,
                                    modifier = Modifier.weight(1f).then(kachelHoehe),
                                )
                            }
                            QuickActionButton(
                                text = stringResource(R.string.home_alle_boulder),
                                icon = Icons.Filled.GridView,
                                onClick = onOpenAllBoulders,
                                primary = false,
                                modifier = Modifier.weight(1f).then(kachelHoehe),
                            )
                        }
                    }
                }

                // --- Letzte Session ---
                // Nur anzeigen, wenn es überhaupt schon eine Session gibt.
                state.lastSession?.let { last ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                            SectionHeader(text = stringResource(R.string.home_letzte_session))
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
                userName = "Deniz",
                sessionsPerWeek = 4,
                totalTops = 23,
                topGrade = "6c",
                topGradeSystemName = "Französisch",
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
