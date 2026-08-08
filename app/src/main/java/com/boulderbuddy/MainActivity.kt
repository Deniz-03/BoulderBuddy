package com.boulderbuddy

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulderbuddy.proximity.ProximityIntent
import com.boulderbuddy.ui.navigation.AppNavigation
import com.boulderbuddy.ui.theme.BoulderBuddyTheme
import com.boulderbuddy.ui.viewmodel.ThemeViewModel
import com.boulderbuddy.widget.WidgetIntent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    /**
     * Offenes Sprungziel. Als Flow und nicht als einmalig gelesener Wert, weil es auf zwei
     * Wegen hereinkommt: beim Start über `onCreate` und bei laufender App über
     * [onNewIntent]. `null` = nichts anzusteuern.
     */
    private val navZiel = MutableStateFlow<NavZiel?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Randlos schon vor dem ersten Frame — sonst springt das Layout sichtbar, sobald der
        // Effekt in der Composition greift. Die Icon-Helligkeit stellt dieser Aufruf noch
        // nach dem System ein; korrigiert wird sie unten, sobald das App-Theme feststeht.
        enableEdgeToEdge()

        /*
         * Ein Sprungziel wird genau einmal angesteuert — danach ist der Intent verbraucht.
         *
         * `getIntent()` liefert nach einem Neuaufbau dieser Activity (Drehen, Theme-Wechsel)
         * denselben Intent noch einmal, mitsamt Extras. Der Effekt in `AppNavigation` hat
         * daraufhin ein zweites Mal navigiert, während der Back-Stack längst wiederhergestellt
         * war: das Ziel lag danach doppelt auf dem Stapel. Am Gerät brauchte es nach zwei
         * Drehungen drei Zurück-Drücker bis Home statt einem — beim Widget-Sprung in die
         * laufende Session wie beim Näherungs-Push. Die Tab-Ziele blieben unauffällig, weil
         * `navigateToTab` mit `popUpTo(Home)` + `launchSingleTop` gar nicht stapeln kann; das
         * hat den Fehler lange verdeckt.
         *
         * Der Marker steht **im Intent** und nicht in `savedInstanceState`. Die naheliegende
         * Prüfung auf `savedInstanceState == null` wäre falsch: tippt man die Notification an,
         * während die App schon läuft, baut `FLAG_ACTIVITY_CLEAR_TOP` diese Activity neu auf und
         * reicht ihr den gespeicherten Zustand mit — das Ziel wäre dann verworfen worden und der
         * Tap hätte nichts getan. Am Intent ist die Unterscheidung dagegen eindeutig: ein neuer
         * Tap bringt einen frischen Intent ohne Marker, ein Neuaufbau denselben Intent mit.
         */
        navZiel.value = zielAus(intent)
        setContent {
            val ziel by navZiel.collectAsStateWithLifecycle()
            // Dark-Mode-Override aus den Einstellungen; null = dem System folgen (7.4a).
            val darkModeOverride by themeViewModel.darkModeOverride.collectAsStateWithLifecycle()
            val darkTheme = darkModeOverride ?: isSystemInDarkTheme()

            /*
             * Die Icons der Status- und Navigationsleiste müssen dem THEME DER APP folgen,
             * nicht dem des Systems.
             *
             * `enableEdgeToEdge()` ohne Argumente entscheidet anhand der Ressourcen-
             * Konfiguration — und die kennt nur den System-Schalter. Steht das System auf
             * hell und der App-Schalter oben auf dunkel, zeichnet Android dunkle Icons auf
             * unser dunkles Chrome. Sie verschwinden schlicht.
             *
             * Deshalb hier statt einmalig in `onCreate`: `darkTheme` ist erst in der
             * Composition bekannt und kann sich zur Laufzeit ändern. Der Effekt läuft bei
             * jedem Wechsel neu.
             */
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        TRANSPARENT, TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        TRANSPARENT, TRANSPARENT,
                    ) { darkTheme },
                )
                onDispose {}
            }

            BoulderBuddyTheme(darkTheme = darkTheme) {
                // Die Fensterbreite wird nicht mehr von hier durchgereicht: sie steht über
                // `aktuelleBreite()` (ui/theme/Breite.kt) überall in der Composition zur
                // Verfügung. Ein Parameter hätte sie nur bis zur Navigation getragen, während
                // sie inzwischen auch einzelne Screens und Raster interessiert.
                AppNavigation(
                    initialNavTarget = ziel?.target,
                    initialNavSessionId = ziel?.sessionId,
                    initialNavGymId = ziel?.gymId,
                )
            }
        }
    }

    /**
     * Der zweite Weg, auf dem ein Sprungziel hereinkommt.
     *
     * Heute läuft er nicht: sowohl [WidgetIntent.toApp] als auch der Näherungs-Push setzen
     * `FLAG_ACTIVITY_CLEAR_TOP`, und damit baut das System diese Activity neu auf — das Ziel
     * kommt über `onCreate`. Ohne dieses Flag würde der Intent dagegen hier landen, und ohne
     * diese Überschreibung liefe er ins Leere: `onCreate` ist sonst die einzige Stelle, die das
     * Ziel je liest.
     *
     * Steht hier also als Absicherung gegen eine Änderung der Flags oder des `launchMode` —
     * beides eine Zeile weit weg, mit einem Fehlerbild („der Tap tut nichts"), das man nicht
     * sofort mit ihr in Verbindung brächte.
     *
     * `setIntent` ist nötig, damit ein späterer Neuaufbau (Drehen) den *neuen* Intent sieht und
     * nicht den alten vom Start.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navZiel.value = zielAus(intent)
    }

    /**
     * Liest das Sprungziel aus [intent] und **verbraucht** es dabei.
     *
     * Der Marker bleibt im Intent zurück, damit ein Neuaufbau derselben Activity (Drehen,
     * Theme-Wechsel) dasselbe Ziel nicht ein zweites Mal ansteuert — genau das legte den
     * Zielscreen vorher doppelt auf den Back-Stack.
     */
    private fun zielAus(intent: Intent?): NavZiel? {
        if (intent == null || intent.getBooleanExtra(EXTRA_ZIEL_VERBRAUCHT, false)) return null
        val target = intent.getStringExtra(WidgetIntent.EXTRA_NAV_TARGET) ?: return null
        intent.putExtra(EXTRA_ZIEL_VERBRAUCHT, true)
        return NavZiel(
            target = target,
            // Nur für TARGET_ACTIVE_SESSION gesetzt: die Session, in die das Widget springt.
            sessionId = intent.getIntExtra(WidgetIntent.EXTRA_SESSION_ID, -1).takeIf { it > 0 },
            // Nur von der Näherungs-Notification gesetzt: Halle fürs Vorbefüllen.
            gymId = intent.getIntExtra(ProximityIntent.EXTRA_GYM_ID, -1).takeIf { it > 0 },
        )
    }

    /** Sprungziel aus Widget (7.4c) bzw. Näherungs-Notification (M4). */
    private data class NavZiel(
        val target: String,
        val sessionId: Int?,
        val gymId: Int?,
    )

    private companion object {
        /**
         * Merkt im Intent selbst, dass sein Sprungziel schon angesteuert wurde. Bewusst kein
         * Teil des öffentlichen [WidgetIntent]-Vertrags: niemand von außen setzt das, es
         * entsteht erst hier beim Verbrauchen.
         */
        const val EXTRA_ZIEL_VERBRAUCHT = "com.boulderbuddy.NAV_TARGET_VERBRAUCHT"
    }
}
