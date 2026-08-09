package com.boulderbuddy.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.boulderbuddy.R
import com.boulderbuddy.data.camera.CameraCaptureController
import com.boulderbuddy.data.camera.CaptureAuftrag
import com.boulderbuddy.data.camera.CaptureFehler
import com.boulderbuddy.data.camera.CaptureModus
import com.boulderbuddy.data.camera.CaptureState
import com.boulderbuddy.data.camera.VideoEreignis
import com.boulderbuddy.data.camera.darfModusWechseln
import com.boulderbuddy.data.camera.formatAufnahmedauer
import com.boulderbuddy.data.camera.mussAutomatischStoppen
import com.boulderbuddy.data.camera.startModusFuer
import com.boulderbuddy.ui.components.EmptyState
import com.boulderbuddy.ui.theme.BoulderBuddy
import com.boulderbuddy.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * Eigener Aufnahme-Screen auf CameraX (Phase 7.4d) — Ersatz für den Kamera-Intent.
 *
 * Der Grund für den eigenen Screen ist nicht das Aussehen, sondern die **Bestimmbarkeit**:
 * über einen Intent liefert die installierte Kamera-App eine beliebige Auflösung und
 * Kompression. Der Ghost Climber vergleicht zwei Videos miteinander; dort ist eine feste
 * Aufnahmeseite ein echter Vorteil (siehe [CameraCaptureController]).
 *
 * Der Screen ist absichtlich vollflächig statt im `BoulderBuddyScaffold`: eine Kamera-Vorschau
 * mit Kopfzeile und Bottom-Nav darüber verschenkt genau den Platz, auf den es ankommt.
 *
 * @param auftrag legt fest, was aufgenommen werden darf (Ghost: nur Video).
 * @param onErgebnis liefert die `content://`-URI der fertigen Aufnahme als String.
 */
