package com.boulderbuddy.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Science
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
import com.boulderbuddy.proximity.hasBackgroundLocationPermission
import com.boulderbuddy.proximity.hasFineLocationPermission
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
import com.boulderbuddy.ui.theme.M3OnPrimary
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
    // Einmalige Export-Rückmeldung (Toast); null = keine offene Meldung.
    exportMessage: String? = null,
    // Meldet dem ViewModel, dass die Export-Rückmeldung angezeigt wurde.
    onExportMessageShown: () -> Unit = {},
    // Öffnet den experimentellen Ghost-Climber-Flow (Phase 7.5) — bewusst hier statt
    // im MVP-Kernfluss verankert (Plan A.4).
    onOpenGhostClimber: () -> Unit = {},
    // Öffnet die Hallen-Verwaltung (Gym-Näherungs-Push M1: Koordinaten + Erinnerungen).
    onOpenGymVerwaltung: () -> Unit = {},
    // Master-Toggle des Gym-Näherungs-Push (M2); registriert/entfernt die Geofences.
    onSetProximityAlerts: (Boolean) -> Unit = {},
    // Navigations-Callback (Phase 2). Default = {} hält Preview & Tests lauffähig.
    onBack: () -> Unit = {},
) {
    // Lokaler UI-State (Geräte-Toggles noch nicht persistiert).
    // TODO: aus den App-Einstellungen lesen/schreiben (DataStore via ViewModel).
    var smartwatchVerbunden by remember { mutableStateOf(true) }
    var haptischesFeedback by remember { mutableStateOf(true) }
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

    // Hintergrund-Standort-Flow des Gym-Näherungs-Push (M2). Android erzwingt die
    // Reihenfolge: erst Foreground (FINE) gewähren lassen, DANN Background anfragen —
    // ab API 30 öffnet die Background-Anfrage den System-Settings-Flow ("Immer erlauben").
    // Zum Schluss (M4) POST_NOTIFICATIONS (Runtime-Permission ab API 33) mit anfragen.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Toggle unabhängig vom Grant setzen — ohne Notification-Permission degradiert
        // nur die Anzeige (der Notifier prüft selbst und zeigt dann nichts).
        onSetProximityAlerts(true)
        if (!granted) {
            Toast.makeText(
                context,
                "Ohne Benachrichtigungs-Erlaubnis können keine Erinnerungen erscheinen.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val requestNotificationsOrEnable = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSetProximityAlerts(true)
        }
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Auch ohne Background-Grant weitermachen: der Nutzer kann "Immer erlauben"
        // später in den System-Einstellungen nachreichen; bis dahin degradiert das
        // Feature still (GeofenceManager registriert nichts).
        if (!granted) {
            Toast.makeText(
                context,
                "Erinnerungen funktionieren nur mit Standort „Immer erlauben“ " +
                    "(App-Einstellungen).",
                Toast.LENGTH_LONG,
            ).show()
        }
        requestNotificationsOrEnable()
    }
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(
                context,
                "Ohne Standort-Berechtigung sind Gym-Erinnerungen nicht möglich.",
                Toast.LENGTH_LONG,
            ).show()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasBackgroundLocationPermission(context)
        ) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestNotificationsOrEnable()
        }
    }
    val enableProximityAlerts = {
        when {
            !hasFineLocationPermission(context) -> foregroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasBackgroundLocationPermission(context) ->
                backgroundPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                )
            else -> requestNotificationsOrEnable()
        }
    }

    // Steuert die Grading-Dialoge (Standard wählen / anlegen / verwalten).
    var showGradingDialog by remember { mutableStateOf(false) }
    var showCreateGradingDialog by remember { mutableStateOf(false) }
    var showManageGradingDialog by remember { mutableStateOf(false) }
    // Zu löschendes System (Bestätigung); null = kein Löschdialog offen.
    var pendingDelete by remember { mutableStateOf<GradeSystemUi?>(null) }

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
                    // Hallen-Verwaltung (Näherungs-Push M1): Koordinaten, Radius, Erinnerungen.
                    SettingsRow(
                        icon = Icons.Outlined.LocationOn,
                        label = "Hallen verwalten",
                        onClick = onOpenGymVerwaltung,
                        trailing = {
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = BoulderBuddy.colors.textTertiary,
                                modifier = Modifier.size(Dimens.iconS),
                            )
                        },
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
                    // Gym-Näherungs-Push (M2): "Session starten?"-Erinnerung, wenn man an
                    // einer hinterlegten Halle ankommt. Einschalten stößt den (mehrstufigen)
                    // Standort-Permission-Flow an.
                    SettingsRow(
                        icon = Icons.Outlined.NotificationsActive,
                        label = "Gym-Erinnerungen",
                        trailing = {
                            ToggleSwitch(
                                checked = state.proximityAlertsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) enableProximityAlerts()
                                    else onSetProximityAlerts(false)
                                },
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
                        icon = Icons.Outlined.Info,
                        label = "Über BoulderBuddy",
                        value = "v0.1", // TODO: aus BuildConfig.VERSION_NAME
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BoulderBuddy.colors.textTertiary,
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BoulderBuddy.colors.textTertiary,
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
                    style = MaterialTheme.typography.labelSmall,
                    color = BoulderBuddy.colors.textTertiary,
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
