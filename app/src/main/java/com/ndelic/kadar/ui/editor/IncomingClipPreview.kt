package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.video.TransitionSpan
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.incomingAlpha
import com.ndelic.kadar.video.incomingOffset
import com.ndelic.kadar.video.overlayNeedsSeek
import com.ndelic.kadar.video.sourceMs
import com.ndelic.kadar.video.wipeRect
import kotlinx.coroutines.delay

/**
 * The incoming clip of an overlapping transition (dissolve, wipe, slide, push) on the stage, over the main preview,
 * which keeps playing the clip before: the clip's head in its own player (a photo as its picture from [stills]),
 * fitted into the canvas like the export fits it, and faded, moved or clipped by the view at [clock] (the main
 * player's position, every frame while it plays). It follows [main] like [VideoLayerPreview]; the export draws the same
 * heads with Media3 (VideoClip.incomingSequence), placed by the same TransitionKind functions.
 */
@Composable
fun IncomingClipPreview(clip: VideoClip, stills: Map<Uri, List<Bitmap>>, canvasWidth: Int, canvasHeight: Int, scale: Float, main: Player?, clock: LongState) {
    val context = LocalContext.current
    val spans = remember(clip) { clip.transitionSpans.filter { it.kind.overlaps } }
    // One playlist item per video clip, whole rather than trimmed, so positions are source time like the trims.
    val sources = clip.segments.filter { !it.isImage }.map { it.uri }
    val player = remember(sources) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(sources.map(MediaItem::fromUri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val gate = rememberFrameGate(player)
    // The transition on screen or coming up next, which sets the view's clip, size and look.
    var span by remember { mutableStateOf<TransitionSpan?>(null) }
    val current by rememberUpdatedState(clip)
    val currentSpans by rememberUpdatedState(spans)
    LaunchedEffect(player, main) {
        if (main == null) return@LaunchedEffect
        var lastSeek = 0L
        while (true) {
            val c = current
            val t = main.currentPosition
            // Waiting at the next one's start, so it plays in step as soon as it comes up.
            val next = currentSpans.firstOrNull { t < it.endMs }
            span = next
            if (next != null) {
                val s = c.segments[next.clip]
                val inside = t >= next.startMs
                val play = inside && main.isPlaying && !s.isImage
                player.playWhenReady = play
                player.volume = if (inside) s.volume else 0f
                if (!s.isImage) {
                    val params = PlaybackParameters(s.speed, if (s.keepPitch) 1f else s.speed)
                    if (player.playbackParameters != params) player.playbackParameters = params
                    val item = c.segments.take(next.clip).count { !it.isImage }
                    val want = sourceMs(s.startMs, s.speed, (t - next.startMs).coerceAtLeast(0))
                    val now = SystemClock.uptimeMillis()
                    val firstFrame = gate.due(player, now - lastSeek)
                    val otherClip = player.currentMediaItemIndex != item
                    if (firstFrame || otherClip || player.playbackState != Player.STATE_BUFFERING && overlayNeedsSeek(player.currentPosition, want, play, now - lastSeek)) {
                        player.seekTo(item, if (firstFrame) gate.nudge(player, want) else want)
                        gate.seeked = true
                        lastSeek = now
                    }
                }
            } else {
                player.playWhenReady = false
            }
            if (main.isPlaying) withFrameMillis {} else delay(POLL_MS)
        }
    }
    val shown = span ?: spans.firstOrNull() ?: return
    val s = clip.segments.getOrElse(shown.clip) { clip.segments.first() }
    // Fitted into the canvas, centered: the export's Presentation.LAYOUT_SCALE_TO_FIT.
    val fit = minOf(canvasWidth / s.width.toFloat(), canvasHeight / s.height.toFloat()) * scale
    val frame = with(LocalDensity.current) { DpSize((s.width * fit).toDp(), (s.height * fit).toDp()) }
    val filter = remember(s.adjust) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || s.adjust.isNone) null
        else RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(s.adjust.filterMatrix())).asComposeRenderEffect()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val t = clock.longValue
            val p = shown.progress(t)
            val (x, y) = shown.kind.incomingOffset(p)
            alpha = if (t in shown) shown.kind.incomingAlpha(p) else 0f
            translationX = x * size.width
            translationY = y * size.height
            shape = shown.kind.wipeRect(p)?.let(::FractionShape) ?: RectangleShape
            this.clip = true
        },
    ) {
        val placed = Modifier.fillMaxSize().wrapContentSize(Alignment.Center, unbounded = true).requiredSize(frame).graphicsLayer { renderEffect = filter }
        key(player) {
            AndroidView(
                factory = { TextureView(it).also(player::setVideoTextureView) },
                modifier = placed.graphicsLayer { alpha = if (s.isImage) 0f else 1f },
            )
        }
        val still = remember(s.isImage, stills[s.uri]) { stills[s.uri]?.firstOrNull()?.takeIf { s.isImage }?.asImageBitmap() }
        still?.let { Image(it, null, placed, contentScale = ContentScale.FillBounds) }
    }
}

/** The part of a box given by [r]: left, top, right and bottom as fractions of its size. */
private class FractionShape(private val r: FloatArray) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(r[0] * size.width, r[1] * size.height, r[2] * size.width, r[3] * size.height))
}

private const val POLL_MS = 32L
