package com.boulderbuddy.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val paddingXS  = 4.dp
    val paddingS   = 8.dp
    val paddingM   = 12.dp
    val paddingL   = 16.dp
    val paddingXL  = 24.dp

    val borderAccent = 2.dp    // farbige Route-Card-Ränder
    val borderSubtle = 0.5.dp  // dezente Chip/Card-Ränder

    // --- Obergrenzen für Inhaltsbreite -------------------------------------------------
    //
    // Bis hierher kannte das Layout nur `fillMaxWidth()` und `weight(1f)` — beides wächst
    // unbegrenzt mit dem Fenster. Auf dem Tablet (1280 dp quer) ergab das Textzeilen von über
    // 150 Zeichen, Formularfelder von 1248 dp und Einstellungs-Zeilen, deren Wert 1240 dp
    // vom Label entfernt stand. Nicht einzelne Fehler, sondern eine fehlende Obergrenze.
    //
    // Zwei Stufen, weil zwei verschiedene Dinge begrenzt werden:

    /**
     * Zusammenhängend gelesener Text und Formulare: Notizfelder, Einstellungen, Dialoge.
     * 600 dp entsprechen bei unserer Textgröße etwa 70 Zeichen je Zeile — die obere Grenze
     * dessen, was das Auge beim Zeilenwechsel noch sicher trifft.
     */
    val spaltenBreiteText = 600.dp

    /**
     * Flächige Ansichten, die mehrspaltig aufgehen: Dashboards, Karten-Raster. Sie dürfen
     * breiter werden als eine Textspalte, aber nicht beliebig — jenseits davon zerfällt der
     * Zusammenhang zwischen linkem und rechtem Bildrand.
     */
    val spaltenBreiteWeit = 1040.dp

    /** Mindestbreite einer Karte im adaptiven Raster; darunter wird eine Spalte weniger gelegt. */
    val rasterSpalteMin = 280.dp

    /** Obergrenze für quadratische Kacheln (Schnellaktionen). Siehe QuickActionButton. */
    val kachelMaxHoehe = 160.dp

    /**
     * Obergrenze für eine Bild-/Videovorschau.
     *
     * Vorschauen sind über `aspectRatio` gebaut, ihre Höhe folgt also der Breite. Ohne Deckel
     * wurde aus einem 16:9-Rahmen im Detail-Pane des Tablets eine Fläche von 920 × 517 dp —
     * bei einem noch nicht gesetzten Foto ein riesiger leerer Rahmen mit einem kleinen
     * Platzhalter-Icon in der Mitte.
     */
    val medienMaxBreite = 640.dp

    /**
     * Mindesthöhe des Inhaltsbereichs einer TopBar (ohne Statusleiste).
     *
     * Vorher hatte jede Leiste die Höhe ihres Inhalts: eine mit Aktions-Icon war 72 dp hoch
     * (48 dp Button + 2 × 12 dp), eine ohne nur ~52 dp. Am Telefon fällt das nicht auf, weil
     * immer nur eine Leiste sichtbar ist. Im Zwei-Pane-Layout des Tablets stehen zwei
     * nebeneinander — und dort wurde aus dem Unterschied eine sichtbare Stufe im Chrome-Band.
     *
     * 64 dp ist Materials Höhe für die einzeilige App-Leiste. Leisten mit Untertitel wachsen
     * darüber hinaus; die stehen nie neben einer anderen.
     */
    val topBarHoehe = 64.dp

    val dotRadius  = 1.3.dp    // Lochstruktur Punktgröße
    val dotSpacing = 24.dp     // Lochstruktur Rasterabstand

    val swatchSize = 36.dp     // Durchmesser einer Farb-Auswahl im ColorPicker
    val iconS      = 18.dp     // Inline-/Button-Icon (PrimaryButton)
    val iconL      = 28.dp     // Icon in größeren Slots (AddRouteCard, PhotoPicker)

    // Gestrichelter Platzhalter-Rahmen (AddRouteCard, PhotoPicker).
    // radiusMedium spiegelt shapes.medium (14dp) — DrawScope hat keinen Zugriff
    // auf MaterialTheme, daher hier zentral als Token statt pro Komponente hartkodiert.
    val radiusMedium = 14.dp
    val dashLength   = 10.dp   // Strichlänge
    val dashGap      = 6.dp    // Lücke zwischen den Strichen
}

