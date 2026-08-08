package com.boulderbuddy.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hält den Inhalt frei von Navigationsleiste **und aufgeklappter Tastatur**.
 *
 * Für jeden Screen mit einem Eingabefeld der Ersatz für `navigationBarsPadding()`.
 *
 * **Warum es das braucht:** Die App zeichnet randlos (`enableEdgeToEdge`), und ohne
 * `windowSoftInputMode` schiebt Android dann nur so weit, dass das *fokussierte* Feld sichtbar
 * bleibt. Alles darunter verschwindet hinter der Tastatur — auf „Boulder hinzufügen" lagen nach
 * dem Antippen des Namensfelds Versuche, Status, Notiz und der **Speichern-Knopf** darunter, und
 * auch fünf kräftige Wischer holten ihn nicht hervor: der Scrollbereich war am Ende, weil das
 * Layout von der Tastatur nichts wusste. Man musste sie erst schließen, um speichern zu können,
 * ohne dass irgendetwas darauf hinwies.
 *
 * `union` statt zweier Modifier hintereinander: die Tastatur überdeckt die Navigationsleiste
 * bereits. `navigationBarsPadding().imePadding()` addierte beide Höhen und ließe bei offener
 * Tastatur einen leeren Streifen stehen; `union` nimmt je Seite den größeren Wert.
 */
@Composable
fun Modifier.inhaltsAbstandMitTastatur(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
