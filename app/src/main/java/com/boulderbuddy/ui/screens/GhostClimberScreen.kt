package com.boulderbuddy.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.GhostAnchorEditor
import com.boulderbuddy.ui.components.GhostPathEditor
import com.boulderbuddy.ui.components.GhostSideBySidePlayer
import com.boulderbuddy.ui.components.GhostSkeletonPlayer
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
import com.boulderbuddy.ui.theme.M3OnPrimary
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
    onSelectVideo: (GhostRole, String) -> Unit = { _, _ -> },
    onAnalyze: () -> Unit = {},
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
    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Ghost Climber",
                subtitle = "Experimental",
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
                when (state.step) {
                    GhostStep.SELECTION -> SelectionStep(
                        state = state,
                        onSelectVideo = onSelectVideo,
                        onAnalyze = onAnalyze,
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
    onAnalyze: () -> Unit,
    onRestoreAnalysis: (Int) -> Unit,
    onDeleteAnalysis: (Int) -> Unit,
) {
    Text(
        text = "Vergleiche zwei Versuche derselben Route (feste Kamera). " +
            "Wähle ein Referenz- und ein Vergleichs-Video.",
        style = MaterialTheme.typography.bodyMedium,
        color = BoulderBuddy.colors.textSecondary,
    )

    VideoSlotPicker(
        title = "Referenz-Video",
        slot = state.reference,
        onSelected = { onSelectVideo(GhostRole.REFERENCE, it) },
    )
    VideoSlotPicker(
        title = "Vergleichs-Video",
        slot = state.comparison,
        onSelected = { onSelectVideo(GhostRole.COMPARISON, it) },
    )

    if (state.analyzing) {
        AnalysisProgress(label = "Referenz", slot = state.reference)
        AnalysisProgress(label = "Vergleich", slot = state.comparison)
    } else if (state.canAnalyze) {
        PrimaryButton(
            text = "Posen analysieren",
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            onClick = onAnalyze,
        )
    }

    // Gespeicherte Analysen (M5): antippen lädt direkt die Vergleichsansicht.
    if (state.savedAnalyses.isNotEmpty() && !state.analyzing) {
        SectionHeader(text = "Gespeicherte Analysen")
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
                text = "Vorschlag: ${analysis.modeLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Analyse löschen",
                tint = BoulderBuddy.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun VideoSlotPicker(
    title: String,
    slot: GhostVideoSlot,
    onSelected: (String) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onSelected(it.toString()) } }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
        SectionHeader(text = title)
        PhotoPicker(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            },
            label = "Video auswählen",
            imageUri = slot.uri,
            isVideo = slot.uri != null,
        )
    }
}

