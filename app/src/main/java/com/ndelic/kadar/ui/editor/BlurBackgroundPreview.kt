package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.video.BLUR_SIGMA
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.overlayNeedsSeek
import com.ndelic.kadar.video.sourceMs
import kotlinx.coroutines.delay

/**
 * The blurred canvas behind the video on the stage: the clip under the playhead again, in its own muted player (a
 * photo clip as its picture from [stills]), scaled to cover the canvas and blurred and darkened by the view (Android
 * 12+; older phones show it unblurred). It follows [main] like [VideoLayerPreview]. The export draws the same
 * background with Media3 (VideoClip.blurredBackground).
 */
@Composable
fun BlurBackgroundPreview(clip: VideoClip, stills: Map<Uri, List<Bitmap>>, canvasWidth: Int, canvasHeight: Int, scale: Float, main: Player?) {
    val context = LocalContext.current
    // One playlist item per video clip, whole rather than trimmed, so positions are source time like the trims.
    val sources = clip.segments.filter { !it.isImage }.map { it.uri }
    val player = remember(sources) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            // No sound at all, so the clock isn't the audio sink's, which stops at 8x (clips go up to 10x).
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            setMediaItems(sources.map(MediaItem::fromUri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val gate = rememberFrameGate(player)
    // The clip under the playhead, which sets the view's size and look.
    var index by remember { mutableIntStateOf(0) }
    val current by rememberUpdatedState(clip)
    LaunchedEffect(player, main) {
        if (main == null) return@LaunchedEffect
        var lastSeek = 0L
        while (true) {
            val c = current
            val (i, into) = c.segmentAt(main.currentPosition)
            val s = c.segments[i]
            index = i
            val play = main.isPlaying && !s.isImage
            player.playWhenReady = play
            if (!s.isImage) {
                if (player.playbackParameters.speed != s.speed) player.playbackParameters = PlaybackParameters(s.speed)
                val item = c.segments.take(i).count { !it.isImage }
                val want = sourceMs(s.startMs, s.speed, into).coerceAtMost(s.endMs)
                val now = SystemClock.uptimeMillis()
                val firstFrame = gate.due(player, now - lastSeek)
                // The next clip came up under the playhead (or another one was scrubbed to): go there right away.
                val otherClip = player.currentMediaItemIndex != item
                if (firstFrame || otherClip || player.playbackState != Player.STATE_BUFFERING && overlayNeedsSeek(player.currentPosition, want, play, now - lastSeek)) {
                    player.seekTo(item, if (firstFrame) gate.nudge(player, want) else want)
                    gate.seeked = true
                    lastSeek = now
                }
            }
            if (main.isPlaying) withFrameMillis {} else delay(POLL_MS)
        }
    }
    val s = clip.segments.getOrElse(index) { clip.segments.first() }
    // Cover: the clip scaled until it fills the canvas both ways, centered, the overflow clipped by the stage.
    val cover = maxOf(canvasWidth / s.width.toFloat(), canvasHeight / s.height.toFloat()) * scale
    // Drawn and blurred at 1/BLUR_DOWNSCALE of its size, then scaled up: a blur costs its pixels times its radius, and a
    // blurred picture loses nothing by being small.
    val size = with(LocalDensity.current) { DpSize((s.width * cover / BLUR_DOWNSCALE).toDp(), (s.height * cover / BLUR_DOWNSCALE).toDp()) }
    // The export blurs at 320 px on the long side with sigma BLUR_SIGMA times the strength; the same share of the stage here. Android turns a
    // blur radius into sigma = 0.57735 * radius + 0.5, so the radius is scaled up to match.
    val radius = BLUR_SHARE * clip.blurStrength * maxOf(canvasWidth, canvasHeight) * scale / 0.57735f / BLUR_DOWNSCALE
    val effect = remember(s.adjust, radius) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@remember null
        val m = s.adjust.filterMatrix().copyOf()
        // Darker, like the export's Brightness(-0.15), so captions over the background stay readable.
        for (i in intArrayOf(4, 9, 14)) m[i] -= 0.15f * 255
        RenderEffect.createChainEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(m)),
        ).asComposeRenderEffect()
    }
    val placed = Modifier.fillMaxSize().wrapContentSize(Alignment.Center, unbounded = true).requiredSize(size)
    key(player) {
        AndroidView(
            factory = { TextureView(it).also(player::setVideoTextureView) },
            modifier = placed.graphicsLayer {
                renderEffect = effect
                alpha = if (s.isImage) 0f else 1f
                scaleX = BLUR_DOWNSCALE
                scaleY = BLUR_DOWNSCALE
            },
        )
    }
    val still = remember(s.isImage, stills[s.uri]) { stills[s.uri]?.firstOrNull()?.takeIf { s.isImage }?.asImageBitmap() }
    still?.let { Image(it, null, placed.graphicsLayer {
        renderEffect = effect
        scaleX = BLUR_DOWNSCALE
        scaleY = BLUR_DOWNSCALE
    }, contentScale = ContentScale.FillBounds) }
}

private const val BLUR_SHARE = BLUR_SIGMA / 320
private const val POLL_MS = 32L
private const val BLUR_DOWNSCALE = 8f
