package com.boulderbuddy.data.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Vibrationsmuster des Hangboard-Timers. */
enum class HapticPattern {
    /** Kurzer Impuls: Wechsel zwischen Hängen und Pause. */
    PHASENWECHSEL,

    /** Doppelimpuls: Durchlauf komplett. */
    FERTIG,
}

/**
 * Spielt kurze Vibrationen ab. Als Interface hinterlegt, damit ViewModels ohne Android-Framework
 * getestet werden können — das Gerät kommt nur in [SystemHapticPlayer] vor.
 *
 * Der Nutzer-Schalter („Haptisches Feedback") wird **nicht** hier ausgewertet, sondern beim
 * Aufrufer: so bleibt diese Klasse eine reine Ausgabe ohne Einstellungs-Abhängigkeit.
 */
interface HapticPlayer {
    fun play(pattern: HapticPattern)
}

@Singleton
class SystemHapticPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HapticPlayer {

    // Lazy, weil der Service auf Geräten ohne Vibrationsmotor null sein kann.
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    override fun play(pattern: HapticPattern) {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = when (pattern) {
            HapticPattern.PHASENWECHSEL -> VibrationEffect.createOneShot(
                PHASENWECHSEL_MS,
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
            // timings/amplitudes im Wechsel: warten – vibrieren – warten – vibrieren.
            HapticPattern.FERTIG -> VibrationEffect.createWaveform(
                longArrayOf(0, FERTIG_MS, FERTIG_PAUSE_MS, FERTIG_MS),
                -1,
            )
        }
        device.vibrate(effect)
    }

    private companion object {
        const val PHASENWECHSEL_MS = 120L
        const val FERTIG_MS = 200L
        const val FERTIG_PAUSE_MS = 120L
    }
}
