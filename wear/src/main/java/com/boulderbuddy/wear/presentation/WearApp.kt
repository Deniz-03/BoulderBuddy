package com.boulderbuddy.wear.presentation

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

/**
 * Compose-Root der Wear-App: setzt das Wear-Material-Theme und zeigt den Hangboard-Timer.
 * Minimal-Scope (Companion) → aktuell nur ein Screen, daher (noch) keine Wear-Navigation.
 */
@Composable
fun WearApp() {
    MaterialTheme {
        TimerScreen()
    }
}
