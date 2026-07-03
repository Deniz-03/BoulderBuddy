package com.boulderbuddy.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.ColorPicker
import com.boulderbuddy.ui.components.PhotoPicker
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.model.toHexRgb
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.viewmodel.RouteFormInput

@Composable
fun RouteHinzufuegenScreen(
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    // Übergibt die Formulareingabe nach oben; Speichern + Navigation macht der NavHost
    // via RouteHinzufuegenViewModel (Phase 6.5). Die sessionId liest das ViewModel selbst.
    onSave: (RouteFormInput) -> Unit = {},
) {
    val context = LocalContext.current

    var sektor by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }
    var versuche by remember { mutableIntStateOf(1) }
    // Status als 2 Zustände, die direkt aufs Datenmodell mappen: Geschafft = SENT, Projekt = PROJECT.
    // Flash (SENT + 1 Versuch) wird beim Anzeigen abgeleitet, hier nicht separat erfasst.
    var geschafft by remember { mutableStateOf(true) }

    // Gewähltes Foto (content-URI als String); null = noch keins gewählt.
    var mediaUri by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Dauerhaften Lesezugriff sichern, damit die URI Prozess-Neustarts überlebt.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            mediaUri = uri.toString()
        }
    }

    // Die 7 Routenfarben in Wireframe-Reihenfolge.
    val routes = BoulderBuddy.colors.routes
    val palette = listOf(
        routes.red, routes.orange, routes.yellow, routes.green,
        routes.blue, routes.purple, routes.pink,
    )
    var selected by remember { mutableStateOf(routes.green) }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Boulder hinzufügen",
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
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                PhotoPicker(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    imageUri = mediaUri,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                ) {
                    TextField(
                        value = grade,
                        placeholder = "6b",
                        label = "Grade",
                        onChange = { grade = it },
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = sektor,
                        placeholder = "A",
                        label = "Sektor",
                        onChange = { sektor = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Farbe",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoulderBuddy.colors.textTertiary,
                )
                ColorPicker(
                    colors = palette,
                    selected = selected,
                    onSelect = { selected = it },
                )

                TextField(
                    value = name,
                    placeholder = "z.B. Überhang",
                    label = "Name",
                    onChange = { name = it },
                )

                // --- Versuche (Stepper, min. 1) ---
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = "Versuche",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (versuche > 1) versuche-- }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Weniger Versuche")
                        }
                        Text(
                            text = versuche.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = Dimens.paddingM),
                        )
                        IconButton(onClick = { versuche++ }) {
                            Icon(Icons.Filled.Add, contentDescription = "Mehr Versuche")
                        }
                    }
                }

                // --- Status ---
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                        SelectableChip(
                            label = "Geschafft",
                            selected = geschafft,
                            onClick = { geschafft = true },
                            modifier = Modifier.weight(1f),
                        )
                        SelectableChip(
                            label = "Projekt",
                            selected = !geschafft,
                            onClick = { geschafft = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                TextField(
                    value = notiz,
                    placeholder = "Notiz (optional)",
                    label = "Notiz",
                    onChange = { notiz = it },
                    singleLine = false,
                    minLines = 2,
                )

                PrimaryButton(
                    text = "Speichern",
                    icon = Icons.Filled.Check,
                    onClick = {
                        onSave(
                            RouteFormInput(
                                grade = grade,
                                sektor = sektor,
                                name = name,
                                colorHex = selected.toHexRgb(),
                                attempts = versuche,
                                status = if (geschafft) RouteStatus.SENT else RouteStatus.PROJECT,
                                mediaUri = mediaUri,
                                notiz = notiz,
                            )
                        )
                    },
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun RouteHinzufuegenScreenPreview() {
    BoulderBuddyTheme {
        RouteHinzufuegenScreen()
    }
}
