package com.boulderbuddy.data.speech

import android.speech.SpeechRecognizer

/**
 * Zustandsmodell und Entscheidungslogik der Spracheingabe (Weg A: direkte
 * `SpeechRecognizer`-API mit eigener UI statt des Google-Dialogs).
 *
 * Diese Datei ist bewusst **frei von Android-Objekten** — sie referenziert aus `android.speech`
 * nur `static final int`-Konstanten, die der Compiler einsetzt. Dadurch läuft die gesamte
 * Entscheidungs- und Fehlerlogik in JVM-Unit-Tests, ohne Emulator und ohne Robolectric.
 * Der Geräte-Teil steckt ausschließlich in [SystemSpeechRecognitionClient].
 */

/** Welcher Erkennungsweg auf diesem Gerät benutzt wird. */
enum class RecognitionMode {
    /**
     * On-Device-Modell über `createOnDeviceSpeechRecognizer` (ab API 31). Läuft ohne Netz und
     * ohne dass gesprochene Notizen das Gerät verlassen — der gewünschte Normalfall.
     */
    ON_DEVICE,

    /**
     * Installierter Erkennungsdienst über `createSpeechRecognizer`. Wir bitten per
     * `EXTRA_PREFER_OFFLINE` um Offline-Verarbeitung; ob der Dienst das befolgt, ist seine
     * Entscheidung. Der Weg für Geräte unter API 31 und für Geräte ohne On-Device-Modell.
     */
    SERVICE,

    /**
     * Kein direkt ansprechbarer Erkenner vorhanden → Rückfall auf den System-`RecognizerIntent`
     * (Google-Spracheingabe-Dialog). Fremde UI, dafür braucht die App kein `RECORD_AUDIO`,
     * weil die Erkenner-App die Aufnahme selbst verantwortet.
     */
    INTENT_FALLBACK,
}

/**
 * Wählt den Erkennungsweg. Ausgelagert aus [SystemSpeechRecognitionClient], damit die
 * Fallback-Kette testbar ist, statt nur auf dem jeweils vorliegenden Gerät sichtbar zu werden.
 *
 * @param sdkInt `Build.VERSION.SDK_INT` des Geräts.
 * @param onDeviceAvailable ob ein On-Device-Modell gemeldet wird (unter API 31 immer `false`).
 * @param serviceAvailable `SpeechRecognizer.isRecognitionAvailable(context)`.
 */
fun pickRecognitionMode(
    sdkInt: Int,
    onDeviceAvailable: Boolean,
    serviceAvailable: Boolean,
): RecognitionMode = when {
    sdkInt >= ON_DEVICE_MIN_SDK && onDeviceAvailable -> RecognitionMode.ON_DEVICE
    serviceAvailable -> RecognitionMode.SERVICE
    else -> RecognitionMode.INTENT_FALLBACK
}

/** `SpeechRecognizer.createOnDeviceSpeechRecognizer` existiert erst ab Android 12. */
const val ON_DEVICE_MIN_SDK = 31

/**
 * Grund, aus dem eine Erkennung abgebrochen ist — mit einer Meldung, die im Dialog steht, und
 * der Information, ob ein zweiter Versuch überhaupt Sinn ergibt.
 */
enum class SpeechFailure(val message: String, val retryable: Boolean) {
    /** Es wurde nichts Verwertbares verstanden (Standardfall bei Stille oder Störgeräusch). */
    NICHTS_VERSTANDEN("Nichts verstanden. Nochmal versuchen?", retryable = true),

    /** Der Erkenner hat keinen Ton bekommen — Mikrofon belegt oder defekt. */
    KEIN_AUDIO("Kein Ton vom Mikrofon. Ist es von einer anderen App belegt?", retryable = true),

    /** Die App darf nicht aufnehmen. Die UI bietet hier den Weg in die Einstellungen an. */
    KEINE_BERECHTIGUNG("Ohne Mikrofon-Freigabe geht die Spracheingabe nicht.", retryable = false),

