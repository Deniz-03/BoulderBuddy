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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.M3OnPrimary

private val TopBarSubtitleColor = Color(0xFFBBB5A5)

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                    color = M3OnPrimary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TopBarSubtitleColor,
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
                        tint = M3OnPrimary,
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
                        tint = M3OnPrimary,
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
                        tint = M3OnPrimary,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Einstellungen",
                        tint = M3OnPrimary,
                    )
                }
            },
        )
    }
}
