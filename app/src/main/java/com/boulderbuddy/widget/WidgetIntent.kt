package com.boulderbuddy.widget

import android.content.Context
import android.content.Intent
import com.boulderbuddy.MainActivity

/**
 * Contract für den Sprung Widget → App (7.4c). Das Widget startet [MainActivity] mit einem
 * Ziel-Extra; die Activity reicht es an die Navigation weiter (siehe `AppNavigation`).
 */
object WidgetIntent {
    const val EXTRA_NAV_TARGET = "com.boulderbuddy.widget.NAV_TARGET"

    /** Direkt zum Hangboard-Timer. */
    const val TARGET_TIMER = "timer"

    /** Direkt in den „Session starten"-Flow. */
    const val TARGET_NEW_SESSION = "new_session"

    /** Explizites [Intent] auf [MainActivity] mit optionalem Navigationsziel [target]. */
    fun toApp(context: Context, target: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (target != null) putExtra(EXTRA_NAV_TARGET, target)
        }
}
