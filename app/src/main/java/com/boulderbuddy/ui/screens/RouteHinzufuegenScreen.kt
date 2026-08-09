package com.boulderbuddy.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.data.model.RouteStatus
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.ColorPicker
import com.boulderbuddy.ui.components.MedienQuelleDialog
import com.boulderbuddy.ui.components.PhotoPicker
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.SpeechToTextButton
import com.boulderbuddy.ui.components.TextField
import com.boulderbuddy.ui.components.appendSpokenNote
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.inhaltsAbstandMitTastatur
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.aktuelleBreite
import com.boulderbuddy.ui.theme.Breite
import com.boulderbuddy.ui.theme.inhaltsBreite
import com.boulderbuddy.ui.theme.keyForRouteColor
import com.boulderbuddy.ui.theme.routeColorForKey
import com.boulderbuddy.ui.theme.routeColorPalette
import com.boulderbuddy.util.MediaType
import com.boulderbuddy.util.mediaTypeOf
import com.boulderbuddy.ui.viewmodel.GradeOption
import com.boulderbuddy.ui.viewmodel.RouteFormInitial
import com.boulderbuddy.ui.viewmodel.RouteFormInput
import com.boulderbuddy.ui.viewmodel.RouteFormUiState

@Composable
fun RouteHinzufuegenScreen(
    // Anzeige-Zustand aus dem RouteHinzufuegenViewModel (Grade-Optionen + Startwerte + Edit-Flag).
    state: RouteFormUiState = RouteFormUiState(ready = true),
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    // Öffnet den eigenen Aufnahme-Screen (CameraX, 7.4d).
    onOpenKamera: () -> Unit = {},
    // Fertige Aufnahme, die von dort zurückkam; null = keine. Wird einmal übernommen und
    // danach über onAufnahmeVerbraucht gelöscht, sonst käme sie bei jeder Rückkehr erneut.
    aufnahmeUri: String? = null,
    onAufnahmeVerbraucht: () -> Unit = {},
    // Übergibt die Formulareingabe nach oben; Speichern + Navigation macht der NavHost.
    onSave: (RouteFormInput) -> Unit = {},
) {
    val context = LocalContext.current

    // Solange die (Edit-)Startwerte noch laden: Spinner statt Formular mit falschen Werten.
    if (!state.ready) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Formularfelder aus den Startwerten initialisieren. Der Schlüssel `initial` sorgt dafür,
    // dass sie neu gesetzt werden, sobald im Edit-Fall die geladenen Werte eintreffen —
    // die Vorbefüllung greift damit auch dann, wenn sie später kommt als der erste Frame.
    //
    // `rememberSaveable` und nicht `remember`: sonst war ein halb ausgefülltes Formular nach
    // dem Drehen leer. Am Gerät geprüft war das im Session-Formular, hier trifft es dieselbe
    // Konstruktion — nur mit mehr Feldern, die dabei verlorengehen.
    val initial = state.initial
    var sektor by rememberSaveable(initial) { mutableStateOf(initial.sektor) }
    var name by rememberSaveable(initial) { mutableStateOf(initial.name) }
    var notiz by rememberSaveable(initial) { mutableStateOf(initial.notiz) }
    var versuche by rememberSaveable(initial) { mutableIntStateOf(initial.attempts) }
    // Status als 2 Zustände: Geschafft = SENT, Projekt = PROJECT. Flash wird beim Anzeigen abgeleitet.
    var geschafft by rememberSaveable(initial) { mutableStateOf(initial.status == RouteStatus.SENT) }
    var selectedGradeId by rememberSaveable(initial) { mutableStateOf(initial.gradeId) }
    // Route-Farbe (Farb-Key), von der Schwierigkeit entkoppelt — dient nur dem Wiedererkennen.
    var colorKey by rememberSaveable(initial) { mutableStateOf(initial.color) }

    // Gewähltes Foto/Video (content-URI als String); null = noch keins gewählt.
    var mediaUri by rememberSaveable(initial) { mutableStateOf(initial.mediaUri) }
    // Medientyp aus der URI abgeleitet (kein DB-Feld, Phase 7.3c) — steuert die Vorschau.
    val isVideo = remember(mediaUri) { mediaTypeOf(context, mediaUri) == MediaType.VIDEO }
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

    // Quellenwahl beim Antippen des Slots: eigener Aufnahme-Screen oder Galerie.
    var zeigeQuellenwahl by remember { mutableStateOf(false) }

    // Aufnahme aus dem Kamera-Screen übernehmen. Sie ist eine app-eigene FileProvider-URI und
    // braucht deshalb KEIN takePersistableUriPermission — die Datei gehört uns bereits.
    LaunchedEffect(aufnahmeUri) {
        aufnahmeUri?.let {
            mediaUri = it
            onAufnahmeVerbraucht()
        }
    }

    if (zeigeQuellenwahl) {
        MedienQuelleDialog(
            onAufnehmen = {
                zeigeQuellenwahl = false
                onOpenKamera()
            },
            onGalerie = {
                zeigeQuellenwahl = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            onDismiss = { zeigeQuellenwahl = false },
        )
    }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = stringResource(
                    if (state.isEditing) R.string.boulder_bearbeiten
                    else R.string.boulder_hinzufuegen,
                ),
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.aktion_zurueck),
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
                },
            )
        },
        content = { _ ->
            /*
             * ZWEISPALTIG AB TABLET-BREITE: Medien links, Eingaben rechts.
             *
             * Die Vorschau ist über `aspectRatio` gebaut und damit so hoch wie breit × 9/16.
             * In einer einspaltigen 600-dp-Formularspalte sind das ~340 dp — die halbe
             * Bildhöhe, bevor das erste Eingabefeld kommt. Man scrollte an einem großen
             * leeren Rahmen vorbei, um zu den Feldern zu gelangen, während rechts daneben
             * 600 dp Platz frei blieben.
             *
             * Nebeneinander passt das ganze Formular auf eine Seite, und die Vorschau steht
             * dort, wo man sie beim Ausfüllen sehen will: neben den Feldern, nicht darüber.
             */
            val zweispaltig = aktuelleBreite() == Breite.Weit

            // Die Eingabefelder — in beiden Anordnungen dieselben, nur einmal geschrieben.
            val felder: @Composable ColumnScope.() -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                ) {
                    GradeDropdown(
                        grades = state.grades,
                        selectedGradeId = selectedGradeId,
                        onSelect = { selectedGradeId = it },
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = sektor,
                        placeholder = stringResource(R.string.boulder_sektor_platzhalter),
                        label = stringResource(R.string.boulder_sektor),
                        onChange = { sektor = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                // --- Farbe (immer sichtbar, von der Schwierigkeit entkoppelt) ---
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = stringResource(R.string.boulder_farbe),
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    ColorPicker(
                        colors = routeColorPalette.map { it.second },
                        selected = routeColorForKey(colorKey),
                        onSelect = { color -> keyForRouteColor(color)?.let { colorKey = it } },
                    )
                }

                TextField(
                    value = name,
                    placeholder = stringResource(R.string.boulder_name_platzhalter),
                    label = stringResource(R.string.boulder_name),
                    onChange = { name = it },
                )

                // --- Versuche (Stepper, min. 1) ---
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = stringResource(R.string.boulder_versuche),
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (versuche > 1) versuche-- }) {
                            Icon(
                                Icons.Filled.Remove,
                                contentDescription = stringResource(
                                    R.string.boulder_versuche_weniger,
                                ),
                            )
                        }
                        Text(
                            text = versuche.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = Dimens.paddingM),
                        )
                        IconButton(onClick = { versuche++ }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(
                                    R.string.boulder_versuche_mehr,
                                ),
                            )
                        }
                    }
                }

                // --- Status ---
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                    Text(
                        text = stringResource(R.string.boulder_status),
                        style = MaterialTheme.typography.labelSmall,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                        SelectableChip(
                            label = stringResource(R.string.boulder_geschafft),
                            selected = geschafft,
                            onClick = { geschafft = true },
                            modifier = Modifier.weight(1f),
                        )
                        SelectableChip(
                            label = stringResource(R.string.boulder_projekt),
                            selected = !geschafft,
                            onClick = { geschafft = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                TextField(
                    value = notiz,
                    placeholder = stringResource(R.string.boulder_notiz_platzhalter),
                    label = stringResource(R.string.boulder_notiz),
                    onChange = { notiz = it },
                    singleLine = false,
                    minLines = 2,
                    // Spracheingabe: erkannten Text an die Notiz anhängen (7.4b).
                    trailing = {
                        SpeechToTextButton(
                            onResult = { spoken -> notiz = appendSpokenNote(notiz, spoken) },
                        )
                    },
                )

                PrimaryButton(
                    text = stringResource(R.string.aktion_speichern),
                    icon = Icons.Filled.Check,
                    onClick = {
                        onSave(
                            RouteFormInput(
                                gradeId = selectedGradeId,
                                color = colorKey,
                                sektor = sektor,
                                name = name,
                                attempts = versuche,
                                status = if (geschafft) RouteStatus.SENT else RouteStatus.PROJECT,
                                mediaUri = mediaUri,
                                notiz = notiz,
                            )
                        )
                    },
                )
            }

            val medien: @Composable () -> Unit = {
                PhotoPicker(
                    onClick = { zeigeQuellenwahl = true },
                    label = stringResource(R.string.boulder_medien_waehlen),
                    imageUri = mediaUri,
                    isVideo = isVideo,
                )
            }

            if (zweispaltig) {
                Row(
                    modifier = Modifier
                        .inhaltsBreite(Dimens.spaltenBreiteWeit)
                        .inhaltsAbstandMitTastatur()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingL),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
                ) {
                    // Die Vorschau oben ausgerichtet: sie ist so hoch, wie ihr
                    // Seitenverhältnis es vorgibt, und soll nicht mit der Feldspalte mitwachsen.
                    Column(modifier = Modifier.weight(1f)) { medien() }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
                        content = felder,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        // Formularspalte statt Fensterbreite — siehe SessionErstellenScreen.
                        .inhaltsBreite()
                        .inhaltsAbstandMitTastatur()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingL),
                    verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
                ) {
                    medien()
                    felder()
                }
            }
        }
    )
}

// Dropdown der erlaubten Grade des gewählten Grading-Systems. Jeder Eintrag zeigt nur das
// Label (Grade = reine Schwierigkeit); die Auswahl liefert direkt die gradeId. Ist kein System
// gewählt (leere Liste), bleibt das Feld leer und weist darauf hin.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeDropdown(
    grades: List<GradeOption>,
    selectedGradeId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = grades.firstOrNull { it.id == selectedGradeId }
    val placeholder = stringResource(
        if (grades.isEmpty()) R.string.boulder_grade_kein_system
        else R.string.boulder_grade_waehlen,
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (grades.isNotEmpty()) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.boulder_grade)) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            grades.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RouteHinzufuegenScreenPreview() {
    BoulderBuddyTheme {
        RouteHinzufuegenScreen(
            state = RouteFormUiState(
                ready = true,
                grades = listOf(
                    GradeOption(1, "V4"),
                    GradeOption(2, "V5"),
                ),
                initial = RouteFormInitial(gradeId = 1),
            ),
        )
    }
}
