package com.boulderbuddy.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.PluralsRes
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
import androidx.compose.material.icons.automirrored.outlined.Undo
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.boulderbuddy.R
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
            title = { Text(stringResource(R.string.abgleich_rechte_titel)) },
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
                }) { Text(stringResource(R.string.abgleich_rechte_weiter)) }
            },
            dismissButton = {
                TextButton(onClick = { zeigeBegruendung = false }) {
                    Text(stringResource(R.string.aktion_abbrechen))
                }
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
                title = stringResource(R.string.einstellungen_abgleich),
                navIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.aktion_zurueck),
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
                    text = stringResource(R.string.abgleich_hinweis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoulderBuddy.colors.textSecondary,
                )

                // Fortschritt: entweder der Datei-Weg (im ViewModel) oder der Funkweg (in
                // der Sitzung). Beide gleichzeitig gibt es nicht.
                val funkText = when (funkStand) {
                    is Sitzungsstand.Suche -> stringResource(R.string.abgleich_suche)
                    is Sitzungsstand.Laeuft -> funkStand.was
                    else -> null
                }
                val funkAnteil = (funkStand as? Sitzungsstand.Laeuft)?.anteil

                if (state.laeuft || funkText != null) {
                    Text(
                        text = funkText ?: state.schritt
                            ?: stringResource(R.string.abgleich_moment),
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
                    TextButton(onClick = onFunkAbbrechen) {
                        Text(stringResource(R.string.aktion_abbrechen))
                    }
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
                    text = stringResource(R.string.abgleich_verbinden),
                    icon = Icons.Outlined.Sync,
                    onClick = { rechteAnfordern() },
                )

                Text(
                    text = stringResource(R.string.abgleich_verbinden_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )

                SectionHeader(text = stringResource(R.string.abgleich_datei_ueberschrift))

                Text(
                    text = stringResource(R.string.abgleich_datei_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = BoulderBuddy.colors.textSecondary,
                )

                PrimaryButton(
                    text = stringResource(R.string.abgleich_abgeben),
                    icon = Icons.Outlined.FileUpload,
                    onClick = { abgeben.launch(abgabeName) },
                )
                PrimaryButton(
                    text = stringResource(R.string.abgleich_einlesen),
                    icon = Icons.Outlined.FileDownload,
                    onClick = { einlesen.launch(arrayOf("*/*")) },
                )

                state.bilanz?.let { BilanzBlock(it) }

                if (state.kannRueckgaengig) {
                    SectionHeader(
                        text = stringResource(R.string.abgleich_rueckgaengig_ueberschrift),
                    )
                    SettingsRow(
                        icon = Icons.AutoMirrored.Outlined.Undo,
                        label = stringResource(R.string.abgleich_rueckgaengig),
                        subtitle = stringResource(R.string.abgleich_rueckgaengig_hinweis),
                        onClick = onRueckgaengig,
                    )
                }

                if (state.neustartNoetig) {
                    Text(
                        text = stringResource(R.string.abgleich_neustart),
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
            title = { Text(stringResource(R.string.abgleich_bestaetigen_titel)) },
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
                        stringResource(R.string.abgleich_bestaetigen_hinweis),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onBestaetigen(true) }) {
                    Text(stringResource(R.string.abgleich_bestaetigen_ja))
                }
            },
            dismissButton = {
                TextButton(onClick = { onBestaetigen(false) }) {
                    Text(stringResource(R.string.aktion_abbrechen))
                }
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
        title = { Text(stringResource(R.string.abgleich_erst_titel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                Text(stringResource(R.string.abgleich_erst_text))
                Text(stringResource(R.string.abgleich_erst_meine, beschreibe(meine)))
                Text(stringResource(R.string.abgleich_erst_fremde, beschreibe(fremde)))
                Text(
                    stringResource(R.string.abgleich_erst_danach),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUebernehmen) {
                Text(stringResource(R.string.abgleich_erst_uebernehmen))
            }
        },
        dismissButton = {
            TextButton(onClick = onBehalten) {
                Text(stringResource(R.string.abgleich_erst_behalten))
            }
        },
    )
}

/**
 * „12 Hallen, 3 Sessions, …" — jede Zahl mit ihrer eigenen Einzahl.
 *
 * Composable, weil die Mehrzahlformen aus den Ressourcen kommen. Das ist der Preis dafür,
 * dass hier nicht „1 Sessions" steht, und er ist es wert: diese Zeile ist die einzige
 * Entscheidungsgrundlage dafür, welcher Stand gleich verworfen wird.
 */
@Composable
private fun beschreibe(zahlen: Bestandszahlen): String {
    val hallen = pluralStringResource(R.plurals.abgleich_hallen, zahlen.hallen, zahlen.hallen)
    val sessions =
        pluralStringResource(R.plurals.abgleich_sessions, zahlen.sessions, zahlen.sessions)
    val boulder =
        pluralStringResource(R.plurals.abgleich_boulder, zahlen.boulder, zahlen.boulder)
    val trainings =
        pluralStringResource(R.plurals.abgleich_trainings, zahlen.trainings, zahlen.trainings)
    val analysen =
        pluralStringResource(R.plurals.abgleich_analysen, zahlen.analysen, zahlen.analysen)

    val teile = mutableListOf(hallen, sessions, boulder, trainings)
    if (zahlen.analysen > 0) teile += analysen
    return teile.joinToString(", ")
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
                pluralStringResource(
                    R.plurals.abgleich_konflikt_titel,
                    konflikte.size,
                    konflikte.size,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingM)) {
                Text(stringResource(R.string.abgleich_konflikt_frage))
                konflikte.take(HOECHSTENS_GENANNT).forEach {
                    Text(stringResource(R.string.abgleich_konflikt_punkt, beschreibe(it)))
                }
                val weitere = konflikte.size - HOECHSTENS_GENANNT
                if (weitere > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.abgleich_konflikt_weitere,
                            weitere,
                            weitere,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.abgleich_konflikt_nur_diese),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onWahl(Seite.MEINS) }) {
                Text(stringResource(R.string.abgleich_konflikt_meins))
            }
        },
        dismissButton = {
            TextButton(onClick = { onWahl(Seite.FREMDES) }) {
                Text(stringResource(R.string.abgleich_konflikt_fremdes))
            }
        },
    )
}

