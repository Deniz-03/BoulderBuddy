package com.boulderbuddy.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.GhostAnchorEditor
import com.boulderbuddy.ui.components.GhostPathEditor
import com.boulderbuddy.ui.components.GhostSideBySidePlayer
import com.boulderbuddy.ui.components.GhostSkeletonPlayer
import com.boulderbuddy.ui.components.MedienQuelleDialog
import com.boulderbuddy.ui.components.PhotoPicker
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SelectableChip
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ghost.model.GhostViewMode
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.aktuelleBreite
import com.boulderbuddy.ui.theme.Breite
import com.boulderbuddy.ui.viewmodel.EigeneAufnahmeUi
import com.boulderbuddy.ui.viewmodel.GhostClimberUiState
import com.boulderbuddy.ui.viewmodel.GhostRole
import com.boulderbuddy.ui.viewmodel.GhostStep
import com.boulderbuddy.ui.viewmodel.GhostVideoSlot
import com.boulderbuddy.ui.viewmodel.SavedAnalysisUi

/**
 * Ghost Climber (Phase 7.5) — bewusst als "Experimental" gekennzeichneter Einstieg
 * außerhalb des MVP-Kernflusses (Plan A.4). Geführter Flow über die Pipeline:
 * 1. Auswahl: Referenz- + Vergleichs-Video wählen, Posen extrahieren (M1).
 * 2. Anker: ≥4 korrespondierende Wandpunkte in beiden Videos antippen (M2).
 * 3. Pfad: vorgeschlagenen Routenpfad prüfen/korrigieren (M3, P3).
 * 4. Vorschau: beide Skelette im Wand-Referenzraum, per DTW zeitsynchronisiert (M3).
 */
