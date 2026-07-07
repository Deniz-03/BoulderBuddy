package com.boulderbuddy.proximity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.boulderbuddy.MainActivity
import com.boulderbuddy.R
import com.boulderbuddy.widget.WidgetIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Contract für den Sprung Notification → App: Session-Formular mit vorbefülltem Gym (M4). */
object ProximityIntent {
    /** Int-Extra mit der Gym-ID; ergänzt [WidgetIntent.TARGET_NEW_SESSION] als Nav-Ziel. */
    const val EXTRA_GYM_ID = "com.boulderbuddy.proximity.GYM_ID"
}

/**
 * Zeigt die "Session starten?"-Notification des Gym-Näherungs-Push (M4).
 *
 * Der Tap nutzt exakt das Widget-Deep-Link-Muster (7.4c): MainActivity-Intent mit
 * [WidgetIntent.EXTRA_NAV_TARGET] = TARGET_NEW_SESSION, plus [ProximityIntent.EXTRA_GYM_ID]
 * fürs Vorbefüllen des Ort-Felds in `SessionErstellen`.
 */
@Singleton
class ProximityNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun showSessionReminder(gymId: Int, gymName: String) {
        // POST_NOTIFICATIONS ist ab API 33 Runtime-Permission (wird beim Feature-Opt-in
        // angefragt); ohne Grant degradiert der Push still.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel()

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WidgetIntent.EXTRA_NAV_TARGET, WidgetIntent.TARGET_NEW_SESSION)
            putExtra(ProximityIntent.EXTRA_GYM_ID, gymId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            // requestCode = gymId: unterscheidbare PendingIntents pro Gym.
            gymId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_gym)
            .setContentTitle("Bist du im $gymName?")
            .setContentText("Session starten?")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Notification-ID = gymId: ein neuer Push fürs selbe Gym ersetzt den alten.
        NotificationManagerCompat.from(context).notify(gymId, notification)
    }

    // Channel einmalig anlegen (idempotent — createNotificationChannel ist ein No-Op,
    // wenn er schon existiert).
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gym-Erinnerungen",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Erinnert dich, eine Session zu starten, wenn du an einer Halle ankommst."
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "gym_proximity"
    }
}