@Composable
fun KameraScreen(
    auftrag: CaptureAuftrag,
    onErgebnis: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hatFreigabe by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var freigabeAbgelehnt by remember { mutableStateOf(false) }
    // Nach der **ersten** Ablehnung fragt Android weiterhin; erst nach der zweiten ist der
    // Dialog endgültig zu. `shouldShowRequestPermissionRationale` unterscheidet genau das:
    // `true` = es lohnt sich, mit Begründung nochmal zu fragen, `false` = nur noch über die
    // System-Einstellungen. Ohne diese Unterscheidung wirkte schon ein versehentliches
    // „Nicht erlauben" endgültig und schickte den Nutzer unnötig in die Einstellungen.
    var darfNochFragen by remember { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hatFreigabe = granted
        freigabeAbgelehnt = !granted
        darfNochFragen = !granted && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA,
            )
    }

    var modus by remember { mutableStateOf(startModusFuer(auftrag)) }
    var vorderkamera by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<CaptureState>(CaptureState.Bereit) }

    val controller = remember { CameraCaptureController(context) }
    val previewView = remember { PreviewView(context) }

    // Eine laufende Aufnahme wird beim Verlassen verworfen, nicht gespeichert — wer den
    // Screen verlässt, will das Video nicht.
    DisposableEffect(controller) {
        onDispose { controller.freigeben() }
    }

    // Neu binden, sobald sich Modus oder Objektiv ändern. Foto und Video laufen bewusst nicht
    // gleichzeitig gebunden (nicht auf jedem Gerät zugesichert).
    LaunchedEffect(hatFreigabe, modus, vorderkamera) {
        if (!hatFreigabe) return@LaunchedEffect
        val rotation = ContextCompat.getDisplayOrDefault(context).rotation
        val ergebnis = controller.bind(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = previewView.surfaceProvider,
            modus = modus,
            vorderkamera = vorderkamera,
            rotation = rotation,
        )
        state = if (ergebnis.isFailure) {
            CaptureState.Fehler(CaptureFehler.KEINE_KAMERA)
        } else {
            CaptureState.Bereit
        }
    }

    if (!hatFreigabe) {
        KameraFreigabeFehlt(
            abgelehnt = freigabeAbgelehnt,
            darfNochFragen = darfNochFragen,
            onAnfragen = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onBack,
        )
        return
    }

    fun videoEreignis(ereignis: VideoEreignis) {
        when (ereignis) {
            is VideoEreignis.Laeuft -> {
                state = CaptureState.VideoLaeuft(ereignis.dauerMs)
                // Obergrenze: der Screen stoppt selbst, statt bis zum vollen Speicher zu laufen.
                if (mussAutomatischStoppen(ereignis.dauerMs)) {
                    state = CaptureState.WirdGespeichert
                    controller.videoStoppen()
                }
            }

            is VideoEreignis.Fertig -> onErgebnis(ereignis.uri.toString())
            VideoEreignis.Fehlgeschlagen ->
                state = CaptureState.Fehler(CaptureFehler.AUFNAHME_FEHLGESCHLAGEN)
        }
    }

    fun ausloesen() {
        when {
            state is CaptureState.VideoLaeuft -> {
                state = CaptureState.WirdGespeichert
                controller.videoStoppen()
            }

            modus == CaptureModus.VIDEO -> {
                state = CaptureState.VideoLaeuft(0L)
                controller.videoStarten(::videoEreignis)
            }

            else -> scope.launch {
                state = CaptureState.FotoLaeuft
                controller.fotoAufnehmen()
                    .onSuccess { onErgebnis(it.toString()) }
                    .onFailure { state = CaptureState.Fehler(CaptureFehler.SPEICHERN_FEHLGESCHLAGEN) }
            }
        }
    }

    val nimmtAuf = state is CaptureState.VideoLaeuft
    // Während einer laufenden Aufnahme sind Moduswechsel und Objektivwechsel gesperrt: beides
    // würde die Kamera neu binden und die Aufnahme mittendrin abreißen lassen.
    val bedienbar = state !is CaptureState.FotoLaeuft && state !is CaptureState.WirdGespeichert

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // --- Kopfzeile über der Vorschau ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(Dimens.paddingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.aktion_abbrechen),
                    tint = Color.White,
                )
            }
            if (nimmtAuf) {
                val dauer = (state as CaptureState.VideoLaeuft).dauerMs
                Aufnahmeanzeige(dauer)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Dimens.paddingXL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingL),
        ) {
            (state as? CaptureState.Fehler)?.let {
                Text(
                    text = it.grund.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Dimens.paddingXL),
                )
            }

            if (darfModusWechseln(auftrag) && !nimmtAuf) {
                ModusUmschalter(
                    modus = modus,
                    enabled = bedienbar,
                    onWechsel = { modus = it },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Platzhalter links, damit der Auslöser mittig bleibt.
                Box(modifier = Modifier.size(AUSLOESER_GROESSE))
                Ausloeser(
                    modus = modus,
                    nimmtAuf = nimmtAuf,
                    enabled = bedienbar,
                    onClick = ::ausloesen,
                )
                IconButton(
                    onClick = { vorderkamera = !vorderkamera },
                    enabled = bedienbar && !nimmtAuf,
                    modifier = Modifier.size(AUSLOESER_GROESSE),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.kamera_wechseln),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** Roter Punkt plus mitlaufende Zeit — die Rückmeldung, dass wirklich aufgezeichnet wird. */
@Composable
private fun Aufnahmeanzeige(dauerMs: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = Dimens.paddingM, vertical = Dimens.paddingXS),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(BoulderBuddy.colors.routes.red),
        )
        Text(
            text = formatAufnahmedauer(dauerMs),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun ModusUmschalter(
    modus: CaptureModus,
    enabled: Boolean,
    onWechsel: (CaptureModus) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(Dimens.paddingXS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingXS),
    ) {
        ModusKnopf(
            text = stringResource(R.string.kamera_foto),
            aktiv = modus == CaptureModus.FOTO,
            enabled = enabled,
            onClick = { onWechsel(CaptureModus.FOTO) },
        )
        ModusKnopf(
            text = stringResource(R.string.kamera_video),
            aktiv = modus == CaptureModus.VIDEO,
            enabled = enabled,
            onClick = { onWechsel(CaptureModus.VIDEO) },
        )
    }
}

@Composable
private fun ModusKnopf(
    text: String,
    aktiv: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        // Weiß auf Schwarz, nicht die Theme-Tokens: über einer Kamera-Vorschau gibt es keine
        // Themenfläche, gegen die ein Token Kontrast garantieren könnte.
        color = if (aktiv) Color.Black else Color.White,
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(if (aktiv) Color.White else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.paddingL, vertical = Dimens.paddingS),
    )
}

