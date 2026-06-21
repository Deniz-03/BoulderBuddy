package com.boulderbuddy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Button-Größen lokal gehalten (nur hier genutzt).
private val ControlPrimarySize = 64.dp    // Play/Pause, hervorgehoben
private val ControlSecondarySize = 48.dp  // Reset, Settings

// Steuerleiste des Hangboard-Timers: Reset · Play/Pause · Settings.
// Der mittlere Play/Pause-Button ist hervorgehoben (größer, dunkel gefüllt),
// die beiden äußeren sind dezent umrandet.
@Composable
fun TimerControls(
    isRunning: Boolean,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleControl(
            icon = Icons.Outlined.Refresh,
            contentDescription = "Zurücksetzen",
            onClick = onReset,
            primary = false,
        )
        CircleControl(
            icon = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isRunning) "Pause" else "Start",
            onClick = onPlayPause,
            primary = true,
        )
        CircleControl(
            icon = Icons.Outlined.Settings,
            contentDescription = "Timer-Einstellungen",
            onClick = onSettings,
            primary = false,
        )
    }
}

// Runder Steuer-Button. primary = hervorgehoben (groß, dunkel gefüllt),
// sonst dezent (kleiner, helle Card mit Rand). Nur im TimerControls-Kontext nutzbar.
@Composable
private fun CircleControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(if (primary) ControlPrimarySize else ControlSecondarySize),
        shape = CircleShape,
        color = if (primary) BoulderBuddy.colors.surfaceInverse else BoulderBuddy.colors.surfaceCard,
        contentColor = if (primary) BoulderBuddy.colors.surfaceBackground else BoulderBuddy.colors.textSecondary,
        border = if (primary) null else BorderStroke(Dimens.borderSubtle, BoulderBuddy.colors.borderSubtle),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Dimens.iconL),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun TimerControlsPreview() {
    BoulderBuddyTheme {
        var running by remember { mutableStateOf(true) }
        TimerControls(
            isRunning = running,
            onPlayPause = { running = !running },
            onReset = {},
            onSettings = {},
        )
    }
}
