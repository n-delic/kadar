@file:OptIn(UnstableApi::class, ExperimentalApi::class)

package com.ndelic.kadar.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.ndelic.kadar.EditorUiState
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.ImageLayer
import com.ndelic.kadar.Layer
import com.ndelic.kadar.Project
import com.ndelic.kadar.R
import com.ndelic.kadar.TextLayer
import com.ndelic.kadar.Transform
import com.ndelic.kadar.VideoClip
import com.ndelic.kadar.composition
import com.ndelic.kadar.drawLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

enum class Panel { Sticker, Canvas, VideoCanvas, Speed, Audio, Font, Color, Stroke, Box, Style, Adjust, Filters, Layers }

@Composable
fun EditorScreen(state: EditorUiState, vm: EditorViewModel, onBrowse: () -> Unit) {
    val project = state.project ?: return
    val selected = state.selected
    val video = project.video
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    // null: closed; otherwise the text being typed (for a new layer, or the selected text layer).
    var typing by rememberSaveable { mutableStateOf<String?>(null) }
    val image = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::addImage) }
    val music = rememberLauncherForActivityResult(OpenDocument()) { it?.let(vm::setMusic) }
    val player = if (video != null) rememberCompositionPlayer() else null
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var trimming by rememberSaveable { mutableStateOf(false) }
    var erasing by remember { mutableStateOf<Pair<ImageLayer, Bitmap?>?>(null) }
    var cropping by remember { mutableStateOf<ImageLayer?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.selectedId) {
        // Selecting from the Layers list keeps the list open; anything else starts from the layer's tools.
        if (panel != Panel.Layers) panel = null
        if (state.selectedId != null) trimming = false
    }
    BackHandler {
        when {
            panel != null -> panel = null
            selected != null -> vm.select(null)
            else -> vm.close()
        }
    }
    if (player != null && video != null) {
        LaunchedEffect(video) {
            player.setComposition(video.composition(null))
            player.prepare()
            // A paused player keeps showing the old frame after a new composition; seek once it's loaded to repaint.
            delay(300)
            if (!player.isPlaying) player.seekTo(player.currentPosition)
        }
        LaunchedEffect(playing) { player.playWhenReady = playing }
        LaunchedEffect(player) {
            while (true) {
                positionMs = player.currentPosition
                delay(50)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(R.drawable.ic_close, "Back to projects", onClick = vm::close)
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(22.dp)).background(Ink.raised)) {
                PillButton(R.drawable.ic_undo, "Undo", state.canUndo, vm::undo)
                PillButton(R.drawable.ic_redo, "Redo", state.canRedo, vm::redo)
            }
            Spacer(Modifier.weight(1f))
            // Photos only: video shows the stack as timeline tracks.
            if (video == null) {
                CircleButton(R.drawable.ic_layers, "Layers", enabled = project.layers.isNotEmpty(), active = panel == Panel.Layers) {
                    panel = if (panel == Panel.Layers) null else Panel.Layers
                }
                Spacer(Modifier.width(10.dp))
            }
            CircleButton(R.drawable.ic_download, "Save to gallery") { vm.export(share = false) }
            Spacer(Modifier.width(10.dp))
            CircleButton(R.drawable.ic_share, "Share") { vm.export(share = true) }
        }

        Stage(project, selected, player, if (video != null) positionMs else null, vm, onEditText = { typing = it.text }, Modifier.weight(1f).fillMaxWidth())

        if (video != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${formatMs(positionMs)} / ${formatMs(video.outputDurationMs)}",
                    color = Ink.dim,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                )
                Box(Modifier.size(48.dp).clip(CircleShape).background(Ink.raised).clickableNoRipple { playing = !playing }, contentAlignment = Alignment.Center) {
                    KIcon(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow, Modifier.size(30.dp), description = if (playing) "Pause" else "Play")
                }
            }
        }

        AnimatedContent(
            targetState = panel,
            transitionSpec = { (fadeIn(tween(160)) togetherWith fadeOut(tween(120))).using(SizeTransform(clip = true)) },
            label = "panel",
        ) { p ->
            if (p != null) Box(Modifier.fullWidthPanel()) {
                when (p) {
                    Panel.Sticker -> StickerPanel(state.stickers, vm)
                    Panel.Canvas -> CanvasPanel(project, state.settings.exportSize, vm)
                    Panel.Speed -> video?.let { SpeedPanel(it, vm) }
                    Panel.Audio -> video?.let { AudioPanel(it, vm, onAddMusic = { music.launch(arrayOf("audio/*")) }) }
                    Panel.Font -> (selected as? TextLayer)?.let { FontPanel(it, vm) }
                    Panel.Color -> (selected as? TextLayer)?.let { t -> ColorRow(t.color, allowNone = false) { c -> c?.let { vm.editText { copy(color = it) } } } }
                    Panel.Stroke -> (selected as? TextLayer)?.let { t -> ColorRow(t.outline, allowNone = true) { vm.editText { copy(outline = it) } } }
                    Panel.Box -> (selected as? TextLayer)?.let { t -> ColorRow(t.box, allowNone = true) { vm.editText { copy(box = it) } } }
                    Panel.Layers -> LayersPanel(project.layers, state.selectedId, vm)
                    Panel.VideoCanvas -> video?.let { VideoCanvasPanel(project, it, state.settings.exportSize, vm) }
                    Panel.Style -> (selected as? TextLayer)?.let { TextStylePanel(it, vm) }
                    Panel.Adjust -> when (val l = selected) {
                        is ImageLayer -> AdjustPanel(l.adjust) { a -> vm.editImage { copy(adjust = a) } }
                        null -> video?.let { v -> AdjustPanel(v.adjust) { a -> vm.updateClip { copy(adjust = a) } } }
                        else -> {}
                    }
                    Panel.Filters -> when (val l = selected) {
                        is ImageLayer -> FiltersPanel(l.bitmap, l.adjust) { a -> vm.editImage { copy(adjust = a) } }
                        null -> video?.let { v -> FiltersPanel(state.filmstrip.firstOrNull(), v.adjust) { a -> vm.updateClip { copy(adjust = a) } } }
                        else -> {}
                    }
                }
            }
        }

        val toolset = when (selected) {
            null -> 0
            is TextLayer -> 1
            is ImageLayer -> 2
        }
        AnimatedContent(targetState = toolset, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "tools") { tools ->
            Row(
                Modifier.fillMaxWidth().background(Ink.panel).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fun toggle(p: Panel) {
                    panel = if (panel == p) null else p
                }
                when (tools) {
                    0 -> {
                        IconTool(R.drawable.ic_title, "Text") { typing = "" }
                        IconTool(R.drawable.ic_sentiment_satisfied, "Sticker", panel == Panel.Sticker) { toggle(Panel.Sticker) }
                        IconTool(R.drawable.ic_image, "Image") { image.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                        IconTool(R.drawable.ic_language, "Web", onClick = onBrowse)
                        if (video != null) {
                            IconTool(R.drawable.ic_content_cut, "Trim", trimming) { trimming = !trimming }
                            IconTool(R.drawable.ic_speed, "Speed", panel == Panel.Speed) { toggle(Panel.Speed) }
                            IconTool(R.drawable.ic_aspect_ratio, "Canvas", panel == Panel.VideoCanvas) { toggle(Panel.VideoCanvas) }
                            IconTool(R.drawable.ic_tune, "Adjust", panel == Panel.Adjust) { toggle(Panel.Adjust) }
                            IconTool(R.drawable.ic_filter_vintage, "Filters", panel == Panel.Filters) { toggle(Panel.Filters) }
                            IconTool(R.drawable.ic_music_note, "Audio", panel == Panel.Audio) { toggle(Panel.Audio) }
                        } else {
                            IconTool(R.drawable.ic_aspect_ratio, "Canvas", panel == Panel.Canvas) { toggle(Panel.Canvas) }
                        }
                    }
                    1 -> {
                        IconTool(R.drawable.ic_check, "Done") { vm.select(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_edit, "Edit") { (state.selected as? TextLayer)?.let { typing = it.text } }
                        IconTool(R.drawable.ic_match_case, "Font", panel == Panel.Font) { toggle(Panel.Font) }
                        IconTool(R.drawable.ic_palette, "Color", panel == Panel.Color) { toggle(Panel.Color) }
                        IconTool(R.drawable.ic_border_color, "Stroke", panel == Panel.Stroke) { toggle(Panel.Stroke) }
                        IconTool(R.drawable.ic_rectangle, "Box", panel == Panel.Box) { toggle(Panel.Box) }
                        IconTool(R.drawable.ic_text_fields, "Style", panel == Panel.Style) { toggle(Panel.Style) }
                        IconTool(R.drawable.ic_content_copy, "Duplicate", onClick = vm::duplicateSelected)
                        IconTool(R.drawable.ic_flip_to_front, "Front", onClick = vm::bringToFront)
                        IconTool(R.drawable.ic_flip_to_back, "Back", onClick = vm::sendToBack)
                        IconTool(R.drawable.ic_delete, "Delete", onClick = vm::deleteSelected)
                    }
                    else -> {
                        IconTool(R.drawable.ic_check, "Done") { vm.select(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_background_replace, "Remove BG", onClick = vm::removeBackground)
                        IconTool(R.drawable.ic_ink_eraser, "Eraser") {
                            (state.selected as? ImageLayer)?.let { l -> scope.launch { erasing = l to vm.loadSource(l) } }
                        }
                        IconTool(R.drawable.ic_crop, "Crop") { cropping = state.selected as? ImageLayer }
                        IconTool(R.drawable.ic_tune, "Adjust", panel == Panel.Adjust) { toggle(Panel.Adjust) }
                        IconTool(R.drawable.ic_filter_vintage, "Filters", panel == Panel.Filters) { toggle(Panel.Filters) }
                        IconTool(R.drawable.ic_library_add, "Save sticker", onClick = vm::saveSticker)
                        IconTool(R.drawable.ic_content_copy, "Duplicate", onClick = vm::duplicateSelected)
                        IconTool(R.drawable.ic_flip_to_front, "Front", onClick = vm::bringToFront)
                        IconTool(R.drawable.ic_flip_to_back, "Back", onClick = vm::sendToBack)
                        IconTool(R.drawable.ic_delete, "Delete", onClick = vm::deleteSelected)
                    }
                }
            }
        }

        if (video != null && player != null) {
            if (trimming) {
                TrimStrip(
                    clip = video,
                    frames = state.filmstrip,
                    positionMs = positionMs,
                    onSeek = { player.seekTo(it) },
                    onTrim = { start, end -> vm.updateClip { copy(startMs = start, endMs = end) } },
                    onDone = { trimming = false },
                )
            } else {
                Timeline(
                    clip = video,
                    layers = project.layers,
                    selectedId = state.selectedId,
                    frames = state.filmstrip,
                    positionMs = positionMs,
                    onSeek = { player.seekTo(it) },
                    onSelect = vm::select,
                    onLayerTime = vm::setLayerTime,
                    onClipTap = {
                        vm.select(null)
                        trimming = true
                    },
                )
            }
        }
    }

    erasing?.let { (layer, source) ->
        EraseScreen(
            layer = layer,
            source = source,
            onDone = {
                erasing = null
                vm.applyErase(layer.id, it)
            },
            onCancel = { erasing = null },
        )
    }
    cropping?.let { layer ->
        CropScreen(
            layer = layer,
            onDone = { r ->
                cropping = null
                vm.applyCrop(layer.id, r[0], r[1], r[2], r[3])
            },
            onCancel = { cropping = null },
        )
    }

    typing?.let { initial ->
        val layer = selected as? TextLayer
        TextEntry(
            initial = initial,
            font = layer?.font?.typeface ?: vm.fonts.first().typeface,
            color = layer?.color ?: android.graphics.Color.WHITE,
            onDone = { text, color ->
                typing = null
                if (layer != null) vm.editText { copy(text = text, color = color) } else vm.addText(text, color)
            },
            onCancel = { typing = null },
        )
    }
}

@Composable
private fun PillButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(width = 52.dp, height = 44.dp).clickableNoRipple(enabled, onClick), contentAlignment = Alignment.Center) {
        KIcon(icon, Modifier.size(24.dp), if (enabled) Ink.text else Ink.line, description)
    }
}

