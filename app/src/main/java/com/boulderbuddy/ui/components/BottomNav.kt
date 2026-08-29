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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.ui.draw.drawWithContent

import androidx.compose.ui.geometry.Size
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

enum class BottomNavTab(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
) {
    Home(Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    Sessions(Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "Sessions"),
    Stats(Icons.Filled.BarChart, Icons.Outlined.BarChart, "Statistik"),
    Timer(Icons.Filled.Timer, Icons.Outlined.Timer, "Hangboard-Timer"),
}


/**
 * Die untere Navigationsleiste am Telefon — vier Ziele, kein fünftes.
 *
 * Sichtbar ausschließlich auf den vier Tab-Zielen; Push-Screens füllen das Fenster allein
 * (die Entscheidung darüber trifft `AppNavigation`, nicht diese Datei). Ab 600 dp Breite
 * übernimmt stattdessen [SideNav] — eine unten angeheftete Leiste ist auf einem Tablet
 * weder gut erreichbar noch üblich.
 */
@Composable
fun BottomNav(
    selectedTab: BottomNavTab,
    onTabSelect: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Trennlinie nach oben, aus demselben Grund wie bei der TopBar: helles Chrome auf
    // hellem Inhalt braucht eine Kante.
    val randfarbe = BoulderBuddy.colors.borderSubtle
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // drawWithContent statt drawBehind — siehe TopBar: die Füllfarbe der Surface
            // kommt nach dem übergebenen Modifier und würde die Linie überdecken.
            .drawWithContent {
                drawContent()
                val staerke = 1.dp.toPx()
                drawRect(color = randfarbe, size = Size(size.width, staerke))
            },
        color = BoulderBuddy.colors.surfaceChrome,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = Dimens.paddingS),
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
    // Beide Farben kommen aus dem Theme, weil das Chrome mitdreht. Der inaktive Eintrag
    // nimmt textTertiary statt eines Alpha-Werts auf der Inhaltsfarbe: 45 % Deckkraft
    // ergaben in beiden Themes nur 3,94:1, und darunter steht eine 11-sp-Beschriftung.
    val activeColor = BoulderBuddy.colors.navActive
    val iconColor = if (isSelected) activeColor else BoulderBuddy.colors.textTertiary

    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
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

@Preview(name = "Scaffold + TopBar + BottomNav", showBackground = true, backgroundColor = 0xFFFCF6E4)
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
