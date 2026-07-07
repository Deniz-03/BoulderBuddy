package com.boulderbuddy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.ToggleSwitch
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
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
    onCaptureLocation: () -> Unit = {},
    onSetCoordinates: (Double, Double) -> Unit = { _, _ -> },
    onClearCoordinates: () -> Unit = {},
    onLocationErrorShown: () -> Unit = {},
    onSave: () -> Unit = {},
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
                "Ohne Standort-Berechtigung kannst du die Koordinaten nur manuell eingeben.",
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

    var showManualDialog by remember { mutableStateOf(false) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Halle bearbeiten",
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
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
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                TextField(
                    value = state.name,
                    onChange = onNameChange,
                    label = "NAME",
                    placeholder = "z.B. Boulderhalle Nord",
                )
                TextField(
                    value = state.location,
                    onChange = onLocationChange,
                    label = "ADRESSE (OPTIONAL)",
                    placeholder = "z.B. Musterstraße 1",
                )

                // --- Näherungs-Erinnerung -----------------------------------------
                SectionHeader(text = "Näherungs-Erinnerung")
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
                        "Standort: %.5f, %.5f".format(
                            Locale.GERMANY, state.latitude, state.longitude,
                        )
                    } else {
                        "Kein Standort hinterlegt — ohne Standort kann diese Halle " +
                            "keine Erinnerung auslösen."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )
                PrimaryButton(
                    text = if (state.capturingLocation) {
                        "Ermittle Standort…"
                    } else {
                        "Aktuellen Standort übernehmen"
                    },
                    icon = Icons.Outlined.MyLocation,
                    // Während der Fix läuft, keine zweite Anfrage starten.
                    onClick = { if (!state.capturingLocation) requestCapture() },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { showManualDialog = true }) {
                        Text("Koordinaten manuell eingeben")
                    }
                    if (state.hasCoordinates) {
                        TextButton(onClick = onClearCoordinates) {
                            Text("Standort entfernen")
                        }
                    }
                }

                if (state.hasCoordinates) {
                    Column {
                        Text(
                            text = "Erkennungs-Radius: ${state.radiusMeters} m",
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
                        contentDescription = null,
                        tint = BoulderBuddy.colors.textSecondary,
                        modifier = Modifier.size(Dimens.iconS),
                    )
                    Text(
                        text = "Erinnerungen für diese Halle",
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
                    text = "Speichern",
                    onClick = onSave,
                )
            }
        },
    )

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

// Manuelle Koordinaten-Eingabe (Notnagel — Primärweg ist der Standort-Button).
// Akzeptiert Punkt oder Komma als Dezimaltrenner und validiert die Wertebereiche.
@Composable
private fun KoordinatenDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var latText by remember { mutableStateOf(initialLatitude?.toString() ?: "") }
    var lngText by remember { mutableStateOf(initialLongitude?.toString() ?: "") }

    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    val lat = parse(latText)
    val lng = parse(lngText)
    val valid = lat != null && lat in -90.0..90.0 && lng != null && lng in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Koordinaten eingeben") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                TextField(
                    value = latText,
                    onChange = { latText = it },
                    label = "BREITENGRAD",
                    placeholder = "z.B. 52.520008",
                )
                TextField(
                    value = lngText,
                    onChange = { lngText = it },
                    label = "LÄNGENGRAD",
                    placeholder = "z.B. 13.404954",
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(lat!!, lng!!) },
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun GymBearbeitenScreenPreview() {
    BoulderBuddyTheme {
        GymBearbeitenScreen(
            state = GymBearbeitenUiState(
                ready = true,
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
