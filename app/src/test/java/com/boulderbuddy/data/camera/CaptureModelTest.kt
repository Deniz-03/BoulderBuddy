package com.boulderbuddy.data.camera

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Die Regeln des Aufnahme-Screens als JVM-Test. Alles, was hier geprüft wird, wäre sonst nur
 * am Gerät mit laufender Kamera sichtbar — und dort schlecht reproduzierbar (Dateiendungen,
 * Zeitanzeige, Obergrenze).
 */
class CaptureModelTest {

    // --- Auftrag → Startmodus / Umschaltbarkeit ------------------------------

    @Test
    fun ghostAuftrag_startetImVideoModusUndSperrtDenWechsel() {
        // Der Ghost Climber vergleicht zwei Videos; ein Foto wäre dort nicht verwertbar.
        assertThat(startModusFuer(CaptureAuftrag.NUR_VIDEO)).isEqualTo(CaptureModus.VIDEO)
        assertThat(darfModusWechseln(CaptureAuftrag.NUR_VIDEO)).isFalse()
    }

    @Test
    fun boulderAuftrag_startetBeimFotoUndDarfWechseln() {
        // Das Boulder-Formular nimmt beides; der häufigere Fall ist das Foto.
        assertThat(startModusFuer(CaptureAuftrag.FOTO_UND_VIDEO)).isEqualTo(CaptureModus.FOTO)
        assertThat(darfModusWechseln(CaptureAuftrag.FOTO_UND_VIDEO)).isTrue()
    }

    @Test
    fun nurFoto_startetBeimFotoUndSperrtDenWechsel() {
        assertThat(startModusFuer(CaptureAuftrag.NUR_FOTO)).isEqualTo(CaptureModus.FOTO)
        assertThat(darfModusWechseln(CaptureAuftrag.NUR_FOTO)).isFalse()
    }

    // --- Dateiname ----------------------------------------------------------

    @Test
    fun dateiendung_entscheidetUeberDenSpaeterErkanntenMedientyp() {
        // Der FileProvider leitet den MIME-Typ aus der Endung ab, und mediaTypeOf() liest ihn
        // wieder aus — eine falsche Endung ließe ein Video später als Bild erscheinen.
        assertThat(aufnahmeDateiname(CaptureModus.FOTO, 1_000L)).endsWith(".jpg")
        assertThat(aufnahmeDateiname(CaptureModus.VIDEO, 1_000L)).endsWith(".mp4")
    }

    @Test
    fun dateinamen_verschiedenerZeitpunkte_kollidierenNicht() {
        val erste = aufnahmeDateiname(CaptureModus.FOTO, 1_000L)
        val zweite = aufnahmeDateiname(CaptureModus.FOTO, 1_001L)
        assertThat(erste).isNotEqualTo(zweite)
    }

    @Test
    fun dateiname_enthaeltKeineZeichenDieEinenPfadZerlegen() {
        // Der Name landet unverändert im Dateisystem; ein Doppelpunkt oder Schrägstrich aus
        // einer formatierten Zeit würde ihn zerlegen.
        val name = aufnahmeDateiname(CaptureModus.VIDEO, 1_754_000_000_000L)
        assertThat(name).doesNotContain("/")
        assertThat(name).doesNotContain(":")
        assertThat(name).doesNotContain(" ")
    }

    // --- Obergrenze ---------------------------------------------------------

    @Test
    fun aufnahme_stopptErstAbDerObergrenze() {
        assertThat(mussAutomatischStoppen(0L)).isFalse()
        assertThat(mussAutomatischStoppen(MAX_VIDEO_DAUER_MS - 1)).isFalse()
        assertThat(mussAutomatischStoppen(MAX_VIDEO_DAUER_MS)).isTrue()
        assertThat(mussAutomatischStoppen(MAX_VIDEO_DAUER_MS + 5_000)).isTrue()
    }

    // --- Laufzeit-Anzeige ---------------------------------------------------

    @Test
    fun laufzeit_zaehltVonAnfangAnInDerselbenForm() {
        // Bewusst anders als formatHangTime: eine mitlaufende Uhr, die von "59s" auf "1:00min"
        // springt, sieht aus wie ein Fehler.
        assertThat(formatAufnahmedauer(0L)).isEqualTo("0:00")
        assertThat(formatAufnahmedauer(5_000L)).isEqualTo("0:05")
        assertThat(formatAufnahmedauer(59_000L)).isEqualTo("0:59")
        assertThat(formatAufnahmedauer(60_000L)).isEqualTo("1:00")
        assertThat(formatAufnahmedauer(75_000L)).isEqualTo("1:15")
    }

    @Test
    fun laufzeit_schneidetAngefangeneSekundenAbStattZuRunden() {
        // Eine Uhr, die bei 4,9 s schon "0:05" zeigt, läuft der Aufnahme voraus.
        assertThat(formatAufnahmedauer(4_999L)).isEqualTo("0:04")
    }

    @Test
    fun laufzeit_negativeWerteWerdenGeklemmt() {
        // Kommt nicht vor, wäre aber ein "-1:-1" in der Anzeige.
        assertThat(formatAufnahmedauer(-5_000L)).isEqualTo("0:00")
    }

    @Test
    fun laufzeit_ueberEinerStunde_bleibtLesbar() {
        // Die Obergrenze verhindert das im Betrieb; die Formatierung darf trotzdem nicht
        // bei 60 Minuten auf "0:00" zurückspringen.
        assertThat(formatAufnahmedauer(3_600_000L)).isEqualTo("60:00")
    }
}
