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
     * **Dialoge und Menüs** — und nur deshalb ein fester Wert.
     *
     * Hier stand `RoundedCornerShape(percent = 50)`, gedacht als Pillenform für Chips. Die
     * Rolle gehört aber nicht uns: Material greift für `AlertDialog`, `ModalBottomSheet` und
     * die Picker-Dialoge auf genau diese Stufe zurück. Ein prozentualer Radius nimmt dort die
     * halbe **kürzere Kante des Dialogs** — bei einem Dialog von 300 × 400 dp also 150 dp
     * Eckenradius. Die Ecken fressen sich damit bis in die Textspalte hinein, und das Feld
     * „Name" oder die Zeile „Aus der Galerie" wird an den Rändern abgeschnitten.
     *
     * Der Fehler war nicht der Wert, sondern die **doppelte Belegung**: eine Stufe für zwei
     * Aufgaben, von denen Material eine selbst benutzt. Die Pillenform steht deshalb jetzt
     * separat als [PillShape] — dieselbe Trennung wie damals bei `surfaceInverse`.
     *
     * 28 dp ist Materials eigener Dialog-Radius; er passt zur Reihe darunter (6 · 10 · 14 · 20).
     */
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Pillen: Chips, Filter, Status-Badges. Voll gerundet statt fester Radius — bei einem festen
 * Wert hängt die Form von der Texthöhe ab und Chips unterschiedlicher Zeilenhöhe sehen
 * verschieden aus.
 *
 * Bewusst **außerhalb** von [BoulderBuddyShapes]: die fünf Stufen dort sind Materials Skala,
 * und Material bedient sich daraus für seine eigenen Bausteine. Eine Form, die nur unsere
 * Komponenten meinen, gehört nicht in diese Skala.
 */
val PillShape = RoundedCornerShape(percent = 50)
