package com.boulderbuddy.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.boulderbuddy.R
import com.boulderbuddy.ui.theme.HEX_DARK_BACKGROUND
import com.boulderbuddy.ui.theme.HEX_DARK_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_DARK_ON_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_DARK_ON_SURFACE
import com.boulderbuddy.ui.theme.HEX_DARK_SURFACE_HIGHEST
import com.boulderbuddy.ui.theme.HEX_DARK_TEXT_SECONDARY
import com.boulderbuddy.ui.theme.HEX_LIGHT_BACKGROUND
import com.boulderbuddy.ui.theme.HEX_LIGHT_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_LIGHT_ON_FILL_STRONG
import com.boulderbuddy.ui.theme.HEX_LIGHT_ON_SURFACE
import com.boulderbuddy.ui.theme.HEX_LIGHT_SURFACE_HIGHEST
import com.boulderbuddy.ui.theme.HEX_LIGHT_TEXT_SECONDARY
import kotlinx.coroutines.flow.first

/**
 * Die Farben des Widgets — dieselben Werte wie in der App (`PaletteHex.kt` bzw. `colors.xml`).
 *
 * Vorher standen hier fünf handgeschriebene Werte, „an Color.kt angelehnt" — und genau das war
 * das Problem: angelehnt ist nicht gleich. Der Hintergrund lag bei #F3ECD6, die App zeichnet
 * #FCF6E4; der Akzent war das helle Rosé aus der Zeit vor der Light-Mode-Runde, das es in der
 * Palette gar nicht mehr gibt. Nebeneinander auf dem Homescreen sah das Widget aus wie eine
 * ältere Version der App — und im Dark Mode blieb es cremefarben stehen.
 *
 * ## Warum es drei Paletten gibt und nicht zwei
 *
 * Glance 1.1.1 hat keinen Tag/Nacht-`ColorProvider`. Der erste Anlauf hat das Theme deshalb
 * im App-Prozess ausgerechnet und als festen Farbwert übergeben — und ist am Gerät
 * durchgefallen: das System schaltete auf dunkel, alles ringsum wechselte, das Widget nicht.
 * Der Grund ist grundsätzlich und nicht zu umgehen: ein Widget zeichnet nicht selbst,
 * sondern liefert RemoteViews, die der **Launcher** auflöst. Ein fertiger Farbwert reagiert
 * auf dessen Konfigurationswechsel nicht, und die Widget-Daten haben keinen Anlass, wegen
 * eines Theme-Wechsels neu zu emittieren.
 *
 * Was live mitschaltet, sind Farb**ressourcen** — die löst der Launcher gegen sein eigenes
 * `values-night` auf. Das ist [AutoWidgetPalette], und es ist der Normalfall.
 *
 * Nur reicht das allein nicht, denn diese App hat einen eigenen Dark-Mode-Schalter, den das
 * System nicht kennt. Ist er gesetzt, sollen die Farben gerade NICHT mit dem System wandern —
 * dafür die festen [LightWidgetPalette] / [DarkWidgetPalette]. Die zwei Fälle brauchen also
 * zwei verschiedene Arten von ColorProvider, nicht bloß zwei Wertesätze.
 */
internal data class WidgetPalette(
    val bg: ColorProvider,
    val ink: ColorProvider,
    val secondary: ColorProvider,
    // Primäre Aktion: dieselbe Paarung wie PrimaryButton — fillStrong/onFillStrong. Sie dreht
    // zwischen den Themes (Light dunkel-auf-hell, Dark hell-auf-dunkel), damit der Knopf in
    // beiden Fällen das auffälligste Element bleibt.
    val fill: ColorProvider,
    val onFill: ColorProvider,
    /*
     * Sekundäre Aktion. In der App ist das `QuickActionButton(primary = false)`: die helle
     * Card-Fläche, deren Kante ein dezenter Rand zieht.
     *
     * Genau dieser Rand geht hier verloren — Glance hat keinen Border-Modifier. Mit der
     * Card-Farbe (#FFFDF7 auf #FCF6E4, 1,06:1) war der „Timer"-Knopf am Gerät deshalb keine
     * Fläche mehr, sondern ein Wort, das im Hintergrund schwebte. Ohne Rand muss die Füllung
     * die Kante allein tragen, also die kräftigste Stufe der Rampe statt der hellsten.
     * Beide Textfarben halten darauf ihre 4,5:1 — `surfaceHighest` ist in beiden Themes
     * ohnehin der bindende Fall der Palette.
     */
    val fillMuted: ColorProvider,
)

/** Ohne gesetzten Schalter: der Launcher entscheidet je Ressource gegen sein `values-night`. */
private val AutoWidgetPalette = WidgetPalette(
    bg = ColorProvider(R.color.widget_bg),
    ink = ColorProvider(R.color.widget_ink),
    secondary = ColorProvider(R.color.widget_secondary),
    fill = ColorProvider(R.color.widget_fill),
    onFill = ColorProvider(R.color.widget_on_fill),
    fillMuted = ColorProvider(R.color.widget_fill_muted),
)

private val LightWidgetPalette = WidgetPalette(
    bg = ColorProvider(Color(HEX_LIGHT_BACKGROUND)),
    ink = ColorProvider(Color(HEX_LIGHT_ON_SURFACE)),
    secondary = ColorProvider(Color(HEX_LIGHT_TEXT_SECONDARY)),
    fill = ColorProvider(Color(HEX_LIGHT_FILL_STRONG)),
    onFill = ColorProvider(Color(HEX_LIGHT_ON_FILL_STRONG)),
    fillMuted = ColorProvider(Color(HEX_LIGHT_SURFACE_HIGHEST)),
)

