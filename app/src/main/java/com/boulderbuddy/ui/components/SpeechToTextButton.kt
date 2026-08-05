package com.boulderbuddy.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
// Nur noch für den KDoc-Verweis unten: der Intent-Weg selbst ist entfallen.
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.boulderbuddy.data.speech.ModellDownload
import com.boulderbuddy.data.speech.RecognitionMode
import com.boulderbuddy.data.speech.SpeechFailure
import com.boulderbuddy.data.speech.SpeechInputState
import com.boulderbuddy.data.speech.SpeechRecognitionClient
import com.boulderbuddy.data.speech.SystemSpeechRecognitionClient
import com.boulderbuddy.data.speech.modellHinweis
import com.boulderbuddy.ui.theme.BoulderBuddy

/**
 * Mikrofon-Button für Spracheingabe in Notizfeldern (7.4b, ausgebaut zu Weg A).
 *
 * Spricht den `SpeechRecognizer` direkt an und zeigt eine eigene Aufnahme-UI
 * ([SpeechInputDialog]) — mit Live-Text beim Sprechen statt eines fremden, stummen Dialogs.
 * Bevorzugt wird das On-Device-Modell, damit gesprochene Notizen das Gerät nicht verlassen;
 * gibt es keins, übernimmt der installierte Erkennungsdienst.
 *
 * **Was hier bewusst NICHT mehr passiert: der Rückfall auf den System-[RecognizerIntent].**
 *
 * Die Kette hatte eine dritte Stufe — Googles Sprachdialog, angeworfen sobald der direkte Weg
 * aufgab. Sie war als Freundlichkeit gemeint und war eine Hintertür: dieser Dialog nimmt in
 * fremdem Prozess auf und braucht unser `RECORD_AUDIO` nicht. Wer die Mikrofon-Freigabe
 * verweigert hatte, bekam die Funktion also trotzdem, nur mit anderer Oberfläche. Eine
 * Ablehnung, die nichts ablehnt, ist keine Entscheidung — und ein Nutzer, der das bemerkt, hat
 * allen Grund, dem Rest der App auch zu misstrauen.
 *
 * Zweiter, kleinerer Grund: der Sprung war unsichtbar. Wer auf das Mikrofon tippte, stand
 * unvermittelt in einem fremden Dialog, ohne je zu erfahren, dass und warum der eigene Weg
 * gescheitert war. Jetzt steht der Grund im Dialog, und wiederholbare Fehler bieten „Nochmal".
 *
 * Das erkannte Ergebnis geht über [onResult] zurück (der Aufrufer entscheidet anhängen/ersetzen).
 *
 * @param client nur für Tests und Previews überschreibbar; normal kommt der Geräte-Client.
 */
@Composable
fun SpeechToTextButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    // `prompt` ist mit dem Intent-Weg entfallen — die Beschriftung setzte den Text im
    // Google-Dialog. Unser eigener Dialog beschriftet sich selbst.
    languageTag: String = "de-DE",
    client: SpeechRecognitionClient? = null,
) {
    val context = LocalContext.current
    // Konstruktion ist folgenlos (der Client merkt sich nur den Context) — die Geräteabfragen
    // passieren erst beim Klick. Dadurch bleiben die @Preview der Formular-Screens lauffähig.
    val recognition = remember(context, client) {
        client ?: SystemSpeechRecognitionClient(context)
    }

    var state by remember { mutableStateOf<SpeechInputState>(SpeechInputState.Idle) }
    // Zähler statt Boolean: „Nochmal" muss den LaunchedEffect neu starten, und dafür braucht es
    // einen Key, der sich bei jedem Versuch ändert. 0 heißt: keine Erkennung aktiv.
    var versuch by remember { mutableIntStateOf(0) }

    fun beenden() {
        versuch = 0
        state = SpeechInputState.Idle
    }

    fun starteErkennung() {
        state = SpeechInputState.Vorbereiten
        versuch++
    }

    /** Beendet den Versuch mit einer Meldung im eigenen Dialog. */
    fun melde(grund: SpeechFailure) {
        versuch = 0
        state = SpeechInputState.Fehler(grund)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Abgelehnt heißt jetzt abgelehnt. Vorher wich diese Zeile auf den System-Dialog aus,
        // der ohne unsere Freigabe aufnimmt — die Ablehnung hatte damit keine Wirkung.
        if (granted) starteErkennung() else melde(SpeechFailure.KEINE_BERECHTIGUNG)
    }

    // Verlauf eines angeforderten Sprachmodells. `null` = nichts angefordert.
    var download by remember { mutableStateOf<ModellDownload?>(null) }
    var ladeVersuch by remember { mutableIntStateOf(0) }

    if (versuch > 0) {
        LaunchedEffect(versuch) {
            // Compose sammelt auf dem Main-Thread — genau das verlangt SpeechRecognizer.
            recognition.recognize(languageTag).collect { state = it }
        }
    }

    if (ladeVersuch > 0) {
        LaunchedEffect(ladeVersuch) {
            recognition.ladeModell(languageTag).collect { download = it }
        }
    }

    // Hier saß der `LaunchedEffect`, der bei drei Fehlergründen ungefragt den System-Dialog
    // startete. Ersatzlos entfallen: der Fehler bleibt jetzt stehen, wo er entstanden ist, und
    // wird gelesen. Was der Dialog daraus macht, hängt an `SpeechFailure.retryable`.

    if (state != SpeechInputState.Idle) {
        // Fehlt nur das Sprachpaket, ist das kein Grund aufzugeben: seit Android 13 lässt es
        // sich anfordern. Der Nutzer soll dafür nicht die Systemeinstellungen durchsuchen
        // müssen — das war bis hierher die einzige Antwort auf diesen Fehler.
        val fehlendesModell = (state as? SpeechInputState.Fehler)?.grund == SpeechFailure.SPRACHE_FEHLT
        val aktion = when {
            download == ModellDownload.Fertig ->
                DialogAktion("Nochmal") { download = null; starteErkennung() }

            download != null -> null

            fehlendesModell && recognition.kannModellLaden() ->
                DialogAktion("Modell laden") { ladeVersuch++ }

            else -> null
        }

        SpeechInputDialog(
            state = state,
            onUebernehmen = { text ->
                if (text.isNotBlank()) onResult(text)
                beenden()
            },
            onWiederholen = { starteErkennung() },
            onAbbrechen = { download = null; beenden() },
            hinweis = download?.let(::modellHinweis),
            zusatzAktion = aktion,
        )
    }

    IconButton(
        onClick = {
            when {
                recognition.mode() == RecognitionMode.NICHT_MOEGLICH ->
                    melde(SpeechFailure.NICHT_VERFUEGBAR)

                hatMikrofonFreigabe(context) -> starteErkennung()

                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = "Notiz einsprechen",
            tint = BoulderBuddy.colors.textSecondary,
        )
    }
}

private fun hatMikrofonFreigabe(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/** Hängt gesprochenen Text an eine bestehende Notiz an (mit Leerzeichen), leer = ersetzt. */
fun appendSpokenNote(current: String, spoken: String): String =
    if (current.isBlank()) spoken else "${current.trimEnd()} $spoken"
