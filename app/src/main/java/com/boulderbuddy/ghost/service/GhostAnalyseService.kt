package com.boulderbuddy.ghost.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.boulderbuddy.R
import com.boulderbuddy.ghost.GhostAnalyseRunner
import com.boulderbuddy.ghost.GhostAnalyseStand
import com.boulderbuddy.widget.WidgetIntent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hält die Ghost-Analyse am Leben, solange sie rechnet.
 *
 * Ein Video-Paar braucht am Gerät rund sieben Minuten (289 + 315 Frames). So lange auf dem
 * Bildschirm zu warten will niemand — und genau das verlangte die App vorher, weil die
 * Arbeit am ViewModel hing und mit ihm starb. Der Dienst löst beides: die Rechnung läuft
 * weiter, während der Nutzer etwas anderes tut, und die Notification sagt, wie weit sie ist.
 *
 * Der Dienst rechnet selbst nichts. Er startet den [GhostAnalyseRunner], zeigt dessen Stand
 * an und beendet sich, sobald der Lauf zu Ende ist — fertig, abgebrochen oder gescheitert.
 *
 * Typ `dataSync`: `mediaProcessing` wäre inhaltlich der passendere, es gibt ihn aber erst ab
 * API 35, und die App läuft ab 26. Ein Typ für alle Versionen ist die einfachere Wahrheit
 * als zwei mit einer Versionsweiche.
 */
@AndroidEntryPoint
class GhostAnalyseService : Service() {

    @Inject
    lateinit var runner: GhostAnalyseRunner

    private val bereich = CoroutineScope(SupervisorJob())
    private var gestartet = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Abbrechen kommt als eigener Start herein (Notification-Aktion) und muss VOR der
        // Startsperre unten stehen — sonst führe der Knopf ins Leere, solange gerechnet wird.
        if (intent?.action == ACTION_ABBRECHEN) {
            hoereAuf()
            return START_NOT_STICKY
        }
        if (gestartet) return START_NOT_STICKY
        gestartet = true

        legeKanalAn()
        // Vor jeder Prüfung: `startForegroundService` ist ein Versprechen, das innerhalb
        // weniger Sekunden eingelöst sein muss. Wer erst die Extras prüft und beim Fehlen
        // aussteigt, bricht es — und das System beendet den Prozess mit einer Ausnahme.
        starteImVordergrund()

        val refUri = intent?.getStringExtra(EXTRA_REF)
        val cmpUri = intent?.getStringExtra(EXTRA_CMP)
        if (refUri == null || cmpUri == null) {
            hoereAuf()
            return START_NOT_STICKY
        }

        runner.starte(refUri, cmpUri)

        // Mitlesen für die Anzeige …
        bereich.launch {
            var zuletzt: Anzeige? = null
            runner.stand.collect { stand ->
                val anzeige = anzeigeFuer(stand) ?: return@collect
                // Der Extraktor meldet je Frame, also rund zwölfmal pro Sekunde. So oft zu
                // benachrichtigen ist Arbeit ohne Wirkung — das System zeichnet die Leiste
                // ohnehin nicht in dieser Auflösung. Nur bei sichtbarer Änderung schicken.
                if (anzeige != zuletzt) {
                    zuletzt = anzeige
                    zeige(anzeige)
                }
            }
        }

        // … und getrennt davon auf das Ende warten.
        //
        // Bewusst über den Job und nicht über den Zustand: Das ViewModel quittiert einen
        // Endzustand, sobald der Bildschirm ihn übernommen hat, und setzt ihn dabei auf
        // „untätig" zurück. Wer das Ende am Zustand ablesen wollte, verpasste es genau dann,
        // wenn der Nutzer zusieht — der Dienst liefe endlos weiter.
        bereich.launch {
            runner.warteAufEnde()
            meldeAbschluss(runner.stand.value)
            hoereAuf()
        }

