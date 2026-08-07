package com.boulderbuddy.widget

import com.boulderbuddy.ui.theme.HEX_DARK_BACKGROUND
import com.boulderbuddy.ui.theme.HEX_DARK_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_DARK_ON_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_DARK_ON_SURFACE
import com.boulderbuddy.ui.theme.HEX_DARK_SURFACE_HIGHEST
import com.boulderbuddy.ui.theme.HEX_DARK_TEXT_SECONDARY
import com.boulderbuddy.ui.theme.HEX_LIGHT_BACKGROUND
import com.boulderbuddy.ui.theme.HEX_LIGHT_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_LIGHT_ON_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_LIGHT_ON_SURFACE
import com.boulderbuddy.ui.theme.HEX_LIGHT_SURFACE_HIGHEST
import com.boulderbuddy.ui.theme.HEX_LIGHT_TEXT_SECONDARY
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Hält die Widget-Farbressourcen und die Compose-Palette zusammen.
 *
 * **Warum es diesen Test gibt.** Die Widget-Farben standen jahrelang als handgeschriebene
 * Werte im Kotlin-Code, „an Color.kt angelehnt". Über fünf Design-Runden hinweg ist die
 * Palette gewandert, das Widget nicht: der Hintergrund lag am Ende bei #F3ECD6, während die
 * App #FCF6E4 zeichnete, und der Akzent war ein Rosé, das es in der Palette nicht mehr gab.
 * Auf dem Homescreen sah das Widget aus wie eine ältere Version der App.
 *
 * Die Werte können nicht in eine gemeinsame Quelle zusammengezogen werden — ein Widget
 * liefert RemoteViews, die der Launcher auflöst, und dafür braucht es echte Farbressourcen
 * (siehe `BoulderWidget`). Wenn zwei Quellen unvermeidbar sind, muss ein Test sie verbinden;
 * genau wie `PaletteContrastTest` gegen die echten Hex-Konstanten rechnet statt gegen eine
 * Kopie davon.
 */
class WidgetPaletteTest {

    // Zuordnung Ressourcenname → erwarteter Wert je Theme. Wer hier eine Farbe ergänzt,
    // muss sie in beiden XML-Dateien anlegen — sonst schlägt der Vollständigkeits-Test zu.
    private val erwartet: Map<String, Pair<Long, Long>> = mapOf(
        "widget_bg" to (HEX_LIGHT_BACKGROUND to HEX_DARK_BACKGROUND),
        "widget_ink" to (HEX_LIGHT_ON_SURFACE to HEX_DARK_ON_SURFACE),
        "widget_secondary" to (HEX_LIGHT_TEXT_SECONDARY to HEX_DARK_TEXT_SECONDARY),
        "widget_fill" to (HEX_LIGHT_FILL_STRONG to HEX_DARK_FILL_STRONG),
        "widget_on_fill" to (HEX_LIGHT_ON_FILL_STRONG to HEX_DARK_ON_FILL_STRONG),
        "widget_fill_muted" to (HEX_LIGHT_SURFACE_HIGHEST to HEX_DARK_SURFACE_HIGHEST),
    )

    @Test
    fun `die hellen Widget-Farben sind die der Light-Palette`() {
        val farben = leseFarben("values")
        erwartet.forEach { (name, werte) ->
            assertThat(farben[name]).isEqualTo(werte.first)
        }
    }

    @Test
    fun `die dunklen Widget-Farben sind die der Dark-Palette`() {
        val farben = leseFarben("values-night")
        erwartet.forEach { (name, werte) ->
            assertThat(farben[name]).isEqualTo(werte.second)
        }
    }

    @Test
    fun `jede Widget-Farbe existiert in beiden Themes`() {
        // Fehlt ein Name in values-night, fällt das Widget für DIESE eine Farbe auf den
        // hellen Wert zurück — ein halb umgeschaltetes Widget statt eines offensichtlichen
        // Fehlers. Deshalb die Vollständigkeit getrennt prüfen.
        val hell = leseFarben("values").keys.filter { it.startsWith("widget_") }
        val dunkel = leseFarben("values-night").keys.filter { it.startsWith("widget_") }

        assertThat(hell).containsExactlyElementsIn(erwartet.keys)
        assertThat(dunkel).containsExactlyElementsIn(erwartet.keys)
    }

    /**
     * Liest `colors.xml` des angegebenen Ressourcen-Ordners als Name → 0xAARRGGBB.
     *
     * Bewusst über den Dateipfad und nicht über `R.color` oder Robolectric: geprüft werden
     * soll, was in der Datei STEHT. Über die Ressourcen-API gelesen käme derselbe Wert wieder
     * heraus, den der Test gerade bestätigen soll.
     */
    private fun leseFarben(ordner: String): Map<String, Long> {
        val datei = File("src/main/res/$ordner/colors.xml")
        assertThat(datei.exists()).isTrue()

        val muster = Regex("""<color\s+name="([^"]+)"\s*>\s*#([0-9A-Fa-f]{6,8})\s*</color>""")
        return muster.findAll(datei.readText())
            .associate { treffer ->
                val (name, hex) = treffer.destructured
                // Sechsstellige Angaben sind voll deckend — auf 0xFF ergänzen, damit sie mit
                // den Konstanten vergleichbar sind.
                name to (if (hex.length == 6) "FF$hex" else hex).lowercase().toLong(16)
            }
    }
}
