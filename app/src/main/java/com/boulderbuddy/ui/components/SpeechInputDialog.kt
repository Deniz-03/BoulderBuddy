package com.boulderbuddy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boulderbuddy.R
import com.boulderbuddy.data.speech.SpeechFailure
import com.boulderbuddy.data.speech.SpeechInputState
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens

/**
 * Eigene Aufnahme-UI der Spracheingabe (Weg A) — der Ersatz für den Google-Dialog.
 *
 * Der Gewinn gegenüber dem System-Dialog ist die Sichtbarkeit: der erkannte Text erscheint schon
 * beim Sprechen, der Ring um das Mikrofon zeigt den Pegel (also dass überhaupt Ton ankommt), und
 * Fehler stehen als Satz da statt als kommentarloses Zuklappen.
 *
 * Der Dialog ist zustandslos: [state] kommt von außen, alle Aktionen gehen als Callback zurück.
 */
/** Eine zusätzliche Schaltfläche, die der Aufrufer situativ einhängt (z.B. „Modell laden"). */
data class DialogAktion(val beschriftung: String, val onClick: () -> Unit)

@Composable
fun SpeechInputDialog(
    state: SpeechInputState,
    onUebernehmen: (String) -> Unit,
    onWiederholen: () -> Unit,
    onAbbrechen: () -> Unit,
    // Überschreibt den Statussatz. Trägt den Verlauf des Modell-Downloads, der kein
    // Erkennungszustand ist und deshalb nicht in `SpeechInputState` gehört.
    hinweis: String? = null,
    // Verdrängt die Standard-Schaltfläche, solange gesetzt.
    zusatzAktion: DialogAktion? = null,
) {
    val fehler = (state as? SpeechInputState.Fehler)?.grund
    val teiltext = when (state) {
        is SpeechInputState.Hoert -> state.teiltext
        is SpeechInputState.Fehler -> state.teiltext
        is SpeechInputState.Fertig -> state.text
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (fehler != null) "Spracheingabe" else "Notiz einsprechen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                MikrofonIndikator(state)

                Text(
                    text = hinweis ?: fehler?.message ?: statusText(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                // Feste Mindesthöhe: sonst springt der Dialog bei jedem neuen Teilergebnis.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TEILTEXT_HOEHE),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (teiltext.isNotEmpty()) {
                        Text(
                            text = teiltext,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                zusatzAktion != null -> TextButton(onClick = zusatzAktion.onClick) {
                    Text(zusatzAktion.beschriftung)
                }

                // Wiederholbarer Fehler: der zweite Versuch ist die naheliegende Aktion.
                fehler != null && fehler.retryable ->
                    TextButton(onClick = onWiederholen) {
                        Text(stringResource(R.string.sprache_nochmal))
                    }

                // Endgültiger Fehler ohne gerettetes Halbergebnis. Hier stand ein
                // ausgegrautes „Übernehmen" neben „Abbrechen" — zwei Schaltflächen, von denen
                // die eine nicht geht und die andere nichts abbricht. Seit die Spracheingabe
                // bei so einem Fehler stehen bleibt statt in den System-Dialog zu springen,
                // ist das der sichtbare Normalfall und nicht mehr ein Randfall.
                fehler != null && teiltext.isBlank() -> Unit

                else -> TextButton(
                    onClick = { onUebernehmen(teiltext) },
                    enabled = teiltext.isNotBlank(),
                ) { Text(stringResource(R.string.sprache_uebernehmen)) }
            }
        },
        dismissButton = {
            // „Schließen", wenn es nichts abzubrechen gibt: die Erkennung läuft dann nicht
            // mehr, und „Abbrechen" würde eine laufende Aufnahme suggerieren.
            val abbrechbar = fehler == null && state !is SpeechInputState.Fertig
            TextButton(onClick = onAbbrechen) {
                Text(
                    stringResource(
                        if (abbrechbar) R.string.aktion_abbrechen else R.string.aktion_schliessen,
                    ),
                )
            }
        },
    )
}

/**
 * Mikrofon in einer dunklen Kreisfläche, deren Ring mit dem Pegel atmet. Im Fehlerfall ein
 * durchgestrichenes Mikrofon ohne Animation.
 */
@Composable
private fun MikrofonIndikator(state: SpeechInputState) {
    val fehlerhaft = state is SpeechInputState.Fehler
    val pegel = (state as? SpeechInputState.Hoert)?.pegel ?: 0f
    // Kurze Interpolation, damit der Ring nicht bei jedem RMS-Callback zuckt.
    val ringPegel by animateFloatAsState(
        targetValue = if (fehlerhaft) 0f else pegel,
        animationSpec = tween(durationMillis = 120),
        label = "pegel",
    )

    Box(contentAlignment = Alignment.Center) {
        // Pegelring hinter dem Kreis: wächst mit der Lautstärke und wird dabei kräftiger.
        Box(
            modifier = Modifier
                .size(KREIS_GROESSE + RING_MAX * ringPegel)
                .clip(CircleShape)
                .background(
                    BoulderBuddy.colors.surfaceChrome.copy(alpha = RING_ALPHA * ringPegel),
                ),
        )
        Box(
            modifier = Modifier
                .size(KREIS_GROESSE)
                .clip(CircleShape)
                .background(BoulderBuddy.colors.surfaceChrome),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (fehlerhaft) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                // null: der Statustext daneben sagt bereits, was gerade passiert.
                contentDescription = null,
                // Inhalt auf der Chrome-Fläche. Das Chrome bleibt in beiden Themes dunkel,
                // deshalb ist onChrome hier in beiden Themes creme.
                tint = BoulderBuddy.colors.onChrome,
                modifier = Modifier.size(Dimens.iconL),
            )
        }
    }
}

