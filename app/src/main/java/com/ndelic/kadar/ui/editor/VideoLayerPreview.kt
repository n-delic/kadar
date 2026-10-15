package com.ndelic.kadar.ui.editor

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.model.texturePaints
import com.ndelic.kadar.video.ChromaKey
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
    val current by rememberUpdatedState(layer)
    val keyed = layer.key != null
    // Keyed, it runs the export's key on its frames (a new player, since effects are set before it prepares).
    val player = remember(layer.uri, keyed) {
        ExoPlayer.Builder(context).build().apply {
            if (keyed) setVideoEffects(listOf(ChromaKey(premultiply = true) { current }))
            setMediaItem(MediaItem.fromUri(layer.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val gate = rememberFrameGate(player)
    LaunchedEffect(player, layer.volume) { player.volume = layer.volume }
    var showing by remember { mutableStateOf(false) }
    // Counts the frames played, so the grain moves like the export's.
    var grainFrame by remember { mutableIntStateOf(0) }
    // Paused, the key reads its new settings only on a new frame.
    LaunchedEffect(player, layer.key, layer.keyStrength, layer.keySoftness) {
        if (keyed && !player.isPlaying && player.playbackState == Player.STATE_READY) player.seekTo(gate.nudge(player, player.currentPosition))
    }
    LaunchedEffect(player, main, totalMs) {
        if (main == null) return@LaunchedEffect
        var lastSeek = 0L
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
            val firstFrame = gate.due(player, now - lastSeek)
            if (firstFrame || player.playbackState != Player.STATE_BUFFERING && overlayNeedsSeek(player.currentPosition, want, play, now - lastSeek)) {
                player.seekTo(if (firstFrame) gate.nudge(player, want) else want)
                gate.seeked = true
                lastSeek = now
            }
            if (main.isPlaying) withFrameMillis { grainFrame++ } else delay(POLL_MS)
        }
    }
    val w = layer.width * scale
    val h = layer.height * scale
    val density = LocalDensity.current
    val size = with(density) { DpSize(w.toDp(), h.toDp()) }
    // The whole frame, shifted so the crop sits in the layer's box, which clips the rest.
    val frame = with(density) { DpSize((layer.videoWidth * scale).toDp(), (layer.videoHeight * scale).toDp()) }
    // Unbounded, so a layer bigger than the canvas keeps its size (the stage clips it) and its top-left anchor.
    Box(
        Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart, unbounded = true).size(size).graphicsLayer {
            translationX = layer.t.x * scale - w / 2
            translationY = layer.t.y * scale - h / 2
            scaleX = layer.t.scale
            scaleY = layer.t.scale
            rotationZ = layer.t.rotation
            alpha = if (showing) layer.opacity else 0f
            clip = true
        },
    ) {
        // The same matrix as the export's RgbMatrix, offsets in 0..255 on the view and divided by 255 there.
        val look = remember(layer.adjust) { if (layer.adjust.isNone) null else Paint().apply { colorFilter = ColorMatrixColorFilter(layer.adjust.filterMatrix()) } }
        key(player) {
            AndroidView(
                factory = { TextureView(it).also(player::setVideoTextureView) },
                update = {
                    it.setLayerPaint(look)
                    it.isOpaque = !keyed
                },
                modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(frame).graphicsLayer {
                    translationX = -layer.crop.left * layer.videoWidth * scale
                    translationY = -layer.crop.top * layer.videoHeight * scale
                },
            )
        }
        // Over the view, since a layer paint takes only a color filter; the export draws them on the cropped frame too.
        if (layer.adjust.hasTexture) {
            Canvas(Modifier.fillMaxSize()) {
                val w = layer.width
                val h = layer.height
                val paints = layer.adjust.texturePaints(w, h, grainFrame)
                val box = this.size
                drawIntoCanvas {
                    it.nativeCanvas.apply {
                        scale(box.width / w, box.height / h)
                        translate(w / 2, h / 2)
                        paints.forEach { p -> drawRect(-w / 2, -h / 2, w / 2, h / 2, p) }
                    }
                }
            }
        }
    }
}

/**
 * Whether a stage player still owes its view a frame. A paused player draws nothing into a view that gets its surface
 * after it is ready, and the frame it renders on its own when it gets ready can stay black, so after every new surface
 * (the view re-attaches when the stage's layers change) the previews seek, and again every [FRAME_RETRY_MS] until a
 * frame rendered after a seek arrives. A seek to the position the player is already at does nothing, hence [nudge].
 */
class FrameGate {
    var surface = false
    var seeked = false
    var shown = false

    /** Whether to seek now for a first frame. */
    fun due(player: Player, sinceSeekMs: Long) = surface && !shown && player.playbackState == Player.STATE_READY && sinceSeekMs >= FRAME_RETRY_MS

    /** [ms], moved off the player's position so the seek really happens; the drift check then brings it back. */
    fun nudge(player: Player, ms: Long) = if (player.currentPosition == ms) ms + 1 else ms
}

@Composable
fun rememberFrameGate(player: Player): FrameGate {
    val gate = remember(player) { FrameGate() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (gate.seeked) gate.shown = true
            }

            override fun onSurfaceSizeChanged(width: Int, height: Int) {
                gate.surface = width > 0 && height > 0
                gate.seeked = false
                gate.shown = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return gate
}

private const val POLL_MS = 32L
const val FRAME_RETRY_MS = 500L
