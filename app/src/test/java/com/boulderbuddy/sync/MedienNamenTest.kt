package com.boulderbuddy.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Namensregeln der inhaltsadressierten Medien (Sync-Plan E5, Ablauf 18).
 *
 * Der Umzug läuft auf beiden Geräten getrennt. Dass beidseitig dasselbe herauskommt, hängt
 * allein an diesen Regeln — deshalb stehen sie android-frei und werden hier geprüft.
 */
class MedienNamenTest {

    private val hash = "a".repeat(64)

    @Test
    fun der_name_ist_der_hash_und_sonst_nichts() {
        // Kein Zeitstempel, kein Zähler, keine Reihenfolge — sonst ergäbe der Umzug auf
        // zwei Geräten zwei Namen für dieselbe Datei.
        assertThat(medienDateiname(hash, "mp4")).isEqualTo("$hash.mp4")
    }

    @Test
    fun ein_umgezogenes_medium_wird_wiedererkannt() {
        // Das macht den Umzug wiederholbar: was schon so heißt, wird übersprungen.
        assertThat(istInhaltsadressiert("$hash.mp4")).isTrue()
        assertThat(istInhaltsadressiert("$hash.jpg")).isTrue()
    }

    @Test
    fun alte_aufnahmenamen_gelten_nicht_als_umgezogen() {
        assertThat(istInhaltsadressiert("BB_1720000000000.mp4")).isFalse()
        assertThat(istInhaltsadressiert("$hash")).isFalse()
        // Zu kurz, Großbuchstaben, unbekannte Endung: alles kein Inhaltsname.
        assertThat(istInhaltsadressiert("abc.mp4")).isFalse()
        assertThat(istInhaltsadressiert("${"A".repeat(64)}.mp4")).isFalse()
        assertThat(istInhaltsadressiert("$hash.txt")).isFalse()
    }

    @Test
    fun der_mime_typ_entscheidet_vor_dem_dateinamen() {
        // Eine Galerie-URI hat oft gar keinen Namen; der MIME-Typ ist die verlässlichere Quelle.
        assertThat(endungFuer("video/mp4", null)).isEqualTo("mp4")
        assertThat(endungFuer("image/jpeg", "irgendwas.png")).isEqualTo("jpg")
        assertThat(endungFuer("video/mp4; codecs=avc1", null)).isEqualTo("mp4")
    }

    @Test
    fun jpeg_und_jpg_ergeben_denselben_namen() {
        // Sonst trüge dieselbe Datei auf zwei Geräten zwei Namen — Dauerkonflikt bei jedem
        // Abgleich, genau wie bei den absoluten Keypoint-Pfaden.
        assertThat(endungFuer(null, "foto.jpeg")).isEqualTo("jpg")
        assertThat(endungFuer(null, "foto.jpg")).isEqualTo("jpg")
        assertThat(endungFuer("image/jpeg", null)).isEqualTo("jpg")
        assertThat(endungFuer("image/jpg", null)).isEqualTo("jpg")
    }

    @Test
    fun ohne_brauchbare_auskunft_reist_die_datei_trotzdem_mit() {
        // Lieber ein Medium ohne erkennbaren Typ als ein Medium, das beim Abgleich fehlt.
        assertThat(endungFuer(null, null)).isEqualTo("bin")
        assertThat(endungFuer("application/octet-stream", "datei.xyz")).isEqualTo("bin")
    }

    @Test
    fun die_endung_ist_unabhaengig_von_der_schreibweise() {
        assertThat(endungFuer("VIDEO/MP4", null)).isEqualTo("mp4")
        assertThat(endungFuer(null, "AUFNAHME.MP4")).isEqualTo("mp4")
    }
}
