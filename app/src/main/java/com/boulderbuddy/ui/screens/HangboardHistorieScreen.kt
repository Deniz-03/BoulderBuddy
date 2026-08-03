package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.SessionListItem
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.HangboardHistorieEntryUi
import com.boulderbuddy.ui.viewmodel.HangboardHistorieUiState

/**
 * Hangboard-Historie (§0 Säule 5): Liste ALLER Workouts über das vereinte Modell —
 * manuell wie auto, Phone wie Uhr, mit Session ("Halle X") wie eigenständig.
 * Einstieg: antippbarer Hangboard-Block im Statistik-Screen. Function-first,
 * wiederverwendet [SessionListItem] als Listenzeile.
 */
@Composable
fun HangboardHistorieScreen(
    state: HangboardHistorieUiState,
    onBack: () -> Unit = {},
) {
    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Hangboard-Historie",
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
                },
            )
        },
        content = { _ ->
            if (!state.loading && state.entries.isEmpty()) {
                Text(
                    text = "Noch keine Hangboard-Workouts. Starte den Timer — jeder " +
                        "abgeschlossene Durchlauf landet hier.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                    modifier = Modifier.fillMaxSize().padding(Dimens.paddingXL),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Dimens.paddingL,
                        vertical = Dimens.paddingL,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        SessionListItem(
                            gym = entry.title,
                            date = entry.subtitle,
                            // Akzent: Auto-Workouts blau, manuelle grün — eigenständige
                            // Trainings dezent (kein Session-Bezug).
                            accentColor = when {
                                entry.standalone -> BoulderBuddy.colors.borderSubtle
                                entry.auto -> BoulderBuddy.colors.routes.blue
                                else -> BoulderBuddy.colors.routes.green
                            },
                            badges = entry.badges,
                        )
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6)
@Composable
private fun HangboardHistorieScreenPreview() {
    BoulderBuddyTheme {
        HangboardHistorieScreen(
            state = HangboardHistorieUiState(
                loading = false,
                entries = listOf(
                    HangboardHistorieEntryUi(
                        id = 1,
                        title = "Boulderhalle Nord",
                        subtitle = "Heute · Manuell",
                        badges = listOf("6 Sätze", "00:42 Hängezeit"),
                        standalone = false,
                        auto = false,
                    ),
                    HangboardHistorieEntryUi(
                        id = 2,
                        title = "Eigenständig",
                        subtitle = "Gestern · Auto",
                        badges = listOf("5 Sätze", "00:51 Hängezeit"),
                        standalone = true,
                        auto = true,
                    ),
                ),
            ),
        )
    }
}
