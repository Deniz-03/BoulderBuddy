package com.boulderbuddy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.StatusBadge
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.components.VideoPlayer
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.viewmodel.BoulderDetailUiState
import com.boulderbuddy.util.MediaType
import com.boulderbuddy.util.mediaTypeOf

// ─────────────────────────────────────────────────────────────────────────────
// Boulder-Detailansicht (#6 der Wireframes). Zeigt GENAU EINEN Boulder, geladen
// per boulderId aus dem BoulderDetailViewModel (Phase 6.7).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BoulderDetailScreen(
    // Anzeige-Zustand aus dem BoulderDetailViewModel.
    state: BoulderDetailUiState = BoulderDetailUiState(),
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    // Schnell-Versuche (nur bei aktiver Session sichtbar, siehe state.isSessionActive).
    onIncrementAttempts: () -> Unit = {},
    onDecrementAttempts: () -> Unit = {},
) {
    val accentColor = state.accentColor
    val (statusText, statusColor) = statusBadgeStyle(state.status)
    val subtitle = if (state.sektor.isBlank()) null else "Sektor ${state.sektor}"

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = state.name.ifBlank { "Boulder" },
                subtitle = subtitle,
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
                },
                actions = {
                    // Öffnet das Formular im Edit-Modus (vorbefüllt, aktualisiert die Route).
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Bearbeiten",
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
                },
            )
        },
        content = { _ ->
            if (!state.loading && !state.exists) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Boulder nicht gefunden.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                return@BoulderBuddyScaffold
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingL,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
            ) {
                // --- Foto/Video mit farbigem Rahmen (Routenfarbe) ---
                item {
                    val context = LocalContext.current
                    val isVideo = remember(state.fotoUri) {
                        mediaTypeOf(context, state.fotoUri) == MediaType.VIDEO
                    }
                    BoulderFoto(
                        fotoUri = state.fotoUri,
                        isVideo = isVideo,
                        accentColor = accentColor,
                    )
                }

                // --- Grade-Badge + Status ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadge(text = state.grade, color = accentColor)
                        StatusBadge(text = statusText, color = statusColor)
                    }
                }

                // --- Stats (Versuche, Sektor) ---
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        StatCard(
                            value = state.versuche.toString(),
                            label = "Versuche",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        StatCard(
                            value = state.sektor.ifBlank { "–" },
                            label = "Sektor",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                // --- Schnell-Versuche (nur solange die Session läuft) ---
                if (state.isSessionActive) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = BoulderBuddy.colors.surfaceCard,
                            border = BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = Dimens.paddingL,
                                        vertical = Dimens.paddingS,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Versuche anpassen",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = BoulderBuddy.colors.textSecondary,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onDecrementAttempts) {
                                        Icon(
                                            Icons.Filled.Remove,
                                            contentDescription = "Ein Versuch weniger",
                                        )
                                    }
                                    Text(
                                        text = state.versuche.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = Dimens.paddingS),
                                    )
                                    IconButton(onClick = onIncrementAttempts) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Ein Versuch mehr",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Notiz (nur wenn vorhanden) ---
                state.notiz?.takeIf { it.isNotBlank() }?.let { notiz ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                            SectionHeader(text = "Notiz")
                            Text(
                                text = notiz,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
    )
}

// Foto-/Video-Bereich. Rahmen in der Routenfarbe (Wireframe: "Foto mit farbigem Rahmen").
// Zeigt das gespeicherte Foto (Coil, Phase 6.11), das Video (Media3/ExoPlayer, Phase 7.3c)
// oder einen Platzhalter, wenn kein Medium da ist.
@Composable
private fun BoulderFoto(
    fotoUri: String?,
    isVideo: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(BoulderBuddy.colors.surfaceCard)
            .border(BorderStroke(Dimens.borderAccent, accentColor), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        when {
            fotoUri == null -> Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "Kein Medium vorhanden",
                tint = accentColor,
                modifier = Modifier.size(Dimens.iconL),
            )

            isVideo -> VideoPlayer(
                uri = fotoUri,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
            )

            else -> AsyncImage(
                model = fotoUri,
                contentDescription = "Boulder-Foto",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
            )
        }
    }
}

// Text + Farbe des StatusBadge je Status.
@Composable
private fun statusBadgeStyle(status: BoulderStatus): Pair<String, Color> {
    val routes = BoulderBuddy.colors.routes
    return when (status) {
        BoulderStatus.TOP -> "${status.symbol} Top" to routes.green
        BoulderStatus.FLASH -> "${status.symbol} Flash" to routes.orange
        BoulderStatus.PROJEKT -> "${status.symbol} Projekt" to routes.blue
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3ECD6)
@Composable
private fun BoulderDetailScreenPreview() {
    BoulderBuddyTheme {
        BoulderDetailScreen(
            state = BoulderDetailUiState(
                loading = false,
                exists = true,
                name = "Überhang-Killer",
                sektor = "C",
                grade = "6b",
                accentColor = Color(0xFF2E9E52),
                status = BoulderStatus.TOP,
                versuche = 4,
                notiz = "Schlüsselstelle ist der dynamische Zug zum Henkel.",
                fotoUri = null,
                isSessionActive = true,
            ),
        )
    }
}
