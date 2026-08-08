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

    // Die Sprach-Codes stehen erst ab API 33 in `SpeechRecognizer` — hier wie in der
    // Produktionsquelle als Zahl, damit der Test unabhängig vom compileSdk übersetzt.
    private val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    private val ERROR_LANGUAGE_UNAVAILABLE = 13
    private val ERROR_CANNOT_CHECK_SUPPORT = 14

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
    fun ohneJedenErkenner_istDieSpracheingabeAus() {
        // Früher hieß diese Stufe INTENT_FALLBACK und startete Googles Sprachdialog. Der nahm
        // in fremdem Prozess auf und brauchte unser RECORD_AUDIO nicht — die Funktion lief
        // also auch für jemanden, der die Mikrofon-Freigabe abgelehnt hatte.
        val mode = pickRecognitionMode(
            sdkInt = 34,
            onDeviceAvailable = false,
            serviceAvailable = false,
        )
        assertThat(mode).isEqualTo(RecognitionMode.NICHT_MOEGLICH)
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
        // Bei diesen beiden bietet der Dialog kein „Nochmal" an — ein zweiter Versuch würde
        // exakt dasselbe ergeben.
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
    fun fehlendesSprachmodell_heisstNichtGeraetKannKeineSprache() {
        // Der Fall, in dem die Spracheingabe sofort im System-Dialog landete, obwohl die
        // Mikrofon-Freigabe erteilt war: der On-Device-Erkenner existiert, aber ohne
        // heruntergeladenes de-DE-Paket bricht er beim Start mit Code 13 ab. Vorher fiel das
        // in den `else`-Zweig — dieselbe Meldung wie „kein Erkenner installiert".
        assertThat(speechFailureFor(ERROR_LANGUAGE_UNAVAILABLE))
            .isEqualTo(SpeechFailure.SPRACHE_FEHLT)
        assertThat(speechFailureFor(ERROR_LANGUAGE_NOT_SUPPORTED))
            .isEqualTo(SpeechFailure.SPRACHE_FEHLT)
        assertThat(speechFailureFor(ERROR_CANNOT_CHECK_SUPPORT))
            .isEqualTo(SpeechFailure.SPRACHE_FEHLT)
        assertThat(SpeechFailure.SPRACHE_FEHLT.message)
            .isNotEqualTo(SpeechFailure.NICHT_VERFUEGBAR.message)
    }

    // --- Wer darf wohin ausweichen? ------------------------------------------

    @Test
    fun nurDasEndgueltigeScheitern_wechseltDenErkenner() {
        // „Nicht ich" — eine andere Stufe kann es können.
        assertThat(andererErkennerKoennteHelfen(SpeechFailure.SPRACHE_FEHLT)).isTrue()
        assertThat(andererErkennerKoennteHelfen(SpeechFailure.NICHT_VERFUEGBAR)).isTrue()

        // „Nicht jetzt" — ein Erkennerwechsel ändert daran nichts und würde den Nutzer nur
        // mitten im Satz neu starten lassen.
        assertThat(andererErkennerKoennteHelfen(SpeechFailure.NICHTS_VERSTANDEN)).isFalse()
        assertThat(andererErkennerKoennteHelfen(SpeechFailure.KEIN_AUDIO)).isFalse()
        assertThat(andererErkennerKoennteHelfen(SpeechFailure.BESETZT)).isFalse()
        assertThat(andererErkennerKoennteHelfen(SpeechFailure.KEIN_NETZ)).isFalse()

        // Die fehlende Freigabe betrifft UNS, nicht den Erkenner — dagegen hilft nur der
        // System-Dialog, der in fremdem Prozess aufnimmt.
        assertThat(andererErkennerKoennteHelfen(SpeechFailure.KEINE_BERECHTIGUNG)).isFalse()
    }

    @Test
    fun jederGrund_sagtWasLosIstUndNichtNurDassEtwasLosIst() {
        // Seit die Spracheingabe bei einem endgültigen Fehler stehen bleibt, statt in den
        // System-Dialog zu springen, IST die Meldung das Ergebnis — vorher sah der Nutzer sie
        // in drei von sechs Fällen nie. Sie muss deshalb einen Satz bilden und darf sich nicht
        // mit einer anderen decken, sonst ist die Unterscheidung der Gründe folgenlos.
        val meldungen = SpeechFailure.entries.map { it.message }
        assertThat(meldungen).containsNoDuplicates()
        SpeechFailure.entries.forEach { grund ->
            // Ein ganzer Satz, kein Stichwort: „Fehler" oder „Nicht verfügbar" lässt den
            // Nutzer mit derselben Frage zurück, mit der er hingekommen ist.
            assertThat(grund.message.length).isAtLeast(20)
        }
    }

    @Test
    fun weitereCodes_landenAufDenPassendenGruenden() {
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_AUDIO))
            .isEqualTo(SpeechFailure.KEIN_AUDIO)
        assertThat(speechFailureFor(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
            .isEqualTo(SpeechFailure.BESETZT)
    }

    // --- Modell-Download -----------------------------------------------------

    @Test
    fun derDownloadHinweis_verspricht_nichtsWasNichtGesichertIst() {
        // Android 13 kann den Download nur anstoßen, ohne Rückkanal. Der Satz darf deshalb
        // nicht behaupten, es sei etwas fertig — er ist die einzige Rückmeldung, die der
        // Nutzer auf dieser Version je bekommt.
        assertThat(modellHinweis(ModellDownload.Angestossen)).contains("Hintergrund")
        assertThat(modellHinweis(ModellDownload.Angestossen)).doesNotContain("geladen.")

        assertThat(modellHinweis(ModellDownload.Laeuft(42))).contains("42")
        assertThat(modellHinweis(ModellDownload.Fertig)).isEqualTo("Modell geladen.")

        // Beim Scheitern muss der Ausweg dastehen, sonst ist die Meldung eine Sackgasse.
        assertThat(modellHinweis(ModellDownload.Gescheitert)).contains("Systemeinstellungen")
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
