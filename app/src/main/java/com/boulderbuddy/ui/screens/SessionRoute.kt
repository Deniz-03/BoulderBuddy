package com.boulderbuddy.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulderbuddy.ui.viewmodel.SessionViewModel

// Routing-/Dispatcher-Ebene für die beiden Session-Ansichten — KEIN sichtbarer Screen.
// Holt den [SessionViewModel] (liest die sessionId selbst aus den Nav-Argumenten),
// beobachtet dessen Zustand und entscheidet am Aktiv-Marker (endedAt == null), ob die
// aktive (editierbare) oder die abgeschlossene (read-only) Ansicht gezeigt wird.
// onAddRoute wird hier an die sessionId gebunden, bevor es an die aktive Ansicht geht.
@Composable
fun SessionRoute(
    sessionId: Int,
    onBack: () -> Unit = {},
    onOpenBoulder: (Int) -> Unit = {},
    onAddRoute: (Int) -> Unit = {},
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.loading -> LoadingBox()
        !state.exists -> NotFoundBox()
        state.istAktiv -> SessionDetailScreen(
            gym = state.gym,
            startMillis = state.startMillis,
            topGrade = state.topGrade,
            boulders = state.boulders,
            hangboardSessions = state.hangboardSessions,
            onBack = onBack,
            onOpenBoulder = onOpenBoulder,
            onAddRoute = { onAddRoute(sessionId) },
            onEndSession = viewModel::endSession,
        )
        else -> AlteSessionScreen(
            gym = state.gym,
            dateSubtitle = state.dateSubtitle,
            durationText = state.durationText,
            notes = state.notes,
            boulders = state.boulders,
            onBack = onBack,
            onOpenBoulder = onOpenBoulder,
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotFoundBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Session nicht gefunden.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
