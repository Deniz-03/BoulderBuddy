package com.boulderbuddy.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boulderbuddy.ui.theme.BoulderBuddy

/**
 * Mikrofon-Button für Spracheingabe in Notizfeldern (7.4b).
 *
 * Nutzt den System-[RecognizerIntent] (Google-Spracheingabe-Dialog) statt der direkten
 * `SpeechRecognizer`-API. Vorteil: die **Mikrofon-Permission wird von der Erkenner-App
 * gehandhabt** — die App braucht selbst kein `RECORD_AUDIO` im Manifest. Das erkannte
 * Ergebnis wird über [onResult] zurückgegeben (Aufrufer entscheidet, ob anhängen/ersetzen).
 */
@Composable
fun SpeechToTextButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String = "Notiz einsprechen…",
    languageTag: String = "de-DE",
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
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

    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            }
            try {
                launcher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                // Kein Spracheingabe-Dienst installiert (z.B. Emulator ohne Google-Apps).
                Toast.makeText(
                    context,
                    "Spracheingabe auf diesem Gerät nicht verfügbar",
                    Toast.LENGTH_SHORT,
                ).show()
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

/** Hängt gesprochenen Text an eine bestehende Notiz an (mit Leerzeichen), leer = ersetzt. */
fun appendSpokenNote(current: String, spoken: String): String =
    if (current.isBlank()) spoken else "${current.trimEnd()} $spoken"