/**
 * Klassischer Auslöserring; im Video-Modus wird der Kern zum Stopp-Quadrat.
 *
 * Der Ring ist reine Grafik — ein Kreis aus `border` und ein weißer `Box`-Kern, kein `Icon` und
 * kein `Text`. Ohne die Beschriftung unten hätte der Knopf für TalkBack deshalb **gar keinen
 * Namen**, und die Aufnahme wäre mit Screenreader nicht auslösbar. Aufgefallen im Gerätelauf am
 * 09.08.2026: alle Nachbarn waren beschriftet, ausgerechnet der Auslöser nicht.
 */
@Composable
private fun Ausloeser(
    modus: CaptureModus,
    nimmtAuf: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val beschriftung = when {
        nimmtAuf -> stringResource(R.string.kamera_aufnahme_beenden)
        modus == CaptureModus.VIDEO -> stringResource(R.string.kamera_aufnahme_starten)
        else -> stringResource(R.string.kamera_foto_aufnehmen)
    }
    Box(
        modifier = Modifier
            .size(AUSLOESER_GROESSE)
            .clip(CircleShape)
            .border(3.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = beschriftung
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (nimmtAuf) {
            Icon(
                imageVector = Icons.Filled.Stop,
                // Der Name sitzt jetzt am Knopf selbst; hier wäre er eine Dopplung, die
                // TalkBack zweimal vorlesen würde.
                contentDescription = null,
                tint = BoulderBuddy.colors.routes.red,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/**
 * Zustand ohne Kamera-Freigabe. Kein Rückfall auf einen Kamera-Intent wie bei der
 * Spracheingabe: dort gibt es einen System-Dialog, der die Aufnahme im fremden Prozess macht —
 * hier wäre der Intent-Weg genau der, dessen Unbestimmtheit der Grund für diesen Screen ist.
 * Wer die Freigabe verweigert, kommt über die Galerie weiter.
 */
@Composable
private fun KameraFreigabeFehlt(
    abgelehnt: Boolean,
    darfNochFragen: Boolean,
    onAnfragen: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BoulderBuddy.colors.surfaceBackground)
            .statusBarsPadding(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(Dimens.paddingS)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.aktion_zurueck),
                tint = BoulderBuddy.colors.textSecondary,
            )
        }
        EmptyState(
            modifier = Modifier.align(Alignment.Center),
            icon = Icons.Outlined.PhotoCamera,
            title = stringResource(R.string.kamera_freigabe_titel),
            description = when {
                abgelehnt && darfNochFragen ->
                    stringResource(R.string.kamera_freigabe_abgelehnt_nochmal)

                abgelehnt ->
                    stringResource(R.string.kamera_freigabe_abgelehnt_endgueltig)

                else ->
                    stringResource(R.string.kamera_freigabe_erklaerung)
            },
            // Der Knopf verschwindet nur, wenn Android wirklich nicht mehr fragt. Ein Knopf,
            // der einen Dialog verspricht, der nicht mehr kommt, wäre schlimmer als keiner.
            actionText = when {
                abgelehnt && darfNochFragen -> stringResource(R.string.kamera_erneut_fragen)
                abgelehnt -> null
                else -> stringResource(R.string.kamera_freigeben)
            },
            onAction = if (abgelehnt && !darfNochFragen) null else onAnfragen,
        )
    }
}

/**
 * Die [Activity] hinter einem Compose-[Context]. `LocalContext` liefert nicht zwingend die
 * Activity selbst, sondern kann ein [ContextWrapper] darum sein — deshalb die Kette abwärts.
 * Gebraucht für `shouldShowRequestPermissionRationale`, das eine Activity verlangt.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val AUSLOESER_GROESSE = 72.dp
