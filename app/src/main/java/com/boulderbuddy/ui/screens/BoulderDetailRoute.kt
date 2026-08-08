package com.boulderbuddy.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulderbuddy.ui.viewmodel.BoulderDetailViewModel

/**
 * Beschafft den [BoulderDetailViewModel] für eine boulderId und rendert [BoulderDetailScreen] —
 * **kein** sichtbarer Screen, dieselbe Rolle wie [SessionRoute].
 *
 * Es gibt sie, weil derselbe Detailinhalt an zwei Stellen gebraucht wird: als klassisches
 * Push-Ziel am Telefon und als Detail-Pane der Boulder-Übersicht auf dem Tablet. Ohne diese
 * Ebene stünde die ViewModel-Beschaffung zweimal im Baum.
 *
 * `key = "boulder_$boulderId"` ist nicht optional: im Detail-Pane wechselt die Auswahl, ohne
 * dass der Composable verlassen wird. Ohne eigenen Schlüssel bekäme der zweite Boulder den
 * ViewModel des ersten.
 */
@Composable
fun BoulderDetailRoute(
    boulderId: Int,
    // `null` = kein Zurück-Pfeil (Zwei-Pane-Layout, die Liste steht daneben).
    onBack: (() -> Unit)? = {},
    onEdit: (Int) -> Unit = {},
    viewModel: BoulderDetailViewModel = hiltViewModel<BoulderDetailViewModel, BoulderDetailViewModel.Factory>(
        key = "boulder_$boulderId",
    ) { factory -> factory.create(boulderId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BoulderDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(boulderId) },
        onIncrementAttempts = viewModel::incrementAttempts,
        onDecrementAttempts = viewModel::decrementAttempts,
    )
}
