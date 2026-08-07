package com.boulderbuddy.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.sync.Abgleichvorschlag
import com.boulderbuddy.sync.Bestandszahlen
import com.boulderbuddy.sync.Bilanz
import com.boulderbuddy.sync.Konflikt
import com.boulderbuddy.sync.KonfliktArt
import com.boulderbuddy.sync.Seite
import com.boulderbuddy.ui.components.BoulderBuddyScaffold
import com.boulderbuddy.ui.components.PrimaryButton
import com.boulderbuddy.ui.components.SectionHeader
import com.boulderbuddy.ui.components.SettingsRow
import com.boulderbuddy.ui.components.TopBar
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.inhaltsBreite
import com.boulderbuddy.ui.viewmodel.AbgleichUiState

/**
 * „Geräte abgleichen" über den Datei-Weg (Sync-Plan S7/S8).
 *
 * Die Sprache kommt ohne Fachbegriffe aus (Ablauf 6): kein „Merge", kein „Konflikt", kein
 * „Generation". Der Nutzer soll wissen, was passiert, ohne zu wissen, wie.
 */
@Composable
fun AbgleichScreen(
    state: AbgleichUiState = AbgleichUiState(),
    // Dateiname, den der Speichern-Dialog vorschlägt.
    abgabeName: String = "BoulderBuddy-Stand.db",
    onGibAb: (Uri) -> Unit = {},
    onLieseEin: (Uri) -> Unit = {},
    onEntscheideKonflikt: (Seite) -> Unit = {},
    onUebernimmFremden: () -> Unit = {},
    onBehalteEigenen: () -> Unit = {},
    onAbbrechen: () -> Unit = {},
    onRueckgaengig: () -> Unit = {},
    onMeldungGesehen: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current

    // SAF: eine SQLite-Datei anlegen bzw. auswählen. `application/octet-stream`, weil kein
    // Standard-MIME-Typ für SQLite existiert und Dateimanager sonst filtern.
    val abgeben = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let(onGibAb) }
    val einlesen = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onLieseEin) }

    LaunchedEffect(state.meldung) {
        state.meldung?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onMeldungGesehen()
        }
    }

    BoulderBuddyScaffold(
        topBar = {
            TopBar(
                title = "Geräte abgleichen",
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = BoulderBuddy.colors.onChrome,
                        )
                    }
                },
            )
        },
        content = { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    // Auf dem Tablet sonst ein Knopf über die volle Fensterbreite. Der
                    // Modifier greift auf dem Telefon nicht und kann deshalb bedingungslos
                    // stehen — er muss nur VOR dem Padding kommen, sonst deckelt er die
                    // bereits eingerückte Breite.
                    .inhaltsBreite()
                    .padding(Dimens.paddingL),
                verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
            ) {
                Text(
                    text = "Phone und Tablet auf denselben Stand bringen. " +
                        "Gib den Stand auf einem Gerät ab und lies ihn auf dem anderen ein — " +
                        "danach dasselbe in die andere Richtung, dann sind beide gleich.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                )

                if (state.laeuft) {
                    Text(
                        text = state.schritt ?: "Einen Moment …",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                PrimaryButton(
                    text = "Stand abgeben",
                    icon = Icons.Outlined.FileUpload,
                    onClick = { abgeben.launch(abgabeName) },
                )
                PrimaryButton(
                    text = "Stand einlesen",
                    icon = Icons.Outlined.FileDownload,
                    onClick = { einlesen.launch(arrayOf("*/*")) },
                )

                state.bilanz?.let { BilanzBlock(it) }

                if (state.kannRueckgaengig) {
                    SectionHeader(text = "Falls etwas schiefging")
                    SettingsRow(
                        icon = Icons.Outlined.Undo,
                        label = "Letzten Abgleich rückgängig machen",
                        // Genau das, was die Funktion kann — nicht mehr (E13).
                        subtitle = "Nimmt zurück, was der Abgleich auf diesem Gerät " +
                            "geändert hat.",
                        onClick = onRueckgaengig,
                    )
                }

                if (state.neustartNoetig) {
                    Text(
                        text = "Der Stand ist übernommen. Bitte schließe die App einmal " +
                            "vollständig und öffne sie neu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoulderBuddy.colors.textSecondary,
                    )
                }
            }
        },
    )

    when (val vorschlag = state.vorschlag) {
        is Abgleichvorschlag.Erstbegegnung -> ErstbegegnungDialog(
            meine = vorschlag.meine,
            fremde = vorschlag.fremde,
            onUebernehmen = onUebernimmFremden,
            onBehalten = onBehalteEigenen,
        )

        is Abgleichvorschlag.Zusammenfuehren -> KonfliktDialog(
            konflikte = vorschlag.konflikte,
            onWahl = onEntscheideKonflikt,
            onAbbrechen = onAbbrechen,
        )

        else -> Unit
    }
}

/**
 * Die Erstbegegnung ist per Definition ein Konflikt — hier wird nicht zusammengeführt,
 * sondern ein Stand gewählt (E10). Deshalb Zahlen statt Fachbegriffen: der Nutzer muss
 * sehen, was er aufgibt.
 */
