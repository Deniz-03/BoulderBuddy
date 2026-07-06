package com.boulderbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.boulderbuddy.ghost.model.GhostPoseTrack
import com.boulderbuddy.ui.theme.Dimens
import com.boulderbuddy.ui.theme.RouteBlue
import com.boulderbuddy.ui.theme.RouteOrange
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Side-by-Side-Vergleich (M4, P7): links das Referenz-Video (mit Controls, führend),
 * rechts das Vergleichs-Video — OHNE eigene Controls, es läuft 1:1 mit der Referenz
 * mit (echter Wiedergabe, gespiegeltes Play/Pause, Seek nur zur Drift-Korrektur).
 *
 * BEWUSST OHNE DTW-Zeitwarp: das Vergleichs-Video in die Referenzzeit zu warpen ließ
 * es ruckeln/springen (ständiges Seeken eines pausierten Players). Side-by-Side zeigt
 * beide Versuche in ihrem eigenen, unverzerrten Tempo — der ehrliche Vergleich bei
 * unterschiedlicher Beta (P6); die Zeit-Synchronisation bleibt dem Overlay vorbehalten.
 * Jede Seite zeichnet ihr Skelett im EIGENEN Frame-Raum zur eigenen Videozeit.
 */
@Composable
fun GhostSideBySidePlayer(
    refUri: String,
    refTrack: GhostPoseTrack,
    cmpUri: String,
    cmpTrack: GhostPoseTrack,
    modifier: Modifier = Modifier,
    refColor: Color = RouteOrange,
    cmpColor: Color = RouteBlue,
    /** Abbruchzeitpunkte (P4c), jeweils auf der eigenen Video-Zeitachse. */
    refAbortTimeMs: Long? = null,
    cmpAbortTimeMs: Long? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val refPlayer = remember(refUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(refUri.toUri()))
            prepare()
            playWhenReady = false
        }
    }
    // Folge-Player: läuft echt mit (kein Seek-Stepping), Play/Pause spiegelt die Referenz.
    val cmpPlayer = remember(cmpUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(cmpUri.toUri()))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(lifecycleOwner, refPlayer, cmpPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) refPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            refPlayer.release()
            cmpPlayer.release()
        }
    }

    var refPositionMs by remember { mutableLongStateOf(0L) }
    var cmpPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(refPlayer, cmpPlayer) {
        while (true) {
            refPositionMs = refPlayer.currentPosition
            cmpPositionMs = cmpPlayer.currentPosition
            delay(33)
        }
    }
    // Vergleich läuft 1:1 mit der Referenz: Play/Pause spiegeln, Position nur bei
    // spürbarer Drift (> 200 ms, z.B. nach einem Scrub) nachziehen — sonst normal
    // dekodieren lassen, damit die Wiedergabe flüssig bleibt (kein Seek-Stepping).
    LaunchedEffect(refPlayer, cmpPlayer) {
        while (true) {
            if (refPlayer.isPlaying && !cmpPlayer.isPlaying) cmpPlayer.play()
            if (!refPlayer.isPlaying && cmpPlayer.isPlaying) cmpPlayer.pause()
            if (abs(cmpPlayer.currentPosition - refPlayer.currentPosition) > 200) {
                cmpPlayer.seekTo(refPlayer.currentPosition)
            }
            delay(200)
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingS),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(refTrack.frameWidth.toFloat() / refTrack.frameHeight),
        ) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = refPlayer
                        useController = true
                    }
                },
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                drawSkeletonOverlay(
                    track = refTrack,
                    timeMs = refPositionMs,
                    color = refColor,
                    abortTimeMs = refAbortTimeMs,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(cmpTrack.frameWidth.toFloat() / cmpTrack.frameHeight),
        ) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = cmpPlayer
                        useController = false
                    }
                },
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                drawSkeletonOverlay(
                    track = cmpTrack,
                    timeMs = cmpPositionMs,
                    color = cmpColor,
                    abortTimeMs = cmpAbortTimeMs,
                )
            }
        }
    }
}
