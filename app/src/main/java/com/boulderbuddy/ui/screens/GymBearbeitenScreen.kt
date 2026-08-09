package com.boulderbuddy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import androidx.core.content.ContextCompat
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.ToggleSwitch
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.inhaltsAbstandMitTastatur
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.viewmodel.GradeSystemUi
import com.boulderbuddy.ui.viewmodel.GymBearbeitenUiState
import java.util.Locale

/**
 * Gym-Editor (Gym-Näherungs-Push, M1): Name/Adresse pflegen und den Standort der Halle
 * hinterlegen — primär über den Button "Aktuellen Standort übernehmen" (FusedLocation,
 * während man an der Halle steht), als Notnagel per manueller Koordinaten-Eingabe.
 * Dazu Geofence-Radius und der Pro-Gym-Toggle "Erinnerungen aktiv".
 */
@Composable
fun GymBearbeitenScreen(
    state: GymBearbeitenUiState = GymBearbeitenUiState(ready = true),
    onNameChange: (String) -> Unit = {},
    onLocationChange: (String) -> Unit = {},
    onRadiusChange: (Int) -> Unit = {},
    onAlertsEnabledChange: (Boolean) -> Unit = {},
    onDefaultGradeSystemChange: (Int) -> Unit = {},
    onCaptureLocation: () -> Unit = {},
    onSetCoordinates: (Double, Double) -> Unit = { _, _ -> },
    onClearCoordinates: () -> Unit = {},
    onLocationErrorShown: () -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current

    // Foreground-Location-Flow: erst prüfen, sonst anfragen; bei Erteilung direkt erfassen.
    // (Background-Location ist NICHT hier nötig — die braucht erst das Geofencing, M2.)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onCaptureLocation()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.halle_ohne_standort_recht),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val requestCapture = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            onCaptureLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    // Fehler der Standort-Erfassung als Toast zeigen und quittieren.
    LaunchedEffect(state.locationError) {
        state.locationError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onLocationErrorShown()
        }
    }

    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = stringResource(
                    if (state.neu) R.string.hallen_neu else R.string.halle_bearbeiten,
                ),
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
                    .inhaltsAbstandMitTastatur()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                TextField(
                    value = state.name,
                    onChange = onNameChange,
                    label = stringResource(R.string.halle_name_label),
                    placeholder = stringResource(R.string.halle_name_platzhalter),
                )
                TextField(
                    value = state.location,
                    onChange = onLocationChange,
                    label = stringResource(R.string.halle_adresse_label),
                    placeholder = stringResource(R.string.halle_adresse_platzhalter),
                )

                // --- Standard-Gradsystem ------------------------------------------
                // Was hier steht, ist beim Session-Anlegen in dieser Halle vorgewählt. Der
                // Text sagt ausdrücklich, dass es ein Vorschlag bleibt: sonst liest sich eine
                // Einstellung an der Halle wie eine Festlegung für jede Session dort.
                SectionHeader(text = stringResource(R.string.einstellungen_standard_grading))
                Text(
                    text = stringResource(R.string.halle_grading_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )
                if (state.systems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.einstellungen_grading_leer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoulderBuddy.colors.textSecondary,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                    ) {
                        state.systems.forEach { system ->
                            SelectableChip(
                                label = system.name,
                                selected = system.id == state.defaultGradeSystemId,
                                // Nochmal auf dasselbe tippen hebt die Wahl auf — "kein
                                // Standard" muss erreichbar bleiben, ohne einen Extra-Chip.
                                onClick = { onDefaultGradeSystemChange(system.id) },
                            )
                        }
                    }
                }

                // --- Näherungs-Erinnerung -----------------------------------------
                SectionHeader(text = stringResource(R.string.halle_naeherung))
                // Gelerntes Besuchsmuster (M3) — nur wenn schon Besuche geloggt sind.
                if (state.visitSummary != null) {
                    Text(
                        text = state.visitSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                }
                Text(
                    text = if (state.hasCoordinates) {
                        // Der Dezimaltrenner bleibt bewusst fest deutsch: die Zahlen daneben
                        // (Radius) sind es auch, und eine Zeile mit beiden Schreibweisen
                        // liest sich wie ein Fehler.
                        stringResource(R.string.halle_standort).format(
                            Locale.GERMANY, state.latitude, state.longitude,
                        )
                    } else {
                        stringResource(R.string.halle_standort_fehlt)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )
                PrimaryButton(
                    text = stringResource(
                        if (state.capturingLocation) R.string.halle_standort_ermitteln
                        else R.string.halle_standort_uebernehmen,
                    ),
                    icon = Icons.Outlined.MyLocation,
                    // Während der Fix läuft, keine zweite Anfrage starten.
                    onClick = { if (!state.capturingLocation) requestCapture() },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { showManualDialog = true }) {
                        Text(stringResource(R.string.halle_standort_manuell))
                    }
                    if (state.hasCoordinates) {
                        TextButton(onClick = onClearCoordinates) {
                            Text(stringResource(R.string.halle_standort_entfernen))
                        }
                    }
                }

                if (state.hasCoordinates) {
                    Column {
                        Text(
                            text = stringResource(R.string.halle_radius, state.radiusMeters),
                            style = MaterialTheme.typography.labelSmall,
                            color = BoulderBuddy.colors.textTertiary,
                        )
                        // 50–500 m in 25-m-Schritten; Default 150 m (empirisch zu kalibrieren, M5).
                        Slider(
                            value = state.radiusMeters.toFloat(),
                            onValueChange = { onRadiusChange((it / 25f).toInt() * 25) },
                            valueRange = 50f..500f,
                            steps = 17,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        // null: "Erinnerungen für diese Halle" steht daneben.
                        contentDescription = null,
                        tint = BoulderBuddy.colors.textSecondary,
                        modifier = Modifier.size(Dimens.iconS),
                    )
                    Text(
                        text = stringResource(R.string.halle_erinnerungen),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    ToggleSwitch(
                        checked = state.alertsEnabled,
                        onCheckedChange = onAlertsEnabledChange,
                    )
                }

                PrimaryButton(
                    text = stringResource(
                        if (state.neu) R.string.halle_anlegen else R.string.aktion_speichern,
                    ),
                    onClick = onSave,
                    // Ohne Namen speichert das ViewModel ohnehin nicht — der Knopf sagt das
                    // jetzt, statt den Tap stillschweigend verfallen zu lassen.
                    enabled = state.name.isNotBlank(),
                )

                // Löschen nur für bestehende Hallen, und bewusst unauffällig: ein TextButton
                // unter dem Primärweg, nicht daneben. Die Rückfrage kommt trotzdem.
                if (!state.neu) {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.halle_loeschen),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )

    if (showDeleteDialog) {
        LoeschenDialog(
            gymName = state.name,
            sessionCount = state.sessionCount,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showManualDialog) {
        KoordinatenDialog(
            initialLatitude = state.latitude,
            initialLongitude = state.longitude,
            onConfirm = { lat, lng ->
                onSetCoordinates(lat, lng)
                showManualDialog = false
            },
            onDismiss = { showManualDialog = false },
        )
    }
}

/**
 * Rückfrage vor dem Löschen.
 *
 * Sie sagt ausdrücklich, was **bleibt**, nicht nur was verschwindet. Bei einer Halle mit
 * Trainingshistorie ist genau das die Frage, die jemand vor dem Tippen im Kopf hat — und die
 * Antwort ist beruhigend, also gehört sie in den Dialog und nicht ins Changelog.
 */
@Composable
private fun LoeschenDialog(
    gymName: String,
    sessionCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (gymName.isBlank()) {
                    stringResource(R.string.halle_loeschen_titel)
                } else {
                    stringResource(R.string.halle_loeschen_titel_benannt, gymName)
                },
            )
        },
        text = {
            Text(
                if (sessionCount == 0) {
                    stringResource(R.string.halle_loeschen_ohne_sessions)
                } else {
                    val sessionen = pluralStringResource(
                        R.plurals.halle_loeschen_sessions,
                        sessionCount,
                        sessionCount,
                    )
                    stringResource(R.string.halle_loeschen_mit_sessions, sessionen)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.aktion_loeschen),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aktion_abbrechen)) }
        },
    )
}

