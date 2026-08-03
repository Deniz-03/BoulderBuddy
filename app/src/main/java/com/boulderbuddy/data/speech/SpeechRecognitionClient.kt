package com.boulderbuddy.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Spracherkennung als Datenquelle. Als Interface hinterlegt, damit die UI gegen einen Fake
 * laufen kann und `SpeechRecognizer` nur in [SystemSpeechRecognitionClient] vorkommt — dieselbe
 * Trennung wie bei `HapticPlayer`.
 */
interface SpeechRecognitionClient {

    /** Welcher Weg auf diesem Gerät greift. Bestimmt, ob die App überhaupt `RECORD_AUDIO` braucht. */
    fun mode(): RecognitionMode

    /**
     * Startet eine Erkennung und liefert ihren Verlauf. Der Flow endet von selbst, sobald der
     * Erkenner ein Endergebnis oder einen Fehler meldet; Abbrechen heißt schlicht, die
     * Collection zu canceln.
     *
     * **Muss auf dem Main-Thread gesammelt werden** — `SpeechRecognizer` verlangt das. Aus einem
     * Composable heraus ist das durch `LaunchedEffect` gegeben.
     */
    fun recognize(languageTag: String): Flow<SpeechInputState>
}

/**
 * Erkennung über die direkte `SpeechRecognizer`-API mit eigener UI (Weg A).
 *
 * Bevorzugt wird das On-Device-Modell: die gesprochene Notiz verlässt das Gerät dann nicht und
 * die Erkennung funktioniert in Hallen ohne Empfang. Erst wenn es das nicht gibt, geht es an
 * den installierten Erkennungsdienst — der bekommt `EXTRA_PREFER_OFFLINE` als Bitte, ist aber
 * frei, doch das Netz zu benutzen. Gibt es auch den nicht, meldet [mode]
 * [RecognitionMode.INTENT_FALLBACK] und der Aufrufer nimmt den alten System-Dialog.
 */
class SystemSpeechRecognitionClient(
    private val context: Context,
) : SpeechRecognitionClient {

    override fun mode(): RecognitionMode = pickRecognitionMode(
        sdkInt = Build.VERSION.SDK_INT,
        onDeviceAvailable = onDeviceAvailable(),
        serviceAvailable = SpeechRecognizer.isRecognitionAvailable(context),
    )

    /**
     * Ab Android 13 fragt das System sauber ab. Auf 12/12L gibt es diese Abfrage noch nicht —
     * dort gehen wir optimistisch von einem Modell aus und fangen das Scheitern beim Erzeugen
     * in [createRecognizer] ab, statt den On-Device-Weg auf diesen Versionen ganz zu streichen.
     */
    private fun onDeviceAvailable(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        Build.VERSION.SDK_INT >= ON_DEVICE_MIN_SDK -> true
        else -> false
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= ON_DEVICE_MIN_SDK && onDeviceAvailable()) {
            // Auf 12/12L kann das trotz optimistischer Annahme fehlschlagen — dann eine Stufe
            // tiefer weitermachen, statt die Spracheingabe komplett zu verweigern.
            val onDevice = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }.getOrNull()
            if (onDevice != null) return onDevice
        }
        return if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    override fun recognize(languageTag: String): Flow<SpeechInputState> = callbackFlow {
        val recognizer = createRecognizer()
        if (recognizer == null) {
            trySend(SpeechInputState.Fehler(SpeechFailure.NICHT_VERFUEGBAR))
            close()
            return@callbackFlow
        }

        // Letztes Zwischenergebnis merken: bricht die Erkennung ab, soll das halb Verstandene
        // im Fehlerzustand sichtbar bleiben, statt kommentarlos zu verschwinden.
        var teiltext = ""

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechInputState.Hoert())
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechInputState.Hoert(teiltext, normalisierePegel(rmsdB)))
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            // Der Erkenner wertet nach dem Sprechende noch aus; der Dialog bleibt so lange offen.
            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.ersteTranskription()?.let {
                    teiltext = it
                    trySend(SpeechInputState.Hoert(teiltext))
                }
            }

            override fun onResults(results: Bundle?) {
                val endergebnis = results.ersteTranskription().orEmpty()
                trySend(SpeechInputState.Fertig(uebernehmbarerText(endergebnis, teiltext)))
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechInputState.Fehler(speechFailureFor(error), teiltext))
                close()
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        val intent = erkennungsIntent(languageTag)
        runOnMain {
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(intent)
        }
        trySend(SpeechInputState.Vorbereiten)

        awaitClose {
            // Auch beim Abbrechen durch den Nutzer: erst stoppen, dann freigeben. Ohne destroy()
            // bleibt das Mikrofon belegt und der nächste Start scheitert mit ERROR_RECOGNIZER_BUSY.
            runOnMain {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
        // Die RMS-Callbacks kommen im zweistelligen Takt pro Sekunde. Ohne Puffer würde
        // callbackFlow (Rendezvous) sie verwerfen — und im schlechten Fall auch das Endergebnis.
    }.buffer(Channel.UNLIMITED)

    private fun erkennungsIntent(languageTag: String) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            // Zwischenergebnisse sind der Grund für die eigene UI: der Nutzer sieht beim
            // Sprechen mit, statt in einen stummen Dialog zu reden.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Bitte um Offline-Verarbeitung. Im ON_DEVICE-Modus ohnehin gegeben, im SERVICE-Modus
            // eine Bitte, die der Dienst befolgen kann oder auch nicht.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Manche Erkennungsdienste verweigern ohne aufrufendes Paket die Arbeit.
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
}

/** Beste Transkription aus einem Ergebnis-Bundle, leer/blank wird zu `null`. */
private fun Bundle?.ersteTranskription(): String? = this
    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    ?.firstOrNull()
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/**
 * Führt [block] auf dem Main-Thread aus — sofort, wenn wir schon dort sind. Das direkte
 * Ausführen ist wichtig: ein `post` beim Aufräumen könnte nach `destroy()` des nächsten
 * Durchlaufs landen.
 */
private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        Handler(Looper.getMainLooper()).post(block)
    }
}
