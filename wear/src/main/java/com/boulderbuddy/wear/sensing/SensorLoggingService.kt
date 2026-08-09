package com.boulderbuddy.wear.sensing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.boulderbuddy.wear.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground-Service für die Sensor-Aufzeichnung auf der Uhr (B.5.1 Debug-Logging).
 *
 * Zeichnet `TYPE_GRAVITY` (Arm-Orientierung) und `TYPE_LINEAR_ACCELERATION` (Bewegung ohne
 * Gravitation) mit ~50 Hz (`SENSOR_DELAY_GAME`) in eine CSV-Datei auf. Labels („jetzt Hängen /
 * jetzt Pause") setzt der [com.boulderbuddy.wear.presentation.SensorLogScreen] per Intent —
 * sie landen als eigene Zeilen im selben Zeitstrahl und machen die Logs offline auswertbar
 * (Schwellen-Bestimmung B.5.3, State-Machine-Tests B.5.4).
 *
 * Foreground + kurzer Partial-Wakelock, damit die CPU während des Hängens (Display aus,
 * Arm ruhig) nicht einschläft. FGS-Typ `health` (Workout-Aufzeichnung am Körper).
 *
 * CSV-Format (eine Zeile je Ereignis, gemeinsame Zeitbasis elapsedRealtime in ms):
 * ```
 * tMs;GRAV;x;y;z
 * tMs;LIN;x;y;z
 * tMs;LABEL;HANG|REST|NONE
 * ```
 */
class SensorLoggingService : Service(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var writer: BufferedWriter? = null
    private var logFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLogging()
            ACTION_LABEL -> writeLabel(intent.getStringExtra(EXTRA_LABEL) ?: LABEL_NONE)
            ACTION_STOP -> stopLogging()
        }
        return START_NOT_STICKY
    }

    private fun startLogging() {
        if (_isRecording.value) return

        val dir = File(filesDir, LOG_DIR).apply { mkdirs() }
        val name = "sensorlog_${FILE_TIMESTAMP.format(Date())}.csv"
        val file = File(dir, name)
        writer = file.bufferedWriter().apply {
            write("# BoulderBuddy Sensor-Log v1 — tMs;GRAV|LIN;x;y;z bzw. tMs;LABEL;<label>")
            newLine()
        }
        logFile = file

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boulderbuddy:sensorlog")
            .apply { acquire(MAX_WAKELOCK_MS) }

        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager
        listOf(Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION).forEach { type ->
            val sensor = manager.getDefaultSensor(type)
            if (sensor == null) {
                Log.w(TAG, "Sensor $type nicht verfügbar auf diesem Gerät.")
            } else {
                manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        _isRecording.value = true
        _currentLabel.value = LABEL_NONE
        writeLabel(LABEL_NONE)
        Log.d(TAG, "Aufzeichnung gestartet: $name")
    }

    private fun stopLogging() {
        if (_isRecording.value) {
            sensorManager?.unregisterListener(this)
            synchronized(this) {
                writer?.flush()
                writer?.close()
                writer = null
            }
            _lastLogFile.value = logFile
            Log.d(TAG, "Aufzeichnung beendet: ${logFile?.name} (${logFile?.length()} Bytes)")
        }
        _isRecording.value = false
        _currentLabel.value = LABEL_NONE
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun writeLabel(label: String) {
        if (writer == null) return
        _currentLabel.value = label
        writeLine("${SystemClock.elapsedRealtime()};LABEL;$label")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val tag = when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> "GRAV"
            Sensor.TYPE_LINEAR_ACCELERATION -> "LIN"
            else -> return
        }
        // event.timestamp ist elapsedRealtimeNanos → gleiche Zeitbasis wie die Label-Zeilen.
        val tMs = event.timestamp / 1_000_000
        writeLine("$tMs;$tag;${event.values[0]};${event.values[1]};${event.values[2]}")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // Sensor-Callbacks und Label-Intents können auf verschiedenen Threads eintreffen.
    private fun writeLine(line: String) = synchronized(this) {
        writer?.apply {
            write(line)
            newLine()
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.dienst_log_kanal),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.dienst_log_titel))
            .setContentText(getString(R.string.dienst_log_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        // Sicherheitsnetz, falls das System den Service abräumt, ohne dass STOP kam.
        stopLogging()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SensorLogging"
        private const val CHANNEL_ID = "sensor_logging"
        private const val NOTIFICATION_ID = 42
        private const val LOG_DIR = "sensorlogs"
        // Obergrenze als Leak-Schutz; eine Kalibrier-Session ist deutlich kürzer.
        private const val MAX_WAKELOCK_MS = 30L * 60 * 1000

        const val ACTION_START = "com.boulderbuddy.wear.sensing.START"
        const val ACTION_STOP = "com.boulderbuddy.wear.sensing.STOP"
        const val ACTION_LABEL = "com.boulderbuddy.wear.sensing.LABEL"
        const val EXTRA_LABEL = "label"
        const val LABEL_HANG = "HANG"
        const val LABEL_REST = "REST"
        const val LABEL_NONE = "NONE"

        private val FILE_TIMESTAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        // Beobachtbarer Zustand für den Debug-Screen (Service läuft im selben Prozess).
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
        private val _currentLabel = MutableStateFlow(LABEL_NONE)
        val currentLabel: StateFlow<String> = _currentLabel.asStateFlow()
        private val _lastLogFile = MutableStateFlow<File?>(null)
        val lastLogFile: StateFlow<File?> = _lastLogFile.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, SensorLoggingService::class.java).setAction(ACTION_START),
            )
        }

        fun label(context: Context, label: String) {
            context.startService(
                Intent(context, SensorLoggingService::class.java)
                    .setAction(ACTION_LABEL)
                    .putExtra(EXTRA_LABEL, label),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SensorLoggingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
