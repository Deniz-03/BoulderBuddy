package com.boulderbuddy.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Geometrie des Switches. Lokal gehalten (nicht im Theme), da nur hier genutzt und
// eng verzahnt: TrackHeight = ThumbSizeOn + 2 * paddingXS (24 + 2*4 = 32).
private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val ThumbSizeOn = 24.dp   // An: voller Thumb
private val ThumbSizeOff = 16.dp  // Aus: kleinerer Thumb

// An/Aus-Schalter für die Einstellungen. Bewusst als Custom-Switch statt M3-Switch:
// es gibt keinen Schatten (flach, passend zur Designsprache). Der Thumb gleitet animiert
// zwischen links und rechts und wird im Aus-Zustand kleiner.
//
// Track im An-Zustand ist `fillStrong` — dasselbe Paar wie beim PrimaryButton, also im Light
// Mode dunkel und im Dark Mode hell. Der Thumb nimmt deshalb `onFillStrong` und nicht wie
// früher den Screen-Hintergrund: nur so bleibt er in beiden Themes der Gegenpol zum Track.
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor =
        if (checked) BoulderBuddy.colors.fillStrong else BoulderBuddy.colors.borderSubtle
    // Thumb gleitet von links (paddingXS) nach rechts (Track minus voller Thumb minus Inset).
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSizeOn - Dimens.paddingXS else Dimens.paddingXS,
        label = "thumbOffset",
    )
    // Thumb-Größe animiert mit: klein wenn aus, voll wenn an.
    val thumbSize by animateDpAsState(
        targetValue = if (checked) ThumbSizeOn else ThumbSizeOff,
        label = "thumbSize",
    )

    Box(
        modifier = modifier
            .size(width = TrackWidth, height = TrackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(BoulderBuddy.colors.onFillStrong),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun ToggleSwitchPreview() {
    BoulderBuddyTheme {
        var on by remember { mutableStateOf(true) }
        var off by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.padding(Dimens.paddingL),
            horizontalArrangement = Arrangement.spacedBy(Dimens.paddingL),
        ) {
            ToggleSwitch(checked = on, onCheckedChange = { on = it })
            ToggleSwitch(checked = off, onCheckedChange = { off = it })
        }
    }
}