@Composable
fun GhostClimberScreen(
    state: GhostClimberUiState = GhostClimberUiState(),
    // Eigener Aufnahme-Screen (CameraX, 7.4d) — für Ghost bewusst nur Video.
    onOpenKamera: () -> Unit = {},
    // Fertige Aufnahme von dort; null = keine. Wird der zuletzt angetippten Rolle zugeordnet.
    aufnahmeUri: String? = null,
    onAufnahmeVerbraucht: () -> Unit = {},
    onSelectVideo: (GhostRole, String) -> Unit = { _, _ -> },
    onAnalyze: () -> Unit = {},
    onAbbrechen: () -> Unit = {},
    onSelectAnchorFrame: (GhostRole, Long) -> Unit = { _, _ -> },
    onAddAnchor: (GhostRole, GhostPoint) -> Unit = { _, _ -> },
    onRemoveLastAnchor: (GhostRole) -> Unit = {},
    onComputeAlignment: () -> Unit = {},
    onAddPathPoint: (GhostPoint) -> Unit = {},
    onRemoveLastPathPoint: () -> Unit = {},
    onResetPath: () -> Unit = {},
    onConfirmPath: () -> Unit = {},
    onSetViewMode: (GhostViewMode) -> Unit = {},
    onSaveAnalysis: () -> Unit = {},
    onRestoreAnalysis: (Int) -> Unit = {},
    onDeleteAnalysis: (Int) -> Unit = {},
    onBackToSelection: () -> Unit = {},
    onBackToAnchors: () -> Unit = {},
    onBackToPath: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    // Für welche Rolle wurde die Kamera geöffnet? Muss den Ausflug auf den Aufnahme-Screen
    // überleben (Prozesstod eingeschlossen) — daher rememberSaveable und der Enum-Name als
    // String, weil GhostRole selbst nicht ohne Weiteres speicherbar ist.
    var wartendeRolle by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(aufnahmeUri, wartendeRolle) {
        val uri = aufnahmeUri ?: return@LaunchedEffect
        val rolle = wartendeRolle?.let { name ->
            runCatching { GhostRole.valueOf(name) }.getOrNull()
        }
        // Ohne bekannte Rolle wird die Aufnahme verworfen statt geraten — sie dem falschen
        // Slot zuzuordnen wäre schlimmer als sie zu ignorieren.
        if (rolle != null) onSelectVideo(rolle, uri)
        wartendeRolle = null
        onAufnahmeVerbraucht()
    }

    /*
     * Weggehen hat in diesem Flow bis hierher alles Handgemachte kommentarlos gelöscht: das
     * ViewModel stirbt mit dem Bildschirm, und mit ihm Anker, Routenpfad und Ergebnis. Ein
     * Tipp auf den Zurück-Pfeil nach minutenlanger Analyse sah dabei aus wie jeder andere.
     *
     * Was NICHT verloren geht, steht bewusst im Dialogtext: die Videos liegen im
     * Aufnahme-Ordner und sind seit „Aus der App" wieder auffindbar, und die teure Pose-Spur
     * liegt gecacht im GhostArtifactStore. Ein zweiter Anlauf ist deshalb eine Sache von
     * Sekunden — das ändert, wie schlimm ein Verwerfen ist, und das soll man wissen.
     *
     * Gefragt wird ab dem ersten gesetzten Anker. Vorher ist nichts entstanden, was ein
     * Zurück vernichten könnte: die Video-Auswahl ist zwei Tipps.
     */
    val etwasZuVerlieren = when (state.step) {
        GhostStep.SELECTION -> false
        GhostStep.ANCHORS ->
            state.reference.anchors.isNotEmpty() || state.comparison.anchors.isNotEmpty()
        GhostStep.PATH -> true
        GhostStep.PREVIEW -> !state.analysisSaved
    }
    // Speichern gibt es nur, wo es etwas zu speichern gibt: vor der Vorschau fehlen
    // Zeitmapping und Modus-Vorschlag, die Analyse ist noch gar keine.
    val kannSpeichern = state.step == GhostStep.PREVIEW && !state.analysisSaved
    var zeigeVerwerfen by remember { mutableStateOf(false) }
    // Speichern läuft asynchron im ViewModel. Direkt hinterher zu navigieren würde dessen
    // Scope abräumen, bevor der Schreibvorgang durch ist — deshalb erst gehen, wenn der
    // Zustand die Speicherung bestätigt. Schlägt sie fehl, bleibt man mit der Meldung da.
    var geheNachSpeichern by remember { mutableStateOf(false) }
    LaunchedEffect(geheNachSpeichern, state.analysisSaved, state.error) {
        if (!geheNachSpeichern) return@LaunchedEffect
        when {
            state.analysisSaved -> {
                geheNachSpeichern = false
                onBack()
            }
            // Gescheitert: hier bleiben, damit die Meldung lesbar ist — und die Absicht
            // fallen lassen. Bliebe sie stehen, schlösse der nächste erfolgreiche Griff zum
            // regulären Speichern-Knopf den Bildschirm, ohne dass jemand darum gebeten hat.
            // `saveAnalysis` räumt die alte Meldung vorher weg, deshalb ist ein gesetzter
            // Fehler hier zuverlässig DIESER Versuch und kein übriggebliebener.
            state.error != null -> geheNachSpeichern = false
        }
    }
    val verlassen = { if (etwasZuVerlieren) zeigeVerwerfen = true else onBack() }
    BackHandler(enabled = etwasZuVerlieren) { zeigeVerwerfen = true }

    if (zeigeVerwerfen) {
        AlertDialog(
            onDismissRequest = { zeigeVerwerfen = false },
            title = { Text(stringResource(R.string.ghost_verwerfen_titel)) },
            text = { Text(stringResource(R.string.ghost_verwerfen_text)) },
            // Alle Knöpfe untereinander in EINEM Slot statt auf Bestätigen/Verwerfen
            // verteilt: drei Aktionen passen nicht nebeneinander in einen Dialog, und die
            // Zeile brach dann so um, dass „Speichern" allein über den anderen beiden hing
            // wie ein Versehen. Gestapelt ist die Reihenfolge die Aussage — obenan das,
            // was der Nutzer hier fast immer will.
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (kannSpeichern) {
                        TextButton(
                            onClick = {
                                zeigeVerwerfen = false
                                geheNachSpeichern = true
                                onSaveAnalysis()
                            },
                        ) { Text(stringResource(R.string.ghost_speichern_und_schliessen)) }
                    }
                    TextButton(
                        onClick = {
                            zeigeVerwerfen = false
                            onBack()
                        },
                    ) { Text(stringResource(R.string.ghost_verwerfen_ja)) }
                    // Abbrechen zuletzt, also am dichtesten am Daumen: ein Fehlgriff auf den
                    // untersten Knopf soll der harmlose sein — der Dialog kommt schließlich
                    // gerade deshalb, weil vorher einer danebengegangen ist.
                    TextButton(onClick = { zeigeVerwerfen = false }) {
                        Text(stringResource(R.string.aktion_abbrechen))
                    }
                }
            },
        )
    }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.ghost_titel),
                subtitle = stringResource(R.string.ghost_untertitel),
                navIcon = {
                    IconButton(onClick = verlassen) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                when (state.step) {
                    GhostStep.SELECTION -> SelectionStep(
                        state = state,
                        onSelectVideo = onSelectVideo,
                        onKameraFuerRolle = { rolle ->
                            wartendeRolle = rolle.name
                            onOpenKamera()
                        },
                        onAnalyze = onAnalyze,
                        onAbbrechen = onAbbrechen,
                        onRestoreAnalysis = onRestoreAnalysis,
                        onDeleteAnalysis = onDeleteAnalysis,
                    )
                    GhostStep.ANCHORS -> AnchorsStep(
                        state = state,
                        onSelectAnchorFrame = onSelectAnchorFrame,
                        onAddAnchor = onAddAnchor,
                        onRemoveLastAnchor = onRemoveLastAnchor,
                        onComputeAlignment = onComputeAlignment,
                        onBackToSelection = onBackToSelection,
                    )
                    GhostStep.PATH -> PathStep(
                        state = state,
                        onAddPathPoint = onAddPathPoint,
                        onRemoveLastPathPoint = onRemoveLastPathPoint,
                        onResetPath = onResetPath,
                        onConfirmPath = onConfirmPath,
                        onBackToAnchors = onBackToAnchors,
                    )
                    GhostStep.PREVIEW -> PreviewStep(
                        state = state,
                        onSetViewMode = onSetViewMode,
                        onSaveAnalysis = onSaveAnalysis,
                        onBackToPath = onBackToPath,
                    )
                }

                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

// --- Schritt 1: Video-Auswahl + Pose-Extraktion ------------------------------

@Composable
private fun SelectionStep(
    state: GhostClimberUiState,
    onSelectVideo: (GhostRole, String) -> Unit,
    onKameraFuerRolle: (GhostRole) -> Unit,
    onAnalyze: () -> Unit,
    onAbbrechen: () -> Unit,
    onRestoreAnalysis: (Int) -> Unit,
    onDeleteAnalysis: (Int) -> Unit,
) {
    // Die Analyse meldet ihren Fortschritt per Benachrichtigung — ab Android 13 braucht das
    // eine Freigabe. Gefragt wird erst beim Antippen von „Posen analysieren": vorher hätte
    // die Frage keinen erkennbaren Anlass.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Antwort egal — ohne Freigabe rechnet der Dienst weiter, nur stumm. */ }

    Text(
        text = stringResource(R.string.ghost_auswahl_hinweis),
        style = MaterialTheme.typography.bodyMedium,
        color = BoulderBuddy.colors.textSecondary,
    )

    /*
     * Die beiden Auswahlflächen stehen ab Tablet-Breite NEBENEINANDER.
     *
     * Untereinander sind sie am Telefon richtig — dort ist kein Platz für etwas anderes. Am
     * Tablet ergab dieselbe Anordnung zwei 16:9-Flächen von je ~1200 dp Breite, die zusammen
     * über die Bildhöhe hinausreichten: man musste scrollen, um beide zu sehen. Ausgerechnet
     * bei einem Schritt, dessen ganze Aufgabe der **Vergleich zweier Videos** ist, sah man nie
     * beide gleichzeitig.
     *
     * Nebeneinander entspricht die Anordnung außerdem dem, was danach passiert: die
     * Vergleichsansicht stellt dieselben zwei Videos nebeneinander.
     */
    val nebeneinander = aktuelleBreite() != Breite.Kompakt

    if (nebeneinander) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
        ) {
            VideoSlotPicker(
                title = stringResource(R.string.ghost_referenz_video),
                slot = state.reference,
                eigeneAufnahmen = state.eigeneAufnahmen,
                onSelected = { onSelectVideo(GhostRole.REFERENCE, it) },
                onAufnehmen = { onKameraFuerRolle(GhostRole.REFERENCE) },
                modifier = Modifier.weight(1f),
            )
            VideoSlotPicker(
                title = stringResource(R.string.ghost_vergleich_video),
                slot = state.comparison,
                eigeneAufnahmen = state.eigeneAufnahmen,
                onSelected = { onSelectVideo(GhostRole.COMPARISON, it) },
                onAufnehmen = { onKameraFuerRolle(GhostRole.COMPARISON) },
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        VideoSlotPicker(
            title = stringResource(R.string.ghost_referenz_video),
            slot = state.reference,
            eigeneAufnahmen = state.eigeneAufnahmen,
            onSelected = { onSelectVideo(GhostRole.REFERENCE, it) },
            onAufnehmen = { onKameraFuerRolle(GhostRole.REFERENCE) },
        )
        VideoSlotPicker(
            title = stringResource(R.string.ghost_vergleich_video),
            slot = state.comparison,
            eigeneAufnahmen = state.eigeneAufnahmen,
            onSelected = { onSelectVideo(GhostRole.COMPARISON, it) },
            onAufnehmen = { onKameraFuerRolle(GhostRole.COMPARISON) },
        )
    }

    if (state.analyzing) {
        // Der Fortschritt folgt der Anordnung darüber — zwei Balken untereinander unter zwei
        // Flächen nebeneinander wären nicht mehr zuzuordnen.
        if (nebeneinander) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                AnalysisProgress(
                    label = stringResource(R.string.ghost_video_referenz),
                    slot = state.reference,
                    modifier = Modifier.weight(1f),
                )
                AnalysisProgress(
                    label = stringResource(R.string.ghost_video_vergleich),
                    slot = state.comparison,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            AnalysisProgress(
                label = stringResource(R.string.ghost_video_referenz),
                slot = state.reference,
            )
            AnalysisProgress(
                label = stringResource(R.string.ghost_video_vergleich),
                slot = state.comparison,
            )
        }

        // Die Analyse läuft im Hintergrund weiter — beides muss dastehen: dass man gehen
        // darf, und wie man sie loswird. Ohne den Knopf gäbe es keinen Weg mehr, sie zu
        // beenden: der Bildschirm zu verlassen war früher der Abbruch und ist es nicht mehr.
        Text(
            text = stringResource(R.string.ghost_laeuft_im_hintergrund),
            style = MaterialTheme.typography.bodySmall,
            color = BoulderBuddy.colors.textSecondary,
        )
        TextButton(onClick = onAbbrechen) {
            Text(stringResource(R.string.ghost_analyse_abbrechen_knopf))
        }
    } else if (state.canAnalyze) {
        PrimaryButton(
            text = stringResource(R.string.ghost_analysieren),
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            onClick = {
                // Ohne diese Freigabe rechnet der Dienst zwar, zeigt aber nichts an — und
                // genau die Anzeige ist der Grund, warum man den Bildschirm verlassen darf.
                // Die Antwort wird nicht abgewartet: sie ändert nichts an der Analyse.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onAnalyze()
            },
        )
    }

    // Gespeicherte Analysen (M5): antippen lädt direkt die Vergleichsansicht.
    if (state.savedAnalyses.isNotEmpty() && !state.analyzing) {
        SectionHeader(text = stringResource(R.string.ghost_gespeicherte))
        state.savedAnalyses.forEach { analysis ->
            SavedAnalysisRow(
                analysis = analysis,
                onOpen = { onRestoreAnalysis(analysis.id) },
                onDelete = { onDeleteAnalysis(analysis.id) },
            )
        }
    }
}

@Composable
private fun SavedAnalysisRow(
    analysis: SavedAnalysisUi,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            // null: Datum und Vorschlag daneben sagen, was die Zeile ist.
            contentDescription = null,
            tint = BoulderBuddy.colors.textSecondary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = analysis.createdAtText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.ghost_vorschlag, analysis.modeLabel),
                style = MaterialTheme.typography.bodySmall,
                color = BoulderBuddy.colors.textSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.ghost_analyse_loeschen),
                tint = BoulderBuddy.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun VideoSlotPicker(
    title: String,
    slot: GhostVideoSlot,
    eigeneAufnahmen: List<EigeneAufnahmeUi>,
    onSelected: (String) -> Unit,
    onAufnehmen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            // Dauerhaften Lesezugriff sichern, damit die URI Prozess-Neustarts überlebt —
            // wie in RouteHinzufuegenScreen. Fehlte das hier (und es fehlte), überlebten
            // Galerie-Videos im Ghost-Flow schon auf EINEM Gerät keinen Neustart
            // zuverlässig, und der Medien-Umzug aus Sync-Plan S3 scheiterte ausgerechnet
            // an abgelaufenen Berechtigungen.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onSelected(it.toString())
        }
    }

    var zeigeQuellenwahl by rememberSaveable { mutableStateOf(false) }
    var zeigeEigene by rememberSaveable { mutableStateOf(false) }

    if (zeigeEigene) {
        EigeneAufnahmenDialog(
            aufnahmen = eigeneAufnahmen,
            onWaehlen = {
                zeigeEigene = false
                onSelected(it)
            },
            onDismiss = { zeigeEigene = false },
        )
    }

    if (zeigeQuellenwahl) {
        MedienQuelleDialog(
            // Selbst aufnehmen ist hier der bessere Weg: die App legt die Auflösung fest,
            // und zwei gleich aufgenommene Videos vergleichen sich berechenbarer.
            nurVideo = true,
            onAufnehmen = {
                zeigeQuellenwahl = false
                onAufnehmen()
            },
            onEigene = {
                zeigeQuellenwahl = false
                zeigeEigene = true
            },
            onGalerie = {
                zeigeQuellenwahl = false
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            },
            onDismiss = { zeigeQuellenwahl = false },
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        SectionHeader(text = title)
        PhotoPicker(
            onClick = { zeigeQuellenwahl = true },
            label = stringResource(R.string.ghost_video_waehlen),
            imageUri = slot.uri,
            isVideo = slot.uri != null,
        )
    }
}

