package com.boulderbuddy.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.BuildConfig
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.SettingsRow
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.ToggleSwitch
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.inhaltsBreite
import com.boulderbuddy.ui.viewmodel.EinstellungenUiState
import com.boulderbuddy.ui.viewmodel.GradeSystemUi

@Composable
fun EinstellungenScreen(
    // Grading-Verwaltung aus dem EinstellungenViewModel.
    state: EinstellungenUiState = EinstellungenUiState(),
    // Legt ein Custom-Grading-System an (Name + Grade-Labels, von leicht nach schwer).
    onCreateGradeSystem: (String, List<String>) -> Unit = { _, _ -> },
    // Löscht ein (löschbares) Grading-System.
    onDeleteGradeSystem: (Int) -> Unit = {},
    // Standard-Grading wählen (persistiert das Grade-System, das das Boulder-Dropdown speist).
    onSelectGradeSystem: (Int) -> Unit = {},
    // Exportiert alle Sessions als CSV in das gewählte SAF-Dokument (7.3b).
    onExportSessions: (Uri) -> Unit = {},
    // Setzt den Dark-Mode-Override (persistent via DataStore); steuert das App-Theme (7.4a).
    onSetDarkMode: (Boolean) -> Unit = {},
    // Schaltet die Vibration bei Timer-Phasenwechseln (persistent via DataStore).
    onSetHapticFeedback: (Boolean) -> Unit = {},
    // Speichert den Anzeigenamen für die Home-Begrüßung.
    onSetUserName: (String) -> Unit = {},
    // Einmalige Export-Rückmeldung (Toast); null = keine offene Meldung.
    exportMessage: String? = null,
    // Meldet dem ViewModel, dass die Export-Rückmeldung angezeigt wurde.
    onExportMessageShown: () -> Unit = {},
    // Öffnet den experimentellen Ghost-Climber-Flow (Phase 7.5) — bewusst hier statt
    // im MVP-Kernfluss verankert (Plan A.4).
    onOpenGhostClimber: () -> Unit = {},
    // Oeffnet "Geraete abgleichen" (Sync-Plan S7). Steht bei den Daten, nicht unter
    // Experimental: es geht um den eigenen Bestand, nicht um eine Spielerei.
    onOpenAbgleich: () -> Unit = {},
    // Navigations-Callback (Phase 2). Default = {} hält Preview & Tests lauffähig.
    onBack: () -> Unit = {},
) {
    // Effektiver Dark-Mode-Zustand: expliziter Override, sonst dem System folgen. Der Switch
    // zeigt den aktuell wirksamen Zustand; ein Tap persistiert ihn als expliziten Override (7.4a).
    val darkMode = state.darkModeOverride ?: isSystemInDarkTheme()

    // SAF-Launcher: legt ein neues CSV-Dokument an; die gewählte URI geht an den Export.
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(onExportSessions) }

    // Export-Ergebnis als Toast zeigen und danach quittieren.
    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onExportMessageShown()
        }
    }

    // Steuert die Grading-Dialoge (Standard wählen / anlegen / verwalten).
    var showGradingDialog by remember { mutableStateOf(false) }
    var showCreateGradingDialog by remember { mutableStateOf(false) }
    var showManageGradingDialog by remember { mutableStateOf(false) }
    // Zu löschendes System (Bestätigung); null = kein Löschdialog offen.
    var pendingDelete by remember { mutableStateOf<GradeSystemUi?>(null) }
    // Dialog zum Ändern des Anzeigenamens (Home-Begrüßung).
    var showNameDialog by remember { mutableStateOf(false) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Einstellungen",
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = BoulderBuddy.colors.onChrome,
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
                    // Ohne Begrenzung stand am Tablet das Label „Dark Mode" ganz links und
                    // sein Schalter 1240 dp weiter rechts. Beide gehören zur selben Zeile,
                    // aber auf diese Entfernung liest man sie nicht mehr als eine.
                    .inhaltsBreite()
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
                    // Wert-Zeile: öffnet den Auswahldialog, zeigt das gewählte System rechts.
                    val selectedSystemName = state.systems
                        .firstOrNull { it.id == state.selectedGradeSystemId }?.name ?: "—"
                    SettingsRow(
                        icon = Icons.Outlined.Tune,
                        label = "Standard-Grading",
                        value = selectedSystemName,
                        onClick = { showGradingDialog = true },
                    )
                    // Öffnet den Anlege-Dialog fürs Custom-Grading-System.
                    SettingsRow(
                        icon = Icons.Outlined.Add,
                        label = "Grading-System erstellen",
                        onClick = { showCreateGradingDialog = true },
                    )
                    // Öffnet die Verwaltung (Systeme ansehen/löschen). Zeigt die Anzahl als Kontext.
                    SettingsRow(
                        icon = Icons.Outlined.Edit,
                        label = "Grading-Systeme verwalten",
                        onClick = { showManageGradingDialog = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${state.systems.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BoulderBuddy.colors.textTertiary,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = BoulderBuddy.colors.textTertiary,
                                    modifier = Modifier.size(Dimens.iconS),
                                )
                            }
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
                    // Reine Statusanzeige: die Kopplung passiert in der Wear-App bzw. den
                    // Systemeinstellungen — ein Schalter hier würde Steuerbarkeit vortäuschen.
                    SettingsRow(
                        icon = Icons.Outlined.Watch,
                        label = "Smartwatch",
                        value = if (state.watchConnected) "Verbunden" else "Nicht verbunden",
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Vibration,
                        label = "Haptisches Feedback",
                        subtitle = "Vibration bei Timer-Phasenwechseln",
                        trailing = {
                            ToggleSwitch(
                                checked = state.hapticFeedback,
                                onCheckedChange = onSetHapticFeedback,
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
                                onCheckedChange = onSetDarkMode,
                            )
                        },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.FileDownload,
                        label = "Sessions exportieren (CSV)",
                        onClick = { exportLauncher.launch("boulderbuddy_sessions.csv") },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Sync,
                        label = "Geräte abgleichen",
                        subtitle = "Phone und Tablet auf denselben Stand bringen",
                        onClick = onOpenAbgleich,
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Person,
                        label = "Name",
                        value = state.userName.ifBlank { "Nicht gesetzt" },
                        onClick = { showNameDialog = true },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        label = "Über BoulderBuddy",
                        value = "v${BuildConfig.VERSION_NAME}",
                    )
                }

                // --- Gruppe: Experimental (7.5) ---
                Column {
                    SectionHeader(
                        text = "Experimental",
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Science,
                        label = "Ghost Climber",
                        value = "Beta",
                        onClick = onOpenGhostClimber,
                    )
                }
            }
        }
    )

    // Auswahldialog fürs Standard-Grading. Tippen auf ein System wählt aus und schließt.
    if (showGradingDialog) {
        GradingAuswahlDialog(
            systems = state.systems,
            selectedId = state.selectedGradeSystemId,
            onSelect = { onSelectGradeSystem(it) },
            onDismiss = { showGradingDialog = false },
        )
    }

    // Anzeigename ändern (steuert die Begrüßung auf dem Home-Screen).
    if (showNameDialog) {
        NameAendernDialog(
            current = state.userName,
            onSave = {
                onSetUserName(it)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }

    // Anlege-Dialog fürs Custom-Grading-System (Name + Grade-Labels).
    if (showCreateGradingDialog) {
        GradingSystemAnlegenDialog(
            onCreate = { name, labels ->
                onCreateGradeSystem(name, labels)
                showCreateGradingDialog = false
            },
            onDismiss = { showCreateGradingDialog = false },
        )
    }

    // Verwaltungs-Dialog: Systeme ansehen, löschbare (Custom) mit Papierkorb.
    if (showManageGradingDialog) {
        GradingSystemeVerwaltenDialog(
            systems = state.systems,
            onDeleteRequest = { pendingDelete = it },
            onDismiss = { showManageGradingDialog = false },
        )
    }

    // Lösch-Bestätigung für das gewählte System.
    pendingDelete?.let { system ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("System löschen?") },
            text = {
                Text(
                    "„${system.name}\" wird gelöscht. Boulder dieses Systems behalten ihre Farbe, " +
                        "verlieren aber ihre Grad-Zuordnung."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGradeSystem(system.id)
                        pendingDelete = null
                    },
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") }
            },
        )
    }
}