private val DarkWidgetPalette = WidgetPalette(
    bg = ColorProvider(Color(HEX_DARK_BACKGROUND)),
    ink = ColorProvider(Color(HEX_DARK_ON_SURFACE)),
    secondary = ColorProvider(Color(HEX_DARK_TEXT_SECONDARY)),
    fill = ColorProvider(Color(HEX_DARK_FILL_STRONG)),
    onFill = ColorProvider(Color(HEX_DARK_ON_FILL_STRONG)),
    fillMuted = ColorProvider(Color(HEX_DARK_SURFACE_HIGHEST)),
)

/**
 * Die Palette zum Zustand des Dark-Mode-Schalters: `null` = dem System folgen (Ressourcen),
 * sonst der feste Satz. Eigene Funktion, damit die Regel prüfbar bleibt.
 */
internal fun paletteFuer(darkModeOverride: Boolean?): WidgetPalette = when (darkModeOverride) {
    null -> AutoWidgetPalette
    true -> DarkWidgetPalette
    false -> LightWidgetPalette
}

/**
 * Homescreen-Widget (7.4c): zeigt die aktive Session (Halle · Boulder/Tops) bzw. die
 * Gesamt-Tops, plus zwei Schnellstart-Buttons.
 *
 * Einstiegs-Logik: Läuft eine Session, führen Widget-Tap und Session-Knopf direkt in DIESE
 * Session; ohne aktive Session bietet der Knopf „Session starten" (Tap auf die Fläche öffnet
 * dann nur die App, damit ein Fehlgriff nicht im Anlege-Formular landet). Der Timer ist in
 * beiden Fällen über den zweiten Knopf erreichbar.
 *
 * Daten kommen live aus Room ([observeWidgetData]) und werden IN der Composition gesammelt —
 * siehe [provideGlance].
 */
class BoulderWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dataFlow = observeWidgetData(context)
        // Erster Wert noch vor der Composition: sonst zeigt der erste Frame kurz den
        // Leerzustand ("Keine aktive Session").
        val initial = dataFlow.first()
        provideContent {
            // Wichtig: die Sammlung gehört INNERHALB von provideContent. Alles davor läuft nur
            // einmal je Glance-Session; update()/updateAll() rekomponiert eine noch laufende
            // Session nur, ohne die Repositories erneut zu lesen. Ein vor provideContent
            // geladener Snapshot blieb deshalb stehen (z. B. nach dem Beenden einer Session).
            val data by dataFlow.collectAsState(initial)
            WidgetContent(context, data)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData) {
    // Session-Ziel einmal ableiten: aktive Session öffnen ODER neue Session starten.
    val sessionIntent = WidgetIntent.toApp(
        context,
        target = data.sessionNavTarget,
        sessionId = data.activeSessionId,
    )
    // Tap auf die Fläche: mit laufender Session direkt hinein, sonst nur die App öffnen —
    // ein versehentlicher Tap soll niemanden ins Anlege-Formular werfen.
    val surfaceIntent = if (data.hasActiveSession) sessionIntent else WidgetIntent.toApp(context)
    // Der Dark-Mode-Schalter kommt mit den Daten herein — siehe [WidgetPalette].
    val farben = paletteFuer(data.darkModeOverride)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(farben.bg)
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivity(surfaceIntent)),
    ) {
        // Kopf: Titel + Refresh.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BoulderBuddy",
                style = TextStyle(
                    color = farben.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Box(
                modifier = GlanceModifier
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<RefreshCallback>()),
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(color = farben.secondary, fontSize = 15.sp),
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // Kern-Status.
        if (data.hasActiveSession) {
            Text(
                text = data.gymName,
                style = TextStyle(
                    color = farben.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = "läuft gerade · ${data.routeCount} Boulder · ${data.sessionTops} Tops",
                style = TextStyle(color = farben.secondary, fontSize = 12.sp),
                maxLines = 1,
            )
        } else {
            Text(
                text = "Keine aktive Session",
                style = TextStyle(
                    color = farben.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = "${data.totalTops} Tops insgesamt",
                style = TextStyle(color = farben.secondary, fontSize = 12.sp),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        // Schnellstart-Buttons: Session als primäre Aktion (nimmt den Restplatz), der Timer
        // daneben in fester Breite — er muss in JEDEM Zustand erreichbar bleiben.
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetButton(
                text = if (data.hasActiveSession) "Session öffnen" else "Session starten",
                filled = true,
                farben = farben,
                action = actionStartActivity(sessionIntent),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetButton(
                text = "Timer",
                filled = false,
                farben = farben,
                action = actionStartActivity(
                    WidgetIntent.toApp(context, WidgetIntent.TARGET_TIMER),
                ),
                modifier = GlanceModifier.width(64.dp),
            )
        }
    }
}

@Composable
private fun WidgetButton(
    text: String,
    filled: Boolean,
    farben: WidgetPalette,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (filled) farben.fill else farben.fillMuted)
            .cornerRadius(12.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            // Einzeilig: das Widget kann auf 3 Zellen schrumpfen, ein Umbruch würde die
            // 40-dp-Fläche sprengen.
            maxLines = 1,
            style = TextStyle(
                color = if (filled) farben.onFill else farben.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** Lädt die Widget-Daten neu (Refresh-Knopf) — stößt ein erneutes [provideGlance] an. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        BoulderWidget().update(context, glanceId)
    }
}
