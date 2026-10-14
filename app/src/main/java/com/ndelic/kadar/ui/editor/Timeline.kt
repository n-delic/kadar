package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ndelic.kadar.R
import com.ndelic.kadar.model.DrawLayer
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.video.AudioDrag
import com.ndelic.kadar.video.AudioPart
import com.ndelic.kadar.video.AudioRef
import com.ndelic.kadar.video.MIN_CLIP_MS
import com.ndelic.kadar.video.Segment
import com.ndelic.kadar.video.TransitionKind
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.dragged
import com.ndelic.kadar.video.edgeTrim
import com.ndelic.kadar.video.outputMs
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Trim mode for one clip: its whole source as a strip of frames, with handles. Positions are in source time; the player
 * works in output time (after trim and speed, the clip starting at [offsetMs]), so seeking converts between the two.
 */
@Composable
fun TrimStrip(clip: Segment, offsetMs: Long, frames: List<Bitmap>, positionMs: Long, onSeek: (Long) -> Unit, onTrim: (Long, Long) -> Unit, onDone: () -> Unit) {
    var start by remember(clip.startMs) { mutableLongStateOf(clip.startMs) }
    var end by remember(clip.endMs) { mutableLongStateOf(clip.endMs) }
    val clipState by rememberUpdatedState(clip)
    Column(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = TIMELINE_PAD, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelLabel(stringResource(R.string.tool_trim))
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.done), color = Ink.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDone).padding(8.dp))
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
                                // Short of the clip's end, which is where the next clip starts.
                                else -> onSeek(offsetMs + ((timeAt(x).coerceIn(start, end) - start) / c.speed).toLong().coerceAtMost(outputMs(start, end, c.speed) - 1))
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
                val xp = (pad + (start + (positionMs - offsetMs) * clip.speed) / clip.durationMs * width).coerceIn(pad, pad + width)
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
 * The NLE timeline in output time: one track per layer (top of the stack first) above the clips, one per song and
 * voiceover under them (tap to select, drag the middle to move, the ends to trim).
 * Drag a track's ends to change when it shows, its middle to move it; long-press it and drag up or down to reorder;
 * the eye and lock at its left hide and lock it; tap a clip to pick it, drag the picked clip's ends to trim it,
 * long-press a clip and drag sideways to move it, + adds one; drag empty space, the ruler or the clips to scrub.
 * Two fingers are for the view only: pinch to zoom the time axis, slide to scroll it. One finger edits and scrubs the
 * same way at any zoom, and while playing the view pages along with the playhead.
 */
