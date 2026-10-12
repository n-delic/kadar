package com.ndelic.kadar.ui.editor

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.overlayNeedsSeek
import kotlinx.coroutines.delay

/**
 * The blurred canvas behind the video on the stage: the clip again in its own muted player, scaled to cover the
 * canvas and blurred and darkened by the view (Android 12+; older phones show it unblurred). It follows [main] like
 * [VideoLayerPreview]. The export draws the same background with Media3 (VideoClip.blurredBackground).
 */
@Composable
fun BlurBackgroundPreview(clip: VideoClip, canvasWidth: Int, canvasHeight: Int, scale: Float, main: Player?) {
    val context = LocalContext.current
    val player = remember(clip.uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            setMediaItem(MediaItem.fromUri(clip.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, clip.speed) { player.playbackParameters = PlaybackParameters(clip.speed) }
    val current by rememberUpdatedState(clip)
    LaunchedEffect(player, main) {
        if (main == null) return@LaunchedEffect
        var lastSeek = 0L
        while (true) {
            val c = current
            val play = main.isPlaying
            player.playWhenReady = play
            val want = (c.startMs + (main.currentPosition * c.speed).toLong()).coerceAtMost(c.endMs)
            val now = SystemClock.uptimeMillis()
            if (player.playbackState != Player.STATE_BUFFERING && overlayNeedsSeek(player.currentPosition, want, play, now - lastSeek)) {
                player.seekTo(want)
                lastSeek = now
            }
            if (play) withFrameMillis {} else delay(POLL_MS)
        }
    }
    // Cover: the clip scaled until it fills the canvas both ways, centered, the overflow clipped by the stage.
    val cover = maxOf(canvasWidth / clip.width.toFloat(), canvasHeight / clip.height.toFloat()) * scale
    val size = with(LocalDensity.current) { DpSize((clip.width * cover).toDp(), (clip.height * cover).toDp()) }
    // The export blurs at 320 px on the long side with sigma 16; the same share of the stage here. Android turns a
    // blur radius into sigma = 0.57735 * radius + 0.5, so the radius is scaled up to match.
    val radius = BLUR_SHARE * maxOf(canvasWidth, canvasHeight) * scale / 0.57735f
    val effect = remember(clip.adjust, radius) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@remember null
        val m = clip.adjust.filterMatrix().copyOf()
        // Darker, like the export's Brightness(-0.15), so captions over the background stay readable.
        for (i in intArrayOf(4, 9, 14)) m[i] -= 0.15f * 255
        RenderEffect.createChainEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(m)),
        ).asComposeRenderEffect()
    }
    key(player) {
        AndroidView(
            factory = { TextureView(it).also(player::setVideoTextureView) },
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center, unbounded = true).requiredSize(size)
                .graphicsLayer { renderEffect = effect },
        )
    }
}

private const val BLUR_SHARE = 16f / 320
private const val POLL_MS = 32L