/**
 * Die Videos, die schon in der App liegen — der Weg zurück zu einer eigenen Aufnahme.
 *
 * Angezeigt wird der Aufnahmezeitpunkt und nicht der Dateiname: `BB_1756458012345.mp4` sagt
 * niemandem etwas. Der Zeitpunkt reicht zum Wiedererkennen, weil man genau weiß, wann man
 * gefilmt hat.
 */
@Composable
private fun EigeneAufnahmenDialog(
    aufnahmen: List<EigeneAufnahmeUi>,
    onWaehlen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ghost_eigene_titel)) },
        text = {
            if (aufnahmen.isEmpty()) {
                Text(
                    text = stringResource(R.string.ghost_eigene_leer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                ) {
                    aufnahmen.forEach { aufnahme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWaehlen(aufnahme.uri) }
                                .padding(vertical = Dimens.paddingS),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VideoLibrary,
                                // null: die Zeile daneben benennt den Eintrag bereits.
                                contentDescription = null,
                                tint = BoulderBuddy.colors.textSecondary,
                            )
                            Text(
                                text = stringResource(
                                    R.string.ghost_eigene_zeile,
                                    aufnahme.zeitText,
                                    aufnahme.groesseText,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.aktion_abbrechen)) }
        },
    )
}

@Composable
private fun AnalysisProgress(
    label: String,
    slot: GhostVideoSlot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        when {
            slot.track != null -> Text(
                text = stringResource(
                    R.string.ghost_fortschritt_fertig,
                    label,
                    slot.track.frames.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = BoulderBuddy.colors.textSecondary,
            )
            slot.progressTotal > 0 -> {
                LinearProgressIndicator(
                    progress = { slot.progressDone.toFloat() / slot.progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.ghost_fortschritt_frame,
                        label,
                        slot.progressDone,
                        slot.progressTotal,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.textTertiary,
                )
            }
            else -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.ghost_fortschritt_vorbereitung, label),
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.textTertiary,
                )
            }
        }
    }
}

