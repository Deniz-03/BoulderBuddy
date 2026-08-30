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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.BuildConfig
import com.boulderbuddy.R
import com.boulderbuddy.proximity.hasBackgroundLocationPermission
import com.boulderbuddy.proximity.hasFineLocationPermission
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.EingabeDialog
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

/**
 * Die Einstellungen — und zugleich die Tür zu allem, was kein eigener Tab ist.
 *
 * Drei Sorten Zeile stehen hier nebeneinander: echte Schalter (Dark Mode, Haptik,
 * Näherungs-Erinnerungen), Verwaltungs-Sprünge (Hallen, Gradsysteme, Geräte-Abgleich) und
 * unter „Experimental" der Ghost Climber. Letzteres ist Absicht: der Ghost Climber ist
 * bewusst kein fünfter Tab, damit der MVP-Kernfluss unberührt bleibt.
 *
 * Die Standort- und Notification-Freigaben werden von hier aus angefragt, in der
 * Reihenfolge, die Android erzwingt — erst Vordergrund, dann Hintergrund, dann Benachrichtigungen.
 */
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
    // Öffnet die Hallen-Verwaltung (Gym-Näherungs-Push M1: Koordinaten + Erinnerungen).
    onOpenGymVerwaltung: () -> Unit = {},
    // Master-Toggle des Gym-Näherungs-Push (M2); registriert/entfernt die Geofences.
    onSetProximityAlerts: (Boolean) -> Unit = {},
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

    // Texte, die aus Rückrufen heraus gebraucht werden: einmal hier aus der Composition
    // holen. In einem Launcher-Rückruf gibt es keine Composition mehr — ein getString dort
    // liest die Ressourcen an der Composition vorbei und bekommt nach einem Sprachwechsel
    // die alte Fassung.
    val ohneNotification = stringResource(R.string.einstellungen_ohne_notification)
    val ohneHintergrundStandort = stringResource(R.string.einstellungen_ohne_hintergrund_standort)
    val ohneStandort = stringResource(R.string.einstellungen_ohne_standort)
    val exportDateiname = stringResource(R.string.einstellungen_export_dateiname)

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
                ohneNotification,
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
                ohneHintergrundStandort,
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
                ohneStandort,
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
    var showCreateGradingDialog by rememberSaveable { mutableStateOf(false) }
    var showManageGradingDialog by remember { mutableStateOf(false) }
    // Zu löschendes System (Bestätigung); null = kein Löschdialog offen.
    var pendingDelete by remember { mutableStateOf<GradeSystemUi?>(null) }
    // Dialog zum Ändern des Anzeigenamens (Home-Begrüßung).
    var showNameDialog by rememberSaveable { mutableStateOf(false) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.einstellungen_titel),
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.aktion_zurueck),
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
                        text = stringResource(R.string.einstellungen_gruppe_klettern),
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    // Wert-Zeile: öffnet den Auswahldialog, zeigt das gewählte System rechts.
                    val selectedSystemName = state.systems
                        .firstOrNull { it.id == state.selectedGradeSystemId }?.name
                        ?: stringResource(R.string.einstellungen_kein_wert)
                    SettingsRow(
                        icon = Icons.Outlined.Tune,
                        label = stringResource(R.string.einstellungen_standard_grading),
                        value = selectedSystemName,
                        onClick = { showGradingDialog = true },
                    )
                    // Öffnet den Anlege-Dialog fürs Custom-Grading-System.
                    SettingsRow(
                        icon = Icons.Outlined.Add,
                        label = stringResource(R.string.einstellungen_grading_erstellen),
                        onClick = { showCreateGradingDialog = true },
                    )
                    // Hallen-Verwaltung (Näherungs-Push M1): Koordinaten, Radius, Erinnerungen.
                    SettingsRow(
                        icon = Icons.Outlined.LocationOn,
                        label = stringResource(R.string.einstellungen_hallen_verwalten),
                        onClick = onOpenGymVerwaltung,
                        trailing = {
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                // null: das Winkelzeichen wiederholt nur, dass die Zeile
                                // weiterführt — das sagt ihr Klickverhalten schon.
                                contentDescription = null,
                                tint = BoulderBuddy.colors.textTertiary,
                                modifier = Modifier.size(Dimens.iconS),
                            )
                        },
                    )
                    // Öffnet die Verwaltung (Systeme ansehen/löschen). Zeigt die Anzahl als Kontext.
                    SettingsRow(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.einstellungen_grading_verwalten),
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
                                    // null: wie oben — reines Weiter-Zeichen.
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
                        text = stringResource(R.string.einstellungen_gruppe_geraet),
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    // Reine Statusanzeige: die Kopplung passiert in der Wear-App bzw. den
                    // Systemeinstellungen — ein Schalter hier würde Steuerbarkeit vortäuschen.
                    SettingsRow(
                        icon = Icons.Outlined.Watch,
                        label = stringResource(R.string.einstellungen_smartwatch),
                        value = stringResource(
                            if (state.watchConnected) R.string.einstellungen_smartwatch_verbunden
                            else R.string.einstellungen_smartwatch_getrennt,
                        ),
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Vibration,
                        label = stringResource(R.string.einstellungen_haptik),
                        subtitle = stringResource(R.string.einstellungen_haptik_hinweis),
                        trailing = {
                            ToggleSwitch(
                                checked = state.hapticFeedback,
                                onCheckedChange = onSetHapticFeedback,
                            )
                        },
                    )
                    // Gym-Näherungs-Push (M2): "Session starten?"-Erinnerung, wenn man an
                    // einer hinterlegten Halle ankommt. Einschalten stößt den (mehrstufigen)
                    // Standort-Permission-Flow an.
                    SettingsRow(
                        icon = Icons.Outlined.NotificationsActive,
                        label = stringResource(R.string.einstellungen_gym_erinnerungen),
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
                        text = stringResource(R.string.einstellungen_gruppe_app),
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    SettingsRow(
                        icon = Icons.Outlined.DarkMode,
                        label = stringResource(R.string.einstellungen_dark_mode),
                        trailing = {
                            ToggleSwitch(
                                checked = darkMode,
                                onCheckedChange = onSetDarkMode,
                            )
                        },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.FileDownload,
                        label = stringResource(R.string.einstellungen_export),
                        onClick = { exportLauncher.launch(exportDateiname) },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Sync,
                        label = stringResource(R.string.einstellungen_abgleich),
                        subtitle = stringResource(R.string.einstellungen_abgleich_hinweis),
                        onClick = onOpenAbgleich,
                    )
                    val nichtGesetzt = stringResource(R.string.einstellungen_name_leer)
                    SettingsRow(
                        icon = Icons.Outlined.Person,
                        label = stringResource(R.string.einstellungen_name),
                        value = state.userName.ifBlank { nichtGesetzt },
                        onClick = { showNameDialog = true },
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        label = stringResource(R.string.einstellungen_ueber),
                        value = stringResource(
                            R.string.einstellungen_version,
                            BuildConfig.VERSION_NAME,
                        ),
                    )
                }

                // --- Gruppe: Experimental (7.5) ---
                Column {
                    SectionHeader(
                        text = stringResource(R.string.einstellungen_gruppe_experimental),
                        modifier = Modifier.padding(
                            horizontal = Dimens.paddingL,
                            vertical = Dimens.paddingS,
                        ),
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Science,
                        label = stringResource(R.string.einstellungen_ghost),
                        value = stringResource(R.string.einstellungen_ghost_beta),
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
            title = { Text(stringResource(R.string.einstellungen_grading_loeschen_titel)) },
            text = {
                Text(
                    stringResource(
                        R.string.einstellungen_grading_loeschen_text,
                        system.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGradeSystem(system.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.aktion_loeschen)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.aktion_abbrechen))
                }
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
    var name by rememberSaveable { mutableStateOf(current) }

    EingabeDialog(
        titel = stringResource(R.string.einstellungen_name),
        bestaetigenText = stringResource(R.string.aktion_speichern),
        onBestaetigen = { onSave(name) },
        onAbbrechen = onDismiss,
    ) {
        // Kein Platzhalter-Beispiel: bei einem Feld, das genau eine offensichtliche
        // Eingabe hat, erklärt ein „z.B. …" nichts — es schiebt nur einen fremden
        // Namen ins Feld, den man beim Tippen erst mental wegräumen muss.
        TextField(
            value = name,
            onChange = { name = it },
        )
        Text(
            text = stringResource(R.string.einstellungen_name_hinweis),
            // Ein Satz gehört in einen Body-Stil, nicht in den Versalien-Label-Stil.
            style = MaterialTheme.typography.bodySmall,
            color = BoulderBuddy.colors.textSecondary,
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
        title = { Text(stringResource(R.string.einstellungen_grading_systeme)) },
        text = {
            if (systems.isEmpty()) {
                Text(
                    text = stringResource(R.string.einstellungen_grading_leer),
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
                                    text = pluralStringResource(
                                        R.plurals.grad_anzahl,
                                        system.gradeCount,
                                        system.gradeCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BoulderBuddy.colors.textSecondary,
                                )
                            }
                            if (system.deletable) {
                                IconButton(onClick = { onDeleteRequest(system) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(
                                            R.string.einstellungen_grading_loeschen,
                                        ),
                                        tint = BoulderBuddy.colors.textSecondary,
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(
                                        R.string.einstellungen_grading_standard,
                                    ),
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aktion_schliessen)) }
        },
    )
}

// Legt ein Custom-Grading-System an: Name + dynamische Liste von Grade-Labels (von leicht
// nach schwer). Grade sind reine Schwierigkeit — keine Farbe (die hängt an der Route).
@Composable
private fun GradingSystemAnlegenDialog(
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    // Start mit zwei leeren Zeilen; weitere per "Grad hinzufügen".
    //
    // rememberSaveable und nicht remember: der Name daneben überlebte das Drehen längst, die
    // Grade nicht — man drehte einmal und hatte einen benannten Dialog voll leerer Felder.
    // `mutableStateListOf` kann kein Bundle, deshalb der listSaver über die reine Liste.
    val labels = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf("", "") }

    EingabeDialog(
        titel = stringResource(R.string.einstellungen_grading_neu),
        bestaetigenText = stringResource(R.string.aktion_anlegen),
        bestaetigenAktiv = name.isNotBlank() && labels.any { it.isNotBlank() },
        onBestaetigen = { onCreate(name, labels.toList()) },
        onAbbrechen = onDismiss,
    ) {
        TextField(
            value = name,
            onChange = { name = it },
            label = stringResource(R.string.einstellungen_grading_name_label),
            placeholder = stringResource(
                R.string.einstellungen_grading_name_platzhalter,
            ),
        )
        Text(
            text = stringResource(R.string.einstellungen_grading_grade_hinweis),
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
                    placeholder = stringResource(
                        R.string.einstellungen_grading_grad_platzhalter,
                        index,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (labels.size > 1) labels.removeAt(index) },
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(
                            R.string.einstellungen_grading_grad_entfernen,
                        ),
                    )
                }
            }
        }
        TextButton(onClick = { labels.add("") }) {
            Text(stringResource(R.string.einstellungen_grading_grad_hinzufuegen))
        }
    }
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
        title = { Text(stringResource(R.string.einstellungen_standard_grading)) },
        text = {
            // Der Hinweis steht ÜBER der Auswahl, nicht darunter: er beantwortet die Frage,
            // die man vor dem Tippen hat ("wirkt das rückwirkend?"), und danach liest ihn
            // niemand mehr. Der Dialog scrollt, weil Hinweis + eigene Systeme zusammen über
            // die Höhe eines AlertDialogs hinauswachsen können — der schneidet sonst ab.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingM),
            ) {
                Text(
                    text = stringResource(R.string.einstellungen_grading_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )
                if (systems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.einstellungen_grading_leer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoulderBuddy.colors.textSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                        systems.forEach { system ->
                            SelectableChip(
                                label = stringResource(
                                    R.string.einstellungen_grading_chip,
                                    system.name,
                                    pluralStringResource(
                                        R.plurals.grad_anzahl,
                                        system.gradeCount,
                                        system.gradeCount,
                                    ),
                                ),
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
            }
        },
        // Auswahl erfolgt per Chip-Tap, daher kein Bestätigen-Button nötig.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aktion_abbrechen)) }
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
