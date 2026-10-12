package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ndelic.kadar.R
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.video.VideoClip
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Trim mode: the whole source as a strip of frames, with handles. Positions are in source time; the player works in
 * output time (after trim and speed), so seeking converts between the two.
 */
@Composable
fun TrimStrip(clip: VideoClip, frames: List<Bitmap>, positionMs: Long, onSeek: (Long) -> Unit, onTrim: (Long, Long) -> Unit, onDone: () -> Unit) {
    var start by remember(clip.startMs) { mutableLongStateOf(clip.startMs) }
    var end by remember(clip.endMs) { mutableLongStateOf(clip.endMs) }
    val clipState by rememberUpdatedState(clip)
    Column(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = TIMELINE_PAD, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelLabel("Trim")
            Spacer(Modifier.weight(1f))
            Text("Done", color = Ink.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDone).padding(8.dp))
        }
        // Full width, so the handles in the padding still get touches, and those touches aren't taken by the
        // system back gesture at the screen edges.
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp).systemGestureExclusion()) {
            val density = LocalDensity.current
            val pad = with(density) { TIMELINE_PAD.toPx() }
            val width = constraints.maxWidth - 2 * pad
            val grab = with(density) { 28.dp.toPx() }
            Row(Modifier.fillMaxSize().padding(horizontal = TIMELINE_PAD).clip(RoundedCornerShape(6.dp)).background(Ink.raised)) {
                frames.forEach { Image(it.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) }
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(width, clip.startMs, clip.endMs) {
                    awaitEachGesture {
                        val c = clipState
                        fun timeAt(x: Float) = ((x - pad) / width * c.durationMs).toLong().coerceIn(0, c.durationMs)
                        fun xOf(ms: Long) = pad + ms.toFloat() / c.durationMs * width
                        val down = awaitFirstDown()
                        // Handles sit just outside the kept range, so bias the grab zones outward.
                        val dragging = when {
                            down.position.x in xOf(start) - grab..xOf(start) + grab / 2 -> 0
                            down.position.x in xOf(end) - grab / 2..xOf(end) + grab -> 1
                            else -> 2
                        }
                        var x = down.position.x
                        while (true) {
                            when (dragging) {
                                // Keep at least 0.3 s, the encoder can't do an empty clip.
                                0 -> start = timeAt(x).coerceAtMost(end - MIN_CLIP_MS)
                                1 -> end = timeAt(x).coerceAtLeast(start + MIN_CLIP_MS)
                                else -> onSeek(((timeAt(x).coerceIn(start, end) - start) / c.speed).toLong())
                            }
                            val change = awaitPointerEvent().changes.first()
                            if (!change.pressed) break
                            x = change.position.x
                            change.consume()
                        }
                        if (dragging != 2) onTrim(start, end)
                    }
                },
            ) {
                val xs = pad + start.toFloat() / clip.durationMs * width
                val xe = pad + end.toFloat() / clip.durationMs * width
                val dim = Color.Black.copy(alpha = 0.65f)
                drawRect(dim, Offset(pad, 0f), Size(xs - pad, size.height))
                drawRect(dim, Offset(xe, 0f), Size(pad + width - xe, size.height))
                val edge = 2.dp.toPx()
                drawRect(Color.White, Offset(xs, 0f), Size(xe - xs, size.height), style = Stroke(edge))
                val tab = 14.dp.toPx()
                drawRoundRect(Color.White, Offset(xs - tab, 0f), Size(tab, size.height), CornerRadius(4.dp.toPx()))
                drawRoundRect(Color.White, Offset(xe, 0f), Size(tab, size.height), CornerRadius(4.dp.toPx()))
                val grip = Color(0xFF555555)
                drawLine(grip, Offset(xs - tab / 2, size.height * 0.35f), Offset(xs - tab / 2, size.height * 0.65f), edge)
                drawLine(grip, Offset(xe + tab / 2, size.height * 0.35f), Offset(xe + tab / 2, size.height * 0.65f), edge)
                val xp = pad + (start + positionMs * clip.speed) / clip.durationMs * width
                drawLine(Color.White, Offset(xp, -6.dp.toPx()), Offset(xp, size.height + 6.dp.toPx()), 3.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = TIMELINE_PAD, end = TIMELINE_PAD, top = 6.dp)) {
            Text(formatMs(start), color = Ink.dim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatMs(end), color = Ink.dim, fontSize = 11.sp)
        }
    }
}

/**
 * The NLE timeline in output time: one track per layer (top of the stack first) above the video clip.
 * Drag a track's ends to change when it shows, its middle to move it; long-press it and drag up or down to reorder;
 * the eye and lock at its left hide and lock it; tap the clip to trim; drag empty space to scrub.
 */
