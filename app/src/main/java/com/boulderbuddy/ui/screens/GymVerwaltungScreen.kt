package com.boulderbuddy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.theme.inhaltsBreite
import com.boulderbuddy.ui.viewmodel.GymUi
import com.boulderbuddy.ui.viewmodel.GymVerwaltungUiState

/**
 * Hallen-Verwaltung (Gym-Näherungs-Push, M1): listet alle Hallen mit Standort-Status.
 * Tippen öffnet den Editor ([GymBearbeitenScreen]) — für eine bestehende Halle wie für eine
 * neue. Hallen entstehen seit der Auswahl im Session-Formular nicht mehr nebenbei aus einem
 * Textfeld, sondern immer über diesen Editor; deshalb führt auch von hier ein Weg dorthin.
 */
@Composable
fun GymVerwaltungScreen(
    state: GymVerwaltungUiState = GymVerwaltungUiState(),
    onOpenGym: (Int) -> Unit = {},
    onNeueHalle: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.hallen_titel),
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.aktion_zurueck),
                            tint = M3OnPrimary,
                        )
                    }
                },
            )
        },
        content = { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Wie in den Einstellungen, zu denen diese Liste gehört: am Tablet sonst
                    // Zeilen über die volle Fensterbreite (kam mit dem Näherungs-Push nach
                    // der Tablet-Runde, siehe GymBearbeitenScreen).
                    .inhaltsBreite()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Dimens.paddingL),
            ) {
                if (state.gyms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.paddingL),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.hallen_leer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BoulderBuddy.colors.textSecondary,
                        )
                    }
                } else {
                    SectionHeader(
                        text = stringResource(R.string.hallen_deine),
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    state.gyms.forEach { gym ->
                        GymRow(gym = gym, onClick = { onOpenGym(gym.id) })
                    }
                }

                PrimaryButton(
                    text = stringResource(R.string.hallen_neu),
                    icon = Icons.Outlined.Add,
                    onClick = onNeueHalle,
                    modifier = Modifier.padding(
                        horizontal = Dimens.paddingL,
                        vertical = Dimens.paddingL,
                    ),
                )
            }
        },
    )
}

// Eine Hallen-Zeile: Name + Standort-Status; rechts ein Hinweis, wenn Erinnerungen
// für dieses Gym aus sind, und der Navigations-Chevron.
@Composable
private fun GymRow(
    gym: GymUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            // null: die Farbe unterscheidet Hallen mit und ohne Standort, der Text
            // darunter sagt dasselbe in Worten — nur der zählt für TalkBack.
            contentDescription = null,
            // Gyms mit Koordinaten (geofenced) heben sich farblich ab.
            tint = if (gym.hasCoordinates) {
                MaterialTheme.colorScheme.primary
            } else {
                BoulderBuddy.colors.textTertiary
            },
            modifier = Modifier.size(Dimens.iconS),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gym.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    gym.hasCoordinates -> stringResource(R.string.hallen_standort_da)
                    gym.location != null -> gym.location
                    else -> stringResource(R.string.hallen_standort_keiner)
                },
                style = MaterialTheme.typography.labelSmall,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
        if (!gym.proximityAlertsEnabled) {
            Icon(
                imageVector = Icons.Outlined.NotificationsOff,
                contentDescription = stringResource(R.string.hallen_erinnerungen_aus),
                tint = BoulderBuddy.colors.textTertiary,
                modifier = Modifier.size(Dimens.iconS),
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            // null: reines Weiter-Zeichen der klickbaren Zeile.
            contentDescription = null,
            tint = BoulderBuddy.colors.textTertiary,
            modifier = Modifier.size(Dimens.iconS),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GymVerwaltungScreenPreview() {
    BoulderBuddyTheme {
        GymVerwaltungScreen(
            state = GymVerwaltungUiState(
                gyms = listOf(
                    GymUi(1, "Boulderhalle Nord", "Musterstraße 1", true, true),
                    GymUi(2, "Kletterzentrum Süd", null, false, true),
                    GymUi(3, "Meine Halle", null, true, false),
                ),
            ),
        )
    }
}
