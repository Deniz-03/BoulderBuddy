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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.StatCard
import com.boulderbuddy.ui.components.StatusBadge
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.theme.routeColorForKey

// ─────────────────────────────────────────────────────────────────────────────
// Boulder-Detailansicht (#6 der Wireframes). Zeigt GENAU EINEN Boulder.
//
// Im Gegensatz zum Statistik-Screen braucht dieser Screen keine Aggregation,
// sondern nur eine einzige Zeile aus der Boulder/Route-Tabelle:
//   SELECT * FROM Route WHERE id = :boulderId
//
// Erreichbar von:
//   - BoulderUebersichtScreen (RouteCard.onClick, übergibt boulderId)
//   - SessionDetailScreen (RouteCard im aktiven Session-Grid)
// Das Edit-Icon im Header führt zu RouteHinzufuegenScreen im Bearbeiten-Modus
// (mit boulderId, vorbefüllt).
// ─────────────────────────────────────────────────────────────────────────────

// Status (TOP/FLASH/PROJEKT) wird mit AlteSessionScreen geteilt (gleiches Package).

// TODO(DB): Diese Felder kommen aus dem BoulderDetailViewModel/Room — eine Zeile
//  pro boulderId. Die meisten existieren schon im Boulder-Modell (vgl.
//  BoulderUebersichtScreen): grade, name, sektor, accentColorKey. Neu hier:
//  versuche, status, rating, notiz, fotoUri.
private data class BoulderDetailData(
    val grade: String,
    val name: String,
    val sektor: String,
    val accentColorKey: String,  // → routeColorForKey(); färbt alle Akzente
    val versuche: Int,
    val status: BoulderStatus,
    val rating: Int,             // 1..5
    val notiz: String?,          // nullable → Abschnitt entfällt, wenn leer
    val fotoUri: String?,        // nullable → Platzhalter statt Foto
)

// TODO(DB): Platzhalter, bis ViewModel/Room existiert. Ersetzen durch
//  boulderDetailViewModel.uiState (collectAsStateWithLifecycle) + Lade-/Fehler-
//  Zustand (boulderId nicht gefunden → freundlicher Hinweis statt leerer Screen).
private val placeholderBoulder = BoulderDetailData(
    grade = "6b",
    name = "Überhang-Killer",
    sektor = "Sektor C",
    accentColorKey = "green",
    versuche = 4,
    status = BoulderStatus.TOP,
    rating = 4,
    notiz = "Schlüsselstelle ist der dynamische Zug zum Henkel. " +
        "Beim nächsten Mal mit mehr Spannung im Oberkörper probieren.",
    fotoUri = null,
)

@Composable
fun BoulderDetailScreen(
    // TODO: Wird von der Navigation übergeben (RouteCard.onClick → boulderId).
    //  Aktuell ungenutzt, weil noch Platzhalterdaten geladen werden.
    @Suppress("UNUSED_PARAMETER") boulderId: Int = 0,
    // Navigations-Callbacks (Phase 2). Defaults = {} halten Preview & Tests lauffähig.
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val boulder = placeholderBoulder
    val accentColor = routeColorForKey(boulder.accentColorKey)
    val (statusText, statusColor) = statusBadgeStyle(boulder.status)

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = boulder.name,
                subtitle = boulder.sektor,
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = M3OnPrimary,
                        )
                    }
                },
                actions = {
                    // TODO: Bearbeiten-Modus mit boulderId (Phase 6.5/6.7); aktuell öffnet
                    //  onEdit den "Boulder hinzufügen"-Screen als Platzhalter.
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Bearbeiten",
                            tint = M3OnPrimary,
                        )
                    }
                },
            )
        },
        content = { _ ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.paddingL,
                    vertical = Dimens.paddingL,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
            ) {
                // --- Foto mit farbigem Rahmen (Routenfarbe) ---
                item {
                    BoulderFoto(fotoUri = boulder.fotoUri, accentColor = accentColor)
                }

                // --- Grade-Badge + Status ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Grade-Badge in Routenfarbe.
                        StatusBadge(text = boulder.grade, color = accentColor)
                        StatusBadge(text = statusText, color = statusColor)
                    }
                }

                // --- Stats (Versuche, Bewertung) ---
                // Status ist oben bereits als Badge dargestellt; hier die zählbaren Werte.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingM),
                    ) {
                        StatCard(
                            value = boulder.versuche.toString(),
                            label = "Versuche",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatCard(
                            value = "${boulder.rating}/5",
                            label = "Bewertung",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }

                // --- Notiz ---
                // Nur anzeigen, wenn eine Notiz hinterlegt ist.
                boulder.notiz?.let { notiz ->
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

// Foto-Bereich. Rahmen in der Routenfarbe (Wireframe: "Foto mit farbigem Rahmen").
@Composable
private fun BoulderFoto(
    fotoUri: String?,
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
        // TODO(DB): fotoUri laden (z.B. Coil AsyncImage), sobald Fotos gespeichert
        //  werden. Bis dahin Platzhalter-Icon für Boulder ohne Foto.
        if (fotoUri == null) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "Kein Foto vorhanden",
                tint = accentColor,
                modifier = Modifier.size(Dimens.iconL),
            )
        }
    }
}

// Text + Farbe des StatusBadge je Status. Das Symbol kommt aus dem (geteilten) Enum,
// damit es nur eine Quelle gibt; hier nur Beschriftung + Routenfarbe ergänzt.
@Composable
private fun statusBadgeStyle(status: BoulderStatus): Pair<String, Color> {
    val routes = BoulderBuddy.colors.routes
    return when (status) {
        BoulderStatus.TOP -> "${status.symbol} Top" to routes.green
        BoulderStatus.FLASH -> "${status.symbol} Flash" to routes.orange
        BoulderStatus.PROJEKT -> "${status.symbol} Projekt" to routes.blue
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun BoulderDetailScreenPreview() {
    BoulderBuddyTheme {
        BoulderDetailScreen()
    }
}
