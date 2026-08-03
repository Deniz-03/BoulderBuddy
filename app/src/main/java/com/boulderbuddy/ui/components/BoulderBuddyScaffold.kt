package com.boulderbuddy.ui.components

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
import com.boulderbuddy.ui.theme.M3OnPrimary
import com.boulderbuddy.ui.theme.dotPattern
import androidx.compose.ui.tooling.preview.Preview

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

@Preview(name = "Scaffold – vollständig", showBackground = true, backgroundColor = 0xFFF3ECD6)
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
                                tint = M3OnPrimary,
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

@Preview(name = "Scaffold – nur Content", showBackground = true, backgroundColor = 0xFFF3ECD6)
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
