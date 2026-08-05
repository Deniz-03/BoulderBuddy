package com.boulderbuddy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

/**
 * Die Navigation als seitliche Leiste — ab Breite `Mittel` (≥ 600 dp) anstelle der [BottomNav].
 *
 * Materials Vorgabe dahinter ist eine Aussage über **Verhalten**, nicht über Bauteile: eine
 * unten angeheftete Leiste ist am Telefon der Daumenweg, am Tablet quer aber 1280 dp breit,
 * mit vier Symbolen in ~300 dp Abstand und außerhalb jeder Griffweite.
 *
 * Bewusst ein eigener Nachbau statt `NavigationSuiteScaffold`: die [BottomNav] ist über
 * mehrere Design-Runden auf die Palette abgestimmt — Aktiv-Punkt statt Pillen-Indikator,
 * `textTertiary` für inaktiv statt eines Alpha-Werts (45 % ergaben nur 3,94:1), Chrome-Farbe
 * und Trennlinie aus denselben Tokens. Materials Komponente brächte ihre eigene Optik mit.
 * Beide Leisten teilen sich deshalb [BottomNavTab] und lesen dieselben Farben; wer eine
 * ändert, muss die andere ansehen.
 */
@Composable
fun SideNav(
    selectedTab: BottomNavTab,
    onTabSelect: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Trennlinie nach rechts — dieselbe Aufgabe wie die Linie über der BottomNav: helles
    // Chrome auf hellem Inhalt braucht eine Kante, sonst verschwimmen Leiste und Fläche.
    val randfarbe = BoulderBuddy.colors.borderSubtle
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(SideNavBreite)
            // drawWithContent statt drawBehind, siehe TopBar: die Füllfarbe der Surface kommt
            // nach dem übergebenen Modifier und überdeckte eine dahinter gezeichnete Linie.
            .drawWithContent {
                drawContent()
                val staerke = 1.dp.toPx()
                drawRect(
                    color = randfarbe,
                    topLeft = Offset(size.width - staerke, 0f),
                    size = Size(staerke, size.height),
                )
            },
        color = BoulderBuddy.colors.surfaceChrome,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(vertical = Dimens.paddingL),
            // Oben statt mittig: die Rail steht neben dem Inhalt, und der beginnt oben.
            // Zentriert schwebten die vier Einträge in der Bildmitte ohne Bezug zu irgendetwas.
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BottomNavTab.entries.forEach { tab ->
                SideNavItem(
                    tab = tab,
                    isSelected = tab == selectedTab,
                    onClick = { onTabSelect(tab) },
                )
            }
        }
    }
}

/**
 * 80 dp ist Materials Rail-Breite. Schmaler wird die Trefferfläche kleiner als die 48 dp,
 * die eine Fingerspitze braucht, sobald noch Innenabstand dazukommt.
 */
val SideNavBreite = 80.dp

@Composable
private fun SideNavItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = BoulderBuddy.colors.navActive
    val iconColor = if (isSelected) activeColor else BoulderBuddy.colors.textTertiary

    // Der Aktiv-Punkt wandert nach links neben das Symbol. Unter dem Icon wie in der BottomNav
    // säße er hier zwischen zwei Einträgen und ließe sich keinem davon zuordnen.
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Dimens.paddingM, vertical = Dimens.paddingM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) activeColor else Color.Transparent),
        )
        Icon(
            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

// --- Previews ---

@Preview(name = "SideNav – Home aktiv", showBackground = true, backgroundColor = 0xFFFCF6E4, heightDp = 500)
@Composable
private fun SideNavHomePreview() {
    BoulderBuddyTheme {
        SideNav(selectedTab = BottomNavTab.Home, onTabSelect = {})
    }
}

@Preview(name = "SideNav – Statistik aktiv", showBackground = true, backgroundColor = 0xFFFCF6E4, heightDp = 500)
@Composable
private fun SideNavStatsPreview() {
    BoulderBuddyTheme {
        SideNav(selectedTab = BottomNavTab.Stats, onTabSelect = {})
    }
}
