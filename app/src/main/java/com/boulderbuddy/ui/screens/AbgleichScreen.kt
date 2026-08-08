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
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.sync.Abgleichvorschlag
import com.boulderbuddy.sync.Bestandszahlen
import com.boulderbuddy.sync.Bilanz
import com.boulderbuddy.sync.Konflikt
import com.boulderbuddy.sync.KonfliktArt
import com.boulderbuddy.sync.Seite
import com.boulderbuddy.sync.nearby.NearbyBerechtigungen
import com.boulderbuddy.sync.nearby.Sitzungsstand
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
    // Zustand des Funkwegs; kommt aus der Sitzung, nicht aus dem ViewModel — die Uebertragung
    // laeuft im Foreground Service weiter, auch wenn dieser Screen verschwindet.
    funkStand: Sitzungsstand = Sitzungsstand.Untaetig,
    onStarteFunk: () -> Unit = {},
    onBestaetigeVerbindung: (Boolean) -> Unit = {},
    onFunkKonflikt: (Seite) -> Unit = {},
    onFunkErstbegegnung: (Boolean) -> Unit = {},
    onFunkAbbrechen: () -> Unit = {},
    onFunkFertig: () -> Unit = {},
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

    // Ohne die Freigaben findet Nearby gar nichts. Die noetigen unterscheiden sich je
    // Android-Version — die Liste kommt deshalb aus NearbyBerechtigungen, nicht von hier.
    var zeigeBegruendung by remember { mutableStateOf(false) }
    val rechteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { ergebnis ->
        if (ergebnis.values.all { it }) onStarteFunk()
    }
    val rechteAnfordern: () -> Unit = { zeigeBegruendung = true }

    if (zeigeBegruendung) {
        AlertDialog(
            onDismissRequest = { zeigeBegruendung = false },
            title = { Text("Das andere Gerät finden") },
            // Ein Systemdialog aus dem Nichts beantwortet niemand mit Ja — erst recht nicht
            // "Standort erlauben?" bei einem Abgleich zwischen zwei eigenen Geraeten.
            text = { Text(NearbyBerechtigungen.begruendung(android.os.Build.VERSION.SDK_INT)) },
            confirmButton = {
                TextButton(onClick = {
                    zeigeBegruendung = false
                    rechteLauncher.launch(
                        NearbyBerechtigungen.fuer(android.os.Build.VERSION.SDK_INT)
                            .toTypedArray(),
                    )
                }) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(onClick = { zeigeBegruendung = false }) { Text("Abbrechen") }
            },
        )
    }

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
                    text = "Phone und Tablet auf denselben Stand bringen. Beides bleibt " +
                        "erhalten — was auf einem Gerät dazugekommen ist, kommt aufs andere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                )

                // Fortschritt: entweder der Datei-Weg (im ViewModel) oder der Funkweg (in
                // der Sitzung). Beide gleichzeitig gibt es nicht.
                val funkText = when (funkStand) {
                    is Sitzungsstand.Suche -> "Suche das andere Gerät …"
                    is Sitzungsstand.Laeuft -> funkStand.was
                    else -> null
                }
                val funkAnteil = (funkStand as? Sitzungsstand.Laeuft)?.anteil

                if (state.laeuft || funkText != null) {
                    Text(
                        text = funkText ?: state.schritt ?: "Einen Moment …",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (funkAnteil != null) {
                        LinearProgressIndicator(
                            progress = { funkAnteil },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = onFunkAbbrechen) { Text("Abbrechen") }
                }

                if (funkStand is Sitzungsstand.Abgebrochen) {
                    Text(
                        text = funkStand.grund,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoulderBuddy.colors.textSecondary,
                    )
                }

                (funkStand as? Sitzungsstand.Fertig)?.let { BilanzBlock(it.bilanz) }

                PrimaryButton(
                    text = "Mit dem anderen Gerät verbinden",
                    icon = Icons.Outlined.Sync,
                    onClick = { rechteAnfordern() },
                )

                Text(
                    text = "Beide Geräte müssen dafür diesen Bildschirm offen haben und nah " +
                        "beieinander liegen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )

                SectionHeader(text = "Ohne Funk, über eine Datei")

                Text(
                    text = "Für den Notfall und für Sicherungen. Hier braucht es zwei " +
                        "Durchgänge: abgeben, drüben einlesen, dann in die andere Richtung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )

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

    FunkDialoge(
        stand = funkStand,
        onBestaetigen = onBestaetigeVerbindung,
        onKonflikt = onFunkKonflikt,
        onErstbegegnung = onFunkErstbegegnung,
        onAbbrechen = onFunkAbbrechen,
        onFertig = onFunkFertig,
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
 * Alles, was der Funkweg an Rückfragen stellt (Sync-Plan S4/S5).
 *
 * Getrennt vom Datei-Weg, obwohl die Fragen dieselben sind: der Funkweg läuft im Foreground
 * Service weiter, auch wenn dieser Screen kurz verschwindet. Sein Zustand kommt deshalb aus
 * der Sitzung und nicht aus dem ViewModel — und darf hier nur gelesen werden.
 */
@Composable
private fun FunkDialoge(
    stand: Sitzungsstand,
    onBestaetigen: (Boolean) -> Unit,
    onKonflikt: (Seite) -> Unit,
    onErstbegegnung: (Boolean) -> Unit,
    onAbbrechen: () -> Unit,
    onFertig: () -> Unit,
) {
    LaunchedEffect(stand) {
        if (stand is Sitzungsstand.Fertig) onFertig()
    }

    when (stand) {
        is Sitzungsstand.Bestaetigen -> AlertDialog(
            onDismissRequest = { onBestaetigen(false) },
            title = { Text("Ist das dein anderes Gerät?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                    Text(stand.name)
                    Text(
                        text = stand.zahl,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    // Der einzige Schutz davor, den eigenen Stand einem fremden Tablet zu
                    // geben. Nearby prüft die Zahl nicht — der Mensch tut es.
                    Text(
                        "Auf dem anderen Gerät muss dieselbe Zahl stehen. Steht dort eine " +
                            "andere, brich hier ab.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onBestaetigen(true) }) { Text("Passt") } },
            dismissButton = {
                TextButton(onClick = { onBestaetigen(false) }) { Text("Abbrechen") }
            },
        )

        is Sitzungsstand.KonfliktFrage -> KonfliktDialog(
            konflikte = stand.konflikte,
            onWahl = onKonflikt,
            onAbbrechen = onAbbrechen,
        )

        is Sitzungsstand.ErstbegegnungFrage -> ErstbegegnungDialog(
            meine = stand.meine,
            fremde = stand.fremde,
            onUebernehmen = { onErstbegegnung(true) },
            onBehalten = { onErstbegegnung(false) },
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