@Composable
fun Timeline(
    clip: VideoClip,
    layers: List<Layer>,
    selectedId: Long?,
    pickedClip: Int?,
    frames: Map<Uri, List<Bitmap>>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onSelect: (Long?) -> Unit,
    onLayerTime: (Long, Long, Long, Boolean) -> Unit,
    onFlags: (Long, Boolean, Boolean) -> Unit,
    onMove: (Long, Int) -> Unit,
    onClipTap: (Int) -> Unit,
    onClipMove: (Int, Int) -> Unit,
    onClipTrim: (Int, Long, Long) -> Unit,
    onAddClip: () -> Unit,
    transitionAt: Int?,
    onTransition: (Int) -> Unit,
    selectedAudio: AudioRef?,
    onAudioSelect: (AudioRef) -> Unit,
    onAudioPart: (AudioRef, AudioPart) -> Unit,
) {
    val total = clip.outputDurationMs.coerceAtLeast(1)
    val startDp = if (layers.isEmpty()) TIMELINE_PAD else TRACK_START
    // The track being reordered and how far it's been dragged. Reset by the new layer order, so the drop doesn't flicker.
    var lift by remember(layers) { mutableStateOf<Pair<Long, Float>?>(null) }
    var viewport by remember { mutableStateOf(TimelineViewport()) }
    val view = viewport.fit(total)
    // Not fitted first: a view that was never zoomed keeps showing everything when a clip makes the video longer.
    LaunchedEffect(positionMs, total) { viewport = viewport.follow(positionMs, total) }
    Column(Modifier.fillMaxWidth().background(Ink.bg).padding(vertical = 8.dp).systemGestureExclusion()) {
        // Initial pass, so a second finger takes the gesture from the track, ruler or clip under the first one.
        BoxWithConstraints(
            Modifier.fillMaxWidth().pointerInput(total, startDp) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } < 2) continue
                        val start = startDp.toPx()
                        val width = size.width - start - TIMELINE_PAD.toPx()
                        val center = event.calculateCentroid().x - start
                        viewport = viewport.fit(total).pinch(event.calculateZoom(), center - event.calculatePan().x, center, width, total)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        ) {
            val density = LocalDensity.current
            val start = with(density) { startDp.toPx() }
            val width = constraints.maxWidth - start - with(density) { TIMELINE_PAD.toPx() }
            val rowPx = with(density) { TRACK_HEIGHT.toPx() }
            val tracks = layers.asReversed()
            val from = tracks.indexOfFirst { it.id == lift?.first }
            val to = if (from < 0) -1 else reorderTarget(from, lift!!.second, rowPx, tracks.size)
            Ruler(view, total, start, width, onSeek)
            Column(Modifier.padding(top = RULER_HEIGHT).heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                tracks.forEachIndexed { i, l ->
                    key(l.id) {
                        val room by animateFloatAsState(makeRoom(i, from, to) * rowPx)
                        Track(
                            layer = l,
                            selected = l.id == selectedId,
                            lifted = i == from,
                            offsetY = if (i == from) lift!!.second else if (from >= 0) room else 0f,
                            total = total,
                            view = view,
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
                ClipRow(clip, frames, pickedClip, total, view, startDp, onSeek, onClipTap, onClipMove, onClipTrim, onAddClip, transitionAt, onTransition)
                val voiceLabel = stringResource(R.string.voiceover)
                clip.music.forEachIndexed { i, m ->
                    val ref = AudioRef(voice = false, index = i)
                    // Keyed, so a track's gestures never act on the part that took its place in the list.
                    key(ref) { AudioTrack(m.part, m.name, R.drawable.ic_music_note, Ink.teal, null, ref == selectedAudio, total, view, start, width, onSeek, { onAudioSelect(ref) }) { onAudioPart(ref, it) } }
                }
                clip.voices.forEachIndexed { i, v ->
                    val ref = AudioRef(voice = true, index = i)
                    key(ref) { AudioTrack(v.part, voiceLabel, R.drawable.ic_mic, Ink.sky, v.durationMs, ref == selectedAudio, total, view, start, width, onSeek, { onAudioSelect(ref) }) { onAudioPart(ref, it) } }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val x = start + view.x(positionMs, width)
                if (x in start - 1f..start + width + 1f) drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
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
    view: TimelineViewport,
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
    // Clamped near the visible part, so a zoomed-in bar stays a sane size; the clip below hides the clamped ends.
    val header = with(density) { TRACK_HEADER.toPx() }
    val far = with(density) { (TIMELINE_PAD + 8.dp).toPx() }
    val xs = (start + view.x(layer.startMs.coerceIn(0, total), width)).coerceIn(header - far, start + width + far)
    val xe = (start + view.x(layer.endMs.coerceIn(0, total), width)).coerceIn(header - far, start + width + far)
    val color = when (layer) {
        is TextLayer -> Ink.accent
        is ImageLayer -> Ink.teal
        is VideoLayer -> Ink.violet
        is DrawLayer -> Ink.coral
        is ShapeLayer -> Ink.sky
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
            Modifier.fillMaxSize().drawWithContent { clipRect(left = header) { this@drawWithContent.drawContent() } }.pointerInput(total, view, start, width) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Read after the touch, so a drag starts from where the last one left the layer.
                    val l = current
                    val s0 = l.startMs.coerceIn(0, total)
                    val e0 = l.endMs.coerceIn(0, total)
                    val x0 = start + view.x(s0, width)
                    val x1 = start + view.x(e0, width)
                    val grab = 20.dp.toPx()
                    val mode = when (down.position.x) {
                        in x0 - grab..x0 + grab / 2 -> TrackDrag.Start
                        in x1 - grab / 2..x1 + grab -> TrackDrag.End
                        in x0..x1 -> TrackDrag.Move
                        else -> TrackDrag.Seek
                    }
                    fun timeAt(x: Float) = view.time(x - start, width, total)
                    if (mode == TrackDrag.Seek) {
                        onSeek(timeAt(down.position.x))
                        while (true) {
                            val change = awaitPointerEvent().changes.first()
                            if (!change.pressed || change.isConsumed) break
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
                            if (!change.pressed || change.isConsumed) break
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
                    while (change.pressed && !change.isConsumed) {
                        val d = ((change.position.x - down.position.x) / width * view.spanMs).toLong()
                        val next = when (mode) {
                            // Bounds can cross when the clip got shorter than the layer (trim, speed); coerceIn would throw.
                            TrackDrag.Start -> (s0 + d).coerceIn(0, maxOf(0, e0 - MIN_CLIP_MS)) to e0
                            TrackDrag.End -> s0 to (e0 + d).coerceIn(minOf(s0 + MIN_CLIP_MS, total), total)
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
                        layer is DrawLayer -> R.drawable.ic_draw
                        layer is ShapeLayer -> layer.kind.icon
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
            SmallIcon(if (layer.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, stringResource(if (layer.hidden) R.string.show else R.string.hide)) { onFlags(layer.id, !layer.hidden, layer.locked) }
            SmallIcon(if (layer.locked) R.drawable.ic_lock else R.drawable.ic_lock_open, stringResource(if (layer.locked) R.string.unlock else R.string.lock)) { onFlags(layer.id, layer.hidden, !layer.locked) }
        }
    }
}

/**
 * A song's or voiceover's [part] as an outlined bar, so it reads as sound rather than a layer. Tapping it selects it;
 * dragging its middle moves it, its ends trim it ([sourceMs] long, or null for a song, which loops). The drag draws in
 * place and only the drop changes the project (one undo step, one new preview). Elsewhere a drag scrubs.
 */
@Composable
private fun AudioTrack(
    part: AudioPart,
    label: String,
    icon: Int,
    color: Color,
    sourceMs: Long?,
    selected: Boolean,
    total: Long,
    view: TimelineViewport,
    start: Float,
    width: Float,
    onSeek: (Long) -> Unit,
    onSelect: () -> Unit,
    onChange: (AudioPart) -> Unit,
) {
    val current by rememberUpdatedState(part)
    // Reset by the new part, so the drop doesn't flicker.
    var moving by remember(part) { mutableStateOf<AudioPart?>(null) }
    val shown = moving ?: part
    val density = LocalDensity.current
    val far = with(density) { 8.dp.toPx() }
    fun xOf(ms: Long) = (start + view.x(ms.coerceIn(0, total), width)).coerceIn(start - far, start + width + far)
    val xs = xOf(shown.atMs)
    val xe = xOf(shown.endMs(total))
    Box(
        Modifier.fillMaxWidth().height(AUDIO_TRACK_HEIGHT).pointerInput(total, view, start, width, sourceMs) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // Read after the touch: a gesture starts waiting as soon as the last one ends, before its drop is in.
                val p = current
                val x0 = start + view.x(p.atMs.coerceIn(0, total), width)
                val x1 = maxOf(x0, start + view.x(p.endMs(total), width))
                val grab = 20.dp.toPx()
                val edge = when (down.position.x) {
                    in x0 - grab..x0 + grab / 2 -> AudioDrag.Start
                    in x1 - grab / 2..x1 + grab -> AudioDrag.End
                    in x0..x1 -> AudioDrag.Move
                    else -> null
                }
                fun timeAt(x: Float) = view.time(x - start, width, total)
                if (edge == null) {
                    onSeek(timeAt(down.position.x))
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        if (!change.pressed || change.isConsumed) break
                        onSeek(timeAt(change.position.x))
                        change.consume()
                    }
                    return@awaitEachGesture
                }
                onSelect()
                // Nothing is consumed until it moves sideways, so a vertical swipe scrolls the track list instead.
                var change: PointerInputChange
                do {
                    change = awaitPointerEvent().changes.first()
                    if (!change.pressed || change.isConsumed) return@awaitEachGesture
                } while ((change.position - down.position).getDistance() <= viewConfiguration.touchSlop)
                if (abs(change.position.y - down.position.y) > abs(change.position.x - down.position.x)) return@awaitEachGesture
                while (change.pressed && !change.isConsumed) {
                    moving = p.dragged(edge, ((change.position.x - down.position.x) / width * view.spanMs).toLong(), total, sourceMs)
                    change.consume()
                    change = awaitPointerEvent().changes.first()
                }
                // Consumed: a second finger took over to zoom, which cancels the drag.
                val result = moving
                if (result != null && !change.isConsumed && result != p) onChange(result) else moving = null
            }
        },
    ) {
        Box(
            Modifier
                .padding(vertical = 3.dp)
                .offset { IntOffset(xs.roundToInt(), 0) }
                .width(with(density) { (xe - xs).coerceAtLeast(1f).toDp() })
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.22f))
                .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else color, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                KIcon(icon, Modifier.size(14.dp), color)
                Spacer(Modifier.width(6.dp))
                Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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

/** Time ticks and labels for the part in view, above the tracks; tap or drag it to scrub. */
@Composable
private fun Ruler(view: TimelineViewport, total: Long, start: Float, width: Float, onSeek: (Long) -> Unit) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = Ink.dim, fontSize = 9.sp)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .pointerInput(view, total, start, width) { detectTapGestures { onSeek(view.time(it.x - start, width, total)) } }
            .pointerInput(view, total, start, width) { detectHorizontalDragGestures { change, _ -> onSeek(view.time(change.position.x - start, width, total)) } },
    ) {
        val step = view.rulerStepMs(width, 56.dp.toPx())
        val half = step / 2
        var t = ceil(view.fromMs / half).toLong() * half
        while (t <= view.fromMs + view.spanMs) {
            val x = start + view.x(t, width)
            val major = t % step == 0L
            drawLine(Ink.dim, Offset(x, size.height - (if (major) 6 else 3).dp.toPx()), Offset(x, size.height), 1.dp.toPx())
            if (major) drawText(measurer, rulerLabel(t, step), Offset(x + 3.dp.toPx(), 0f), label)
            t += half
        }
    }
}

/**
 * The kept part of each clip, end to end under the layer tracks. Tap a clip to pick it ([picked] is outlined, with
 * handles), drag its handles to trim it ([onTrim] once, at the end, with the new source start and end), drag elsewhere
 * to scrub, long-press and drag sideways to move it; the + after the strip adds a clip. Frames are tiled at their own
 * aspect and anchored in time, so they scroll with the tracks and repeat when zoomed in.
 */
@Composable
private fun ClipRow(
    clip: VideoClip,
    frames: Map<Uri, List<Bitmap>>,
    picked: Int?,
    total: Long,
    view: TimelineViewport,
    start: Dp,
    onSeek: (Long) -> Unit,
    onTap: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onTrim: (Int, Long, Long) -> Unit,
    onAdd: () -> Unit,
    transitionAt: Int?,
    onTransition: (Int) -> Unit,
) {
    val images = remember(frames) { frames.mapValues { (_, f) -> f.map { it.asImageBitmap() } } }
    val transitionIcon = painterResource(R.drawable.ic_transition_fade)
    val transitionLabels = (1 until clip.segments.size).map { stringResource(R.string.transition, it + 1) }
    val current by rememberUpdatedState(clip)
    val haptics = LocalHapticFeedback.current
    // The clip being moved and how far it's been dragged (px). Reset by the new order, so the drop doesn't flicker.
    var lift by remember(clip.segments) { mutableStateOf<Pair<Int, Float>?>(null) }
    // The picked clip while its edge is dragged, drawn in place of it; only the drop changes the project (one undo
    // step, one new preview). Reset by the new clips, like [lift].
    var trim by remember(clip.segments) { mutableStateOf<EdgeTrim?>(null) }
    val currentPicked by rememberUpdatedState(picked)
    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = start, top = 3.dp, bottom = 3.dp)) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Ink.raised)
                .semantics { customActions = transitionLabels.mapIndexed { i, label -> CustomAccessibilityAction(label) { onTransition(i + 1); true } } }
                .pointerInput(total, view) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // Read after the touch: a gesture starts waiting as soon as the last one ends, before its drop is in.
                        val c = current
                        fun timeAt(x: Float) = view.time(x, size.width.toFloat(), total)
                        val index = c.segmentAt(timeAt(down.position.x)).first
                        // A tap on a cut's button opens its transition.
                        val cuts = c.played.runningFold(0L) { t, s -> t + s.outputDurationMs }
                        val cut = (1 until c.segments.size).firstOrNull { abs(view.x(cuts[it], size.width.toFloat()) - down.position.x) < CUT_BUTTON.toPx() / 2 }
                        // On an end of the picked clip: dragging trims it (a tap still opens the cut's transition).
                        val p = currentPicked?.takeIf { it in c.segments.indices }
                        val left = p?.let {
                            val toStart = abs(view.x(cuts[it], size.width.toFloat()) - down.position.x)
                            val toEnd = abs(view.x(cuts[it + 1], size.width.toFloat()) - down.position.x)
                            val grab = EDGE_GRAB.toPx()
                            if (toStart < grab && toStart <= toEnd) true else if (toEnd < grab) false else null
                        }
                        // Like a track: moving first scrubs, holding still lifts the clip to move it, letting go taps it.
                        var moved: PointerInputChange? = null
                        var tapped = false
                        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val change = awaitPointerEvent().changes.first()
                                // Consumed: a second finger took the gesture to zoom or scroll the view.
                                if (change.isConsumed) break
                                if (!change.pressed) {
                                    tapped = true
                                    break
                                }
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    moved = change
                                    break
                                }
                            }
                        } == null
                        if (tapped) {
                            if (cut != null) onTransition(cut) else onTap(index)
                            return@awaitEachGesture
                        }
                        if (held) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            var dx = 0f
                            lift = index to dx
                            while (true) {
                                val change = awaitPointerEvent().changes.first()
                                if (!change.pressed || change.isConsumed) break
                                dx += change.positionChange().x
                                lift = index to dx
                                change.consume()
                            }
                            val target = c.segmentAt(timeAt(down.position.x + dx)).first
                            if (target == index) lift = null else onMove(index, target)
                            return@awaitEachGesture
                        }
                        var change = moved ?: return@awaitEachGesture
                        // A vertical swipe scrolls the track list.
                        if (abs(change.position.y - down.position.y) > abs(change.position.x - down.position.x)) return@awaitEachGesture
                        if (p != null && left != null) {
                            val s = c.segments[p]
                            while (change.pressed && !change.isConsumed) {
                                val d = ((change.position.x - down.position.x) / size.width * view.spanMs).toLong()
                                val (from, to) = edgeTrim(s.startMs, s.endMs, s.durationMs, s.speed, left, d)
                                trim = EdgeTrim(p, left, s.copy(startMs = from, endMs = to))
                                change.consume()
                                change = awaitPointerEvent().changes.first()
                            }
                            // Consumed: a second finger took over to zoom, which cancels the trim.
                            val t = trim
                            if (t != null && !change.isConsumed && t.segment != s) onTrim(p, t.segment.startMs, t.segment.endMs) else trim = null
                            return@awaitEachGesture
                        }
                        while (change.pressed && !change.isConsumed) {
                            onSeek(timeAt(change.position.x))
                            change.consume()
                            change = awaitPointerEvent().changes.first()
                        }
                    }
                },
        ) {
            // As the main track plays them: the head an overlapping transition plays over the clip before is left out.
            val edge = trim
            val shown = edge?.let { e -> clip.copy(segments = clip.segments.mapIndexed { i, s -> if (i == e.index) e.segment else s }) } ?: clip
            val played = shown.played
            val starts = played.runningFold(0L) { t, s -> t + s.outputDurationMs }.toMutableList()
            // A start being dragged moves its clip's start, not the clips after it, until the drop closes the gap.
            if (edge?.left == true) {
                val shift = clip.played.take(edge.index + 1).sumOf { it.outputDurationMs } - starts[edge.index + 1]
                for (k in edge.index until starts.size) starts[k] += shift
            }
            fun drawClip(i: Int, dx: Float) {
                val s = played[i]
                val x0 = view.x(starts[i], size.width) + dx
                val x1 = view.x(starts[i + 1], size.width) + dx
                if (x1 < 0 || x0 > size.width) return
                val strip = images[s.uri].orEmpty()
                strip.firstOrNull()?.let { first ->
                    val tileWidth = size.height * first.width / first.height
                    val tileMs = tileWidth / size.width * view.spanMs
                    clipRect(left = x0, right = x1) {
                        // Tiles start at the clip's own start, so its frames move with it.
                        var k = floor(maxOf(0f, view.fromMs - starts[i]) / tileMs)
                        while (starts[i] + k * tileMs < minOf(starts[i + 1].toFloat(), view.fromMs + view.spanMs)) {
                            val image = strip[filmstripIndex(s.startMs + (k + 0.5f) * tileMs * s.speed, s.durationMs, strip.size)]
                            val x = view.x(starts[i] + k * tileMs, size.width) + dx
                            drawImage(image, dstOffset = IntOffset(x.roundToInt(), 0), dstSize = IntSize(ceil(tileWidth).toInt() + 1, size.height.roundToInt()))
                            k++
                        }
                    }
                }
                if (i == picked || dx != 0f) {
                    val line = 2.dp.toPx()
                    drawRoundRect(Color.White, Offset(x0 + line / 2, line / 2), Size(x1 - x0 - line, size.height - line), CornerRadius(6.dp.toPx()), style = Stroke(line))
                } else if (i > 0) {
                    drawLine(Ink.bg, Offset(x0, 0f), Offset(x0, size.height), 2.dp.toPx())
                }
            }
            val lifted = lift
            val top = lifted?.first ?: edge?.index
            clip.segments.indices.forEach { if (it != top) drawClip(it, 0f) }
            // On top of the others, so it shows where it's being dragged.
            top?.let { i -> if (i in clip.segments.indices) drawClip(i, lifted?.second ?: 0f) }
            // The picked clip's trim handles, inside its outline.
            if (lifted == null) picked?.takeIf { it in clip.segments.indices }?.let { i ->
                val tab = HANDLE.toPx()
                val radius = CornerRadius(4.dp.toPx())
                val grip = Color(0xFF555555)
                listOf(view.x(starts[i], size.width), view.x(starts[i + 1], size.width) - tab).forEach { x ->
                    drawRoundRect(Color.White, Offset(x, 0f), Size(tab, size.height), radius)
                    drawLine(grip, Offset(x + tab / 2, size.height * 0.3f), Offset(x + tab / 2, size.height * 0.7f), 2.dp.toPx())
                }
            }
            // A button on every cut for its transition, lime when it has one or is open.
            if (lifted == null && edge == null) (1 until clip.segments.size).forEach { i ->
                val on = i == transitionAt || clip.segments[i].transition.kind != TransitionKind.None
                val box = CUT_BUTTON.toPx() * 0.7f
                val topLeft = Offset(view.x(starts[i], size.width) - box / 2, (size.height - box) / 2)
                drawRoundRect(if (on) Ink.accent else Ink.bg, topLeft, Size(box, box), CornerRadius(6.dp.toPx()))
                val iconSize = box * 0.7f
                translate(topLeft.x + (box - iconSize) / 2, topLeft.y + (box - iconSize) / 2) {
                    with(transitionIcon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(if (on) Ink.onAccent else Ink.text)) }
                }
            }
        }
        Box(Modifier.width(TIMELINE_PAD).fillMaxHeight().clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
            KIcon(R.drawable.ic_add, Modifier.size(22.dp), Ink.accent, stringResource(R.string.add_clip))
        }
    }
}

/** The picked clip's end being dragged on the clip strip: which clip, which end, and the clip as trimmed so far. */
private class EdgeTrim(val index: Int, val left: Boolean, val segment: Segment)

private val TIMELINE_PAD = 28.dp
/** How near an end of the picked clip a drag trims it; its handle draws [HANDLE] wide inside the clip. */
private val EDGE_GRAB = 22.dp
private val HANDLE = 10.dp
/** Touch width of a cut's transition button; it draws at 70% of that. */
private val CUT_BUTTON = 34.dp
private val TRACK_HEIGHT = 40.dp
private val AUDIO_TRACK_HEIGHT = 32.dp
private val RULER_HEIGHT = 18.dp
/** Hide and lock toggles, 40dp each. */
private val TRACK_HEADER = 80.dp
/** Where time 0 sits on tracks: past the header, with room left of it to grab a layer's start handle. */
private val TRACK_START = TRACK_HEADER + 12.dp
