package com.boulderbuddy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SessionListItem
import com.boulderbuddy.ui.components.UebersichtTopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.theme.routeColorForKey

// Platzhalter-Datenklasse bis das echte Datenmodell aus Room kommt.
// id: Platzhalter-Sessionschlüssel für die Navigation (später die echte Room-ID).
// Konvention wie in SessionRoute: id 0 = laufende Session, sonst abgeschlossen.
private data class SessionPreviewData(
    val id: Int,
    val gym: String,
    val date: String,
    val accentColorKey: String,
    val badges: List<String>,
    val isActive: Boolean = false,
)

// TODO: Diese Liste kommt später aus dem ViewModel (Datenbank via Room).
//  Aktive Session steht immer zuerst, danach nach Datum absteigend sortiert.
private val placeholderSessions = listOf(
    SessionPreviewData(
        id = 0,
        gym = "Boulderhalle Nord",
        date = "Heute · läuft gerade",
        accentColorKey = "blue",
        badges = listOf("5 Boulder"),
        isActive = true,
    ),
    SessionPreviewData(
        id = 1,
        gym = "Boulderhalle Nord",
        date = "12. Juni · Französisch",
        accentColorKey = "green",
        badges = listOf("8 Boulder", "3 Tops"),
    ),
    SessionPreviewData(
        id = 2,
        gym = "Kletterzentrum Süd",
        date = "9. Juni · V-Scale",
        accentColorKey = "pink",
        badges = listOf("6 Boulder"),
    ),
)

// Bottom-Nav Tab 2. Der Header trägt einen Dropdown, über den man zur Boulder-Übersicht
// umschaltet (beide teilen sich diesen Tab). Das Umschalten ist eine Navigation zum
// anderen Screen.
@Composable
fun SessionUebersichtScreen(
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onOpenSession: (Int) -> Unit = {},
    onCreateSession: () -> Unit = {},
    onOpenBoulderOverview: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // TODO: Sessionanzahl kommt aus der Datenbank via ViewModel
    val sessionCount = placeholderSessions.size

    BoulderBuddyScaffold(
        topBar = {
            UebersichtTopBar(
                current = "Sessions",
                onSelectSessions = { /* bereits hier — Dropdown schließt nur */ },
                // Navigation zur Boulder-Übersicht (BoulderUebersichtScreen).
                onSelectBoulder = onOpenBoulderOverview,
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
        // BottomNav wird ab Phase 1.3 zentral vom Navigations-Gerüst gestellt.
        content = { _ ->
          Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.paddingL,
                    end = Dimens.paddingL,
                    top = Dimens.paddingL,
                    // Zusätzlicher Unterrand, damit der letzte Eintrag nicht hinter dem FAB liegt.
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
            ) {
                // Kopfzeile: Anzahl links, Sortierung rechts
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // TODO: "$sessionCount" durch echten Wert aus der Datenbank ersetzen
                        SectionHeader(text = "$sessionCount Sessions")
                        // Sortier-Steuerung. TODO: Sortier-Logik implementieren (nach Datum, nach Halle etc.)
                        Row(
                            modifier = Modifier.clickable { /* TODO: Sortierung umschalten */ },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
                        ) {
                            Text(
                                text = "Datum",
                                style = MaterialTheme.typography.labelSmall,
                                color = BoulderBuddy.colors.textTertiary,
                            )
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = "Sortierung ändern",
                                tint = BoulderBuddy.colors.textTertiary,
                                modifier = Modifier.size(Dimens.iconS),
                            )
                        }
                    }
                }

                // TODO: Sessions kommen aus der Datenbank via ViewModel.
                //  accentColor wird aus dem gespeicherten Farbwert der Session gelesen.
                items(placeholderSessions) { session ->
                    SessionListItem(
                        gym = session.gym,
                        date = session.date,
                        accentColor = routeColorForKey(session.accentColorKey),
                        badges = session.badges,
                        isActive = session.isActive,
                        // Navigation zur Session (SessionRoute mit der jeweiligen sessionId).
                        onClick = { onOpenSession(session.id) },
                    )
                }
            }

            // FAB: neue Session anlegen (führt wie Home „Session starten" zu SessionErstellen).
            // Schwebt über der Liste, oberhalb der gemeinsamen BottomNav.
            FloatingActionButton(
                onClick = onCreateSession,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimens.paddingL),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Session hinzufügen",
                )
            }
          }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionUebersichtScreenPreview() {
    BoulderBuddyTheme {
        SessionUebersichtScreen()
    }
}
