package com.boulderbuddy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typografische Skala der App.
 *
 * Vorher standen hier **2 von 15** Stilen; alles andere fiel auf die Material-Defaults zurück.
 * Das ist der Android-Studio-Vorlagenstand, und er ist der Hauptgrund dafür, dass die App
 * trotz eigener Farbwelt nach Vorlage aussah: eine Oberfläche wirkt gestaltet, wenn ihre
 * Textgrößen erkennbar aus **einem** System kommen und nicht aus einer Standardtabelle.
 *
 * Drei Regeln, nach denen die Werte gewählt sind:
 *
 * 1. **Größen kommen aus einer Reihe, nicht aus dem Gefühl.** Basis 14 sp, Schritte grob im
 *    Verhältnis 1,2: 11 · 12 · 13 · 14 · 16 · 18 · 20 · 24 · 28 · 32. Keine Zwischengrößen.
 * 2. **Laufweite wandert mit der Größe.** Große Schrift wirkt bei Standard-Laufweite lose,
 *    deshalb hier negativ (bis −0,5 sp); kleine Schrift braucht Luft, deshalb positiv.
 *    Genau das lässt Überschriften „gesetzt" statt „getippt" aussehen.
 * 3. **Zeilenhöhe als Verhältnis.** ~1,25 für Überschriften (kompakt, sie sind kurz), ~1,45
 *    für Fließtext (lesbar über mehrere Zeilen).
 *
 * Die Schrift bleibt die System-Schrift ([FontFamily.Default]) — bewusst, damit die App keine
 * Font-Datei mitschleppt und auf jedem Gerät sofort scharf rendert. Der Gewinn kommt aus der
 * Skala, nicht aus einem exotischen Schriftschnitt.
 */

private val Schrift = FontFamily.Default

val Typography = Typography(
    // --- Display: die großen Zahlen. Nur der Hangboard-Timer nutzt displaySmall. ------------
    displayLarge = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Light,
        fontSize = 38.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Die Restzeit im Timer. Normal statt Light: sie wird aus Armlänge gelesen, und ein
    // dünner Schnitt verliert auf hellem Grund an Kante.
    displaySmall = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),

    // --- Headline: Kennzahlen in StatCards, Begrüßung auf Home ------------------------------
    headlineLarge = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),

    // --- Title: Screen-Titel, Abschnittsüberschriften, Listenzeilen -------------------------
    titleLarge = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    // Der meistgenutzte Titel-Stil (Karten-Überschriften, EmptyState). Medium statt SemiBold:
    // er steht oft direkt über bodyMedium, und zwei kräftige Gewichte übereinander drücken.
    titleMedium = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // --- Body: Fließtext ---------------------------------------------------------------
    // letterSpacing 0 statt der 0,5 sp aus der Vorlage: ein halber Punkt Sperrung auf 16 sp
    // lässt Absätze auseinanderfallen. Die Vorlagenwerte stammen aus Materials eigener
    // Schrift, nicht aus dieser.
    bodyLarge = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),

    // --- Label: Bedienelemente und kleine Auszeichnungen ------------------------------
    // Button-Beschriftungen. Medium + leichte Sperrung: kurze Wörter auf einer Fläche
    // brauchen Halt, sonst „rutschen" sie in der Mitte zusammen.
    labelLarge = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    ),
    // Der meistgenutzte Stil überhaupt (Chips, Badges, Listenwerte).
    labelMedium = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    // Uppercase-Abschnittslabels („HALLE / ORT", „GRADING-SYSTEM"). Die starke Sperrung
    // gehört ins Theme, damit sie nicht in jeder Komponente einzeln hartkodiert wird —
    // Versalien ohne Sperrung wirken gedrängt.
    labelSmall = TextStyle(
        fontFamily = Schrift,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
    ),
)
