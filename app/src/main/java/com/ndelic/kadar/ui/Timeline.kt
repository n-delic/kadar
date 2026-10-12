package com.ndelic.kadar.ui

import android.graphics.Bitmap
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.Layer
import com.ndelic.kadar.R
import com.ndelic.kadar.TextLayer
import com.ndelic.kadar.VideoClip
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
 * Drag a track's ends to change when it shows, its middle to move it; tap the clip to trim; drag empty space to scrub.
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
    onClipTap: () -> Unit,
) {
    val total = clip.outputDurationMs.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp).systemGestureExclusion()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pad = with(LocalDensity.current) { TIMELINE_PAD.toPx() }
            val width = constraints.maxWidth - 2 * pad
            Column(Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                layers.asReversed().forEach { l ->
                    key(l.id) { Track(l, l.id == selectedId, total, pad, width, onSeek, onSelect, onLayerTime) }
                }
                ClipRow(clip, frames, total, onSeek, onClipTap)
            }
            Canvas(Modifier.matchParentSize()) {
                val x = pad + positionMs.toFloat() / total * width
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = TIMELINE_PAD, end = TIMELINE_PAD, top = 4.dp)) {
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
    total: Long,
    pad: Float,
    width: Float,
    onSeek: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onLayerTime: (Long, Long, Long, Boolean) -> Unit,
) {
    val current by rememberUpdatedState(layer)
    val density = LocalDensity.current
    val xs = pad + layer.startMs.coerceIn(0, total).toFloat() / total * width
    val xe = pad + layer.endMs.coerceIn(0, total).toFloat() / total * width
    val color = if (layer is TextLayer) Ink.accent else Ink.teal
    Box(
        Modifier.fillMaxWidth().height(34.dp).padding(vertical = 3.dp).pointerInput(total, width) {
            awaitEachGesture {
                val l = current
                val s0 = l.startMs.coerceIn(0, total)
                val e0 = l.endMs.coerceIn(0, total)
                val x0 = pad + s0.toFloat() / total * width
                val x1 = pad + e0.toFloat() / total * width
                val grab = 20.dp.toPx()
                val down = awaitFirstDown()
                val mode = when (down.position.x) {
                    in x0 - grab..x0 + grab / 2 -> TrackDrag.Start
                    in x1 - grab / 2..x1 + grab -> TrackDrag.End
                    in x0..x1 -> TrackDrag.Move
                    else -> TrackDrag.Seek
                }
                if (mode != TrackDrag.Seek) onSelect(l.id)
                fun timeAt(x: Float) = ((x - pad) / width * total).toLong().coerceIn(0, total)
                var last = s0 to e0
                var first = true
                if (mode == TrackDrag.Seek) onSeek(timeAt(down.position.x))
                while (true) {
                    val change = awaitPointerEvent().changes.first()
                    if (!change.pressed) break
                    val d = ((change.position.x - down.position.x) / width * total).toLong()
                    val next = when (mode) {
                        TrackDrag.Start -> (s0 + d).coerceIn(0, e0 - MIN_CLIP_MS) to e0
                        TrackDrag.End -> s0 to (e0 + d).coerceIn(s0 + MIN_CLIP_MS, total)
                        TrackDrag.Move -> d.coerceIn(-s0, total - e0).let { s0 + it to e0 + it }
                        TrackDrag.Seek -> {
                            onSeek(timeAt(change.position.x))
                            last
                        }
                    }
                    if (next != last) {
                        // Reaching the end means "to the end", so the layer still covers the video if it gets longer.
                        onLayerTime(l.id, next.first, if (next.second >= total) Long.MAX_VALUE else next.second, first)
                        first = false
                        last = next
                    }
                    change.consume()
                }
            }
        },
    ) {
        Box(
            Modifier
                .offset { IntOffset(xs.roundToInt(), 0) }
                .width(with(density) { (xe - xs).coerceAtLeast(1f).toDp() })
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                KIcon(if (layer is TextLayer) R.drawable.ic_title else R.drawable.ic_image, Modifier.size(14.dp), Ink.onAccent)
                Spacer(Modifier.width(6.dp))
                Text(if (layer is TextLayer) layer.text.lines().first() else "Image", color = Ink.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private enum class TrackDrag { Start, End, Move, Seek }

/** The kept part of the video, under the layer tracks. Tap to trim, drag to scrub. */
@Composable
private fun ClipRow(clip: VideoClip, frames: List<Bitmap>, total: Long, onSeek: (Long) -> Unit, onTap: () -> Unit) {
    val kept = if (frames.isEmpty()) frames else {
        val from = (clip.startMs * frames.size / clip.durationMs).toInt().coerceIn(0, frames.size - 1)
        val to = ((clip.endMs * frames.size + clip.durationMs - 1) / clip.durationMs).toInt().coerceIn(from + 1, frames.size)
        frames.subList(from, to)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = TIMELINE_PAD, vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Ink.raised)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .pointerInput(total) { detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width * total).toLong().coerceIn(0, total)) } },
    ) {
        kept.forEach { Image(it.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) }
    }
}

private val TIMELINE_PAD = 28.dp
private const val MIN_CLIP_MS = 300L
