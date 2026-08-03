package com.boulderbuddy.ui.theme

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Rechnet jede Farbpaarung nach, die in der App vorkommen kann.
 *
 * **Warum es diesen Test gibt:** Der Dark Mode paarte eine dunkle Füllfläche mit dunklem Text
 * — gemessen 1,42:1. Aus dem Code war das nicht zu sehen, weil beide Farben **einzeln**
 * plausibel aussahen; falsch war nur ihr Verhältnis.
 *
 * **Warum er in dieser Form aussieht:** Die erste Fassung prüfte eine *Liste* von Paarungen —
 * und fand deshalb nur, was jemand aufgeschrieben hatte. Sie übersah, dass Dialoge und Menüs
 * gar nicht auf `surface` liegen, sondern auf `surfaceContainerHigh`; dort landete die dritte
 * Textebene bei 4,45:1. Ein Test, der eine Auswahl prüft, ist so gut wie die Auswahl.
 *
 * Deshalb jetzt **Kreuzprodukt**: jede Textfarbe gegen *alle* Flächen ihres Themes. Neue
 * Flächen und neue Textfarben werden dadurch automatisch mitgeprüft, sobald sie in den
 * Listen unten stehen — und dort zu fehlen ist der einzige verbleibende Weg, etwas zu
 * übersehen.
 */
class PaletteContrastTest {

    /** Alle Flächen, auf denen im Light Mode Text landen kann. */
    private val lightFlaechen = mapOf(
        "Hintergrund" to HEX_LIGHT_BACKGROUND,
        "Card" to HEX_LIGHT_CARD,
        "Chrome" to HEX_LIGHT_CHROME,
        "Container" to HEX_LIGHT_SURFACE_CONTAINER,
        // Die Lochstruktur liegt auf dem Hintergrund, und Text liegt darüber — ein Buchstabe
        // kann also auf einem Punkt landen. Sie gehört deshalb in die Flächenliste, auch
        // wenn sie rein dekorativ gemeint ist.
        "Punkte-Muster" to HEX_LIGHT_PATTERN,
        "Dialog/Menü" to HEX_LIGHT_SURFACE_HIGH,
        "höchste Fläche" to HEX_LIGHT_SURFACE_HIGHEST,
        "weiß" to HEX_LIGHT_SURFACE_LOWEST,
    )

    private val darkFlaechen = mapOf(
        "Hintergrund" to HEX_DARK_BACKGROUND,
        "Card" to HEX_DARK_CARD,
        "Chrome" to HEX_DARK_CHROME,
        "tiefste Fläche" to HEX_DARK_SURFACE_LOWEST,
        "niedrige Fläche" to HEX_DARK_SURFACE_LOW,
        "Punkte-Muster" to HEX_DARK_PATTERN,
        "Dialog/Menü" to HEX_DARK_SURFACE_HIGH,
        "höchste Fläche" to HEX_DARK_SURFACE_HIGHEST,
    )

    /**
     * Alle Textfarben, die auf einer Inhaltsfläche landen können — inklusive des Akzents:
     * er ist die Beschriftung von Textbuttons, und Material setzt ihn als `primary` überall
     * dort ein, wo es selbst zeichnet. Ihn aus dem Kreuzprodukt zu lassen wäre genau die
     * Auslassung, die diesen Test beim letzten Mal blind gemacht hat.
     */
    private val lightTexte = mapOf(
        "Primärtext" to HEX_LIGHT_ON_SURFACE,
        "textSecondary" to HEX_LIGHT_TEXT_SECONDARY,
        "textTertiary" to HEX_LIGHT_TEXT_TERTIARY,
        "Akzent" to HEX_LIGHT_ACCENT_ON_SURFACE,
    )

    private val darkTexte = mapOf(
        "Primärtext" to HEX_DARK_ON_SURFACE,
        "textSecondary" to HEX_DARK_TEXT_SECONDARY,
        "textTertiary" to HEX_DARK_TEXT_TERTIARY,
        "Akzent" to HEX_DARK_ACCENT_ON_SURFACE,
    )

    private fun pruefe(label: String, vg: Long, hg: Long, schwelle: Double) {
        val r = kontrastVerhaeltnis(vg, hg)
        // Truth ersetzt in seinen Meldungen ausschließlich %s — Zahlenformate müssen deshalb
        // vorher aufgelöst werden, sonst wirft die Meldung selbst eine Exception.
        val meldung = "$label — gemessen %.2f:1, nötig %.1f:1".format(r, schwelle)
        assertWithMessage(meldung).that(r).isAtLeast(schwelle)
    }

    private fun kreuzPruefung(
        theme: String,
        texte: Map<String, Long>,
        flaechen: Map<String, Long>,
    ) {
        texte.forEach { (textName, text) ->
            flaechen.forEach { (flaecheName, flaeche) ->
                pruefe("$theme: $textName auf $flaecheName", text, flaeche, TEXT)
            }
        }
    }

    // --- Das Kreuzprodukt: jede Textebene auf jeder Fläche -------------------

    @Test
    fun jedeTextebene_haeltAufJederLightFlaeche() {
        kreuzPruefung("Light", lightTexte, lightFlaechen)
    }

    @Test
    fun jedeTextebene_haeltAufJederDarkFlaeche() {
        kreuzPruefung("Dark", darkTexte, darkFlaechen)
    }

    @Test
    fun rand_haeltAufJederFlaeche() {
        // WCAG 1.4.11: 3:1 für Grenzen, die ein Bedienelement identifizieren. Der Rand
        // trägt hier die Abgrenzung von Karte, Eingabefeld, ungewähltem Chip — und seit dem
        // hellen Chrome auch die Kante zwischen TopBar und Inhalt.
        lightFlaechen.forEach { (name, flaeche) ->
            pruefe("Light: Rand auf $name", HEX_LIGHT_BORDER, flaeche, UI)
        }
        darkFlaechen.forEach { (name, flaeche) ->
            pruefe("Dark: Rand auf $name", HEX_DARK_BORDER, flaeche, UI)
        }
    }