// Manuelle Koordinaten-Eingabe (Notnagel — Primärweg ist der Standort-Button).
// Akzeptiert Punkt oder Komma als Dezimaltrenner und validiert die Wertebereiche.
@Composable
private fun KoordinatenDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var latText by rememberSaveable { mutableStateOf(initialLatitude?.toString() ?: "") }
    var lngText by rememberSaveable { mutableStateOf(initialLongitude?.toString() ?: "") }

    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    val lat = parse(latText)
    val lng = parse(lngText)
    val valid = lat != null && lat in -90.0..90.0 && lng != null && lng in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.halle_koordinaten_titel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                TextField(
                    value = latText,
                    onChange = { latText = it },
                    label = stringResource(R.string.halle_breitengrad),
                    placeholder = stringResource(R.string.halle_breitengrad_platzhalter),
                )
                TextField(
                    value = lngText,
                    onChange = { lngText = it },
                    label = stringResource(R.string.halle_laengengrad),
                    placeholder = stringResource(R.string.halle_laengengrad_platzhalter),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(lat!!, lng!!) },
            ) { Text(stringResource(R.string.halle_koordinaten_uebernehmen)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aktion_abbrechen)) }
        },
    )
}

private val vorschauSysteme = listOf(
    GradeSystemUi(id = 1, name = "Halle Nord", gradeCount = 5),
    GradeSystemUi(id = 2, name = "V-Scale", gradeCount = 11),
    GradeSystemUi(id = 3, name = "Französisch", gradeCount = 14),
)

@Preview(showBackground = true)
@Composable
private fun GymBearbeitenScreenPreview() {
    BoulderBuddyTheme {
        GymBearbeitenScreen(
            state = GymBearbeitenUiState(
                ready = true,
                systems = vorschauSysteme,
                defaultGradeSystemId = 1,
                name = "Boulderhalle Nord",
                location = "Musterstraße 1",
                latitude = 52.520008,
                longitude = 13.404954,
                radiusMeters = 150,
                alertsEnabled = true,
            ),
        )
    }
}

// Anlegen-Modus: leeres Formular, anderer Titel, anderer Button — derselbe Screen.
@Preview(showBackground = true, name = "Neue Halle")
@Composable
private fun GymAnlegenScreenPreview() {
    BoulderBuddyTheme {
        GymBearbeitenScreen(
            state = GymBearbeitenUiState(ready = true, neu = true, systems = vorschauSysteme),
        )
    }
}
