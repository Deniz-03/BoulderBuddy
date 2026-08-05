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
     * Kein ansprechbarer Erkenner vorhanden. Die Spracheingabe sagt das und hört auf.
     *
     * Hier stand `INTENT_FALLBACK`: der Rückfall auf den System-`RecognizerIntent`, also
     * Googles eigenen Sprachdialog. Der lief in **fremdem Prozess** und nahm dort selbst auf —
     * und kam damit ohne unser `RECORD_AUDIO` aus. Das war als Freundlichkeit gedacht und war
     * in Wahrheit eine Hintertür: wer die Mikrofon-Freigabe bewusst verweigert hat, bekam die
     * Funktion trotzdem, nur über einen anderen Weg. Eine Ablehnung, die nichts ablehnt, ist
     * keine Entscheidung mehr.
     *
     * Deshalb endet die Kette jetzt hier, mit einer Meldung statt eines Umwegs.
     */
    NICHT_MOEGLICH,
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
    else -> RecognitionMode.NICHT_MOEGLICH
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

    /**
     * Der Erkenner läuft, kann diese **Sprache** aber nicht — der häufigste Grund dafür, dass
     * die On-Device-Erkennung sofort abbricht, obwohl alles andere stimmt.
     *
     * `isOnDeviceRecognitionAvailable()` sagt nur, dass es den Dienst gibt; ob das Sprachpaket
     * für `de-DE` heruntergeladen ist, sagt es nicht. Vorher fiel dieser Fall unter
     * [NICHT_VERFUEGBAR] („auf diesem Gerät nicht verfügbar") — eine Diagnose, die auf ein
     * fehlendes Sprachpaket schlicht nicht zutrifft und in die Irre führt.
     */
    SPRACHE_FEHLT("Für diese Sprache fehlt das Erkennungsmodell.", retryable = false),

    /** Kein Erkenner ansprechbar, Modell fehlt oder der Dienst ist abgestürzt. */
    NICHT_VERFUEGBAR("Spracheingabe ist auf diesem Gerät nicht verfügbar.", retryable = false),
}

/**
 * Sagt dieser Grund „**nicht ich**" statt „nicht jetzt"?
 *
 * Trennt die beiden Sorten Scheitern, die vorher in einen Topf fielen: ein Erkenner, der diese
 * Sprache nicht kann oder gar nicht erst anspringt, ist mit einem zweiten Versuch nicht zu
 * retten — ein **anderer** Erkenner aber möglicherweise schon. Genau dort saß der Fehler: die
 * Kette On-Device → Dienst → System-Dialog verzweigte nur beim *Erzeugen* des Erkenners.
 * Sprang der On-Device-Erkenner an und gab erst beim Zuhören auf, wurde die Dienst-Stufe
 * übersprungen und der Nutzer landete unvermittelt im System-Dialog.
 */
fun andererErkennerKoennteHelfen(grund: SpeechFailure): Boolean = when (grund) {
    SpeechFailure.NICHT_VERFUEGBAR, SpeechFailure.SPRACHE_FEHLT -> true
    else -> false
}

/*
 * Hier stand `brauchtSystemDialog()` — die Weiche, die bei drei Gründen ungefragt in Googles
 * Sprachdialog sprang. Sie ist ersatzlos entfallen, und zwar nicht, weil sie falsch
 * funktionierte, sondern weil sie das Falsche tat:
 *
 * Der System-Dialog nimmt in fremdem Prozess auf und braucht unser `RECORD_AUDIO` nicht. Wer
 * die Mikrofon-Freigabe verweigert hatte, bekam die Spracheingabe damit trotzdem — nur mit
 * anderer Oberfläche. Aus Nutzersicht ist das kein Rückfall, sondern eine Umgehung der eigenen
 * Entscheidung.
 *
 * Ein nicht behebbarer Grund führt jetzt zu dem, was er ist: einer Meldung. `retryable`
 * unterscheidet weiterhin, ob der Dialog „Nochmal" anbietet oder nur „Schließen".
 */

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

    /*
     * Die Sprach-Codes (ab API 33). Sie fielen vorher in den `else`-Zweig und damit auf
     * NICHT_VERFUEGBAR — die Meldung „auf diesem Gerät nicht verfügbar" für ein Gerät, dem
     * nur das deutsche Sprachpaket fehlt.
     *
     * Als `static final int` setzt der Compiler sie ein; auf älteren Geräten kommen die Codes
     * schlicht nie an. Deshalb ist der Zugriff auch bei minSdk 26 gefahrlos — dieselbe
     * Überlegung, aus der diese Datei überhaupt ohne Android-Objekte auskommt.
     */
    LANGUAGE_NOT_SUPPORTED,
    LANGUAGE_UNAVAILABLE,
    CANNOT_CHECK_SUPPORT,
    -> SpeechFailure.SPRACHE_FEHLT

    else -> SpeechFailure.NICHT_VERFUEGBAR
}

// Zahlenwerte statt `SpeechRecognizer.ERROR_*`, weil die Konstanten erst ab API 33 in der
// Klasse stehen: der Compiler kann nur einsetzen, was er beim Übersetzen sieht. Die Werte sind
// Teil der öffentlichen API und liegen fest.
private const val LANGUAGE_NOT_SUPPORTED = 12
private const val LANGUAGE_UNAVAILABLE = 13
private const val CANNOT_CHECK_SUPPORT = 14

/**
 * Verlauf eines angestoßenen Modell-Downloads.
 *
 * **Warum das überhaupt die App macht:** Das Erkennungsmodell gehört nicht zur App, sondern zum
 * Erkennungsdienst des Systems — es ist pro Sprache dreistellig viele Megabyte groß, wird von
 * allen Apps auf dem Gerät geteilt und liegt nicht in unserer Hand. Mitliefern könnten wir es
 * also nicht, und bis Android 12 konnten wir es nicht einmal anfordern: der Nutzer musste es in
 * den Systemeinstellungen finden.
 *
 * Seit Android 13 gibt es `SpeechRecognizer.triggerModelDownload()` — eine **Bitte** an den
 * Dienst, nicht mehr. Ob und wann er sie erfüllt (Netz, Akku, Speicherplatz), entscheidet er.
 * Auf Android 13 gibt es dazu nicht einmal einen Rückkanal, deshalb [Angestossen]; erst
 * Android 14 meldet Fortschritt und Ergebnis.
 */
sealed interface ModellDownload {
    /** Angestoßen, ohne Rückmeldung — Android 13 kennt keinen Listener. */
    data object Angestossen : ModellDownload

    /** Läuft, mit Fortschritt in Prozent (ab Android 14). */
    data class Laeuft(val prozent: Int) : ModellDownload

    data object Fertig : ModellDownload

    data object Gescheitert : ModellDownload
}

/** Der Satz, der während bzw. nach einem Modell-Download im Dialog steht. */
fun modellHinweis(download: ModellDownload): String = when (download) {
    ModellDownload.Angestossen ->
        "Download angestoßen. Das Gerät lädt im Hintergrund — das kann ein paar Minuten dauern."

    is ModellDownload.Laeuft -> "Modell wird geladen… ${download.prozent} %"

    ModellDownload.Fertig -> "Modell geladen."

    ModellDownload.Gescheitert ->
        "Der Download hat nicht geklappt. In den Systemeinstellungen unter Spracheingabe lässt " +
            "sich das Sprachpaket von Hand nachladen."
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