// Ändert den Anzeigenamen für die Home-Begrüßung. Ein leeres Feld ist erlaubt und bedeutet
// „keine namentliche Begrüßung" — deshalb kein `enabled`-Gate auf dem Speichern-Button.
@Composable
private fun NameAendernDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                // Kein Platzhalter-Beispiel: bei einem Feld, das genau eine offensichtliche
                // Eingabe hat, erklärt ein „z.B. …" nichts — es schiebt nur einen fremden
                // Namen ins Feld, den man beim Tippen erst mental wegräumen muss.
                TextField(
                    value = name,
                    onChange = { name = it },
                )
                Text(
                    text = "Wird auf dem Home-Screen zur Begrüßung genutzt. Leer lassen für eine " +
                        "neutrale Begrüßung.",
                    // Ein Satz gehört in einen Body-Stil, nicht in den Versalien-Label-Stil.
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// Listet alle Gradsysteme. Löschbare (Custom, `deletable`) bekommen einen Papierkorb; die
// geschützten Standards (V-Scale/Französisch) zeigen einen dezenten "Standard"-Hinweis.
@Composable
private fun GradingSystemeVerwaltenDialog(
    systems: List<GradeSystemUi>,
    onDeleteRequest: (GradeSystemUi) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grading-Systeme") },
        text = {
            if (systems.isEmpty()) {
                Text(
                    text = "Noch keine Grading-Systeme vorhanden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    systems.forEach { system ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = system.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${system.gradeCount} Grade",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BoulderBuddy.colors.textSecondary,
                                )
                            }
                            if (system.deletable) {
                                IconButton(onClick = { onDeleteRequest(system) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "System löschen",
                                        tint = BoulderBuddy.colors.textSecondary,
                                    )
                                }
                            } else {
                                Text(
                                    text = "Standard",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BoulderBuddy.colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

// Legt ein Custom-Grading-System an: Name + dynamische Liste von Grade-Labels (von leicht
// nach schwer). Grade sind reine Schwierigkeit — keine Farbe (die hängt an der Route).
@Composable
private fun GradingSystemAnlegenDialog(
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // Start mit zwei leeren Zeilen; weitere per "Grad hinzufügen".
    val labels = remember { mutableStateListOf("", "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Grading-System") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
            ) {
                TextField(
                    value = name,
                    onChange = { name = it },
                    label = "NAME",
                    placeholder = "z.B. Meine Skala",
                )
                Text(
                    text = "Grade (von leicht nach schwer)",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )
                labels.forEachIndexed { index, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                    ) {
                        TextField(
                            value = label,
                            onChange = { labels[index] = it },
                            placeholder = "z.B. V$index",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { if (labels.size > 1) labels.removeAt(index) },
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Grad entfernen")
                        }
                    }
                }
                TextButton(onClick = { labels.add("") }) { Text("+ Grad hinzufügen") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && labels.any { it.isNotBlank() },
                onClick = { onCreate(name, labels.toList()) },
            ) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// Einfach-Auswahl des Standard-Gradings aus den real vorhandenen Grade-Systemen
// (Standards wie V-Scale/Französisch + Custom-Systeme). Tippen wählt aus und schließt.
@Composable
private fun GradingAuswahlDialog(
    systems: List<GradeSystemUi>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Standard-Grading") },
        text = {
            if (systems.isEmpty()) {
                Text(
                    text = "Noch keine Grade-Systeme vorhanden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    systems.forEach { system ->
                        SelectableChip(
                            label = "${system.name} · ${system.gradeCount} Grade",
                            selected = system.id == selectedId,
                            onClick = {
                                onSelect(system.id)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
        EinstellungenScreen(
            state = EinstellungenUiState(
                systems = listOf(
                    GradeSystemUi(id = 2, name = "V-Scale", gradeCount = 11),
                    GradeSystemUi(id = 3, name = "Französisch", gradeCount = 14),
                ),
                selectedGradeSystemId = 2,
            ),
        )
    }
}
