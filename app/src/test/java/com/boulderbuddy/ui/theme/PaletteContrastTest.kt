package com.boulderbuddy.ui.theme

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Rechnet jede Farbpaarung nach, die in der App tatsächlich vorkommt.
 *
 * **Warum es diesen Test gibt:** Der Dark Mode paarte eine dunkle Füllfläche mit dunklem Text
 * — gemessen 1,42:1, praktisch unsichtbar. Aufgefallen ist das erst, als ein Nutzer sagte
 * „im Dark Mode kann man gar nichts lesen". Aus dem Code war es nicht zu sehen, weil beide
 * Farben **einzeln** plausibel aussahen; falsch war nur ihr Verhältnis. Genau das prüft ein
 * Blick auf eine Farbliste nie und ein Test immer.
 *
 * Die Werte kommen aus `PaletteHex.kt` — derselben Quelle, aus der `Color.kt` seine Farben
 * baut. Der Test kann also nicht gegen eine veraltete Kopie laufen.
 *
 * Konvention der Testnamen: `<was>_<auf welchem Grund>_<Schwelle>`.
 */
class PaletteContrastTest {

    /**
     * Die Fehlermeldung nennt den gemessenen Wert. Ohne ihn stünde beim Fehlschlag nur
     * „expected at least 4.5" da, und man müsste selbst nachrechnen, wie weit es daneben lag.
     */
    private fun pruefe(label: String, vg: Long, hg: Long, schwelle: Double) {
        val r = kontrastVerhaeltnis(vg, hg)
        // Truth ersetzt in seinen Meldungen ausschließlich %s — Zahlenformate müssen deshalb
        // vorher aufgelöst werden, sonst wirft die Meldung selbst eine Exception.
        val meldung = "$label — gemessen %.2f:1, nötig %.1f:1".format(r, schwelle)
        assertWithMessage(meldung).that(r).isAtLeast(schwelle)
    }

    // --- Der behobene Fehler -------------------------------------------------

    @Test
    fun primaerbutton_beschriftungAufFuellung_beideThemes() {
        // Genau diese Paarung stand im Dark Mode bei 1,42:1. Sie deckt zugleich den
        // gewählten SelectableChip, den primären QuickActionButton und die TimerControls ab —
        // alle vier benutzen dasselbe Token-Paar.
        pruefe(
            "Light: onFillStrong auf fillStrong",
            HEX_LIGHT_ON_FILL_STRONG, HEX_LIGHT_FILL_STRONG, TEXT,
        )
        pruefe(
            "Dark: onFillStrong auf fillStrong",
            HEX_DARK_ON_FILL_STRONG, HEX_DARK_FILL_STRONG, TEXT,
        )
    }

    @Test
    fun primaerbutton_hebtSichVomHintergrundAb() {
        // Der zweite Teil desselben Problems: ein lesbarer Button, den man auf dem Screen
        // nicht findet, ist auch keine primäre Aktion. Deshalb dreht fillStrong im Dark Mode
        // auf hell statt nur minimal aufgehellt zu werden.
        pruefe("Light: fillStrong auf Hintergrund", HEX_LIGHT_FILL_STRONG, HEX_LIGHT_BACKGROUND, UI)
        pruefe("Dark: fillStrong auf Hintergrund", HEX_DARK_FILL_STRONG, HEX_DARK_BACKGROUND, UI)
    }

    // --- Text auf den drei Flächen -------------------------------------------

    @Test
    fun primaertext_aufAllenFlaechen() {
        pruefe("Light: onSurface auf Hintergrund", HEX_LIGHT_ON_SURFACE, HEX_LIGHT_BACKGROUND, TEXT)
        pruefe("Light: onSurface auf Card", HEX_LIGHT_ON_SURFACE, HEX_LIGHT_CARD, TEXT)
        pruefe("Dark: onSurface auf Hintergrund", HEX_DARK_ON_SURFACE, HEX_DARK_BACKGROUND, TEXT)
        pruefe("Dark: onSurface auf Card", HEX_DARK_ON_SURFACE, HEX_DARK_CARD, TEXT)
    }

    @Test
    fun textSecondary_aufAllenFlaechen() {
        pruefe("Light: textSecondary auf Hintergrund", HEX_LIGHT_TEXT_SECONDARY, HEX_LIGHT_BACKGROUND, TEXT)
        pruefe("Light: textSecondary auf Card", HEX_LIGHT_TEXT_SECONDARY, HEX_LIGHT_CARD, TEXT)
        pruefe("Dark: textSecondary auf Hintergrund", HEX_DARK_TEXT_SECONDARY, HEX_DARK_BACKGROUND, TEXT)
        pruefe("Dark: textSecondary auf Card", HEX_DARK_TEXT_SECONDARY, HEX_DARK_CARD, TEXT)
    }

