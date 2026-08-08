package com.boulderbuddy.ui.theme

import kotlin.math.pow

/**
 * WCAG-Kontrastverhältnis zweier Farben — Test-Werkzeug, bewusst **nicht** im Produktivcode:
 * die App berechnet zur Laufzeit keine Kontraste, sie benutzt geprüfte Werte.
 *
 * Formel aus WCAG 2.1: relative Leuchtdichte nach sRGB-Linearisierung, Verhältnis
 * `(hell + 0,05) / (dunkel + 0,05)`. Ergebnis liegt zwischen 1 (identisch) und 21
 * (Schwarz gegen Weiß).
 */
fun kontrastVerhaeltnis(argbA: Long, argbB: Long): Double {
    val a = relativeLeuchtdichte(argbA)
    val b = relativeLeuchtdichte(argbB)
    val hell = maxOf(a, b)
    val dunkel = minOf(a, b)
    return (hell + 0.05) / (dunkel + 0.05)
}

private fun relativeLeuchtdichte(argb: Long): Double {
    // Alpha wird verworfen: die Palette enthält ausschließlich deckende Farben.
    val r = kanal((argb shr 16 and 0xFF).toInt())
    val g = kanal((argb shr 8 and 0xFF).toInt())
    val b = kanal((argb and 0xFF).toInt())
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** Linearisiert einen sRGB-Kanal (Gamma-Rücknahme). */
private fun kanal(wert: Int): Double {
    val c = wert / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/**
 * Schwellen aus WCAG 2.1.
 *
 * [TEXT] gilt für normalen Text — in dieser App praktisch jeder Text, weil selbst der größte
 * Stil (32 sp Timer) zwar über der Grenze für „großen Text" läge, aber die Labels bei 11 sp
 * den Ausschlag geben. Die Palette hält die strengere Schwelle durchgehend, damit keine
 * Größenprüfung pro Fundstelle nötig ist.
 *
 * [UI] gilt für Grenzen und Zustände von Bedienelementen (WCAG 1.4.11) — bei uns der Rand,
 * der Karte, Feld und ungewählten Chip abgrenzt.
 */
const val TEXT = 4.5
const val UI = 3.0