@Composable
private fun statusText(state: SpeechInputState): String = when (state) {
    SpeechInputState.Idle, SpeechInputState.Vorbereiten ->
        stringResource(R.string.sprache_vorbereiten)
    is SpeechInputState.Hoert -> stringResource(
        if (state.teiltext.isEmpty()) R.string.sprache_sprich_jetzt
        else R.string.sprache_hoert_zu,
    )
    is SpeechInputState.Fertig -> stringResource(R.string.sprache_fertig)
    is SpeechInputState.Fehler -> state.grund.message
}

private val KREIS_GROESSE = 72.dp
private val RING_MAX = 28.dp
private const val RING_ALPHA = 0.35f
private val TEILTEXT_HOEHE = 64.dp

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun SpeechInputDialogHoertPreview() {
    BoulderBuddyTheme {
        SpeechInputDialog(
            state = SpeechInputState.Hoert("Crimps am Ausstieg waren nass", pegel = 0.6f),
            onUebernehmen = {},
            onWiederholen = {},
            onAbbrechen = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun SpeechInputDialogFehlerPreview() {
    BoulderBuddyTheme {
        SpeechInputDialog(
            state = SpeechInputState.Fehler(SpeechFailure.NICHTS_VERSTANDEN),
            onUebernehmen = {},
            onWiederholen = {},
            onAbbrechen = {},
        )
    }
}

// Der Fall, der diese App tatsächlich lahmgelegt hat: Erkenner da, deutsches Sprachpaket nicht.
@Preview(showBackground = true, backgroundColor = 0xFFFCF6E4)
@Composable
private fun SpeechInputDialogModellFehltPreview() {
    BoulderBuddyTheme {
        SpeechInputDialog(
            state = SpeechInputState.Fehler(SpeechFailure.SPRACHE_FEHLT),
            onUebernehmen = {},
            onWiederholen = {},
            onAbbrechen = {},
            zusatzAktion = DialogAktion("Modell laden") {},
        )
    }
}
