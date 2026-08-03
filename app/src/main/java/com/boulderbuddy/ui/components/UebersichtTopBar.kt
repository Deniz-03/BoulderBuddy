package com.boulderbuddy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

// Eigenständige TopBar-Variante für die beiden Übersicht-Screens (Sessions / Boulder).
// Statt eines festen Titels trägt sie einen Dropdown ("Sessions ▾" / "Boulder ▾"), über
// den man zur jeweils anderen Ansicht umschaltet. Bewusst getrennt von der normalen
// TopBar, damit deren Signatur unverändert bleibt. Aufbau (dunkle Leiste, StatusBar-
// Padding, Actions rechts) spiegelt die normale TopBar.
@Composable
fun UebersichtTopBar(
    current: String,
    onSelectSessions: () -> Unit,
    onSelectBoulder: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    // Trennlinie nach unten, wie bei TopBar: das Chrome dreht mit dem Theme und ist im
    // Light Mode nahezu so hell wie der Inhalt.
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
            // Klickbarer Dropdown-Titel links (nimmt die Restbreite ein).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.paddingS),
            ) {
                Row(
                    modifier = Modifier.clickable { expanded = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
                ) {
                    Text(
                        text = current,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = BoulderBuddy.colors.onChrome,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Ansicht wechseln",
                        tint = BoulderBuddy.colors.onChrome,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Sessions") },
                        onClick = {
                            expanded = false
                            onSelectSessions()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Boulder") },
                        onClick = {
                            expanded = false
                            onSelectBoulder()
                        },
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

@Preview(showBackground = true)
@Composable
private fun UebersichtTopBarPreview() {
    BoulderBuddyTheme {
        UebersichtTopBar(
            current = "Sessions",
            onSelectSessions = {},
            onSelectBoulder = {},
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