        return START_NOT_STICKY
    }

    /**
     * Bricht auch den Lauf ab, nicht nur den Dienst.
     *
     * Gilt für beide Wege hier heraus: den Abbrechen-Knopf (dann ist das der Zweck) und das
     * reguläre Ende (dann ist der Job längst durch und `cancel` ein Nichts). Ohne diese Zeile
     * rechnete ein abgebrochener Lauf unsichtbar weiter — sieben Minuten Rechenzeit und Akku
     * für ein Ergebnis, das niemand mehr abholt.
     */
    override fun onDestroy() {
        runner.brichAb()
        bereich.cancel()
        super.onDestroy()
    }

    private fun hoereAuf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Letzte Meldung, wenn der Lauf ohne Zuschauer zu Ende ging: die laufende Notification
     * verschwindet mit dem Dienst, und nach sieben Minuten hat der Nutzer die App längst
     * verlassen. Ohne dieses Zeichen erführe er nie, dass er zurückkommen kann.
     *
     * Ist der Zustand bereits quittiert (`Untaetig`), sah der Bildschirm das Ergebnis schon —
     * dann wäre die Meldung nur Lärm.
     */
    private fun meldeAbschluss(stand: GhostAnalyseStand) {
        val text = when (stand) {
            is GhostAnalyseStand.Fertig -> getString(R.string.ghost_analyse_fertig)
            is GhostAnalyseStand.Fehler -> stand.meldung
            else -> return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            ABSCHLUSS_ID,
            NotificationCompat.Builder(this, KANAL_ID)
                .setContentTitle(getString(R.string.ghost_analyse_titel))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification_ghost)
                .setContentIntent(zurueckZurApp())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun starteImVordergrund() {
        val typ = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        val anfang = Anzeige(text = getString(R.string.ghost_analyse_startet), prozent = null)
        ServiceCompat.startForeground(this, MELDUNGS_ID, baue(anfang), typ)
    }

    private fun zeige(anzeige: Anzeige) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(MELDUNGS_ID, baue(anzeige))
    }

    private fun baue(anzeige: Anzeige): Notification =
        NotificationCompat.Builder(this, KANAL_ID)
            .setContentTitle(getString(R.string.ghost_analyse_titel))
            .setContentText(anzeige.text)
            .setSmallIcon(R.drawable.ic_notification_ghost)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, anzeige.prozent ?: 0, anzeige.prozent == null)
            .setContentIntent(zurueckZurApp())
            .addAction(
                0,
                getString(R.string.ghost_analyse_abbrechen),
                abbruchAktion(),
            )
            .build()

    /** Tippen führt zurück in den Ghost-Climber-Bildschirm (Deep-Link-Muster des Widgets). */
    private fun zurueckZurApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            WidgetIntent.toApp(this, WidgetIntent.TARGET_GHOST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun abbruchAktion(): PendingIntent =
        PendingIntent.getService(
            this,
            0,
            Intent(this, GhostAnalyseService::class.java).setAction(ACTION_ABBRECHEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun legeKanalAn() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(KANAL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                KANAL_ID,
                getString(R.string.ghost_analyse_kanal),
                // LOW: eine Fortschrittsanzeige soll nicht klingeln.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /** Was in der Notification steht — als Wert, damit „hat sich etwas geändert?" trivial ist. */
    private data class Anzeige(val text: String, val prozent: Int?)

    private fun anzeigeFuer(stand: GhostAnalyseStand): Anzeige? {
        val laeuft = stand as? GhostAnalyseStand.Laeuft ?: return null
        val fortschritt = if (laeuft.beiVergleich) laeuft.cmp else laeuft.ref
        val video = getString(
            if (laeuft.beiVergleich) R.string.ghost_video_vergleich else R.string.ghost_video_referenz,
        )
        return Anzeige(
            text = if (fortschritt.gesamt > 0) {
                getString(
                    R.string.ghost_analyse_fortschritt,
                    video,
                    fortschritt.fertig,
                    fortschritt.gesamt,
                )
            } else {
                getString(R.string.ghost_analyse_startet)
            },
            prozent = laeuft.anteil?.let { (it * 100).toInt() },
        )
    }

    companion object {
        private const val KANAL_ID = "ghost_analyse"
        private const val MELDUNGS_ID = 4712
        private const val ABSCHLUSS_ID = 4713
        private const val ACTION_ABBRECHEN = "com.boulderbuddy.ghost.ABBRECHEN"
        private const val EXTRA_REF = "ref_uri"
        private const val EXTRA_CMP = "cmp_uri"

        fun starte(context: Context, refUri: String, cmpUri: String) {
            val intent = Intent(context, GhostAnalyseService::class.java)
                .putExtra(EXTRA_REF, refUri)
                .putExtra(EXTRA_CMP, cmpUri)
            context.startForegroundService(intent)
        }

        fun stoppe(context: Context) {
            context.stopService(Intent(context, GhostAnalyseService::class.java))
        }
    }
}
