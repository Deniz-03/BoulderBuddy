package com.boulderbuddy.data.speech

import android.speech.SpeechRecognizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Die Entscheidungslogik der Spracheingabe (Weg A) als JVM-Test.
 *
 * Der Punkt dieser Tests ist die **Fallback-Kette**: welcher Erkennungsweg greift, ist sonst nur
 * auf dem jeweils vorliegenden Gerät sichtbar — ein Pixel mit On-Device-Modell würde nie zeigen,
 * was auf einem Gerät ohne Erkenner passiert.
 *
 * `SpeechRecognizer.ERROR_*` sind `static final int`-Konstanten, die der Compiler einsetzt;
 * die Klasse selbst wird zur Testlaufzeit deshalb nicht geladen (kein Robolectric nötig).
 */
class SpeechRecognitionModelTest {

    // --- Fallback-Kette ------------------------------------------------------

    @Test
    fun onDeviceModell_wirdBevorzugt() {
        val mode = pickRecognitionMode(
            sdkInt = 34,
            onDeviceAvailable = true,
            serviceAvailable = true,
        )
        assertThat(mode).isEqualTo(RecognitionMode.ON_DEVICE)
    }

    @Test
    fun unterAndroid12_gibtEsKeinenOnDeviceWeg() {
        // minSdk der App ist 26; createOnDeviceSpeechRecognizer existiert erst ab 31. Selbst
        // wenn der Aufrufer hier true meldet, darf der On-Device-Weg nicht gewählt werden.
        val mode = pickRecognitionMode(
            sdkInt = 30,
            onDeviceAvailable = true,
            serviceAvailable = true,
        )
        assertThat(mode).isEqualTo(RecognitionMode.SERVICE)
    }

    @Test
    fun ohneOnDeviceModell_uebernimmtDerErkennungsdienst() {
        val mode = pickRecognitionMode(
            sdkInt = 34,
            onDeviceAvailable = false,
            serviceAvailable = true,
        )
        assertThat(mode).isEqualTo(RecognitionMode.SERVICE)
    }

    @Test
    fun ohneJedenErkenner_bleibtNurDerSystemDialog() {
        val mode = pickRecognitionMode(
            sdkInt = 34,
            onDeviceAvailable = false,
            serviceAvailable = false,
        )
        assertThat(mode).isEqualTo(RecognitionMode.INTENT_FALLBACK)
    }

    @Test
    fun genauAbApi31_greiftDerOnDeviceWeg() {
        assertThat(pickRecognitionMode(ON_DEVICE_MIN_SDK, true, true))
            .isEqualTo(RecognitionMode.ON_DEVICE)
        assertThat(pickRecognitionMode(ON_DEVICE_MIN_SDK - 1, true, true))
            .isEqualTo(RecognitionMode.SERVICE)
    }

    // --- Fehler-Mapping ------------------------------------------------------

    @Test
    fun stilleUndUnverstandenes_sindWiederholbar() {
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_NO_MATCH))
            .isEqualTo(SpeechFailure.NICHTS_VERSTANDEN)
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
            .isEqualTo(SpeechFailure.NICHTS_VERSTANDEN)
        assertThat(SpeechFailure.NICHTS_VERSTANDEN.retryable).isTrue()
    }

    @Test
    fun netzfehler_werdenAlsSolcheBenannt() {
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_NETWORK))
            .isEqualTo(SpeechFailure.KEIN_NETZ)
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
            .isEqualTo(SpeechFailure.KEIN_NETZ)
    }

    @Test
    fun fehlendeFreigabeUndDefekterDienst_sindNichtWiederholbar() {
        // Diese beiden schickt SpeechToTextButton weiter auf den Intent-Fallback, statt den
        // Nutzer im eigenen Dialog stehen zu lassen.
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
            .isEqualTo(SpeechFailure.KEINE_BERECHTIGUNG)
        assertThat(SpeechFailure.KEINE_BERECHTIGUNG.retryable).isFalse()
        assertThat(SpeechFailure.NICHT_VERFUEGBAR.retryable).isFalse()
    }

    @Test
    fun unbekannterFehlercode_kipptNichtDieEingabe() {
        // Neuere API-Level ergänzen Codes; ein unbekannter darf keine Exception werfen.
        assertThat(speechFailureFor(9999)).isEqualTo(SpeechFailure.NICHT_VERFUEGBAR)
        assertThat(speechFailureFor(-1)).isEqualTo(SpeechFailure.NICHT_VERFUEGBAR)
    }

    @Test
    fun weitereCodes_landenAufDenPassendenGruenden() {
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_AUDIO))
            .isEqualTo(SpeechFailure.KEIN_AUDIO)
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
            .isEqualTo(SpeechFailure.BESETZT)
    }

    // --- Übernahme des Textes ------------------------------------------------

    @Test
    fun endergebnisSchlaegtTeiltext() {
        assertThat(uebernehmbarerText("Crimps waren nass", "Crimps waren"))
            .isEqualTo("Crimps waren nass")
    }

    @Test
    fun ohneEndergebnis_bleibtDasZwischenergebnisErhalten() {
        // Der Fall beim Abbruch mitten im Satz: das halb Verstandene darf nicht verfallen.
        assertThat(uebernehmbarerText("", "Crimps waren")).isEqualTo("Crimps waren")
        assertThat(uebernehmbarerText("   ", "Crimps waren")).isEqualTo("Crimps waren")
    }

    @Test
    fun garNichtsVerstanden_ergibtLeerenText() {
        // Der Dialog schaltet daraufhin „Übernehmen" ab, statt eine leere Notiz einzufügen.
        assertThat(uebernehmbarerText("", "")).isEmpty()
        assertThat(uebernehmbarerText("  ", "  ")).isEmpty()
    }

    // --- Pegel ---------------------------------------------------------------

    @Test
    fun pegel_wirdAufNullBisEinsGekappt() {
        // Manche Erkenner liefern deutlich größere Ausschläge als die dokumentierten -2..10 dB.
        assertThat(normalisierePegel(-50f)).isEqualTo(0f)
        assertThat(normalisierePegel(100f)).isEqualTo(1f)
        assertThat(normalisierePegel(-2f)).isEqualTo(0f)
        assertThat(normalisierePegel(10f)).isEqualTo(1f)
    }

    @Test
    fun pegel_mitteLiegtInDerMitte() {
        assertThat(normalisierePegel(4f)).isWithin(0.01f).of(0.5f)
    }
}
