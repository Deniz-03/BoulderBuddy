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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.boulderbuddy.wear.R
import com.boulderbuddy.wear.data.PhoneConnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ergebnis einer beendeten Auto-Session: gemessene Sätze + Zeitraum (epoch millis). */
data class AutoWorkoutResult(
    val startedAt: Long,
    val endedAt: Long,
    val segments: List<HangSegment>,
    /**
     * Ob der Durchlauf beim Phone angekommen ist. `null` = noch unterwegs.
     *
     * Steht hier, weil die Uhr Workouts **nirgends lokal ablegt**: ohne Verbindung ist der
     * Durchlauf weg. Der Screen hat das vorher trotzdem als "An Phone übertragen" gemeldet.
     */
    val uebertragen: Boolean? = null,
)

/** Live-Zustand der laufenden Auto-Erkennung für den Auto-Screen. */
data class AutoTrackingState(
    val active: Boolean = false,
    val state: HangState = HangState.IDLE,
    /** Beginn der aktuellen Phase (elapsedRealtime ms) — UI rechnet die Laufzeit selbst hoch. */
    val phaseStartElapsedMs: Long = 0,
    val completedSets: Int = 0,
)

/**
 * Foreground-Service der Auto-Hang-Erkennung (M3): treibt den Android-freien [HangDetector]
 * mit dem echten Sensorstrom (GRAVITY + LINEAR_ACCELERATION, ~50 Hz), vibriert bei erkanntem
 * Satz-Start/-Ende (Uhr am Handgelenk — Feedback ohne aufs Display zu schauen) und sammelt
 * die gemessenen Segmente lokal, bis der Nutzer die Session am Auto-Screen beendet.
 *
 * Gleiche Infrastruktur wie [SensorLoggingService] (FGS Typ health, Partial-Wakelock);
 * bewusst getrennte Services — Logging ist Debug-Werkzeug, das ist der Produktivpfad.
 */
class AutoHangService : Service(), SensorEventListener {

    private var detector = HangDetector()
    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAtEpoch = 0L
    private val vibrator: Vibrator? by lazy { resolveVibrator(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_FINISH -> finishTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        if (_tracking.value.active) return
        detector = HangDetector()
        startedAtEpoch = System.currentTimeMillis()
        _result.value = null

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boulderbuddy:autohang")
            .apply { acquire(MAX_WAKELOCK_MS) }

        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = manager
        listOf(Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION).forEach { type ->
            manager.getDefaultSensor(type)?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            } ?: Log.w(TAG, "Sensor $type nicht verfügbar.")
        }

        _tracking.value = AutoTrackingState(
            active = true,
            state = detector.state,
            phaseStartElapsedMs = SystemClock.elapsedRealtime(),
            completedSets = 0,
        )
        Log.d(TAG, "Auto-Erkennung gestartet.")
    }

    private fun finishTracking() {
        if (_tracking.value.active) {
            sensorManager?.unregisterListener(this)
            val segments = detector.finish(SystemClock.elapsedRealtime())
            val endedAt = System.currentTimeMillis()
            _result.value = AutoWorkoutResult(
                startedAt = startedAtEpoch,
                endedAt = endedAt,
                segments = segments,
            )
            // Ergebnis ans Phone (§2 Kanal 1). Welche Session es bekommt, entscheidet das
            // Phone — ob es überhaupt ankommt, muss aber die Uhr wissen und sagen.
            if (segments.isNotEmpty()) {
                PhoneConnector.sendAutoWorkoutCompleted(
                    context = this,
                    startedAt = startedAtEpoch,
                    endedAt = endedAt,
                    segments = segments.map { it.hangMs to it.restMs },
                ) { ok -> _result.value = _result.value?.copy(uebertragen = ok) }
            }
            vibrate(VIBRATE_DONE)
            Log.d(TAG, "Auto-Erkennung beendet: ${segments.size} Sätze.")
        }
        _tracking.value = AutoTrackingState()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val type = when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> SampleType.GRAVITY
            Sensor.TYPE_LINEAR_ACCELERATION -> SampleType.LINEAR
            else -> return
        }
        val sample = SensorSample(
            timeMs = event.timestamp / 1_000_000, // elapsedRealtimeNanos → ms
            type = type,
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
        )
        when (detector.onSample(sample)) {
            is HangDetectionEvent.HangStarted -> vibrate(VIBRATE_PHASE)
            is HangDetectionEvent.HangEnded -> vibrate(VIBRATE_PHASE)
            null -> Unit
        }
        // Zustand für die Live-UI spiegeln (Phasenwechsel + Satz-Zähler).
        val current = _tracking.value
        if (current.active &&
            (current.state != detector.state || current.completedSets != detector.completedSets)
        ) {
            _tracking.value = current.copy(
                state = detector.state,
                phaseStartElapsedMs = detector.phaseStartMs,
                completedSets = detector.completedSets,
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        // Sicherheitsnetz: räumt Sensor + Wakelock ab, falls das System den Service beendet.
        finishTracking()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Auto-Erkennung", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hangboard-Erkennung läuft")
            .setContentText("BoulderBuddy erkennt Sätze automatisch")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    // Haptik-Muster übernommen vom manuellen TimerViewModel (7.2).
    private fun vibrate(millis: Long) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    companion object {
        private const val TAG = "AutoHang"
        private const val CHANNEL_ID = "auto_hang"
        private const val NOTIFICATION_ID = 43
        // Obergrenze als Leak-Schutz; Hangboard-Sessions sind deutlich kürzer.
        private const val MAX_WAKELOCK_MS = 60L * 60 * 1000
        private const val VIBRATE_PHASE = 150L
        private const val VIBRATE_DONE = 500L

        const val ACTION_START = "com.boulderbuddy.wear.sensing.AUTO_START"
        const val ACTION_FINISH = "com.boulderbuddy.wear.sensing.AUTO_FINISH"

        // Beobachtbarer Zustand für den Auto-Screen (Service läuft im selben Prozess).
        private val _tracking = MutableStateFlow(AutoTrackingState())
        val tracking: StateFlow<AutoTrackingState> = _tracking.asStateFlow()
        private val _result = MutableStateFlow<AutoWorkoutResult?>(null)
        val result: StateFlow<AutoWorkoutResult?> = _result.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, AutoHangService::class.java).setAction(ACTION_START),
            )
        }

        fun finish(context: Context) {
            context.startService(
                Intent(context, AutoHangService::class.java).setAction(ACTION_FINISH),
            )
        }

        /** Setzt das angezeigte Ergebnis zurück (beim Verlassen des Summary-Screens). */
        fun clearResult() {
            _result.value = null
        }
    }
}
