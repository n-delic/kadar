@file:OptIn(UnstableApi::class, ExperimentalApi::class)

package com.ndelic.kadar.ui.editor

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.ndelic.kadar.model.Project
import com.ndelic.kadar.video.LiveLooks
import com.ndelic.kadar.video.LiveVolumes
import com.ndelic.kadar.video.PREVIEW_TAIL_MS
import com.ndelic.kadar.video.TransitionSpan
import com.ndelic.kadar.video.headTailsMs
import com.ndelic.kadar.video.headsComposition
import com.ndelic.kadar.video.incomingAlpha
import com.ndelic.kadar.video.incomingOffset
import com.ndelic.kadar.video.overlayNeedsSeek
import kotlinx.coroutines.delay

/**
 * The incoming clips of the overlapping transitions (dissolve, wipe, slide, push) of [project]'s video on the stage, over
 * the main preview, which keeps playing the clip before: the export's heads (VideoClip.headsComposition: cropped, looked,
 * fitted, placed, over the bars' color, blur areas and wipes included, the looks, placement and blur areas live from
 * [looks]) in a player of their own, faded and moved by the view at [clock] (the main player's position, every frame
 * while it plays), like the export's incomingSettings. It follows [main] like [VideoLayerPreview].
 */
@Composable
fun IncomingClipPreview(project: Project, main: Player?, clock: LongState, looks: LiveLooks, volumes: LiveVolumes, copies: Map<Uri, Uri>) {
    val clip = project.video ?: return
    val context = LocalContext.current
    val spans = remember(clip) { clip.transitionSpans.filter { it.kind.overlaps } }
    // Each head stays on top, at its final look, a little past its cut, until the main preview shows the next clip.
    val tails = remember(spans) { headTailsMs(spans, PREVIEW_TAIL_MS) }
    fun TransitionSpan.shownAt(ms: Long) = ms >= startMs && ms < endMs + tails[spans.indexOf(this)]
    // The replayable cache lets a paused head redraw its frame through a new look, like the main preview.
    val player = remember { CompositionPlayer.Builder(context).experimentalSetEnableReplayableCache(true).build() }
    DisposableEffect(player) { onDispose { player.release() } }
    val structure = remember(clip) { clip.previewStructure() }
    LaunchedEffect(player, structure, copies, looks, volumes) {
        structure.headsComposition(looks, volumes, copies)?.let {
            player.setComposition(it)
            player.prepare()
        }
    }
    // Paused, a new look, placement, bar color or blur area shows on the head too (the main preview's LiveLooks swaps them).
    LaunchedEffect(player, project) {
        if (!player.isPlaying && player.playbackState == Player.STATE_READY) player.experimentalRedrawLastFrame()
    }
    // The transition on screen or coming up next, which sets the view's look.
    var span by remember { mutableStateOf<TransitionSpan?>(null) }
    val currentSpans by rememberUpdatedState(spans)
    val currentTails by rememberUpdatedState(tails)
    LaunchedEffect(player, main) {
        if (main == null) return@LaunchedEffect
        var lastSeek = 0L
        // A player told to play shows its first frames this much later; it starts that much before the transition, from
        // the head's first frame, so it is in step when the transition starts instead of that far behind all through it.
        var startupMs = STARTUP_MS
        var startedAt = 0L
        var startedFrom = 0L
        while (true) {
            val t = main.currentPosition
            // Waiting at the next one's start, so it plays in step as soon as it comes up.
            val next = currentSpans.withIndex().firstOrNull { (k, it) -> t < it.endMs + currentTails[k] }?.value
            span = next
            if (next != null) {
                val inside = t >= next.startMs
                val starting = !inside && next.startMs - t <= startupMs
                val play = (inside || starting) && main.isPlaying
                val now = SystemClock.uptimeMillis()
                if (play && !player.playWhenReady) {
                    startedAt = now
                    startedFrom = player.currentPosition
                } else if (startedAt > 0 && player.currentPosition != startedFrom) {
                    startupMs = (now - startedAt).coerceIn(MIN_STARTUP_MS, MAX_STARTUP_MS)
                    startedAt = 0
                }
                player.playWhenReady = play
                // Its positions are output time, like the main player's; while it starts early it runs ahead on purpose.
                val want = maxOf(t, next.startMs)
                if (!starting && player.playbackState != Player.STATE_BUFFERING && overlayNeedsSeek(player.currentPosition, want, play, now - lastSeek, HEAD_MAX_DRIFT_MS)) {
                    player.seekTo(want)
                    lastSeek = now
                }
            } else {
                player.playWhenReady = false
            }
            if (main.isPlaying) withFrameMillis {} else delay(POLL_MS)
        }
    }
    val shown = span ?: spans.firstOrNull() ?: return
    VideoSurface(
        player,
        project.width,
        project.height,
        translucent = true,
        Modifier.fillMaxSize().graphicsLayer {
            val t = clock.longValue
            val p = shown.progress(t)
            val (x, y) = shown.kind.incomingOffset(p)
            alpha = if (shown.shownAt(t)) shown.kind.incomingAlpha(p) else 0f
            translationX = x * size.width
            translationY = y * size.height
        },
    )
}

private const val POLL_MS = 32L
// A CompositionPlayer's seek stalls it far longer than an ExoPlayer's, so a seek while playing costs more than some drift.
private const val HEAD_MAX_DRIFT_MS = 300L
private const val STARTUP_MS = 150L
private const val MIN_STARTUP_MS = 30L
private const val MAX_STARTUP_MS = 400L
