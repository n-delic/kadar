package com.ndelic.kadar.ui.editor

import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.video.overlayNeedsSeek
import com.ndelic.kadar.video.overlaySourceMs
import com.ndelic.kadar.video.overlaySpan
import kotlinx.coroutines.delay

/**
 * A video layer on the stage: its own player drawing into a TextureView placed like the layer ([scale] is the stage's
 * display scale), so moving it moves a view and never rebuilds the main preview. It follows [main]'s position and
 * play state. The export composites the same layer with Media3 instead, from the same transform, span and in-point.
 */
@Composable
fun VideoLayerPreview(layer: VideoLayer, totalMs: Long, scale: Float, main: Player?) {
    val context = LocalContext.current
    val player = remember(layer.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(layer.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, layer.volume) { player.volume = layer.volume }
    var showing by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(layer)
    LaunchedEffect(player, main, totalMs) {
        if (main == null) return@LaunchedEffect
        var lastSeek = 0L
        var drawn = false
        while (true) {
            val l = current
            val target = overlaySourceMs(l.clipStartMs, overlaySpan(l.startMs, l.endMs, l.playableMs, totalMs), main.currentPosition)
            val play = target != null && main.isPlaying
            showing = target != null
            player.playWhenReady = play
            // Hidden, it waits at its in-point, so it starts in step when its span comes up.
            val want = target ?: l.clipStartMs
            val now = SystemClock.uptimeMillis()
            // While a seek is still loading, its position says nothing about drift; seeking again would only stall it.
            // A paused player draws nothing into a view that got its surface after the player was ready, until it seeks.
            val firstDraw = !drawn && player.playbackState == Player.STATE_READY && player.surfaceSize.width > 0
            if (firstDraw || player.playbackState != Player.STATE_BUFFERING && overlayNeedsSeek(player.currentPosition, want, play, now - lastSeek)) {
                drawn = drawn || firstDraw
                player.seekTo(want)
                lastSeek = now
            }
            if (main.isPlaying) withFrameMillis {} else delay(POLL_MS)
        }
    }
    val w = layer.width * scale
    val h = layer.height * scale
    val size = with(LocalDensity.current) { DpSize(w.toDp(), h.toDp()) }
    key(player) {
        AndroidView(
            factory = { TextureView(it).also(player::setVideoTextureView) },
            // Unbounded, so a layer bigger than the canvas keeps its size (the stage clips it) and its top-left anchor.
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart, unbounded = true).size(size).graphicsLayer {
                translationX = layer.t.x * scale - w / 2
                translationY = layer.t.y * scale - h / 2
                scaleX = layer.t.scale
                scaleY = layer.t.scale
                rotationZ = layer.t.rotation
                alpha = if (showing) layer.opacity else 0f
            },
        )
    }
}

private const val POLL_MS = 32L