@Composable
fun Timeline(
    clip: VideoClip,
    layers: List<Layer>,
    selectedId: Long?,
    frames: List<Bitmap>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onLayerTime: (Long, Long, Long, Boolean) -> Unit,
    onFlags: (Long, Boolean, Boolean) -> Unit,
    onMove: (Long, Int) -> Unit,
    onClipTap: () -> Unit,
) {
    val total = clip.outputDurationMs.coerceAtLeast(1)
    val startDp = if (layers.isEmpty()) TIMELINE_PAD else TRACK_START
    // The track being reordered and how far it's been dragged. Reset by the new layer order, so the drop doesn't flicker.
    var lift by remember(layers) { mutableStateOf<Pair<Long, Float>?>(null) }
    Column(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp).systemGestureExclusion()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val start = with(density) { startDp.toPx() }
            val width = constraints.maxWidth - start - with(density) { TIMELINE_PAD.toPx() }
            val rowPx = with(density) { TRACK_HEIGHT.toPx() }
            val tracks = layers.asReversed()
            val from = tracks.indexOfFirst { it.id == lift?.first }
            val to = if (from < 0) -1 else reorderTarget(from, lift!!.second, rowPx, tracks.size)
            Column(Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                tracks.forEachIndexed { i, l ->
                    key(l.id) {
                        val room by animateFloatAsState(makeRoom(i, from, to) * rowPx)
                        Track(
                            layer = l,
                            selected = l.id == selectedId,
                            lifted = i == from,
                            offsetY = if (i == from) lift!!.second else if (from >= 0) room else 0f,
                            total = total,
                            start = start,
                            width = width,
                            onSeek = onSeek,
                            onSelect = onSelect,
                            onLayerTime = onLayerTime,
                            onFlags = onFlags,
                            onLift = { dy -> lift = l.id to dy },
                            onDrop = {
                                val target = reorderTarget(i, lift?.second ?: 0f, rowPx, tracks.size)
                                // Tracks list the top of the stack first, layers list it last.
                                if (target == i) lift = null else onMove(l.id, i - target)
                            },
                        )
                    }
                }
                ClipRow(clip, frames, total, startDp, onSeek, onClipTap)
            }
            Canvas(Modifier.matchParentSize()) {
                val x = start + positionMs.toFloat() / total * width
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = startDp, end = TIMELINE_PAD, top = 4.dp)) {
            Text(formatMs(positionMs), color = Ink.dim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatMs(total), color = Ink.dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Track(
    layer: Layer,
    selected: Boolean,
    lifted: Boolean,
    offsetY: Float,
    total: Long,
    start: Float,
    width: Float,
    onSeek: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onLayerTime: (Long, Long, Long, Boolean) -> Unit,
    onFlags: (Long, Boolean, Boolean) -> Unit,
    onLift: (Float) -> Unit,
    onDrop: () -> Unit,
) {
    val current by rememberUpdatedState(layer)
    val lift by rememberUpdatedState(onLift)
    val drop by rememberUpdatedState(onDrop)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val xs = start + layer.startMs.coerceIn(0, total).toFloat() / total * width
    val xe = start + layer.endMs.coerceIn(0, total).toFloat() / total * width
    val color = when (layer) {
        is TextLayer -> Ink.accent
        is ImageLayer -> Ink.teal
        is VideoLayer -> Ink.violet
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer { translationY = offsetY }
            .then(if (lifted) Modifier.shadow(8.dp, RoundedCornerShape(8.dp)).background(Ink.raised, RoundedCornerShape(8.dp)) else Modifier),
    ) {
        Box(
            Modifier.fillMaxSize().pointerInput(total, start, width) {
                awaitEachGesture {
                    val l = current
                    val s0 = l.startMs.coerceIn(0, total)
                    val e0 = l.endMs.coerceIn(0, total)
                    val x0 = start + s0.toFloat() / total * width
                    val x1 = start + e0.toFloat() / total * width
                    val grab = 20.dp.toPx()
                    val down = awaitFirstDown()
                    val mode = when (down.position.x) {
                        in x0 - grab..x0 + grab / 2 -> TrackDrag.Start
                        in x1 - grab / 2..x1 + grab -> TrackDrag.End
                        in x0..x1 -> TrackDrag.Move
                        else -> TrackDrag.Seek
                    }
                    fun timeAt(x: Float) = ((x - start) / width * total).toLong().coerceIn(0, total)
                    if (mode == TrackDrag.Seek) {
                        onSeek(timeAt(down.position.x))
                        while (true) {
                            val change = awaitPointerEvent().changes.first()
                            if (!change.pressed) break
                            onSeek(timeAt(change.position.x))
                            change.consume()
                        }
                        return@awaitEachGesture
                    }
                    onSelect(l.id)
                    // Locked: a tap still selects, and the list can still scroll from here.
                    if (l.locked) return@awaitEachGesture
                    // Moving first drags in time; holding still lifts the track to reorder it. Nothing is consumed
                    // until then, so a vertical swipe scrolls the track list instead.
                    var moved: PointerInputChange? = null
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent().changes.first()
                            if (!change.pressed || change.isConsumed) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = change
                                break
                            }
                        }
                    } == null
                    if (held) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        var dy = 0f
                        lift(dy)
                        while (true) {
                            val change = awaitPointerEvent().changes.first()
                            if (!change.pressed) break
                            // Deltas, not positions: the track itself moves under the finger.
                            dy += change.positionChange().y
                            lift(dy)
                            change.consume()
                        }
                        drop()
                        return@awaitEachGesture
                    }
                    var change = moved ?: return@awaitEachGesture
                    if (abs(change.position.y - down.position.y) > abs(change.position.x - down.position.x)) return@awaitEachGesture
                    var last = s0 to e0
                    var first = true
                    while (change.pressed) {
                        val d = ((change.position.x - down.position.x) / width * total).toLong()
                        val next = when (mode) {
                            TrackDrag.Start -> (s0 + d).coerceIn(0, e0 - MIN_CLIP_MS) to e0
                            TrackDrag.End -> s0 to (e0 + d).coerceIn(s0 + MIN_CLIP_MS, total)
                            else -> d.coerceIn(-s0, total - e0).let { s0 + it to e0 + it }
                        }
                        if (next != last) {
                            // Reaching the end means "to the end", so the layer still covers the video if it gets longer.
                            onLayerTime(l.id, next.first, if (next.second >= total) Long.MAX_VALUE else next.second, first)
                            first = false
                            last = next
                        }
                        change.consume()
                        change = awaitPointerEvent().changes.first()
                    }
                }
            },
        ) {
            Box(
                Modifier
                    .padding(vertical = 4.dp)
                    .offset { IntOffset(xs.roundToInt(), 0) }
                    .width(with(density) { (xe - xs).coerceAtLeast(1f).toDp() })
                    .fillMaxHeight()
                    .alpha(if (layer.hidden) 0.35f else 1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = when {
                        layer.locked -> R.drawable.ic_lock
                        layer is TextLayer -> R.drawable.ic_title
                        layer is VideoLayer -> R.drawable.ic_movie
                        else -> R.drawable.ic_image
                    }
                    KIcon(icon, Modifier.size(14.dp), Ink.onAccent)
                    Spacer(Modifier.width(6.dp))
                    Text(layer.label, color = Ink.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // On top of the track's gesture area, so taps on the toggles never start a drag.
        Row(Modifier.width(TRACK_HEADER)) {
            SmallIcon(if (layer.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, if (layer.hidden) "Show" else "Hide") { onFlags(layer.id, !layer.hidden, layer.locked) }
            SmallIcon(if (layer.locked) R.drawable.ic_lock else R.drawable.ic_lock_open, if (layer.locked) "Unlock" else "Lock") { onFlags(layer.id, layer.hidden, !layer.locked) }
        }
    }
}

private enum class TrackDrag { Start, End, Move, Seek }

/** Where a track dragged [dy] px from row [from] lands, in a list of [count] rows [rowPx] tall. */
fun reorderTarget(from: Int, dy: Float, rowPx: Float, count: Int) = (from + (dy / rowPx).roundToInt()).coerceIn(0, count - 1)

/** Rows that track [i] steps aside (-1 up, 1 down) to make room while another track moves from row [from] to [to]. */
fun makeRoom(i: Int, from: Int, to: Int) = when (i) {
    in from + 1..to -> -1
    in to..<from -> 1
    else -> 0
}

/** The kept part of the video, under the layer tracks. Tap to trim, drag to scrub. */
@Composable
private fun ClipRow(clip: VideoClip, frames: List<Bitmap>, total: Long, start: Dp, onSeek: (Long) -> Unit, onTap: () -> Unit) {
    val kept = if (frames.isEmpty()) frames else {
        val from = (clip.startMs * frames.size / clip.durationMs).toInt().coerceIn(0, frames.size - 1)
        val to = ((clip.endMs * frames.size + clip.durationMs - 1) / clip.durationMs).toInt().coerceIn(from + 1, frames.size)
        frames.subList(from, to)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = start, end = TIMELINE_PAD, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Ink.raised)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .pointerInput(total) { detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width * total).toLong().coerceIn(0, total)) } },
    ) {
        kept.forEach { Image(it.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) }
    }
}

private val TIMELINE_PAD = 28.dp
private val TRACK_HEIGHT = 40.dp
/** Hide and lock toggles, 40dp each. */
private val TRACK_HEADER = 80.dp
/** Where time 0 sits on tracks: past the header, with room left of it to grab a layer's start handle. */
private val TRACK_START = TRACK_HEADER + 12.dp
private const val MIN_CLIP_MS = 300L
