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
import kotlinx.coroutines.flow.first

// Marken-Farben (an Color.kt angelehnt). Bewusst fest: ein Widget hat keinen App-Theme-Context.
private val WidgetBg = Color(0xFFF9F4E3)
private val WidgetInk = Color(0xFF2B2B2B)
private val WidgetCream = Color(0xFFF9F4E3)
private val WidgetSecondary = Color(0xFF6B6040)
private val WidgetAccent = Color(0xFFC9A89A)

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

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBg))
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
                    color = ColorProvider(WidgetInk),
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
                    style = TextStyle(color = ColorProvider(WidgetSecondary), fontSize = 15.sp),
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // Kern-Status.
        if (data.hasActiveSession) {
            Text(
                text = data.gymName,
                style = TextStyle(
                    color = ColorProvider(WidgetInk),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = "läuft gerade · ${data.routeCount} Boulder · ${data.sessionTops} Tops",
                style = TextStyle(color = ColorProvider(WidgetSecondary), fontSize = 12.sp),
                maxLines = 1,
            )
        } else {
            Text(
                text = "Keine aktive Session",
                style = TextStyle(
                    color = ColorProvider(WidgetInk),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = "${data.totalTops} Tops insgesamt",
                style = TextStyle(color = ColorProvider(WidgetSecondary), fontSize = 12.sp),
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
                action = actionStartActivity(sessionIntent),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetButton(
                text = "Timer",
                filled = false,
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
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(ColorProvider(if (filled) WidgetInk else WidgetAccent))
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
                color = ColorProvider(if (filled) WidgetCream else WidgetInk),
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
