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
 * rechts das Vergleichs-Video — OHNE eigene Controls, es folgt der Referenz über das
 * DTW-Zeitmapping [cmpTimeForPosition]. Jede Seite zeichnet ihr Skelett im EIGENEN
 * Frame-Raum (der Vergleich unverzerrt, nicht homographie-transformiert — genau der
 * Punkt von Side-by-Side bei unterschiedlicher Beta, P6).
 *
 * Der Folge-Player wird per Seek nachgeführt (~4x/s, nur bei nennenswerter Drift) —
 * für flüssiges 1:1-Playback müssten beide Streams frame-genau gekoppelt dekodieren,
 * das steht bewusst nicht im Abgabe-Umfang.
 */
@Composable
fun GhostSideBySidePlayer(
    refUri: String,
    refTrack: GhostPoseTrack,
    cmpUri: String,
    cmpTrack: GhostPoseTrack,
    cmpTimeForPosition: (Long) -> Long,
    modifier: Modifier = Modifier,
    refColor: Color = RouteOrange,
    cmpColor: Color = RouteBlue,
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
    // Folge-Player: bewusst dauerhaft pausiert, Position kommt ausschließlich per Seek.
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
    LaunchedEffect(refPlayer) {
        while (true) {
            refPositionMs = refPlayer.currentPosition
            delay(33)
        }
    }
    // Nachführen des Folge-Players: seltener und nur bei Drift > 150 ms, sonst
    // ruckelt das ständige Seeken mehr als es synchronisiert.
    LaunchedEffect(cmpPlayer) {
        while (true) {
            val target = cmpTimeForPosition(refPlayer.currentPosition)
            if (abs(cmpPlayer.currentPosition - target) > 150) {
                cmpPlayer.seekTo(target)
            }
            delay(250)
        }
    }

    val cmpPositionMs = cmpTimeForPosition(refPositionMs)

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
                drawSkeletonOverlay(track = refTrack, timeMs = refPositionMs, color = refColor)
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
                drawSkeletonOverlay(track = cmpTrack, timeMs = cmpPositionMs, color = cmpColor)
            }
        }
    }
}