@Composable
private fun ErstbegegnungDialog(
    meine: Bestandszahlen,
    fremde: Bestandszahlen,
    onUebernehmen: () -> Unit,
    onBehalten: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBehalten,
        title = { Text("Diese Geräte waren noch nie abgeglichen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                Text(
                    "Beim ersten Mal lässt sich nicht erkennen, was neu dazugekommen und " +
                        "was anderswo gelöscht wurde. Deshalb wird jetzt ein Stand " +
                        "übernommen — der andere geht dabei verloren.",
                )
                Text("Auf diesem Gerät:\n${beschreibe(meine)}")
                Text("Im eingelesenen Stand:\n${beschreibe(fremde)}")
                Text(
                    "Ab dem nächsten Mal wird zusammengeführt statt ersetzt.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUebernehmen) { Text("Eingelesenen Stand übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onBehalten) { Text("Diesen behalten") }
        },
    )
}

private fun beschreibe(zahlen: Bestandszahlen): String = buildString {
    append("${zahlen.hallen} Hallen, ${zahlen.sessions} Sessions, ")
    append("${zahlen.boulder} Boulder, ${zahlen.trainings} Trainings")
    if (zahlen.analysen > 0) append(", ${zahlen.analysen} Analysen")
}

/**
 * Die Frage bei Konflikten — einmal pro Abgleich, mit einer Liste des Betroffenen (E12).
 *
 * Der Zusatz „alles andere wird ohnehin zusammengeführt" ist kein Beiwerk: ohne ihn muss der
 * Nutzer annehmen, seine Antwort entscheide über den ganzen Abgleich, und wählt aus Angst
 * das Gerät mit dem Klettertag statt das mit der Analyse (Ablauf 23).
 */
@Composable
private fun KonfliktDialog(
    konflikte: List<Konflikt>,
    onWahl: (Seite) -> Unit,
    onAbbrechen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = {
            Text(
                if (konflikte.size == 1) {
                    "Ein Eintrag wurde auf beiden Geräten geändert"
                } else {
                    "${konflikte.size} Einträge wurden auf beiden Geräten geändert"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                Text("Welches Gerät soll bei diesen Einträgen gewinnen?")
                konflikte.take(8).forEach { Text("• ${beschreibe(it)}") }
                if (konflikte.size > 8) Text("• … und ${konflikte.size - 8} weitere")
                Text(
                    "Alles andere wird ohnehin zusammengeführt — die Antwort gilt nur für " +
                        "diese Einträge.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onWahl(Seite.MEINS) }) { Text("Dieses Gerät") }
        },
        dismissButton = {
            TextButton(onClick = { onWahl(Seite.FREMDES) }) { Text("Das andere") }
        },
    )
}

private fun beschreibe(konflikt: Konflikt): String {
    val was = when (konflikt.tabelle) {
        "gym" -> "Halle"
        "session" -> "Session"
        "route" -> "Boulder"
        "grade" -> "Grad"
        "grade_system" -> "Gradsystem"
        "hangboard_workout" -> "Hangboard-Training"
        "hangboard_segment" -> "Hangboard-Satz"
        "hangboard_template" -> "Timer-Vorgabe"
        "ghost_analysis" -> "Ghost-Analyse"
        else -> konflikt.tabelle
    }
    val wie = when (konflikt.art) {
        KonfliktArt.BEIDSEITIG_GEAENDERT -> "auf beiden Geräten geändert"
        KonfliktArt.GELOESCHT_GEGEN_GEAENDERT -> "hier gelöscht, dort geändert"
        KonfliktArt.TEILBAUM -> "hier gelöscht, dort ist etwas dazugekommen"
        KonfliktArt.GLEICHE_NUMMER -> "auf beiden Geräten neu angelegt"
    }
    return "$was: $wie"
}

/**
 * Die Bilanz. Ein Abgleich ohne Rückmeldung fühlt sich an wie nichts — und Löschungen
 * müssen ausdrücklich dastehen, sonst wirkt das spätere Fehlen wie ein Fehler (Ablauf 3).
 */
@Composable
private fun BilanzBlock(bilanz: Bilanz) {
    SectionHeader(text = "Ergebnis")
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
        if (bilanz.nichtsZuTun && bilanz.konfliktVerluste == 0) {
            Text("Beide Geräte waren schon auf demselben Stand.")
            return@Column
        }
        if (bilanz.uebernommen > 0) Text("${bilanz.uebernommen} Einträge übernommen")
        if (bilanz.abgegeben > 0) {
            Text(
                "${bilanz.abgegeben} Einträge fehlen noch auf dem anderen Gerät — " +
                    "gib dort ein und lies hier ein, dann sind beide gleich",
            )
        }
        if (bilanz.geloescht > 0) Text("${bilanz.geloescht} Einträge gelöscht")
        if (bilanz.konfliktVerluste > 0) {
            Text("${bilanz.konfliktVerluste} Einträge in der anderen Fassung verworfen")
        }
        if (bilanz.bezuegeGeloest > 0) {
            Text("${bilanz.bezuegeGeloest} Boulder haben ihren Grad verloren")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AbgleichScreenPreview() {
    BoulderBuddyTheme {
        AbgleichScreen(
            state = AbgleichUiState(
                bilanz = Bilanz(
                    uebernommen = 12,
                    abgegeben = 3,
                    geloescht = 1,
                    konfliktVerluste = 0,
                    bezuegeGeloest = 0,
                ),
                kannRueckgaengig = true,
            ),
        )
    }
}