/** So viele Konflikte werden einzeln genannt; der Rest wird gezählt. */
private const val HOECHSTENS_GENANNT = 8

/**
 * „Halle: auf beiden Geräten geändert".
 *
 * Der Tabellenname ist der Rückfall, kein Ziel — steht hier je ein Name des Datenmodells,
 * ist das ein vergessener Fall und keine Übersetzung.
 */
@Composable
private fun beschreibe(konflikt: Konflikt): String {
    val was = when (konflikt.tabelle) {
        "gym" -> stringResource(R.string.abgleich_was_halle)
        "session" -> stringResource(R.string.abgleich_was_session)
        "route" -> stringResource(R.string.abgleich_was_boulder)
        "grade" -> stringResource(R.string.abgleich_was_grad)
        "grade_system" -> stringResource(R.string.abgleich_was_gradsystem)
        "hangboard_workout" -> stringResource(R.string.abgleich_was_hangboard_training)
        "hangboard_segment" -> stringResource(R.string.abgleich_was_hangboard_satz)
        "hangboard_template" -> stringResource(R.string.abgleich_was_timer_vorgabe)
        "ghost_analysis" -> stringResource(R.string.abgleich_was_ghost)
        else -> konflikt.tabelle
    }
    val wie = when (konflikt.art) {
        KonfliktArt.BEIDSEITIG_GEAENDERT -> stringResource(R.string.abgleich_wie_beidseitig)
        KonfliktArt.GELOESCHT_GEGEN_GEAENDERT ->
            stringResource(R.string.abgleich_wie_geloescht_geaendert)
        KonfliktArt.TEILBAUM -> stringResource(R.string.abgleich_wie_teilbaum)
        KonfliktArt.GLEICHE_NUMMER -> stringResource(R.string.abgleich_wie_gleiche_nummer)
    }
    return stringResource(R.string.abgleich_konflikt_zeile, was, wie)
}

/**
 * Die Bilanz. Ein Abgleich ohne Rückmeldung fühlt sich an wie nichts — und Löschungen
 * müssen ausdrücklich dastehen, sonst wirkt das spätere Fehlen wie ein Fehler (Ablauf 3).
 */
@Composable
private fun BilanzBlock(bilanz: Bilanz) {
    SectionHeader(text = stringResource(R.string.abgleich_ergebnis))
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingS)) {
        if (bilanz.nichtsZuTun && bilanz.konfliktVerluste == 0) {
            Text(stringResource(R.string.abgleich_nichts_zu_tun))
            return@Column
        }
        if (bilanz.uebernommen > 0) {
            Text(zeile(R.plurals.abgleich_uebernommen, bilanz.uebernommen))
        }
        if (bilanz.abgegeben > 0) {
            Text(zeile(R.plurals.abgleich_abgegeben, bilanz.abgegeben))
        }
        if (bilanz.geloescht > 0) {
            Text(zeile(R.plurals.abgleich_geloescht, bilanz.geloescht))
        }
        if (bilanz.konfliktVerluste > 0) {
            Text(zeile(R.plurals.abgleich_verworfen, bilanz.konfliktVerluste))
        }
        if (bilanz.bezuegeGeloest > 0) {
            Text(zeile(R.plurals.abgleich_bezuege_geloest, bilanz.bezuegeGeloest))
        }
    }
}

/** Eine Bilanzzeile: dieselbe Zahl bestimmt die Mehrzahlform und steht im Text. */
@Composable
private fun zeile(@PluralsRes plural: Int, anzahl: Int): String =
    pluralStringResource(plural, anzahl, anzahl)

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
