package com.boulderbuddy.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.GhostAnchorEditor
import com.boulderbuddy.ui.components.GhostSkeletonPlayer
import com.boulderbuddy.ui.components.PhotoPicker
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ghost.GhostTuning
import com.boulderbuddy.ghost.model.GhostPoint
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.viewmodel.GhostClimberUiState
import com.boulderbuddy.ui.viewmodel.GhostRole
import com.boulderbuddy.ui.viewmodel.GhostStep
import com.boulderbuddy.ui.viewmodel.GhostVideoSlot

/**
 * Ghost Climber (Phase 7.5) — bewusst als "Experimental" gekennzeichneter Einstieg
 * außerhalb des MVP-Kernflusses (Plan A.4). Geführter Flow über die Pipeline:
 * 1. Auswahl: Referenz- + Vergleichs-Video wählen, Posen extrahieren (M1).
 * 2. Anker: ≥4 korrespondierende Wandpunkte in beiden Videos antippen (M2).
 * 3. Vorschau: beide Skelette im Wand-Referenzraum über dem Referenz-Video (M2;
 *    Zeit-Synchronisation per DTW folgt in M3).
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
    onBackToSelection: () -> Unit = {},
    onBackToAnchors: () -> Unit = {},
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
                    GhostStep.SELECTION -> SelectionStep(state, onSelectVideo, onAnalyze)
                    GhostStep.ANCHORS -> AnchorsStep(
                        state = state,
                        onSelectAnchorFrame = onSelectAnchorFrame,
                        onAddAnchor = onAddAnchor,
                        onRemoveLastAnchor = onRemoveLastAnchor,
                        onComputeAlignment = onComputeAlignment,
                        onBackToSelection = onBackToSelection,
                    )
                    GhostStep.PREVIEW -> PreviewStep(state, onBackToAnchors)
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

// --- Schritt 3: Vorschau im Referenzraum --------------------------------------

@Composable
private fun PreviewStep(
    state: GhostClimberUiState,
    onBackToAnchors: () -> Unit,
) {
    val refUri = state.reference.uri
    val refTrack = state.reference.track
    val ghostTrack = state.ghostTrack
    if (refUri == null || refTrack == null || ghostTrack == null) return

    SectionHeader(text = "Überlagerung (Referenzraum)")
    GhostSkeletonPlayer(
        uri = refUri,
        poseTrack = refTrack,
        ghostTrack = ghostTrack,
        modifier = Modifier
            .fillMaxWidth()
            // Player im Seitenverhältnis des Videos, damit Letterbox-Ränder
            // (und damit Overlay-Versatz) gar nicht erst entstehen.
            .aspectRatio(refTrack.frameWidth.toFloat() / refTrack.frameHeight),
    )
    Text(
        text = "Orange = Referenz, Blau = Geist (Vergleichs-Versuch, per Homographie " +
            "in den Referenzraum gelegt). Beide laufen noch auf der Roh-Zeitachse — " +
            "die Tempo-Synchronisation (DTW) folgt als nächster Schritt.",
        style = MaterialTheme.typography.labelMedium,
        color = BoulderBuddy.colors.textTertiary,
    )
    TextButton(onClick = onBackToAnchors) { Text("Anker anpassen") }
}

@Preview(showBackground = true)
@Composable
private fun GhostClimberScreenPreview() {
    BoulderBuddyTheme {
        GhostClimberScreen()
    }
}