// --- Schritt 2: Anker setzen (Homographie) -----------------------------------

@Composable
private fun AnchorsStep(
    state: GhostClimberUiState,
    onSelectAnchorFrame: (GhostRole, Long) -> Unit,
    onAddAnchor: (GhostRole, GhostPoint) -> Unit,
    onRemoveLastAnchor: (GhostRole) -> Unit,
    onComputeAlignment: () -> Unit,
    onBackToSelection: () -> Unit,
) {
    Text(
        text = stringResource(R.string.ghost_anker_hinweis, GhostTuning.MIN_ANCHORS),
        style = MaterialTheme.typography.bodyMedium,
        color = BoulderBuddy.colors.textSecondary,
    )

    GhostRole.entries.forEach { role ->
        val slot = state.slot(role)
        SectionHeader(
            text = stringResource(
                if (role == GhostRole.REFERENCE) R.string.ghost_referenz_video
                else R.string.ghost_vergleich_video,
            ),
        )
        GhostAnchorEditor(
            frame = slot.anchorFrame,
            anchors = slot.anchors,
            frameTimeMs = slot.anchorFrameTimeMs,
            durationMs = slot.track?.durationMs ?: 0L,
            onFrameTimeSelected = { onSelectAnchorFrame(role, it) },
            onAddAnchor = { onAddAnchor(role, it) },
            onRemoveLastAnchor = { onRemoveLastAnchor(role) },
        )
    }

    if (state.anchorsComplete) {
        PrimaryButton(
            text = stringResource(R.string.ghost_uebereinanderlegen),
            icon = Icons.Filled.Layers,
            onClick = onComputeAlignment,
        )
    } else {
        val ref = state.reference.anchors.size
        val cmp = state.comparison.anchors.size
        Text(
            text = if (ref >= GhostTuning.MIN_ANCHORS && ref != cmp) {
                stringResource(R.string.ghost_anker_ungleich, ref, cmp)
            } else {
                stringResource(R.string.ghost_anker_zu_wenige, GhostTuning.MIN_ANCHORS)
            },
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
    TextButton(onClick = onBackToSelection) {
        Text(stringResource(R.string.ghost_zurueck_auswahl))
    }
}

// --- Schritt 3: Routenpfad prüfen/korrigieren (M3) ----------------------------

@Composable
private fun PathStep(
    state: GhostClimberUiState,
    onAddPathPoint: (GhostPoint) -> Unit,
    onRemoveLastPathPoint: () -> Unit,
    onResetPath: () -> Unit,
    onConfirmPath: () -> Unit,
    onBackToAnchors: () -> Unit,
) {
    Text(
        text = stringResource(R.string.ghost_pfad_hinweis),
        style = MaterialTheme.typography.bodyMedium,
        color = BoulderBuddy.colors.textSecondary,
    )
    GhostPathEditor(
        frame = state.reference.anchorFrame,
        trajectory = state.hipTrajectory,
        path = state.routePath,
        onAddPoint = onAddPathPoint,
        onRemoveLastPoint = onRemoveLastPathPoint,
        onResetToSuggestion = onResetPath,
    )
    if (state.routePath.size >= 2) {
        PrimaryButton(
            text = stringResource(R.string.ghost_synchronisieren),
            icon = Icons.Filled.Layers,
            onClick = onConfirmPath,
        )
    } else {
        Text(
            text = stringResource(R.string.ghost_pfad_zu_kurz),
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
    TextButton(onClick = onBackToAnchors) {
        Text(stringResource(R.string.ghost_anker_anpassen))
    }
}

// --- Schritt 4: Synchronisierter Vergleich (Overlay ⇄ Side-by-Side) -----------

@Composable
private fun PreviewStep(
    state: GhostClimberUiState,
    onSetViewMode: (GhostViewMode) -> Unit,
    onSaveAnalysis: () -> Unit,
    onBackToPath: () -> Unit,
) {
    val refUri = state.reference.uri
    val refTrack = state.reference.track
    val cmpUri = state.comparison.uri
    val cmpTrack = state.comparison.track
    val ghostTrack = state.ghostTrack
    val mapping = state.timeMapping
    if (refUri == null || refTrack == null || cmpUri == null ||
        cmpTrack == null || ghostTrack == null
    ) {
        return
    }
    val cmpTimeForPosition: (Long) -> Long = { pos -> mapping?.mapToComparison(pos) ?: pos }
    // Debug-Ansicht (Stufe 0): Kennzahlen-HUD + Warp-Kurve im Overlay-Player.
    var debugHud by rememberSaveable { mutableStateOf(false) }
    // Die ROH-Spur ist bewusst ein eigener Schalter und standardmäßig aus: sie wackelt
    // per Definition (ungefiltert), und über dem Ergebnis gezeichnet ist nicht mehr
    // auseinanderzuhalten, ob das Ergebnis unruhig ist oder nur die Rohdaten daneben.
    var showRaw by rememberSaveable { mutableStateOf(false) }
    // Einzelne Skelette im Overlay ein-/ausblendbar (7.5c).
    var showReference by rememberSaveable { mutableStateOf(true) }
    var showGhost by rememberSaveable { mutableStateOf(true) }

    SectionHeader(text = stringResource(R.string.ghost_vergleich_ueberschrift))
    // Umschalter, vorbelegt mit dem Vorschlag der Ähnlichkeitsmetrik (P7).
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
        SelectableChip(
            label = stringResource(R.string.ghost_modus_overlay),
            selected = state.viewMode == GhostViewMode.OVERLAY,
            onClick = { onSetViewMode(GhostViewMode.OVERLAY) },
        )
        SelectableChip(
            label = stringResource(R.string.ghost_modus_side_by_side),
            selected = state.viewMode == GhostViewMode.SIDE_BY_SIDE,
            onClick = { onSetViewMode(GhostViewMode.SIDE_BY_SIDE) },
        )
        SelectableChip(
            label = stringResource(R.string.ghost_modus_debug),
            selected = debugHud,
            onClick = { debugHud = !debugHud },
        )
        if (debugHud) {
            SelectableChip(
                label = stringResource(R.string.ghost_modus_roh),
                selected = showRaw,
                onClick = { showRaw = !showRaw },
            )
        }
    }
    if (debugHud && showRaw) {
        Text(
            text = stringResource(R.string.ghost_roh_hinweis),
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
    if (state.suggestionReason.isNotEmpty()) {
        val suggestedLabel = stringResource(
            if (state.suggestedMode == GhostViewMode.OVERLAY) R.string.ghost_modus_overlay
            else R.string.ghost_modus_side_by_side,
        )
        Text(
            text = stringResource(
                R.string.ghost_modus_vorschlag,
                suggestedLabel,
                state.suggestionReason,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }

    when (state.viewMode) {
        GhostViewMode.OVERLAY -> {
            // Sichtbarkeits-Umschalter pro Skelett (7.5c).
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                SelectableChip(
                    label = stringResource(R.string.ghost_skelett_referenz),
                    selected = showReference,
                    onClick = { showReference = !showReference },
                )
                SelectableChip(
                    label = stringResource(R.string.ghost_skelett_geist),
                    selected = showGhost,
                    onClick = { showGhost = !showGhost },
                )
            }
            GhostSkeletonPlayer(
                uri = refUri,
                poseTrack = refTrack,
                ghostTrack = ghostTrack,
                ghostTimeForPosition = cmpTimeForPosition,
                abortTimeMs = state.refAbortTimeMs,
                ghostAbortTimeMs = state.cmpAbortTimeMs,
                showSkeleton = showReference,
                showGhost = showGhost,
                debug = debugHud,
                showRawOverlay = debugHud && showRaw,
                dtwDistanceFraction = state.dtwDistanceFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    // Player im Seitenverhältnis des Videos, damit Letterbox-Ränder
                    // (und damit Overlay-Versatz) gar nicht erst entstehen.
                    .aspectRatio(refTrack.frameWidth.toFloat() / refTrack.frameHeight),
            )
            Text(
                text = stringResource(R.string.ghost_overlay_hinweis),
                style = MaterialTheme.typography.labelMedium,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
        GhostViewMode.SIDE_BY_SIDE -> {
            GhostSideBySidePlayer(
                refUri = refUri,
                refTrack = refTrack,
                cmpUri = cmpUri,
                cmpTrack = cmpTrack,
                refAbortTimeMs = state.refAbortTimeMs,
                cmpAbortTimeMs = state.cmpAbortTimeMs,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.ghost_side_by_side_hinweis),
                style = MaterialTheme.typography.labelMedium,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
    }
    if (state.refAbortTimeMs != null || state.cmpAbortTimeMs != null) {
        Text(
            text = stringResource(R.string.ghost_sturz_hinweis),
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
    if (state.analysisSaved) {
        Text(
            text = stringResource(R.string.ghost_gespeichert),
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textSecondary,
        )
    } else {
        PrimaryButton(
            text = stringResource(R.string.ghost_speichern),
            onClick = onSaveAnalysis,
        )
    }
    TextButton(onClick = onBackToPath) { Text(stringResource(R.string.ghost_pfad_anpassen)) }
}

@Preview(showBackground = true)
@Composable
private fun GhostClimberScreenPreview() {
    BoulderBuddyTheme {
        GhostClimberScreen()
    }
}
