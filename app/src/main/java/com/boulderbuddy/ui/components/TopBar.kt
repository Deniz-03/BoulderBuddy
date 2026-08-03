package com.boulderbuddy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    // Trennlinie nach unten statt Farbunterschied: seit das Chrome mitdreht, ist es im
    // Light Mode nahezu so hell wie der Inhalt und braucht eine Kante, um als Leiste zu
    // lesen. Im Dark Mode schadet sie nicht — dort trennt zusätzlich die Helligkeit.
    // Die Farbe wird außerhalb von drawBehind gelesen: der DrawScope ist kein Composable
    // und kommt an das Theme nicht heran.
    val randfarbe = BoulderBuddy.colors.borderSubtle
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val staerke = 1.dp.toPx()
                drawRect(
                    color = randfarbe,
                    topLeft = Offset(0f, size.height - staerke),
                    size = Size(size.width, staerke),
                )
            },
        color = BoulderBuddy.colors.surfaceChrome,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Dimens.paddingS, vertical = Dimens.paddingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navIcon != null) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    navIcon()
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.paddingS),
            ) {
                Text(
                    text = title,
                    style = if (subtitle != null)
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                    else
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = BoulderBuddy.colors.onChrome,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BoulderBuddy.colors.textSecondary,
                    )
                }
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = actions,
                )
            }
        }
    }
}

// --- Previews ---

@Preview(name = "Titel-only", showBackground = true)
@Composable
private fun TopBarTitleOnlyPreview() {
    BoulderBuddyTheme {
        TopBar(title = "Session-Übersicht")
    }
}

@Preview(name = "Mit Back-Arrow", showBackground = true)
@Composable
private fun TopBarWithBackPreview() {
    BoulderBuddyTheme {
        TopBar(
            title = "Session bearbeiten",
            navIcon = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = BoulderBuddy.colors.onChrome,
                    )
                }
            },
        )
    }
}

@Preview(name = "Greeting + Subtitle + Actions", showBackground = true)
@Composable
private fun TopBarGreetingPreview() {
    BoulderBuddyTheme {
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
    }
}

@Preview(name = "Titel + mehrere Actions", showBackground = true)
@Composable
private fun TopBarMultiActionPreview() {
    BoulderBuddyTheme {
        TopBar(
            title = "Boulder-Übersicht",
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Suchen",
                        tint = BoulderBuddy.colors.onChrome,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Einstellungen",
                        tint = BoulderBuddy.colors.onChrome,
                    )
                }
            },
        )
    }
}
