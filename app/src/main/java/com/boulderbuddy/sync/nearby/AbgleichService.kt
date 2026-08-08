package com.boulderbuddy.sync.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.boulderbuddy.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hält den Abgleich am Leben, solange er läuft (Sync-Plan S5).
 *
 * Der erste Abgleich kann Gigabyte umfassen — Videos vor allem. Ohne Foreground Service
 * beendet Android die Arbeit, sobald der Bildschirm ausgeht, und zwar mitten in einer
 * Übertragung. Das ist der schlechteste Zeitpunkt: die Medien liegen halb drüben, der Stand
 * ist noch nicht angekommen, und beim nächsten Versuch geht alles von vorn los.
 *
 * Der Dienst rechnet nichts. Er startet [AbgleichSitzung], zeigt an, was gerade passiert,
 * und beendet sich, sobald die Sitzung an einem Ende angekommen ist.
 */
@AndroidEntryPoint
class AbgleichService : Service() {

    @Inject
    lateinit var sitzung: AbgleichSitzung

    private val bereich = CoroutineScope(SupervisorJob())
    private var arbeit: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (arbeit != null) return START_NOT_STICKY

        legeKanalAn()
        starteImVordergrund(getString(R.string.abgleich_laeuft))

        val hatGedrueckt = intent?.getBooleanExtra(EXTRA_GEDRUECKT, true) ?: true
        val name = intent?.getStringExtra(EXTRA_NAME) ?: Build.MODEL

        // Den Zustand mitlesen, um die Meldung aktuell zu halten und am Ende aufzuhören.
        //
        // `lief` ist kein Schnörkel: die Sitzung ist ein Singleton und trägt noch das
        // Ergebnis des letzten Abgleichs. Der Sammler startet, bevor `fuehre` den Zustand
        // zurücksetzt, sähe also sofort „fertig" und beendete den Dienst — der zweite
        // Abgleich käme nie zustande. Erst wenn ein laufender Zustand gesehen wurde, zählt
        // ein Endzustand als Ende.
        bereich.launch {
            var lief = false
            sitzung.stand.collectLatest { stand ->
                when (stand) {
                    is Sitzungsstand.Fertig, is Sitzungsstand.Abgebrochen ->
                        if (lief) hoereAuf()

                    is Sitzungsstand.Laeuft -> {
                        lief = true
                        zeige(stand.was)
                    }

                    is Sitzungsstand.Suche -> {
                        lief = true
                        zeige(getString(R.string.abgleich_suche))
                    }

                    is Sitzungsstand.Bestaetigen -> {
                        lief = true
                        zeige(getString(R.string.abgleich_bestaetigen))
                    }

                    is Sitzungsstand.Untaetig -> lief = true
                    else -> Unit
                }
            }
        }

        arbeit = bereich.launch { sitzung.fuehre(hatGedrueckt, name) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bereich.cancel()
        super.onDestroy()
    }

    private fun hoereAuf() {
        // Nicht den ganzen Bereich abbrechen: die Sitzung räumt in ihrem `finally` noch auf.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun starteImVordergrund(text: String) {
        val typ = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, MELDUNGS_ID, baue(text), typ)
    }

    private fun zeige(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(MELDUNGS_ID, baue(text))
    }

    private fun baue(text: String): Notification =
        NotificationCompat.Builder(this, KANAL_ID)
            .setContentTitle(getString(R.string.abgleich_titel))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            // Der Nutzer soll die App öffnen können, um zu antworten — nicht den Abgleich
            // per Wisch beenden.
            .setSilent(true)
            .build()

    private fun legeKanalAn() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(KANAL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                KANAL_ID,
                getString(R.string.abgleich_kanal),
                // LOW: eine Fortschrittsanzeige soll nicht klingeln.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val KANAL_ID = "abgleich"
        private const val MELDUNGS_ID = 4711
        private const val EXTRA_GEDRUECKT = "gedrueckt"
        private const val EXTRA_NAME = "name"

        /**
         * @param hatGedrueckt ob der Nutzer auf diesem Gerät gestartet hat — bestimmt
         *   zusammen mit der Geräte-ID, wer rechnet (E12).
         */
        fun starte(context: Context, hatGedrueckt: Boolean, anzeigename: String) {
            val intent = Intent(context, AbgleichService::class.java)
                .putExtra(EXTRA_GEDRUECKT, hatGedrueckt)
                .putExtra(EXTRA_NAME, anzeigename)
            context.startForegroundService(intent)
        }

        fun stoppe(context: Context) {
            context.stopService(Intent(context, AbgleichService::class.java))
        }
    }
}
