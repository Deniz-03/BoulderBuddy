package com.boulderbuddy.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

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

    /**
     * Ob die App den Download eines fehlenden Sprachmodells überhaupt anstoßen kann — dafür
     * braucht es Android 13 und einen On-Device-Erkenner. Steht das nicht zur Verfügung, bleibt
     * dem Nutzer nur der Weg über die Systemeinstellungen, und der Dialog soll dann auch keine
     * Schaltfläche anbieten, die nichts tut.
     *
     * Default `false`: Fakes in Tests und Previews sollen sich damit nicht befassen müssen.
     */
    fun kannModellLaden(): Boolean = false

    /** Bittet den Erkennungsdienst um das Modell für [languageTag]. */
    fun ladeModell(languageTag: String): Flow<ModellDownload> =
        flowOf(ModellDownload.Gescheitert)
}

/**
 * Erkennung über die direkte `SpeechRecognizer`-API mit eigener UI (Weg A).
 *
 * Bevorzugt wird das On-Device-Modell: die gesprochene Notiz verlässt das Gerät dann nicht und
 * die Erkennung funktioniert in Hallen ohne Empfang. Erst wenn es das nicht gibt, geht es an
 * den installierten Erkennungsdienst — der bekommt `EXTRA_PREFER_OFFLINE` als Bitte, ist aber
 * frei, doch das Netz zu benutzen. Gibt es auch den nicht, meldet [mode]
 * [RecognitionMode.NICHT_MOEGLICH] und der Aufrufer sagt das — es gibt keinen dritten Weg mehr.
 *
 * Beide Stufen nehmen **in unserem Prozess** auf und setzen damit `RECORD_AUDIO` voraus. Das ist
 * der Unterschied zum entfallenen System-Dialog, und der Grund, warum es ihn nicht mehr gibt:
 * er lief ohne diese Freigabe und hat eine Ablehnung damit wirkungslos gemacht.
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

    /**
     * Die Stufen der Kette, in der Reihenfolge, in der sie probiert werden. Jede Stufe erzeugt
     * ihren Erkenner erst beim Aufruf — zwei gleichzeitig offene `SpeechRecognizer` blockieren
     * sich am Mikrofon gegenseitig.
     */
    private fun erkennerStufen(): List<() -> SpeechRecognizer?> = buildList {
        if (Build.VERSION.SDK_INT >= ON_DEVICE_MIN_SDK && onDeviceAvailable()) {
            // Auf 12/12L kann das trotz optimistischer Annahme fehlschlagen — dann greift
            // einfach die nächste Stufe.
            add {
                runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            add { SpeechRecognizer.createSpeechRecognizer(context) }
        }
    }

    /*
     * DIE KETTE VERZWEIGTE NUR BEIM ERZEUGEN, NICHT BEIM ZUHÖREN.
     *
     * `createRecognizer()` wählte **eine** Stufe aus und gab sie zurück. Sprang der
     * On-Device-Erkenner an — was er tut, sobald der Dienst existiert —, war die Dienst-Stufe
     * damit unerreichbar. Gab er erst beim Zuhören auf, ging es in einem Sprung an den
     * System-Dialog: die mittlere Stufe der dokumentierten Dreierkette kam nie zum Zug.
     *
     * Der wahrscheinlichste Auslöser dafür ist ein fehlendes Sprachpaket (Code 13,
     * ERROR_LANGUAGE_UNAVAILABLE): `isOnDeviceRecognitionAvailable()` beantwortet nur, ob es
     * den Dienst gibt — nicht, ob er DIESE Sprache kann. Vorher fiel der Code in den
     * `else`-Zweig von `speechFailureFor` und damit auf NICHT_VERFUEGBAR, was den Sprung in
     * den System-Dialog auslöste. Aus Nutzersicht: Freigabe erteilt, trotzdem sofort der
     * Google-Dialog. Welcher Code auf einem konkreten Gerät wirklich kommt, steht seit dem
     * `Log.w` in `onError` im Logcat.
     *
     * Vorher abfragen lässt sich das nicht zuverlässig — also muss die Kette das Scheitern zur
     * Laufzeit auffangen statt nur beim Erzeugen.
     */
    override fun recognize(languageTag: String): Flow<SpeechInputState> = flow {
        val stufen = erkennerStufen()
        if (stufen.isEmpty()) {
            emit(SpeechInputState.Fehler(SpeechFailure.NICHT_VERFUEGBAR))
            return@flow
        }

        // Der Fehler der letzten Stufe. Wird nur ausgegeben, wenn keine weitere Stufe folgt —
        // ein Fehler, den die nächste Stufe gleich wieder aufhebt, gehört nicht in die UI.
        var offenerFehler: SpeechInputState.Fehler? = null

        stufen.forEachIndexed { index, stufe ->
            val istLetzte = index == stufen.lastIndex
            var wechselt = false
            // Sobald ein Zwischenergebnis da ist, wird nicht mehr gewechselt: der Nutzer hat
            // dann bereits gesprochen, und ein Neustart würde das Verstandene wegwerfen.
            var teiltextGesehen = false

            erkenneMit(stufe, languageTag).collect { zustand ->
                when {
                    zustand is SpeechInputState.Hoert -> {
                        if (zustand.teiltext.isNotEmpty()) teiltextGesehen = true
                        emit(zustand)
                    }

                    zustand is SpeechInputState.Fehler && !istLetzte && !teiltextGesehen &&
                        andererErkennerKoennteHelfen(zustand.grund) -> {
                        Log.i(TAG, "Stufe $index scheitert an ${zustand.grund} — nächste Stufe")
                        offenerFehler = zustand
                        wechselt = true
                    }

                    else -> emit(zustand)
                }
            }
            if (!wechselt) return@flow
        }

        offenerFehler?.let { emit(it) }
    }

    private fun erkenneMit(
        stufe: () -> SpeechRecognizer?,
        languageTag: String,
    ): Flow<SpeechInputState> = callbackFlow {
        val recognizer = stufe()
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
                // Der rohe Code, weil die Zuordnung Code → Grund genau die Stelle war, an der
                // ein fehlendes Sprachpaket als „Gerät kann kein Sprache" gelesen wurde. Mit
                // der Zahl im Log ist auf einem fremden Gerät in Sekunden klar, was los ist.
                Log.w(TAG, "SpeechRecognizer: Fehlercode $error → ${speechFailureFor(error)}")
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

    /*
     * Der Modell-Download. Genau die Lücke, in die diese App gelaufen ist:
     * `isOnDeviceRecognitionAvailable()` meldet den Dienst als da, das Sprachpaket für de-DE ist
     * es aber nicht — und der Erkenner bricht beim Start mit ERROR_LANGUAGE_UNAVAILABLE ab.
     *
     * Vor Android 13 gab es dagegen kein Mittel; der Nutzer musste die Stelle in den
     * Systemeinstellungen selbst finden. `triggerModelDownload` ist seitdem der Weg, danach zu
     * fragen — mehr als eine Bitte ist es nicht, der Dienst entscheidet über Zeitpunkt und
     * Bedingungen (Netz, Akku, Speicher).
     */
    override fun kannModellLaden(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && onDeviceAvailable()

    override fun ladeModell(languageTag: String): Flow<ModellDownload> = callbackFlow {
        val recognizer = if (kannModellLaden()) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
        if (recognizer == null) {
            trySend(ModellDownload.Gescheitert)
            close()
            return@callbackFlow
        }

        val intent = erkennungsIntent(languageTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Ab Android 14 mit Rückkanal: der Flow bleibt offen, bis der Dienst fertig ist
            // oder aufgibt.
            val listener = object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    trySend(ModellDownload.Laeuft(completedPercent))
                }

                override fun onScheduled() {
                    trySend(ModellDownload.Angestossen)
                }

                override fun onSuccess() {
                    trySend(ModellDownload.Fertig)
                    close()
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Modell-Download gescheitert, Fehlercode $error")
                    trySend(ModellDownload.Gescheitert)
                    close()
                }
            }
            runOnMain { recognizer.triggerModelDownload(intent, context.mainExecutor, listener) }
        } else {
            // Android 13 kennt nur die Anforderung ohne Rückmeldung. Mehr als „angestoßen"
            // lässt sich hier ehrlicherweise nicht sagen.
            runOnMain { recognizer.triggerModelDownload(intent) }
            trySend(ModellDownload.Angestossen)
            close()
        }

        awaitClose {
            // Der Download läuft im Erkennungsdienst, nicht in diesem Client — ihn freizugeben
            // beendet nur unsere Bindung.
            runOnMain { recognizer.destroy() }
        }
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

private const val TAG = "SpeechRecognition"

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