    // --- Gefüllte Elemente ---------------------------------------------------

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
        // Ein lesbarer Button, den man auf dem Screen nicht findet, ist auch keine primäre
        // Aktion. Deshalb dreht fillStrong im Dark Mode auf hell.
        pruefe("Light: fillStrong auf Hintergrund", HEX_LIGHT_FILL_STRONG, HEX_LIGHT_BACKGROUND, UI)
        pruefe("Dark: fillStrong auf Hintergrund", HEX_DARK_FILL_STRONG, HEX_DARK_BACKGROUND, UI)
    }

    // --- Chrome: TopBar und Bottom-Nav ---------------------------------------

    @Test
    fun chrome_inhaltUndBeideNavZustaende() {
        pruefe("Light: onChrome auf Chrome", HEX_LIGHT_ON_CHROME, HEX_LIGHT_CHROME, TEXT)
        pruefe("Dark: onChrome auf Chrome", HEX_DARK_ON_CHROME, HEX_DARK_CHROME, TEXT)

        // Der aktive Nav-Eintrag trägt eine 11-sp-Beschriftung, nicht nur ein Icon — deshalb
        // die Textschwelle und nicht 3:1. Der frühere helle Rosé hätte auf hellem Chrome
        // 2,2:1 erreicht; er war nur deshalb in Ordnung, weil das Chrome schwarz war.
        pruefe("Light: navActive auf Chrome", HEX_LIGHT_ACCENT, HEX_LIGHT_CHROME, TEXT)
        pruefe("Dark: navActive auf Chrome", HEX_DARK_ACCENT, HEX_DARK_CHROME, TEXT)

        // Der inaktive Eintrag ist textTertiary und wird oben im Kreuzprodukt mitgeprüft —
        // er war vorher ein Alpha-Wert auf der Inhaltsfarbe und lag bei 3,94:1.
    }

    @Test
    fun markenakzent_haeltAufDenInhaltsflaechen() {
        // Beschriftung von Textbuttons in Dialogen. Eigener Wert, weil Dialoge nicht auf
        // dem Chrome liegen — derselbe Fehler wie beim Nav-Akzent, nur andersherum.
        pruefe("Light: Akzent auf Dialog", HEX_LIGHT_ACCENT_ON_SURFACE, HEX_LIGHT_SURFACE_HIGH, TEXT)
        pruefe("Light: Akzent auf Card", HEX_LIGHT_ACCENT_ON_SURFACE, HEX_LIGHT_CARD, TEXT)
        pruefe("Dark: Akzent auf Dialog", HEX_DARK_ACCENT_ON_SURFACE, HEX_DARK_SURFACE_HIGH, TEXT)
        pruefe("Dark: Akzent auf Card", HEX_DARK_ACCENT_ON_SURFACE, HEX_DARK_CARD, TEXT)
    }

    // --- Was der Test bewusst NICHT fordert ----------------------------------

    @Test
    fun flaechenstufe_istBewusstFeinUndDarfDieSchwelleReissen() {
        // Card gegen Hintergrund liegt unter 3:1 und soll das auch. Die Stufe ist eine
        // Andeutung von Tiefe, keine Grenze — die Grenze zieht der Rand (Test oben).
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
        val gelbAufCard = kontrastVerhaeltnis(HEX_ROUTE_YELLOW, HEX_LIGHT_CARD)
        assertThat(gelbAufCard).isLessThan(TEXT)
        assertThat(kontrastVerhaeltnis(HEX_ROUTE_YELLOW, HEX_LIGHT_BACKGROUND)).isGreaterThan(1.8)
    }

    @Test
    fun chrome_istImLightHellUndImDarkDunkel() {
        // Der Kern der Light-Mode-Korrektur: das Chrome dreht mit dem Theme. Ursprünglich war
        // es in beiden Themes fast-schwarz, und der Light Mode sah deshalb nicht nach Light
        // Mode aus.
        //
        // Geprüft wird die **Helligkeitsklasse**, nicht die Richtung gegenüber dem Inhalt.
        // Die erste Fassung dieses Tests forderte „Chrome heller als der Hintergrund" — das
        // war eine Annahme über die Gestaltung, keine Anforderung, und sie fiel, als sich
        // zeigte, dass ein Chrome *über* dem Inhalt nicht wie ein Rahmen wirkt, sondern wie
        // eine besonders helle Stelle. Ein Test darf festhalten, was gelten muss, nicht wie
        // man es am Tag des Schreibens gelöst hat.
        assertThat(kontrastVerhaeltnis(HEX_LIGHT_CHROME, 0xFF000000)).isGreaterThan(10.0)
        assertThat(kontrastVerhaeltnis(HEX_DARK_CHROME, 0xFFFFFFFF)).isGreaterThan(10.0)
    }

    @Test
    fun chrome_istVomInhaltUnterscheidbar() {
        // Es muss sich vom Hintergrund abheben — in welche Richtung, ist Gestaltung. Ganz
        // gleich darf es nicht sein, sonst ist die Leiste nur noch eine Linie im Inhalt.
        assertThat(kontrastVerhaeltnis(HEX_LIGHT_CHROME, HEX_LIGHT_BACKGROUND))
            .isGreaterThan(1.05)
        // Und die Kante trägt zusätzlich der Rand — dass er auf dem Chrome 3:1 hält, prüft
        // `rand_haeltAufJederFlaeche`, weil das Chrome in der Flächenliste steht.
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
