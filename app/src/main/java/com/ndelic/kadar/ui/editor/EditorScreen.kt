@file:OptIn(UnstableApi::class, ExperimentalApi::class)

package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.ndelic.kadar.EditorUiState
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.Sticker
import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.Anim
import com.ndelic.kadar.model.CropRect
import com.ndelic.kadar.model.DrawLayer
import com.ndelic.kadar.model.Font
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.Loop
import com.ndelic.kadar.model.Project
import com.ndelic.kadar.model.ShapeKind
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.model.Transform
import com.ndelic.kadar.model.UiText
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.model.defaultTextSize
import com.ndelic.kadar.model.drawLayer
import com.ndelic.kadar.model.motionAt
import com.ndelic.kadar.ui.common.CircleButton
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.IconTool
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.LocalEyedropper
import com.ndelic.kadar.ui.common.blockTouches
import com.ndelic.kadar.ui.common.fullWidthPanel
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.composition
import com.ndelic.kadar.video.drawnGroups
import com.ndelic.kadar.video.outgoingOffset
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Panel { Sticker, Canvas, VideoCanvas, Speed, Audio, Presets, Font, Color, Stroke, Shadow, Glow, Box, Style, Adjust, Filters, Layers, Overlay, Animate, Opacity, Shape, ShapeFill, ShapeOutline, Transition }

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
    // Bumped to swap in a fresh preview player after it failed, resuming at [resumeAt].
    var playerGeneration by remember { mutableIntStateOf(0) }
    val resumeAt = remember { longArrayOf(0) }
    val player = if (video != null) rememberCompositionPlayer(playerGeneration) else null
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    val overlayVideo = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let { uri -> vm.addVideo(uri, positionMs) } }
    val newClip = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::addClip) }
    // The clip being trimmed, by index.
    var trimming by rememberSaveable { mutableStateOf<Int?>(null) }
    // The clip whose transition in the Transition panel shows, by index.
    var transitionAt by rememberSaveable { mutableStateOf<Int?>(null) }
    // The clip the clip tools act on: the one picked on the strip, else the one under the playhead.
    val picked = state.selectedClip?.takeIf { video != null && it in video.segments.indices }
    val clipIndex = picked ?: video?.segmentAt(positionMs)?.first ?: 0
    // With several clips a clip tool picks the clip it acts on, so the strip shows which one that is.
    fun pickClip() {
        if (video != null && video.segments.size > 1 && picked == null) vm.selectClip(clipIndex)
    }
    // Filters and Adjust share one Adjust per clip, so Apply to all copies the look and the sliders together.
    fun applyAdjustToAll(v: VideoClip): (() -> Unit)? =
        if (v.segments.size > 1) v.segments[clipIndex].adjust.let { a -> { vm.updateAllSegments { copy(adjust = a) } } } else null
    var erasing by remember { mutableStateOf<Pair<ImageLayer, Bitmap?>?>(null) }
    var cropping by remember { mutableStateOf<ImageLayer?>(null) }
    var croppingVideo by remember { mutableStateOf<Pair<VideoLayer, Bitmap>?>(null) }
    // Open draw screen: the drawing to add to (null for a new one) and the video frame under the playhead.
    var drawing by remember { mutableStateOf<Pair<Long?, Bitmap?>?>(null) }
    val scope = rememberCoroutineScope()
    // The eyedropper while it's on: the stage picture it picks from and where the color goes.
    var dropper by remember { mutableStateOf<Pair<Bitmap, (Int) -> Unit>?>(null) }
    LaunchedEffect(panel, state.selectedId) { dropper = null }

    LaunchedEffect(state.selectedId) {
        // Selecting from the Layers list keeps the list open; anything else starts from the layer's tools.
        if (panel != Panel.Layers) panel = null
        if (state.selectedId != null) trimming = null
    }
    BackHandler {
        when {
            dropper != null -> dropper = null
            panel != null -> panel = null
            selected != null -> vm.select(null)
            else -> vm.close()
        }
    }
    // Long-lived gesture handlers (timeline, trim strip) must seek the current player, not one swapped out since.
    val currentPlayer by rememberUpdatedState(player)
    // When the preview last failed and was rebuilt, to give up instead of rebuilding in a loop.
    val lastFailure = remember { longArrayOf(-RETRY_WINDOW_MS) }
    if (player != null && video != null) {
        // The preview's composition is the clip alone: VideoLayerPreview draws each video layer with its own player and
        // BlurBackgroundPreview the blurred canvas, so the main player stays single-input. Media3 1.11's multi-input
        // preview fails mid-playback ("frame for an ended input") on devices; the export still composites everything.
        LaunchedEffect(player, video) {
            // After an edit (a split, a trim) the playhead stays where it was.
            val start = resumeAt[0].also { resumeAt[0] = 0 }.takeIf { it > 0 } ?: positionMs.coerceAtMost(video.outputDurationMs)
            player.setComposition(video.copy(blur = false).composition(incoming = false, dropFrames = false), start)
            player.prepare()
            // A paused player keeps showing the old frame after a new composition; seek once it's loaded to repaint.
            delay(300)
            if (!player.isPlaying) player.seekTo(maxOf(start, player.currentPosition))
        }
        DisposableEffect(player) {
            // A failed player stays idle whatever play is pressed; rebuild it once, and stop if the new one fails too.
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastFailure[0] < RETRY_WINDOW_MS) {
                        playing = false
                        vm.notify(UiText.Res(R.string.preview_failed))
                        return
                    }
                    lastFailure[0] = now
                    resumeAt[0] = player.currentPosition
                    playerGeneration++
                    vm.notify(UiText.Res(R.string.preview_restarted))
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player, playing) { player.playWhenReady = playing }
        LaunchedEffect(player) {
            while (true) {
                positionMs = player.currentPosition
                delay(50)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(R.drawable.ic_close, stringResource(R.string.back_to_projects), onClick = vm::close)
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(22.dp)).background(Ink.raised)) {
                PillButton(R.drawable.ic_undo, stringResource(R.string.undo), state.canUndo, vm::undo)
                PillButton(R.drawable.ic_redo, stringResource(R.string.redo), state.canRedo, vm::redo)
            }
            Spacer(Modifier.weight(1f))
            // Photos only: video shows the stack as timeline tracks.
            if (video == null) {
                CircleButton(R.drawable.ic_layers, stringResource(R.string.layers), enabled = project.layers.isNotEmpty(), active = panel == Panel.Layers) {
                    panel = if (panel == Panel.Layers) null else Panel.Layers
                }
                Spacer(Modifier.width(10.dp))
            }
            CircleButton(R.drawable.ic_download, stringResource(R.string.save_to_gallery)) { vm.export(share = false) }
            Spacer(Modifier.width(10.dp))
            CircleButton(R.drawable.ic_share, stringResource(R.string.share)) { vm.export(share = true) }
        }

        Stage(
            project, selected, player, if (video != null) positionMs else null, state.filmstrip, vm, onEditText = { typing = it.text },
            picking = dropper?.first,
            onPicked = { c ->
                dropper?.second?.invoke(c)
                dropper = null
            },
            Modifier.weight(1f).fillMaxWidth(),
        )

        if (video != null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(
                    "${formatMs(positionMs)} / ${formatMs(video.outputDurationMs)}",
                    color = Ink.dim,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                )
                Box(Modifier.size(48.dp).clip(CircleShape).background(Ink.raised).clickableNoRipple {
                    // A preview that gave up after failing gets a fresh player on the next press.
                    if (!playing && player?.playerError != null) {
                        resumeAt[0] = player.currentPosition
                        playerGeneration++
                    }
                    playing = !playing
                }, contentAlignment = Alignment.Center) {
                    KIcon(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow, Modifier.size(30.dp), description = stringResource(if (playing) R.string.pause else R.string.play))
                }
            }
        }

        val eyedropper: ((Int) -> Unit) -> Unit = { onColor ->
            playing = false
            scope.launch { vm.stagePicture(positionMs)?.let { dropper = it to onColor } }
        }
        CompositionLocalProvider(LocalEyedropper provides eyedropper) {
            AnimatedContent(
                targetState = panel,
                transitionSpec = { (fadeIn(tween(160)) togetherWith fadeOut(tween(120))).using(SizeTransform(clip = true)) },
                label = "panel",
            ) { p ->
                if (p != null) Box(Modifier.fullWidthPanel()) {
                    when (p) {
                        Panel.Sticker -> StickerPanel(state.stickers, vm)
                        Panel.Canvas -> CanvasPanel(project, state.settings.exportSize, vm)
                        Panel.Speed -> video?.let { SpeedPanel(it, clipIndex, vm) }
                        Panel.Audio -> video?.let { v ->
                            AudioPanel(v, clipIndex, vm, onAddMusic = { music.launch(arrayOf("audio/*")) }) {
                                VoiceoverSection(v, state.recordingFromMs, positionMs, playing, player, vm, onPlay = { playing = it })
                            }
                        }
                        Panel.Presets -> if (selected is TextLayer) TextPresetsPanel(state.fonts, vm)
                        Panel.Font -> (selected as? TextLayer)?.let { FontPanel(it, state.fonts, defaultTextSize(project.width, project.height), vm) }
                        Panel.Glow -> (selected as? TextLayer)?.let { GlowPanel(it, vm) }
                        Panel.Color -> (selected as? TextLayer)?.let { t -> ColorRow(t.color, allowNone = false) { c -> c?.let { vm.editText { copy(color = it) } } } }
                        Panel.Stroke -> (selected as? TextLayer)?.let { OutlinePanel(it, vm) }
                        Panel.Shadow -> (selected as? TextLayer)?.let { ShadowPanel(it, vm) }
                        Panel.Box -> (selected as? TextLayer)?.let { BubblePanel(it, vm) }
                        Panel.Shape -> ShapePanel(selected as? ShapeLayer, vm)
                        Panel.ShapeFill -> (selected as? ShapeLayer)?.let { ShapeFillPanel(it, vm) }
                        Panel.ShapeOutline -> (selected as? ShapeLayer)?.let { ShapeOutlinePanel(it, vm) }
                        Panel.Layers -> LayersPanel(project.layers, state.selectedId, vm)
                        Panel.VideoCanvas -> video?.let { VideoCanvasPanel(project, it, state.settings.exportSize, vm) }
                        Panel.Style -> (selected as? TextLayer)?.let { TextStylePanel(it, vm) }
                        Panel.Overlay -> (selected as? VideoLayer)?.let { VideoLayerPanel(it, vm) }
                        Panel.Animate -> selected?.let { AnimationPanel(it, vm) }
                        Panel.Opacity -> (selected as? ImageLayer)?.let { l -> KSlider(R.drawable.ic_opacity, l.opacity, 0.05f..1f, ::percent) { v -> vm.editImage { copy(opacity = v) } } }
                        Panel.Transition -> video?.let { v -> transitionAt?.takeIf { it in 1 until v.segments.size }?.let { TransitionPanel(v, it, vm) } }
                        Panel.Adjust -> when (val l = selected) {
                            is ImageLayer -> AdjustPanel(l.adjust) { a -> vm.editImage { copy(adjust = a) } }
                            null -> video?.let { v -> AdjustPanel(v.segments[clipIndex].adjust, applyAdjustToAll(v)) { a -> vm.updateSegment(clipIndex) { copy(adjust = a) } } }
                            else -> {}
                        }
                        Panel.Filters -> when (val l = selected) {
                            is ImageLayer -> FiltersPanel(l.bitmap, l.adjust) { a -> vm.editImage { copy(adjust = a) } }
                            null -> video?.let { v -> v.segments[clipIndex].let { s -> FiltersPanel(state.filmstrip[s.uri]?.firstOrNull(), s.adjust, applyAdjustToAll(v)) { a -> vm.updateSegment(clipIndex) { copy(adjust = a) } } } }
                            else -> {}
                        }
                    }
                }
            }
        }

        val toolset = when (selected) {
            null -> if (picked != null) 6 else 0
            is TextLayer -> 1
            is ImageLayer -> 2
            is VideoLayer -> 3
            is DrawLayer -> 4
            is ShapeLayer -> 5
        }
        AnimatedContent(targetState = toolset, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "tools") { tools ->
            Row(
                Modifier.fillMaxWidth().background(Ink.panel).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fun toggle(p: Panel) {
                    panel = if (panel == p) null else p
                }
                fun toggleClip(p: Panel) {
                    pickClip()
                    toggle(p)
                }
                fun trim() {
                    pickClip()
                    trimming = if (trimming != null) null else clipIndex
                }
                fun draw() {
                    playing = false
                    val target = (state.selected as? DrawLayer)?.id
                    scope.launch { drawing = target to vm.frameAt(positionMs) }
                }
                // A selected layer's place on the timeline: cut it, or move its start to the playhead.
                @Composable
                fun timeTools() {
                    if (video == null) return
                    IconTool(R.drawable.ic_split_scene, stringResource(R.string.tool_split)) { vm.splitSelected(positionMs) }
                    IconTool(R.drawable.ic_start, stringResource(R.string.tool_move_here)) { vm.moveSelectedTo(positionMs) }
                }
                @Composable
                fun flipTools() {
                    IconTool(R.drawable.ic_flip, stringResource(R.string.tool_mirror)) { vm.flipSelected(horizontal = true) }
                    IconTool(R.drawable.ic_flip_vertical, stringResource(R.string.tool_flip)) { vm.flipSelected(horizontal = false) }
                }
                when (tools) {
                    0 -> {
                        IconTool(R.drawable.ic_title, stringResource(R.string.tool_text)) { typing = "" }
                        IconTool(R.drawable.ic_sentiment_satisfied, stringResource(R.string.sticker), panel == Panel.Sticker) { toggle(Panel.Sticker) }
                        IconTool(R.drawable.ic_draw, stringResource(R.string.tool_draw), onClick = ::draw)
                        IconTool(R.drawable.ic_shapes, stringResource(R.string.tool_shape), panel == Panel.Shape) { toggle(Panel.Shape) }
                        IconTool(R.drawable.ic_image, stringResource(R.string.image)) { image.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                        IconTool(R.drawable.ic_language, stringResource(R.string.tool_web), onClick = onBrowse)
                        if (video != null) {
                            IconTool(R.drawable.ic_movie, stringResource(R.string.video)) { overlayVideo.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly)) }
                            IconTool(R.drawable.ic_content_cut, stringResource(R.string.tool_trim), trimming != null, onClick = ::trim)
                            IconTool(R.drawable.ic_split_scene, stringResource(R.string.tool_split)) { vm.splitClip(positionMs) }
                            IconTool(R.drawable.ic_speed, stringResource(R.string.tool_speed), panel == Panel.Speed) { toggleClip(Panel.Speed) }
                            IconTool(R.drawable.ic_aspect_ratio, stringResource(R.string.tool_canvas), panel == Panel.VideoCanvas) { toggle(Panel.VideoCanvas) }
                            IconTool(R.drawable.ic_tune, stringResource(R.string.tool_adjust), panel == Panel.Adjust) { toggleClip(Panel.Adjust) }
                            IconTool(R.drawable.ic_filter_vintage, stringResource(R.string.tool_filters), panel == Panel.Filters) { toggleClip(Panel.Filters) }
                            IconTool(R.drawable.ic_music_note, stringResource(R.string.tool_audio), panel == Panel.Audio) { toggleClip(Panel.Audio) }
                        } else {
                            IconTool(R.drawable.ic_aspect_ratio, stringResource(R.string.tool_canvas), panel == Panel.Canvas) { toggle(Panel.Canvas) }
                        }
                    }
                    1 -> {
                        IconTool(R.drawable.ic_check, stringResource(R.string.done)) { vm.select(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_edit, stringResource(R.string.edit)) { (state.selected as? TextLayer)?.let { typing = it.text } }
                        IconTool(R.drawable.ic_style, stringResource(R.string.tool_presets), panel == Panel.Presets) { toggle(Panel.Presets) }
                        IconTool(R.drawable.ic_match_case, stringResource(R.string.tool_font), panel == Panel.Font) { toggle(Panel.Font) }
                        IconTool(R.drawable.ic_palette, stringResource(R.string.tool_color), panel == Panel.Color) { toggle(Panel.Color) }
                        IconTool(R.drawable.ic_border_color, stringResource(R.string.tool_stroke), panel == Panel.Stroke) { toggle(Panel.Stroke) }
                        IconTool(R.drawable.ic_shadow, stringResource(R.string.shadow), panel == Panel.Shadow) { toggle(Panel.Shadow) }
                        IconTool(R.drawable.ic_flare, stringResource(R.string.tool_glow), panel == Panel.Glow) { toggle(Panel.Glow) }
                        IconTool(R.drawable.ic_chat_bubble, stringResource(R.string.tool_box), panel == Panel.Box) { toggle(Panel.Box) }
                        IconTool(R.drawable.ic_text_fields, stringResource(R.string.tool_style), panel == Panel.Style) { toggle(Panel.Style) }
                        if (video != null) IconTool(R.drawable.ic_animation, stringResource(R.string.tool_animate), panel == Panel.Animate) { toggle(Panel.Animate) }
                        timeTools()
                        IconTool(R.drawable.ic_content_copy, stringResource(R.string.duplicate), onClick = vm::duplicateSelected)
                        IconTool(R.drawable.ic_flip_to_front, stringResource(R.string.tool_front), onClick = vm::bringToFront)
                        IconTool(R.drawable.ic_flip_to_back, stringResource(R.string.tool_back), onClick = vm::sendToBack)
                        IconTool(R.drawable.ic_delete, stringResource(R.string.delete), onClick = vm::deleteSelected)
                    }
                    4 -> {
                        IconTool(R.drawable.ic_check, stringResource(R.string.done)) { vm.select(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_draw, stringResource(R.string.tool_draw), onClick = ::draw)
                        flipTools()
                        timeTools()
                        IconTool(R.drawable.ic_content_copy, stringResource(R.string.duplicate), onClick = vm::duplicateSelected)
                        IconTool(R.drawable.ic_flip_to_front, stringResource(R.string.tool_front), onClick = vm::bringToFront)
                        IconTool(R.drawable.ic_flip_to_back, stringResource(R.string.tool_back), onClick = vm::sendToBack)
                        IconTool(R.drawable.ic_delete, stringResource(R.string.delete), onClick = vm::deleteSelected)
                    }
                    5 -> {
                        IconTool(R.drawable.ic_check, stringResource(R.string.done)) { vm.select(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_shapes, stringResource(R.string.tool_shape), panel == Panel.Shape) { toggle(Panel.Shape) }
                        if ((state.selected as? ShapeLayer)?.kind != ShapeKind.Line) {
                            IconTool(R.drawable.ic_format_color_fill, stringResource(R.string.tool_fill), panel == Panel.ShapeFill) { toggle(Panel.ShapeFill) }
                        }
                        IconTool(R.drawable.ic_border_color, stringResource(R.string.tool_outline), panel == Panel.ShapeOutline) { toggle(Panel.ShapeOutline) }
                        if (video != null) IconTool(R.drawable.ic_animation, stringResource(R.string.tool_animate), panel == Panel.Animate) { toggle(Panel.Animate) }
                        timeTools()
                        IconTool(R.drawable.ic_content_copy, stringResource(R.string.duplicate), onClick = vm::duplicateSelected)
                        IconTool(R.drawable.ic_flip_to_front, stringResource(R.string.tool_front), onClick = vm::bringToFront)
                        IconTool(R.drawable.ic_flip_to_back, stringResource(R.string.tool_back), onClick = vm::sendToBack)
                        IconTool(R.drawable.ic_delete, stringResource(R.string.delete), onClick = vm::deleteSelected)
                    }
                    6 -> {
                        IconTool(R.drawable.ic_check, stringResource(R.string.done)) { vm.selectClip(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_content_cut, stringResource(R.string.tool_trim), trimming != null, onClick = ::trim)
                        IconTool(R.drawable.ic_split_scene, stringResource(R.string.tool_split)) { vm.splitClip(positionMs) }
                        IconTool(R.drawable.ic_speed, stringResource(R.string.tool_speed), panel == Panel.Speed) { toggle(Panel.Speed) }
                        IconTool(R.drawable.ic_music_note, stringResource(R.string.tool_audio), panel == Panel.Audio) { toggle(Panel.Audio) }
                        IconTool(R.drawable.ic_tune, stringResource(R.string.tool_adjust), panel == Panel.Adjust) { toggle(Panel.Adjust) }
                        IconTool(R.drawable.ic_filter_vintage, stringResource(R.string.tool_filters), panel == Panel.Filters) { toggle(Panel.Filters) }
                        IconTool(R.drawable.ic_content_copy, stringResource(R.string.duplicate)) { vm.duplicateClip(clipIndex) }
                        if (video != null && video.segments.size > 1) IconTool(R.drawable.ic_delete, stringResource(R.string.delete)) { vm.deleteClip(clipIndex) }
                    }
                    3 -> {
                        IconTool(R.drawable.ic_check, stringResource(R.string.done)) { vm.select(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_tune, stringResource(R.string.edit), panel == Panel.Overlay) { toggle(Panel.Overlay) }
                        IconTool(R.drawable.ic_crop, stringResource(R.string.tool_crop)) {
                            playing = false
                            (state.selected as? VideoLayer)?.let { l -> scope.launch { vm.layerFrame(l, positionMs)?.let { croppingVideo = l to it } } }
                        }
                        timeTools()
                        IconTool(R.drawable.ic_content_copy, stringResource(R.string.duplicate), onClick = vm::duplicateSelected)
                        IconTool(R.drawable.ic_flip_to_front, stringResource(R.string.tool_front), onClick = vm::bringToFront)
                        IconTool(R.drawable.ic_flip_to_back, stringResource(R.string.tool_back), onClick = vm::sendToBack)
                        IconTool(R.drawable.ic_delete, stringResource(R.string.delete), onClick = vm::deleteSelected)
                    }
                    else -> {
                        IconTool(R.drawable.ic_check, stringResource(R.string.done)) { vm.select(null) }
                        ToolDivider()
                        IconTool(R.drawable.ic_background_replace, stringResource(R.string.tool_remove_bg), onClick = vm::removeBackground)
                        IconTool(R.drawable.ic_ink_eraser, stringResource(R.string.tool_eraser)) {
                            (state.selected as? ImageLayer)?.let { l -> scope.launch { erasing = l to vm.loadSource(l) } }
                        }
                        IconTool(R.drawable.ic_crop, stringResource(R.string.tool_crop)) { cropping = state.selected as? ImageLayer }
                        flipTools()
                        IconTool(R.drawable.ic_opacity, stringResource(R.string.tool_opacity), panel == Panel.Opacity) { toggle(Panel.Opacity) }
                        IconTool(R.drawable.ic_tune, stringResource(R.string.tool_adjust), panel == Panel.Adjust) { toggle(Panel.Adjust) }
                        IconTool(R.drawable.ic_filter_vintage, stringResource(R.string.tool_filters), panel == Panel.Filters) { toggle(Panel.Filters) }
                        if (video != null) IconTool(R.drawable.ic_animation, stringResource(R.string.tool_animate), panel == Panel.Animate) { toggle(Panel.Animate) }
                        timeTools()
                        IconTool(R.drawable.ic_library_add, stringResource(R.string.tool_save_sticker), onClick = vm::saveSticker)
                        IconTool(R.drawable.ic_content_copy, stringResource(R.string.duplicate), onClick = vm::duplicateSelected)
                        IconTool(R.drawable.ic_flip_to_front, stringResource(R.string.tool_front), onClick = vm::bringToFront)
                        IconTool(R.drawable.ic_flip_to_back, stringResource(R.string.tool_back), onClick = vm::sendToBack)
                        IconTool(R.drawable.ic_delete, stringResource(R.string.delete), onClick = vm::deleteSelected)
                    }
                }
            }
        }

        if (video != null && player != null) {
            val trimmed = trimming?.let { i -> video.segments.getOrNull(i)?.let { i to it } }
            if (trimmed != null) {
                val (i, segment) = trimmed
                TrimStrip(
                    clip = segment,
                    offsetMs = video.segmentStart(i),
                    frames = state.filmstrip[segment.uri].orEmpty(),
                    positionMs = positionMs,
                    onSeek = { currentPlayer?.seekTo(it) },
                    onTrim = { start, end -> vm.updateSegment(i) { copy(startMs = start, endMs = end) } },
                    onDone = { trimming = null },
                )
            } else {
                Timeline(
                    clip = video,
                    layers = project.layers,
                    selectedId = state.selectedId,
                    pickedClip = picked,
                    frames = state.filmstrip,
                    positionMs = positionMs,
                    onSeek = { currentPlayer?.seekTo(it) },
                    onSelect = vm::select,
                    onLayerTime = vm::setLayerTime,
                    onFlags = vm::setFlags,
                    onMove = vm::moveLayer,
                    // A second tap on the picked clip trims it, as one tap did when there was only one clip.
                    onClipTap = { i -> if (i == picked) trimming = i else vm.selectClip(i) },
                    onClipMove = vm::moveClip,
                    onClipTrim = { i, start, end -> vm.updateSegment(i) { copy(startMs = start, endMs = end) } },
                    onAddClip = { newClip.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)) },
                    transitionAt = transitionAt.takeIf { panel == Panel.Transition },
                    onTransition = { i ->
                        val open = panel == Panel.Transition && transitionAt == i
                        transitionAt = i
                        panel = if (open) null else Panel.Transition
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
            bitmap = layer.bitmap,
            onDone = { r ->
                cropping = null
                vm.applyCrop(layer.id, r[0], r[1], r[2], r[3])
            },
            onCancel = { cropping = null },
            flipX = layer.t.flipX,
            flipY = layer.t.flipY,
        )
    }
    croppingVideo?.let { (layer, frame) ->
        val fw = frame.width.toFloat()
        val fh = frame.height.toFloat()
        CropScreen(
            bitmap = frame,
            initial = layer.crop.run { Rect(left * fw, top * fh, right * fw, bottom * fh) },
            onDone = { r ->
                croppingVideo = null
                vm.cropVideo(layer.id, CropRect(r[0] / fw, r[1] / fh, r[2] / fw, r[3] / fh))
            },
            onCancel = { croppingVideo = null },
        )
    }
    drawing?.let { (target, under) ->
        DrawScreen(
            project = project,
            layers = project.layers.filter { !it.hidden && (video == null || it.visibleAt(positionMs) || it.id == target) },
            under = under,
            onDone = { strokes ->
                drawing = null
                vm.applyDrawing(target, strokes)
            },
            onCancel = { drawing = null },
        )
    }

    typing?.let { initial ->
        val layer = selected as? TextLayer
        TextEntry(
            initial = initial,
            font = layer?.font?.typeface ?: state.fonts.first().typeface,
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

private enum class Handle { Delete, Scale, Tail, WrapLeft, WrapRight }

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
 * double-tap a text layer to edit it. With [picking] (the stage picture) a touch on the canvas picks a color from it for
 * [onPicked] instead.
 */
@Composable
private fun Stage(
    project: Project,
    selected: Layer?,
    player: CompositionPlayer?,
    timeMs: Long?,
    stills: Map<Uri, List<Bitmap>>,
    vm: EditorViewModel,
    onEditText: (TextLayer) -> Unit,
    picking: Bitmap?,
    onPicked: (Int) -> Unit,
    modifier: Modifier,
) {
    // In a video only the layers on screen at the playhead show (and can be grabbed); the selected one always does, to edit it.
    fun Layer.onStage() = !hidden && (timeMs == null || visibleAt(timeMs) || id == selected?.id)
    val shown = project.layers.filter { it.onStage() }
    // Every video layer covers the layers under it in the list, like in the export: those are drawn on canvases of their
    // own under its view. Its view stays in place while it is off screen at the playhead, so its player is kept.
    val stack = project.layers.filter { it.onStage() || it is VideoLayer && !it.hidden }
    val groups = drawnGroups(stack) { it is VideoLayer }
    val videos = stack.filterIsInstance<VideoLayer>()
    BoxWithConstraints(modifier.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        val scale = min(constraints.maxWidth / project.width.toFloat(), constraints.maxHeight / project.height.toFloat())
        val density = LocalDensity.current
        val size = with(density) { DpSize((project.width * scale).toDp(), (project.height * scale).toDp()) }
        val handleRadius = with(density) { 24.dp.toPx() }
        val layers by rememberUpdatedState(shown)
        // Entrances and exits follow the player on every frame while it plays; [timeMs] only updates every 50 ms.
        val videoEnd = project.video?.outputDurationMs
        val animated = player != null && videoEnd != null && project.layers.any { it.animIn != Anim.None || it.animOut != Anim.None || it.animLoop != Loop.None }
        val clock = remember { mutableLongStateOf(0L) }
        // So do the overlapping transitions the stage draws over the main preview.
        val overlapping = project.video?.transitionSpans.orEmpty().filter { it.kind.overlaps }
        if ((animated || overlapping.isNotEmpty()) && player != null) {
            LaunchedEffect(player) {
                while (true) {
                    clock.longValue = player.currentPosition
                    if (player.isPlaying) withFrameMillis {} else delay(50)
                }
            }
        }
        fun DrawScope.drawLayers(group: List<Layer>, after: (android.graphics.Canvas) -> Unit = {}) = drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            c.save()
            c.scale(scale, scale)
            group.forEach { if (animated && videoEnd != null) c.drawLayer(it, it.motionAt(clock.longValue, minOf(it.endMs, videoEnd)), project.height) else c.drawLayer(it) }
            after(c)
            c.restore()
        }
        val current by rememberUpdatedState(selected)
        val editText by rememberUpdatedState(onEditText)
        val picture by rememberUpdatedState(picking)
        val picked by rememberUpdatedState(onPicked)
        // The eyedropper's finger and the color under it, while it touches.
        var loupe by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
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
            Modifier.matchParentSize().pointerInput(scale, project.width, project.height) {
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
                project.video?.takeIf { it.blurred }?.let { BlurBackgroundPreview(it, stills, project.width, project.height, scale, player) }
                player?.let { p ->
                    VideoSurface(
                        p,
                        project.width,
                        project.height,
                        Modifier.fillMaxSize().graphicsLayer {
                            // Pushed out by the incoming clip, like the export's outgoingSettings.
                            val push = overlapping.firstOrNull { it.kind.pushes && clock.longValue in it } ?: return@graphicsLayer
                            val (x, y) = push.kind.outgoingOffset(push.progress(clock.longValue))
                            translationX = x * this.size.width
                            translationY = y * this.size.height
                        },
                    )
                }
                project.video?.takeIf { overlapping.isNotEmpty() }?.let { IncomingClipPreview(it, stills, project.width, project.height, scale, player, clock) }
                // Over the main video and under the top canvas (handles and the layers above every video layer), bottom
                // layer first, each over the layers drawn under it, like the export.
                if (videoEnd != null) {
                    videos.forEachIndexed { i, l ->
                        key(l.id) {
                            if (groups[i].isNotEmpty()) Canvas(Modifier.fillMaxSize()) { drawLayers(groups[i]) }
                            VideoLayerPreview(l, videoEnd, scale, player)
                        }
                    }
                }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(scale, project.width, project.height) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            // Gestures that start on the canvas are the canvas's, not the surrounding stage's.
                            down.consume()
                            picture?.let { pic ->
                                fun colorAt(at: Offset) = pic.getPixel(
                                    (at.x / scale * pic.width / project.width).toInt().coerceIn(0, pic.width - 1),
                                    (at.y / scale * pic.height / project.height).toInt().coerceIn(0, pic.height - 1),
                                ) or 0xFF000000.toInt()
                                var at = down.position
                                while (true) {
                                    loupe = at to colorAt(at)
                                    val change = awaitPointerEvent().changes.first()
                                    if (!change.pressed) break
                                    at = change.position
                                    change.consume()
                                }
                                loupe = null
                                picked(colorAt(at))
                                return@awaitEachGesture
                            }
                            val sel = current?.takeUnless { it.locked }
                            val handle = sel?.let { handleAt(it, down.position, scale, handleRadius) }
                            if (sel != null && handle != null) {
                                down.consume()
                                if (handle == Handle.Delete) {
                                    while (awaitPointerEvent().changes.any { it.pressed }) Unit
                                    vm.deleteSelected()
                                } else if (handle == Handle.Tail) {
                                    var first = true
                                    while (true) {
                                        val change = awaitPointerEvent().changes.first()
                                        if (!change.pressed) break
                                        val (x, y) = sel.t.toLocal(change.position.x / scale, change.position.y / scale)
                                        vm.setTail(x, y, first)
                                        first = false
                                        change.consume()
                                    }
                                } else if (sel is TextLayer && (handle == Handle.WrapLeft || handle == Handle.WrapRight)) {
                                    val side = if (handle == Handle.WrapRight) 1f else -1f
                                    fun localX(at: Offset) = sel.t.toLocal(at.x / scale, at.y / scale).first
                                    var first = true
                                    while (true) {
                                        val change = awaitPointerEvent().changes.first()
                                        if (!change.pressed) break
                                        vm.dragWrap(sel, side, localX(change.position) - localX(down.position), first)
                                        first = false
                                        change.consume()
                                    }
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
                    drawLayers(groups.last()) { c ->
                        selected?.let { c.drawSelection(it, scale) }
                        if (snaps.x) c.drawLine(cx, 0f, cx, project.height.toFloat(), guidePaint.also { it.strokeWidth = 2 / scale })
                        if (snaps.y) c.drawLine(0f, cy, project.width.toFloat(), cy, guidePaint.also { it.strokeWidth = 2 / scale })
                    }
                    loupe?.let { (at, color) ->
                        // Above the finger, so it stays in sight.
                        val o = at - Offset(0f, 56.dp.toPx() / viewZoom)
                        drawCircle(Color.White, 24.dp.toPx() / viewZoom, o)
                        drawCircle(Color(color), 21.dp.toPx() / viewZoom, o)
                    }
                }
            }
            selected?.takeUnless { it.locked || it.hidden }?.let { l ->
                HandleDot(R.drawable.ic_close, cornerOf(l, -1f, scale), Color(0xFFFF3B5C))
                HandleDot(R.drawable.ic_open_in_full, cornerOf(l, 1f, scale), Ink.accent)
                tailOf(l, scale)?.let { HandleDot(R.drawable.ic_chat_bubble, it, Color.White) }
                if (l is TextLayer) for (side in listOf(-1f, 1f)) HandleDot(R.drawable.ic_arrow_range, sideOf(l, side, scale), Color.White)
            }
        }
        if (picking != null) {
            Text(
                stringResource(R.string.eyedropper_hint),
                color = Ink.onAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(14.dp)).background(Ink.accent).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        if (viewZoom > 1.01f) {
            Text(
                stringResource(R.string.fit),
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

/** Screen position of a bubble's tail tip, or null without one. */
private fun tailOf(layer: Layer, scale: Float): Offset? = (layer as? TextLayer)?.tailTip?.let { (x, y) ->
    val t = layer.t.shiftedLocal(x, y)
    Offset(t.x * scale, t.y * scale)
}

/** Screen position of the middle of the layer's left ([side] = -1) or right (+1) edge. */
private fun sideOf(layer: Layer, side: Float, scale: Float): Offset {
    val t = layer.t.shiftedLocal(side * layer.width / 2, 0f)
    return Offset(t.x * scale, t.y * scale)
}

/** The handle nearest to [point] within [radius]: on a one-line caption the side handles sit close to the corners. */
private fun handleAt(layer: Layer, point: Offset, scale: Float, radius: Float): Handle? = listOfNotNull(
    tailOf(layer, scale)?.let { Handle.Tail to it },
    Handle.Delete to cornerOf(layer, -1f, scale),
    Handle.Scale to cornerOf(layer, 1f, scale),
    (Handle.WrapLeft to sideOf(layer, -1f, scale)).takeIf { layer is TextLayer },
    (Handle.WrapRight to sideOf(layer, 1f, scale)).takeIf { layer is TextLayer },
).map { (h, at) -> h to (point - at).getDistance() }.filter { it.second < radius }.minByOrNull { it.second }?.first

@Composable
private fun HandleDot(icon: Int, at: Offset, background: Color) {
    val half = with(LocalDensity.current) { 14.dp.roundToPx() }
    Box(
        Modifier
            .offset { IntOffset(at.x.roundToInt() - half, at.y.roundToInt() - half) }
            .size(28.dp)
            // Handles can sit at the screen edge (a full-width caption's sides); keep the back gesture off them.
            .systemGestureExclusion()
            .clip(CircleShape)
            .background(background)
            // Visible on both light and dark photos.
            .border(1.5.dp, Color.Black.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(icon, Modifier.size(16.dp), if (background == Ink.accent || background == Color.White) Ink.onAccent else Color.White)
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
    // A new canvas shape needs a new buffer, so a new view; so does a new player.
    key(bw, bh, player) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    // The canvas's letterbox bars are transparent, so the blurred background preview shows through.
                    isOpaque = false
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

/** The preview player, null for the moment one is swapped for the next. */
@Composable
private fun rememberCompositionPlayer(generation: Int): CompositionPlayer? {
    val context = LocalContext.current
    var player by remember { mutableStateOf<CompositionPlayer?>(null) }
    // Made in the effect rather than in remember: on a new key the old player is released (onDispose) before the next
    // one is built, so two never fight over decoders and the GPU.
    DisposableEffect(generation) {
        val p = CompositionPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL }
        player = p
        onDispose {
            player = null
            p.release()
        }
    }
    return player
}

private const val RETRY_WINDOW_MS = 5_000L

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
            CircleButton(R.drawable.ic_close, stringResource(R.string.cancel), onClick = onCancel)
            Spacer(Modifier.weight(1f))
            CircleButton(R.drawable.ic_check, stringResource(R.string.done), enabled = value.text.isNotBlank()) { onDone(value.text, fill) }
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
                        if (value.text.isEmpty()) Text(stringResource(R.string.text_hint), color = Ink.dim, fontSize = 36.sp, fontFamily = FontFamily(font), textAlign = TextAlign.Center)
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
