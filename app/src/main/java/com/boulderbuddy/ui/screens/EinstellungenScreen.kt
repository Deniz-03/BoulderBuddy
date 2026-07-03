package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.SettingsRow
import com.boulderbuddy.ui.components.ToggleSwitch
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary

// Auswahl-Optionen fürs Standard-Grading. Identisch zu SessionErstellenScreen.
// TODO: zentral halten (gemeinsame Quelle/DB), sobald Custom-Farbsysteme dazukommen
//  ("Eigenes…" lädt dann die in der DB angelegten Systeme).
private val gradingOptions = listOf("Französisch", "V-Scale", "Farbsystem", "Eigenes…")

@Composable
fun EinstellungenScreen(
    // Navigations-Callback (Phase 2). Default = {} hält Preview & Tests lauffähig.
    onBack: () -> Unit = {},
) {
    // Lokaler UI-State. Hält die Werte vorerst nur in der Komposition.
    // TODO: aus den App-Einstellungen lesen/schreiben (DataStore via ViewModel).
    var standardGradingIndex by remember { mutableIntStateOf(0) }
    var smartwatchVerbunden by remember { mutableStateOf(true) }
    var haptischesFeedback by remember { mutableStateOf(true) }
    var darkMode by remember { mutableStateOf(false) }

    // Steuert den Standard-Grading-Auswahldialog.
    var showGradingDialog by remember { mutableStateOf(false) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Einstellungen",
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = M3OnPrimary,
                        )
                    }
                }
            )
        },
        content = { _ ->
            // Kein horizontales Padding: SettingsRow paddet intern bereits (paddingL),
            // so gehen die Zeilen randlos und sind voll klickbar. Nur die SectionHeader
            // werden einzeln eingerückt. verticalScroll, falls die Liste später wächst.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Dimens.paddingL),
                // 16dp Abstand zwischen den Gruppen.
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                // --- Gruppe: Klettern ---
                Column {
                    SectionHeader(
                        text = "Klettern",
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    // Wert-Zeile: öffnet den Auswahldialog, zeigt aktuelle Wahl rechts.
                    SettingsRow(
                        icon = Icons.Outlined.Tune,
                        label = "Standard-Grading",
                        value = gradingOptions[standardGradingIndex],
                        onClick = { showGradingDialog = true },
                    )
                    // Reine Navigations-Zeile: Chevron als trailing, kein Wert.
                    // TODO: Farbsystem-Verwaltung (CRUD) — Mechanismus noch offen
                    //  (eigener Screen vs. Bottom Sheet). Vorerst Platzhalter.
                    SettingsRow(
                        icon = Icons.Outlined.Palette,
                        label = "Farbsystem verwalten",
                        onClick = { /* TODO: Farbsystem-Verwaltung öffnen */ },
                        trailing = {
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = BoulderBuddy.colors.textTertiary,
                                modifier = Modifier.size(Dimens.iconS),
                            )
                        },
                    )
                }

                // --- Gruppe: Gerät ---
                Column {
                    SectionHeader(
                        text = "Gerät",
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Watch,
                        label = "Smartwatch verbunden",
                        trailing = {
                            ToggleSwitch(
                                checked = smartwatchVerbunden,
                                onCheckedChange = { smartwatchVerbunden = it },
                            )
                        },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Vibration,
                        label = "Haptisches Feedback",
                        trailing = {
                            ToggleSwitch(
                                checked = haptischesFeedback,
                                onCheckedChange = { haptischesFeedback = it },
                            )
                        },
                    )
                }

                // --- Gruppe: App ---
                Column {
                    SectionHeader(
                        text = "App",
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    SettingsRow(
                        icon = Icons.Outlined.DarkMode,
                        label = "Dark Mode",
                        trailing = {
                            ToggleSwitch(
                                checked = darkMode,
                                onCheckedChange = { darkMode = it },
                            )
                        },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        label = "Über BoulderBuddy",
                        value = "v0.1", // TODO: aus BuildConfig.VERSION_NAME
                    )
                }
            }
        }
    )

    // Auswahldialog fürs Standard-Grading. Tippen auf einen Chip wählt aus und schließt.
    if (showGradingDialog) {
        GradingAuswahlDialog(
            options = gradingOptions,
            selectedIndex = standardGradingIndex,
            onSelect = { standardGradingIndex = it },
            onDismiss = { showGradingDialog = false },
        )
    }
}

// Das 2×2-Chip-Raster der Grading-Auswahl. Verwendet dieselben SelectableChip wie
// SessionErstellenScreen — eine visuelle Sprache fürs Grading-System. Ausgelagert,
// damit es sowohl der Dialog als auch die statische Preview nutzen kann.
@Composable
private fun GradingChipGrid(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        // chunked(2) teilt die Optionen in 2er-Reihen → 2×2-Raster.
        options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
            ) {
                rowOptions.forEachIndexed { colIndex, label ->
                    val index = rowIndex * 2 + colIndex
                    SelectableChip(
                        label = label,
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Einfach-Auswahl des Standard-Gradings. Tippen auf einen Chip wählt aus und schließt.
@Composable
private fun GradingAuswahlDialog(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Standard-Grading") },
        text = {
            GradingChipGrid(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = {
                    onSelect(it)
                    onDismiss()
                },
            )
        },
        // Auswahl erfolgt per Chip-Tap, daher kein Bestätigen-Button nötig.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun EinstellungenScreenPreview() {
    BoulderBuddyTheme {
        EinstellungenScreen()
    }
}


// Statische Vorschau: baut die Dialog-Surface nach (Titel + Chip-Raster), damit
// Aussehen und Abstände OHNE Interactive Mode sichtbar sind. Schwarzer Hintergrund
// deutet den Scrim an. Nicht die echte AlertDialog-Chrome, aber inhaltlich identisch.
@Preview(name = "Grading-Auswahl (statisch)", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun GradingAuswahlContentPreview() {
    BoulderBuddyTheme {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(320.dp)
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                Text(
                    text = "Standard-Grading",
                    style = MaterialTheme.typography.headlineSmall,
                )
                GradingChipGrid(
                    options = gradingOptions,
                    selectedIndex = 0,
                    onSelect = {},
                )
            }
        }
    }
}
