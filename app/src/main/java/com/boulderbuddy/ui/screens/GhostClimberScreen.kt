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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.GhostSkeletonPlayer
import com.boulderbuddy.ui.components.PhotoPicker
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.viewmodel.GhostClimberUiState

/**
 * Ghost Climber (Phase 7.5) — bewusst als "Experimental" gekennzeichneter Einstieg
 * außerhalb des MVP-Kernflusses (Plan A.4). M1: Referenz-Video wählen, Pose-Spur
 * extrahieren, Skelett-Overlay über der Wiedergabe prüfen. Die weiteren Pipeline-
 * Schritte (Anker, Pfad, Vergleich) docken in M2+ an diesen Flow an.
 */
@Composable
fun GhostClimberScreen(
    state: GhostClimberUiState = GhostClimberUiState(),
    onSelectVideo: (String) -> Unit = {},
    onAnalyze: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    // Nur Videos anbieten — Ghost Climber vergleicht Bewegtbild, keine Fotos.
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onSelectVideo(it.toString()) } }

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
                Text(
                    text = "Vergleiche zwei Versuche derselben Route. Schritt 1: " +
                        "Referenz-Video wählen und das erkannte Skelett prüfen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                )

                SectionHeader(text = "Referenz-Video")
                PhotoPicker(
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly,
                            ),
                        )
                    },
                    label = "Video auswählen",
                    imageUri = state.videoUri,
                    isVideo = state.videoUri != null,
                )

                if (state.videoUri != null && !state.analyzing && state.poseTrack == null) {
                    PrimaryButton(
                        text = "Skelett analysieren",
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        onClick = onAnalyze,
                    )
                }

                if (state.analyzing) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
                        // total == 0: Dauer/Framezahl noch unbekannt → unbestimmter Balken.
                        if (state.progressTotal > 0) {
                            LinearProgressIndicator(
                                progress = { state.progressDone.toFloat() / state.progressTotal },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Analysiere Pose… Frame ${state.progressDone} / ${state.progressTotal}",
                                style = MaterialTheme.typography.labelMedium,
                                color = BoulderBuddy.colors.textTertiary,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Analyse wird vorbereitet…",
                                style = MaterialTheme.typography.labelMedium,
                                color = BoulderBuddy.colors.textTertiary,
                            )
                        }
                    }
                }

                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                val track = state.poseTrack
                if (track != null && state.videoUri != null) {
                    SectionHeader(text = "Skelett-Vorschau")
                    GhostSkeletonPlayer(
                        uri = state.videoUri,
                        poseTrack = track,
                        modifier = Modifier
                            .fillMaxWidth()
                            // Player im Seitenverhältnis des Videos, damit Letterbox-Ränder
                            // (und damit Overlay-Versatz) gar nicht erst entstehen.
                            .aspectRatio(track.frameWidth.toFloat() / track.frameHeight),
                    )
                    Text(
                        text = "${track.frames.size} Frames · " +
                            "${"%.1f".format(track.durationMs / 1000.0)} s · " +
                            "${track.sampleFps} fps abgetastet",
                        style = MaterialTheme.typography.labelMedium,
                        color = BoulderBuddy.colors.textTertiary,
                    )
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun GhostClimberScreenPreview() {
    BoulderBuddyTheme {
        GhostClimberScreen()
    }
}
