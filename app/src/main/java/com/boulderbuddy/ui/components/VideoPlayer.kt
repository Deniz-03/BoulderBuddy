package com.boulderbuddy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Schlanker ExoPlayer-Wrapper (Phase 7.3c) für Routen-Videos. Kapselt die View-basierte
 * [PlayerView] via [AndroidView] und bindet ihren Lebenszyklus an die Composition:
 * pausiert im Hintergrund und gibt den Player bei Verlassen der Composition frei
 * (sonst laufen Audio/Decoder weiter und lecken).
 *
 * Bewusst ohne Auto-Play — der Nutzer startet die Wiedergabe über die Player-Controls.
 */
@Composable
fun VideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // remember(uri): neuer Player nur, wenn sich die Quelle ändert.
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri.toUri()))
            prepare()
            playWhenReady = false
        }
    }

    // Pausieren, sobald der Screen in den Hintergrund geht; freigeben beim Dispose.
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
    )
}
