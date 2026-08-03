package com.boulderbuddy.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
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
import com.boulderbuddy.data.speech.RecognitionMode
import com.boulderbuddy.data.speech.SpeechFailure
import com.boulderbuddy.data.speech.SpeechInputState
import com.boulderbuddy.data.speech.SpeechRecognitionClient
import com.boulderbuddy.data.speech.SystemSpeechRecognitionClient
import com.boulderbuddy.ui.theme.BoulderBuddy

/**
 * Mikrofon-Button für Spracheingabe in Notizfeldern (7.4b, ausgebaut zu Weg A).
 *
 * Spricht den `SpeechRecognizer` direkt an und zeigt eine eigene Aufnahme-UI
 * ([SpeechInputDialog]) — mit Live-Text beim Sprechen statt eines fremden, stummen Dialogs.
 * Bevorzugt wird das On-Device-Modell, damit gesprochene Notizen das Gerät nicht verlassen.
 *
 * **Fallback-Kette**, absichtlich dreistufig, weil jede Stufe an einer anderen Stelle scheitern
 * kann:
 * 1. On-Device-Erkenner (ab Android 12, wenn ein Modell da ist)
 * 2. installierter Erkennungsdienst mit der Bitte um Offline-Verarbeitung
 * 3. System-[RecognizerIntent] (Google-Dialog) — greift, wenn es gar keinen direkt ansprechbaren
 *    Erkenner gibt, wenn der Nutzer die Mikrofon-Freigabe verweigert oder wenn der direkte Weg
 *    zur Laufzeit aufgibt. Diese Stufe braucht **kein** `RECORD_AUDIO`, weil die Erkenner-App
 *    selbst aufnimmt — deshalb bleibt sie ein echter Rückfall und nicht nur eine Fehlermeldung.
 *
 * Das erkannte Ergebnis geht über [onResult] zurück (der Aufrufer entscheidet anhängen/ersetzen).
 *
 * @param client nur für Tests und Previews überschreibbar; normal kommt der Geräte-Client.
 */
@Composable
fun SpeechToTextButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String = "Notiz einsprechen…",
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

    val intentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) onResult(spoken)
        }
    }

    fun starteIntentFallback() {
        beenden()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            intentLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // Letzte Stufe gescheitert: kein Spracheingabe-Dienst installiert (z.B. Emulator
            // ohne Google-Apps). Mehr als der Hinweis bleibt hier nicht.
            Toast.makeText(
                context,
                SpeechFailure.NICHT_VERFUEGBAR.message,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun starteErkennung() {
        state = SpeechInputState.Vorbereiten
        versuch++
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Abgelehnt heißt nicht „geht nicht": der System-Dialog nimmt in seinem eigenen Prozess
        // auf und kommt ohne unsere Freigabe aus. Also dorthin ausweichen, statt zu blockieren.
        if (granted) starteErkennung() else starteIntentFallback()
    }

    if (versuch > 0) {
        LaunchedEffect(versuch) {
            // Compose sammelt auf dem Main-Thread — genau das verlangt SpeechRecognizer.
            recognition.recognize(languageTag).collect { state = it }
        }
    }

    // Scheitert der direkte Weg grundsätzlich (kein Erkenner erreichbar, Freigabe trotz Abfrage
    // nicht wirksam), ist der System-Dialog die richtige Antwort — nicht eine Sackgasse im
    // eigenen Dialog. Wiederholbare Fehler bleiben dagegen im Dialog mit „Nochmal".
    val grund = (state as? SpeechInputState.Fehler)?.grund
    LaunchedEffect(grund) {
        if (grund == SpeechFailure.NICHT_VERFUEGBAR || grund == SpeechFailure.KEINE_BERECHTIGUNG) {
            starteIntentFallback()
        }
    }

    if (state != SpeechInputState.Idle) {
        SpeechInputDialog(
            state = state,
            onUebernehmen = { text ->
                if (text.isNotBlank()) onResult(text)
                beenden()
            },
            onWiederholen = { starteErkennung() },
            onAbbrechen = { beenden() },
        )
    }

    IconButton(
        onClick = {
            if (recognition.mode() == RecognitionMode.INTENT_FALLBACK) {
                starteIntentFallback()
            } else if (hatMikrofonFreigabe(context)) {
                starteErkennung()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
