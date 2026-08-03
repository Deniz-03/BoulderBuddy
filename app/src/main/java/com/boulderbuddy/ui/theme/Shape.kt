package com.boulderbuddy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rundungsstufen. Vorher waren `medium` und `large` **beide 14 dp** — die Skala hatte damit
 * eine Dublette und nur drei statt fünf wirksame Stufen. Eine Komponente, die bewusst „eine
 * Stufe runder" sein wollte, sah aus wie ihre Nachbarin.
 *
 * Jetzt eine echte Reihe: 6 · 10 · 14 · 20 · voll. Die Zuordnung folgt der Fläche —
 * je größer das Element, desto größer der Radius, sonst wirkt die Rundung an großen Karten
 * geizig und an kleinen Chips klobig.
 */
val BoulderBuddyShapes = Shapes(
    /** Kleinteiliges: Badges, Kennzeichnungen, Eck-Marker. */
    extraSmall = RoundedCornerShape(6.dp),

    /** Stat-Cards und kompakte Listenzeilen. */
    small = RoundedCornerShape(10.dp),

    /** Der Standardfall: Route-Cards, Eingabefelder, Buttons, Dialoge. */
    medium = RoundedCornerShape(14.dp),

    /** Große Flächen: Featured-Card auf Home, Bottom-Sheets, Medien-Slots. */
    large = RoundedCornerShape(20.dp),

    /**
     * Pillen: Chips, Filter, der Modus-Umschalter der Kamera. Voll gerundet statt fester
     * Radius — bei einem festen Wert hängt die Form von der Texthöhe ab und Chips
     * unterschiedlicher Zeilenhöhe sehen verschieden aus.
     */
    extraLarge = RoundedCornerShape(percent = 50),
)