@Composable
private fun ToolDivider() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 32.dp).background(Ink.line))
}

private enum class Handle { Delete, Scale }

/** Which snaps are active during a drag: drives the guide lines and a tick of haptic feedback when one engages. */
private class Snaps(private val haptics: HapticFeedback) {
    var x by mutableStateOf(false)
    var y by mutableStateOf(false)
    private var angle = false

    fun apply(t: Transform, cx: Float, cy: Float, distance: Float): Transform {
        val s = t.snapped(cx, cy, distance)
        val nx = s.x == cx && distance > 0
        val ny = s.y == cy && distance > 0
        val na = s.rotation != t.rotation
        if ((nx && !x) || (ny && !y) || (na && !angle)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        x = nx
        y = ny
        angle = na
        return s
    }

    fun clear() {
        x = false
        y = false
        angle = false
    }
}

private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2DE1C2.toInt() }

/**
 * The canvas: video preview underneath, layers drawn on top at screen scale.
 * Drag, pinch and twist move the selected layer; the corner handles delete it or scale and rotate it with one finger;
 * double-tap a text layer to edit it.
 */
@Composable
private fun Stage(project: Project, selected: Layer?, player: CompositionPlayer?, timeMs: Long?, vm: EditorViewModel, onEditText: (TextLayer) -> Unit, modifier: Modifier) {
    // In a video only the layers on screen at the playhead show (and can be grabbed); the selected one always does, to edit it.
    val shown = project.layers.filter { !it.hidden && (timeMs == null || it.visibleAt(timeMs) || it.id == selected?.id) }
    BoxWithConstraints(modifier.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        val scale = min(constraints.maxWidth / project.width.toFloat(), constraints.maxHeight / project.height.toFloat())
        val density = LocalDensity.current
        val size = with(density) { DpSize((project.width * scale).toDp(), (project.height * scale).toDp()) }
        val handleRadius = with(density) { 24.dp.toPx() }
        val layers by rememberUpdatedState(shown)
        val current by rememberUpdatedState(selected)
        val editText by rememberUpdatedState(onEditText)
        val lastTap = remember { longArrayOf(-1, 0) } // layer id, time
        val haptics = LocalHapticFeedback.current
        val snaps = remember { Snaps(haptics) }
        val cx = project.width / 2f
        val cy = project.height / 2f
        val snapDistance = with(density) { 10.dp.toPx() }
        val transparent = project.background == android.graphics.Color.TRANSPARENT
        // View zoom/pan (display only, never saved). Pan is in screen pixels from the centered position.
        var viewZoom by remember(project.width, project.height) { mutableFloatStateOf(1f) }
        var viewPan by remember(project.width, project.height) { mutableStateOf(Offset.Zero) }
        val boxPx = Size(project.width * scale, project.height * scale)
        /** Zooms by [z] around [focus] and moves by [move]; both in screen pixels relative to the stage center. */
        fun zoomView(z: Float, focus: Offset, move: Offset) {
            val nz = (viewZoom * z).coerceIn(1f, MAX_VIEW_ZOOM)
            if (nz == 1f) {
                viewZoom = 1f
                viewPan = Offset.Zero
                return
            }
            val f = nz / viewZoom
            val p = focus - (focus - viewPan) * f + move
            // Keep at least part of the canvas on screen.
            val maxX = boxPx.width * nz / 2
            val maxY = boxPx.height * nz / 2
            viewZoom = nz
            viewPan = Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
        }
        val stageCenter = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
        // Gestures that start around the canvas: tap deselects, double-tap resets the view, drag and pinch move it.
        Box(
            Modifier.matchParentSize().pointerInput(scale) {
                var lastUp = 0L
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        if (pan != Offset.Zero || zoom != 1f) {
                            moved = true
                            zoomView(zoom, event.calculateCentroid() - stageCenter, pan)
                            event.changes.forEach { it.consume() }
                        }
                    }
                    if (!moved) {
                        if (down.uptimeMillis - lastUp < 300) zoomView(0f, Offset.Zero, Offset.Zero) else vm.select(null)
                        lastUp = down.uptimeMillis
                    }
                }
            },
        )
        Box(
            Modifier.size(size).graphicsLayer {
                scaleX = viewZoom
                scaleY = viewZoom
                translationX = viewPan.x
                translationY = viewPan.y
            },
        ) {
            Box(Modifier.fillMaxSize().clipToBounds().background(if (transparent) Color(0xFF3A3A3A) else Color(project.background))) {
                if (transparent) Checkerboard()
                player?.let { p -> VideoSurface(p, project.width, project.height, Modifier.fillMaxSize()) }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(scale) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            // Gestures that start on the canvas are the canvas's, not the surrounding stage's.
                            down.consume()
                            val sel = current?.takeUnless { it.locked }
                            val handle = sel?.let { handleAt(it, down.position, scale, handleRadius) }
                            if (sel != null && handle != null) {
                                down.consume()
                                if (handle == Handle.Delete) {
                                    while (awaitPointerEvent().changes.any { it.pressed }) Unit
                                    vm.deleteSelected()
                                } else {
                                    val center = Offset(sel.t.x * scale, sel.t.y * scale)
                                    val v0 = down.position - center
                                    var first = true
                                    while (true) {
                                        val change = awaitPointerEvent().changes.first()
                                        if (!change.pressed) {
                                            snaps.clear()
                                            break
                                        }
                                        val v = change.position - center
                                        val turn = Math.toDegrees((atan2(v.y, v.x) - atan2(v0.y, v0.x)).toDouble()).toFloat()
                                        val next = sel.t.copy(scale = sel.t.scale * v.getDistance() / v0.getDistance(), rotation = sel.t.rotation + turn)
                                        vm.setSelectedTransform(snaps.apply(next, cx, cy, distance = 0f), first)
                                        first = false
                                        change.consume()
                                    }
                                }
                                return@awaitEachGesture
                            }
                            val p = down.position / scale
                            val onSelected = sel != null && sel.t.contains(sel.width, sel.height, p.x, p.y)
                            val hit = if (onSelected) sel else layers.lastOrNull { !it.locked && it.t.contains(it.width, it.height, p.x, p.y) }
                            // What this gesture drives, decided on the first movement: the selected layer if it started on it;
                            // otherwise a second finger means "zoom the view", one finger drags the touched layer (or pans the view).
                            var mode = if (onSelected) GestureMode.Layer else GestureMode.Undecided
                            // Track the unsnapped placement, so a layer can be pulled back out of a snap.
                            var raw = hit?.t
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed == 0) break
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val rotation = event.calculateRotation()
                                if (pan == Offset.Zero && zoom == 1f && rotation == 0f) continue
                                if (mode == GestureMode.Undecided) {
                                    mode = when {
                                        pressed > 1 || hit == null -> GestureMode.View
                                        else -> GestureMode.Layer.also { vm.select(hit.id) }
                                    }
                                }
                                when (mode) {
                                    GestureMode.Layer -> raw?.let { r ->
                                        val next = r.copy(x = r.x + pan.x / scale, y = r.y + pan.y / scale, scale = r.scale * zoom, rotation = r.rotation + rotation)
                                        raw = next
                                        vm.setSelectedTransform(snaps.apply(next, cx, cy, snapDistance / scale), first = !moved)
                                    }
                                    // Positions here are in the zoomed canvas's own pixels; the view moves in screen pixels.
                                    else -> {
                                        val half = Offset(boxPx.width / 2, boxPx.height / 2)
                                        val focus = (event.calculateCentroid() - half) * viewZoom + viewPan
                                        zoomView(zoom, focus, pan * viewZoom)
                                    }
                                }
                                moved = true
                                event.changes.forEach { it.consume() }
                            }
                            snaps.clear()
                            if (!moved) {
                                // A tap selects what's under it (or deselects on empty canvas); a second quick tap on text opens the editor.
                                vm.select(hit?.id)
                                val isDoubleTap = hit != null && lastTap[0] == hit.id && down.uptimeMillis - lastTap[1] < 350
                                if (isDoubleTap && hit is TextLayer) editText(hit)
                                lastTap[0] = hit?.id ?: -1
                                lastTap[1] = down.uptimeMillis
                            }
                        }
                    },
                ) {
                    drawIntoCanvas { canvas ->
                        val c = canvas.nativeCanvas
                        c.save()
                        c.scale(scale, scale)
                        shown.forEach { c.drawLayer(it) }
                        selected?.let { c.drawSelection(it, scale) }
                        if (snaps.x) c.drawLine(cx, 0f, cx, project.height.toFloat(), guidePaint.also { it.strokeWidth = 2 / scale })
                        if (snaps.y) c.drawLine(0f, cy, project.width.toFloat(), cy, guidePaint.also { it.strokeWidth = 2 / scale })
                        c.restore()
                    }
                }
            }
            selected?.takeUnless { it.locked || it.hidden }?.let { l ->
                HandleDot(R.drawable.ic_close, cornerOf(l, -1f, scale), Color(0xFFFF3B5C))
                HandleDot(R.drawable.ic_open_in_full, cornerOf(l, 1f, scale), Ink.accent)
            }
        }
        if (viewZoom > 1.01f) {
            Text(
                "Fit",
                color = Ink.onAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.accent)
                    .clickable { zoomView(0f, Offset.Zero, Offset.Zero) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

private enum class GestureMode { Undecided, Layer, View }

private const val MAX_VIEW_ZOOM = 6f

/** Screen position of the layer's top-left ([side] = -1) or bottom-right (+1) corner. */
private fun cornerOf(layer: Layer, side: Float, scale: Float): Offset {
    val t = layer.t.shiftedLocal(side * layer.width / 2, side * layer.height / 2)
    return Offset(t.x * scale, t.y * scale)
}

private fun handleAt(layer: Layer, point: Offset, scale: Float, radius: Float): Handle? = when {
    (point - cornerOf(layer, -1f, scale)).getDistance() < radius -> Handle.Delete
    (point - cornerOf(layer, 1f, scale)).getDistance() < radius -> Handle.Scale
    else -> null
}

@Composable
private fun HandleDot(icon: Int, at: Offset, background: Color) {
    val half = with(LocalDensity.current) { 14.dp.roundToPx() }
    Box(
        Modifier
            .offset { IntOffset(at.x.roundToInt() - half, at.y.roundToInt() - half) }
            .size(28.dp)
            .clip(CircleShape)
            .background(background)
            // Visible on both light and dark photos.
            .border(1.5.dp, Color.Black.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(icon, Modifier.size(16.dp), if (background == Ink.accent) Ink.onAccent else Color.White)
    }
}

@Composable
private fun Checkerboard() {
    Canvas(Modifier.fillMaxSize()) {
        val cell = 12.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(Color(0xFF4A4A4A), Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

private val selectionShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = 0x99000000.toInt()
}
private val selectionLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = 0xFFC6F432.toInt()
}

private fun android.graphics.Canvas.drawSelection(layer: Layer, viewScale: Float) {
    save()
    translate(layer.t.x, layer.t.y)
    rotate(layer.t.rotation)
    scale(layer.t.scale, layer.t.scale)
    val px = 1f / (viewScale * layer.t.scale)
    selectionShadow.strokeWidth = 5 * px
    selectionLine.strokeWidth = 2 * px
    drawRect(-layer.width / 2, -layer.height / 2, layer.width / 2, layer.height / 2, selectionShadow)
    drawRect(-layer.width / 2, -layer.height / 2, layer.width / 2, layer.height / 2, selectionLine)
    restore()
}

/**
 * CompositionPlayer sizes its output once and doesn't follow later surface resizes (panels, keyboard), which left
 * the frame small or cropped in a corner. So the video renders into a fixed-size buffer and a TextureView scales it
 * to the stage like any other view. The buffer has the canvas's aspect ratio, so the scaling never distorts.
 */
@Composable
private fun VideoSurface(player: CompositionPlayer, canvasWidth: Int, canvasHeight: Int, modifier: Modifier) {
    val f = PREVIEW_LONG_SIDE / maxOf(canvasWidth, canvasHeight).toFloat()
    val bw = (canvasWidth * f).roundToInt()
    val bh = (canvasHeight * f).roundToInt()
    // The surface the player draws into right now. On a canvas change the new view attaches before the old one
    // is torn down, and clearing a surface the player isn't using throws.
    val attached = remember { arrayOfNulls<Surface>(1) }
    // A new canvas shape needs a new buffer, so a new view.
    key(bw, bh) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null

                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            texture.setDefaultBufferSize(bw, bh)
                            surface = Surface(texture).also {
                                player.setVideoSurface(it, androidx.media3.common.util.Size(bw, bh))
                                attached[0] = it
                            }
                            // A paused player only draws on a seek.
                            if (player.playbackState != Player.STATE_IDLE && !player.isPlaying) player.seekTo(player.currentPosition)
                        }

                        // TextureView resets the buffer to the view size on every resize; keep it fixed.
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = texture.setDefaultBufferSize(bw, bh)

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            if (attached[0] === surface) {
                                // CompositionPlayer only accepts clearing the exact surface it holds.
                                player.clearVideoSurface(surface)
                                attached[0] = null
                            }
                            surface?.release()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = modifier,
        )
    }
}

private const val PREVIEW_LONG_SIDE = 1280f

@Composable
private fun rememberCompositionPlayer(): CompositionPlayer {
    val context = LocalContext.current
    val player = remember { CompositionPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL } }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * Trim mode: the whole source as a strip of frames, with handles. Positions are in source time; the player works in
 * output time (after trim and speed), so seeking converts between the two.
 */
@Composable
private fun TrimStrip(clip: VideoClip, frames: List<Bitmap>, positionMs: Long, onSeek: (Long) -> Unit, onTrim: (Long, Long) -> Unit, onDone: () -> Unit) {
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
private fun Timeline(
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

/** Full-screen typing: the text appears big, in its own font and color. */
@Composable
private fun TextEntry(initial: String, font: Typeface, color: Int, onDone: (String, Int) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var fill by remember { mutableIntStateOf(color) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(Ink.bg.copy(alpha = 0.94f)).blockTouches().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(R.drawable.ic_close, "Cancel", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            CircleButton(R.drawable.ic_check, "Done", enabled = value.text.isNotBlank()) { onDone(value.text, fill) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = TextStyle(color = Color(fill), fontSize = 36.sp, fontFamily = FontFamily(font), textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Ink.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.text.isEmpty()) Text("Enter your text", color = Ink.dim, fontSize = 36.sp, fontFamily = FontFamily(font), textAlign = TextAlign.Center)
                        field()
                    }
                },
            )
        }
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            ColorRow(fill, allowNone = false) { c -> c?.let { fill = it } }
        }
    }
}

private fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit) =
    clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClick)

fun formatMs(ms: Long): String {
    val tenths = ms / 100
    return "%d:%02d.%d".format(tenths / 600, tenths / 10 % 60, tenths % 10)
}