    /** Der Erkenner läuft schon — z.B. wenn ein zweiter Start zu schnell kommt. */
    BESETZT("Die Spracherkennung ist gerade beschäftigt. Kurz warten.", retryable = true),

    /**
     * Netz nötig, aber nicht da. Kann im [RecognitionMode.SERVICE]-Weg auftreten, wenn der
     * Dienst `EXTRA_PREFER_OFFLINE` ignoriert und kein Offline-Modell hat.
     */
    KEIN_NETZ("Diese Spracherkennung braucht Internet und findet gerade keins.", retryable = true),

    /** Kein Erkenner ansprechbar, Modell fehlt oder der Dienst ist abgestürzt. */
    NICHT_VERFUEGBAR("Spracheingabe ist auf diesem Gerät nicht verfügbar.", retryable = false),
}

/**
 * Übersetzt einen `SpeechRecognizer.ERROR_*`-Code in einen [SpeechFailure].
 *
 * Die Codes kommen als rohe Ints aus dem `RecognitionListener`; unbekannte oder erst in neueren
 * API-Levels ergänzte Codes landen absichtlich auf [SpeechFailure.NICHT_VERFUEGBAR] statt in
 * einer Exception — ein unbekannter Fehler soll die Notiz-Eingabe nicht abschießen.
 */
fun speechFailureFor(errorCode: Int): SpeechFailure = when (errorCode) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> SpeechFailure.NICHTS_VERSTANDEN

    SpeechRecognizer.ERROR_AUDIO -> SpeechFailure.KEIN_AUDIO

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.KEINE_BERECHTIGUNG

    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.BESETZT

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> SpeechFailure.KEIN_NETZ

    else -> SpeechFailure.NICHT_VERFUEGBAR
}

/**
 * Was der Dialog gerade anzeigt. Ein `sealed interface` statt mehrerer Flags, damit
 * „hört zu" und „fehlgeschlagen" sich nicht gegenseitig überschreiben können.
 */
sealed interface SpeechInputState {
    /** Kein Dialog offen. */
    data object Idle : SpeechInputState

    /** Erkenner wird gestartet; der Nutzer soll noch nicht sprechen. */
    data object Vorbereiten : SpeechInputState

    /**
     * Erkennung läuft. [teiltext] ist das laufend aktualisierte Zwischenergebnis, [pegel] der
     * auf 0..1 normierte Lautstärkepegel für die Mikrofon-Animation.
     */
    data class Hoert(val teiltext: String = "", val pegel: Float = 0f) : SpeechInputState

    /**
     * Erkennung abgeschlossen. [text] ist das Endergebnis; es kann leer sein, wenn der Nutzer
     * die Aufnahme beendet hat, ohne dass etwas verstanden wurde.
     */
    data class Fertig(val text: String) : SpeechInputState

    /** Abbruch mit Grund. [teiltext] bleibt erhalten, damit Halbverstandenes nicht verfällt. */
    data class Fehler(val grund: SpeechFailure, val teiltext: String = "") : SpeechInputState
}

/**
 * Der Text, den „Übernehmen" einfügt. Das Endergebnis gewinnt; ist es leer (der Erkenner hat
 * nichts final bestätigt), fällt es auf das letzte Zwischenergebnis zurück, statt die
 * halbfertige Notiz des Nutzers wegzuwerfen.
 */
fun uebernehmbarerText(endergebnis: String, teiltext: String): String =
    endergebnis.trim().ifEmpty { teiltext.trim() }

/**
 * Normiert den RMS-Wert des `RecognitionListener` (Dezibel, laut Doku etwa -2..10) auf 0..1
 * für die Pegel-Animation. Werte außerhalb werden gekappt, weil manche Erkenner deutlich
 * größere Ausschläge liefern als dokumentiert.
 */
fun normalisierePegel(rmsdB: Float): Float =
    ((rmsdB - RMS_MIN_DB) / (RMS_MAX_DB - RMS_MIN_DB)).coerceIn(0f, 1f)

private const val RMS_MIN_DB = -2f
private const val RMS_MAX_DB = 10f