@Composable
private fun AnalysisProgress(label: String, slot: GhostVideoSlot) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
        when {
            slot.track != null -> Text(
                text = "$label: fertig (${slot.track.frames.size} Frames)",
                style = MaterialTheme.typography.labelMedium,
                color = BoulderBuddy.colors.textSecondary,
            )
            slot.progressTotal > 0 -> {
                LinearProgressIndicator(
                    progress = { slot.progressDone.toFloat() / slot.progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "$label: Frame ${slot.progressDone} / ${slot.progressTotal}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BoulderBuddy.colors.textTertiary,
                )
            }
            else -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "$label: wird vorbereitet…",
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
        text = "Tippe in beiden Videos DIESELBEN ${GhostTuning.MIN_ANCHORS}+ markanten " +
            "Wandpunkte (Griffe/Volumes) in derselben Reihenfolge an — gleiche Farbe = " +
            "gleicher Punkt. Mit dem Regler findest du ein Standbild mit freier Wand.",
        style = MaterialTheme.typography.bodyMedium,
        color = BoulderBuddy.colors.textSecondary,
    )

    GhostRole.entries.forEach { role ->
        val slot = state.slot(role)
        SectionHeader(
            text = if (role == GhostRole.REFERENCE) "Referenz-Video" else "Vergleichs-Video",
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
            text = "Übereinanderlegen",
            icon = Icons.Filled.Layers,
            onClick = onComputeAlignment,
        )
    } else {
        val ref = state.reference.anchors.size
        val cmp = state.comparison.anchors.size
        Text(
            text = if (ref >= GhostTuning.MIN_ANCHORS && ref != cmp) {
                "Beide Videos brauchen GLEICH VIELE Anker (aktuell $ref vs. $cmp)."
            } else {
                "Noch mindestens ${GhostTuning.MIN_ANCHORS} Anker pro Video setzen."
            },
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
    TextButton(onClick = onBackToSelection) { Text("Zurück zur Video-Auswahl") }
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
        text = "Der Routenpfad (grün) bestimmt, wie der Fortschritt gemessen wird. " +
            "Vorschlag = deine Hüft-Linie aus dem Referenz-Video (gestrichelt). " +
            "Tippen verlängert den Pfad — z.B. bis zum Top, falls der Versuch " +
            "vorher abbrach.",
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
            text = "Synchronisieren",
            icon = Icons.Filled.Layers,
            onClick = onConfirmPath,
        )
    } else {
        Text(
            text = "Der Pfad braucht mindestens 2 Stützpunkte.",
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
    TextButton(onClick = onBackToAnchors) { Text("Anker anpassen") }
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
    // Debug-Ansicht (Stufe 0): Roh-Keypoints + Kennzahlen-HUD im Overlay-Player.
    var debugHud by rememberSaveable { mutableStateOf(false) }

    SectionHeader(text = "Synchronisierter Vergleich")
    // Umschalter, vorbelegt mit dem Vorschlag der Ähnlichkeitsmetrik (P7).
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
        SelectableChip(
            label = "Overlay",
            selected = state.viewMode == GhostViewMode.OVERLAY,
            onClick = { onSetViewMode(GhostViewMode.OVERLAY) },
        )
        SelectableChip(
            label = "Side-by-Side",
            selected = state.viewMode == GhostViewMode.SIDE_BY_SIDE,
            onClick = { onSetViewMode(GhostViewMode.SIDE_BY_SIDE) },
        )
        SelectableChip(
            label = "Debug",
            selected = debugHud,
            onClick = { debugHud = !debugHud },
        )
    }
    if (state.suggestionReason.isNotEmpty()) {
        val suggestedLabel =
            if (state.suggestedMode == GhostViewMode.OVERLAY) "Overlay" else "Side-by-Side"
        Text(
            text = "Vorschlag: $suggestedLabel — ${state.suggestionReason}",
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }

    when (state.viewMode) {
        GhostViewMode.OVERLAY -> {
            GhostSkeletonPlayer(
                uri = refUri,
                poseTrack = refTrack,
                ghostTrack = ghostTrack,
                ghostTimeForPosition = cmpTimeForPosition,
                abortTimeMs = state.refAbortTimeMs,
                ghostAbortTimeMs = state.cmpAbortTimeMs,
                debug = debugHud,
                modifier = Modifier
                    .fillMaxWidth()
                    // Player im Seitenverhältnis des Videos, damit Letterbox-Ränder
                    // (und damit Overlay-Versatz) gar nicht erst entstehen.
                    .aspectRatio(refTrack.frameWidth.toFloat() / refTrack.frameHeight),
            )
            Text(
                text = "Orange = Referenz, Blau = Geist (Vergleichs-Versuch, in den " +
                    "Referenzraum gelegt). Der Geist ist per DTW auf den Routen-" +
                    "Fortschritt synchronisiert — an jeder Stelle der Route siehst du " +
                    "beide Körperpositionen, unabhängig vom Tempo.",
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
                cmpTimeForPosition = cmpTimeForPosition,
                refAbortTimeMs = state.refAbortTimeMs,
                cmpAbortTimeMs = state.cmpAbortTimeMs,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Links Referenz (steuert die Wiedergabe), rechts der Vergleichs-" +
                    "Versuch — automatisch an derselben Stelle der Route. Ehrlicher als " +
                    "ein Overlay, wenn die Beta sich deutlich unterscheidet.",
                style = MaterialTheme.typography.labelMedium,
                color = BoulderBuddy.colors.textTertiary,
            )
        }
    }
    if (state.refAbortTimeMs != null || state.cmpAbortTimeMs != null) {
        Text(
            text = "Sturz/Abbruch erkannt: das betroffene Skelett blendet am " +
                "Abbruchpunkt aus, der andere Versuch klettert weiter.",
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textTertiary,
        )
    }
    if (state.analysisSaved) {
        Text(
            text = "Analyse gespeichert ✓",
            style = MaterialTheme.typography.labelMedium,
            color = BoulderBuddy.colors.textSecondary,
        )
    } else {
        PrimaryButton(
            text = "Analyse speichern",
            onClick = onSaveAnalysis,
        )
    }
    TextButton(onClick = onBackToPath) { Text("Pfad anpassen") }
}

@Preview(showBackground = true)
@Composable
private fun GhostClimberScreenPreview() {
    BoulderBuddyTheme {
        GhostClimberScreen()
    }
}
