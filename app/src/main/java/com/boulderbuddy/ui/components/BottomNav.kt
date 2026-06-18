package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.M3OnPrimary

enum class BottomNavTab(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
) {
    Home(Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    Sessions(Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "Sessions"),
    Stats(Icons.Filled.BarChart, Icons.Outlined.BarChart, "Statistik"),
    Timer(Icons.Filled.Timer, Icons.Outlined.Timer, "Hangboard-Timer"),
    GhostClimber(Icons.Filled.DirectionsRun, Icons.Outlined.DirectionsRun, "Ghost Climber"),
}

private val InactiveColor = M3OnPrimary.copy(alpha = 0.45f)

@Composable
fun BottomNav(
    selectedTab: BottomNavTab,
    onTabSelect: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BoulderBuddy.colors.surfaceInverse,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavTab.entries.forEach { tab ->
                BottomNavItem(
                    tab = tab,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = BoulderBuddy.colors.navActive
    val iconColor = if (isSelected) activeColor else InactiveColor

    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )

        // Aktiv-Punkt unter dem Icon
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) activeColor else Color.Transparent),
        )
    }
}

// --- Previews ---

@Preview(name = "BottomNav – Home aktiv", showBackground = true)
@Composable
private fun BottomNavHomePreview() {
    BoulderBuddyTheme {
        BottomNav(
            selectedTab = BottomNavTab.Home,
            onTabSelect = {},
        )
    }
}

@Preview(name = "BottomNav – Timer aktiv", showBackground = true)
@Composable
private fun BottomNavTimerPreview() {
    BoulderBuddyTheme {
        BottomNav(
            selectedTab = BottomNavTab.Timer,
            onTabSelect = {},
        )
    }
}

@Preview(name = "Scaffold + TopBar + BottomNav", showBackground = true, backgroundColor = 0xFFF9F4E3)
@Composable
private fun FullLayoutPreview() {
    BoulderBuddyTheme {
        BoulderBuddyScaffold(
            topBar = {
                TopBar(
                    title = "Guten Morgen 👋",
                    subtitle = "Mittwoch, 18. Juni",
                )
            },
            bottomBar = {
                BottomNav(
                    selectedTab = BottomNavTab.Home,
                    onTabSelect = {},
                )
            },
            content = {},
        )
    }
}