    @Test
    fun textTertiary_aufAllenFlaechen() {
        // Die dritte Ebene lag im Light Mode bei 2,93:1 und riss damit sogar die
        // 3:1-Schwelle. An ihr hängen alle Uppercase-Labels, Platzhalter und EmptyState-Texte
        // — sie ist kein Randfall, sondern ein großer Teil der sichtbaren Schrift.
        pruefe("Light: textTertiary auf Hintergrund", HEX_LIGHT_TEXT_TERTIARY, HEX_LIGHT_BACKGROUND, TEXT)
        pruefe("Light: textTertiary auf Card", HEX_LIGHT_TEXT_TERTIARY, HEX_LIGHT_CARD, TEXT)
        pruefe("Dark: textTertiary auf Hintergrund", HEX_DARK_TEXT_TERTIARY, HEX_DARK_BACKGROUND, TEXT)
        pruefe("Dark: textTertiary auf Card", HEX_DARK_TEXT_TERTIARY, HEX_DARK_CARD, TEXT)
    }

    // --- Chrome: TopBar und Bottom-Nav ---------------------------------------

    @Test
    fun chrome_inhaltUndAkzent() {
        pruefe("Light: onChrome auf Chrome", HEX_LIGHT_ON_CHROME, HEX_LIGHT_CHROME, TEXT)
        pruefe("Dark: onChrome auf Chrome", HEX_DARK_ON_CHROME, HEX_DARK_CHROME, TEXT)
        // Der aktive Nav-Eintrag ist ein Bedienelement-Zustand, kein Fließtext → 3:1.
        pruefe("Light: navActive auf Chrome", HEX_NAV_ACTIVE, HEX_LIGHT_CHROME, UI)
        pruefe("Dark: navActive auf Chrome", HEX_NAV_ACTIVE, HEX_DARK_CHROME, UI)
    }

    // --- Ränder --------------------------------------------------------------

    @Test
    fun rand_grenztKarteUndFeldAufBeidenFlaechenAb() {
        // Der Rand trägt die Abgrenzung allein: die Flächenstufe Card-zu-Hintergrund liegt
        // bei rund 1,16:1 und ist als alleinige Grenze zu fein. WCAG 1.4.11 verlangt 3:1 für
        // Grenzen, die ein Bedienelement identifizieren — und Eingabefeld wie Chip sind das.
        pruefe("Light: Rand auf Card", HEX_LIGHT_BORDER, HEX_LIGHT_CARD, UI)
        pruefe("Light: Rand auf Hintergrund", HEX_LIGHT_BORDER, HEX_LIGHT_BACKGROUND, UI)
        pruefe("Dark: Rand auf Card", HEX_DARK_BORDER, HEX_DARK_CARD, UI)
        pruefe("Dark: Rand auf Hintergrund", HEX_DARK_BORDER, HEX_DARK_BACKGROUND, UI)
    }

    // --- Was der Test bewusst NICHT fordert ----------------------------------

    @Test
    fun flaechenstufe_istBewusstFeinUndDarfDieSchwelleReissen() {
        // Card gegen Hintergrund liegt unter 3:1 und soll das auch. Die Stufe ist eine
        // Andeutung von Tiefe, keine Grenze — die Grenze zieht der Rand (Test darüber).
        // Festgehalten, damit niemand die Zahl später „repariert" und das Design verhärtet.
        val light = kontrastVerhaeltnis(HEX_LIGHT_CARD, HEX_LIGHT_BACKGROUND)
        val dark = kontrastVerhaeltnis(HEX_DARK_CARD, HEX_DARK_BACKGROUND)
        assertThat(light).isGreaterThan(1.05)
        assertThat(light).isLessThan(1.5)
        assertThat(dark).isGreaterThan(1.05)
        assertThat(dark).isLessThan(1.5)
    }

    @Test
    fun routeAkzente_sindFlaechenfarbenUndKeineTextfarben() {
        // Gelb erreicht auf der Card nur rund 2,3:1. Das ist in Ordnung, solange die
        // Route-Farben als Rand oder Punkt auftreten — als Textfarbe wären sie unlesbar.
        // Der Test hält fest, warum die Farbe trotz schwachem Kontrast bleiben darf.
        val gelbAufCard = kontrastVerhaeltnis(HEX_ROUTE_YELLOW, HEX_LIGHT_CARD)
        assertThat(gelbAufCard).isLessThan(TEXT)
        // Als Fläche gegen den Hintergrund ist sie dagegen klar zu erkennen.
        assertThat(kontrastVerhaeltnis(HEX_ROUTE_YELLOW, HEX_LIGHT_BACKGROUND)).isGreaterThan(1.8)
    }

    // --- Die Formel selbst ---------------------------------------------------

    @Test
    fun formel_liefertDieBekanntenEckwerte() {
        // Schwarz gegen Weiß ist per Definition 21:1, eine Farbe gegen sich selbst 1:1.
        assertThat(kontrastVerhaeltnis(0xFF000000, 0xFFFFFFFF)).isWithin(0.01).of(21.0)
        assertThat(kontrastVerhaeltnis(0xFF3C7A2B, 0xFF3C7A2B)).isWithin(0.001).of(1.0)
        // Reihenfolge darf keine Rolle spielen.
        assertThat(kontrastVerhaeltnis(0xFF123456, 0xFFEEDDCC))
            .isWithin(0.001).of(kontrastVerhaeltnis(0xFFEEDDCC, 0xFF123456))
    }
}
