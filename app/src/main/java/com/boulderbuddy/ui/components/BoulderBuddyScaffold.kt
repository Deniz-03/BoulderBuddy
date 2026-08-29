package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.dotPattern
import androidx.compose.ui.tooling.preview.Preview

/**
 * Das Grundgerüst jedes Screens: Chrome oben, Chrome unten, dazwischen der Inhalt auf der
 * gemusterten Grundfläche.
 *
 * Bewusst KEIN Material-`Scaffold`. Dessen Fläche gehört Material, und der Hintergrund
 * dieser App ist ein eigener — Creme mit Punktmuster, im Dark Mode dunkel mit demselben
 * Muster. Über ein Material-Scaffold ließe er sich nur als Overlay legen, was jede
 * Elevation-Fläche darüber wieder ausbleicht.
 *
 * `topBar` und `bottomBar` sind nullbar, weil Push-Screens keine untere Leiste haben und
 * einzelne Ansichten (Kamera, Vollbild-Player) gar keine.
 */
@Composable
fun BoulderBuddyScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Erst die Grundfläche, dann die Punkte darauf. Vorher stand hier nur
            // `dotPattern` — die Punktfarbe war damit die EINZIGE Farbe, die das Scaffold
            // je auftrug, und darunter lag der Fenster-Hintergrund der Activity (ein
            // Fast-Weiß). Im Dark Mode ergab das helle Schrift auf hellem Grund.
            //
            // Die Wurzel-`Surface` in `BoulderBuddyTheme` deckt denselben Fall inzwischen
            // ab; hier steht es trotzdem, damit das Scaffold für sich genommen stimmt —
            // ein Punktmuster ohne definierten Untergrund ist kein vollständiges Bauteil.
            .background(BoulderBuddy.colors.surfaceBackground)
            .dotPattern(dotColor = BoulderBuddy.colors.surfacePattern),
    ) {
        if (topBar != null) {
            topBar()
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            content(PaddingValues())
        }

        if (bottomBar != null) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                bottomBar()
            }
        }
    }
}

// --- Previews ---

@Preview(name = "Scaffold – vollständig", showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun BoulderBuddyScaffoldFullPreview() {
    BoulderBuddyTheme {
        BoulderBuddyScaffold(
            topBar = {
                TopBar(
                    title = "Guten Morgen 👋",
                    subtitle = "Mittwoch, 18. Juni",
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Einstellungen",
                                tint = BoulderBuddy.colors.onChrome,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("[ BottomNav Platzhalter ]")
                }
            },
            content = { _ ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Content-Bereich")
                }
            },
        )
    }
}

@Preview(name = "Scaffold – nur Content", showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun BoulderBuddyScaffoldContentOnlyPreview() {
    BoulderBuddyTheme {
        BoulderBuddyScaffold(
            content = { _ ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Nur Content, kein Header/BottomBar")
                }
            },
        )
    }
}
