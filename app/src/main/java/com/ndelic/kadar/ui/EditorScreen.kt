@file:OptIn(UnstableApi::class)

package com.ndelic.kadar.ui

import android.graphics.Paint
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import com.ndelic.kadar.EditorUiState
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.ImageLayer
import com.ndelic.kadar.Layer
import com.ndelic.kadar.Project
import com.ndelic.kadar.TextLayer
import com.ndelic.kadar.VideoClip
import com.ndelic.kadar.composition
import com.ndelic.kadar.drawLayer
import kotlin.math.min

enum class Panel { Sticker, Canvas, Trim, Speed, Audio, Font, Color }

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: EditorUiState, vm: EditorViewModel, onBrowse: () -> Unit) {
    val project = state.project ?: return
    val selected = state.selected
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    // null: closed; otherwise the text being typed (for a new layer, or the selected text layer).
    var typing by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by rememberSaveable { mutableStateOf(true) }
    val image = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::addImage) }
    val music = rememberLauncherForActivityResult(OpenDocument()) { it?.let(vm::setMusic) }

    LaunchedEffect(state.selectedId) { panel = null }
    BackHandler { if (selected != null) vm.select(null) else vm.close() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            navigationIcon = { IconButton(onClick = vm::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to projects") } },
            title = { Text(if (project.video != null) "Video" else "Photo") },
            actions = { Button(onClick = vm::export, Modifier.padding(end = 8.dp)) { Text("Export") } },
        )
        Stage(project, selected, playing, vm, Modifier.weight(1f).fillMaxWidth())
        panel?.let { p ->
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(12.dp)) {
                    when (p) {
                        Panel.Sticker -> StickerPanel(state.stickers, vm)
                        Panel.Canvas -> CanvasPanel(project, vm)
                        Panel.Trim -> project.video?.let { TrimPanel(it, vm) }
                        Panel.Speed -> project.video?.let { SpeedPanel(it, vm) }
                        Panel.Audio -> project.video?.let { AudioPanel(it, vm, onAddMusic = { music.launch(arrayOf("audio/*")) }) }
                        Panel.Font -> (selected as? TextLayer)?.let { FontPanel(it, vm) }
                        Panel.Color -> (selected as? TextLayer)?.let { ColorPanel(it, vm) }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fun toggle(p: Panel) { panel = if (panel == p) null else p }
            when (selected) {
                null -> {
                    Tool("Text") { typing = "" }
                    Tool("Sticker", panel == Panel.Sticker) { toggle(Panel.Sticker) }
                    Tool("Image") { image.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                    Tool("Web image", onClick = onBrowse)
                    if (project.video != null) {
                        Tool(if (playing) "Pause" else "Play") { playing = !playing }
                        Tool("Trim", panel == Panel.Trim) { toggle(Panel.Trim) }
                        Tool("Speed", panel == Panel.Speed) { toggle(Panel.Speed) }
                        Tool("Audio", panel == Panel.Audio) { toggle(Panel.Audio) }
                    } else {
                        Tool("Canvas", panel == Panel.Canvas) { toggle(Panel.Canvas) }
                    }
                }
                is TextLayer -> {
                    Tool("Edit") { typing = selected.text }
                    Tool("Font", panel == Panel.Font) { toggle(Panel.Font) }
                    Tool("Color", panel == Panel.Color) { toggle(Panel.Color) }
                    Tool("To front", onClick = vm::bringToFront)
                    Tool("Delete", onClick = vm::deleteSelected)
                    Tool("Done") { vm.select(null) }
                }
                is ImageLayer -> {
                    Tool("Remove BG", onClick = vm::removeBackground)
                    Tool("Save sticker", onClick = vm::saveSticker)
                    Tool("To front", onClick = vm::bringToFront)
                    Tool("Delete", onClick = vm::deleteSelected)
                    Tool("Done") { vm.select(null) }
                }
            }
        }
    }

    typing?.let { initial ->
        TextDialog(
            initial = initial,
            onDone = { text ->
                typing = null
                if (selected is TextLayer) vm.editText { copy(text = text) } else vm.addText(text)
            },
            onDismiss = { typing = null },
        )
    }
}

@Composable
private fun Tool(label: String, active: Boolean = false, onClick: () -> Unit) {
    FilterChip(selected = active, onClick = onClick, label = { Text(label) })
}

@Composable
private fun TextDialog(initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text") },
        text = { OutlinedTextField(text, { text = it }, minLines = 2) },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The canvas: video preview underneath, layers drawn on top at screen scale. Drag, pinch and twist move the selected layer. */
@Composable
private fun Stage(project: Project, selected: Layer?, playing: Boolean, vm: EditorViewModel, modifier: Modifier) {
    BoxWithConstraints(modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        val scale = min(constraints.maxWidth / project.width.toFloat(), constraints.maxHeight / project.height.toFloat())
        val size = with(LocalDensity.current) { DpSize((project.width * scale).toDp(), (project.height * scale).toDp()) }
        val layers by rememberUpdatedState(project.layers)
        val background = if (project.background == android.graphics.Color.TRANSPARENT) Color(0xFF808080) else Color(project.background)
        Box(Modifier.size(size).clipToBounds().background(background)) {
            project.video?.let { VideoPreview(it, playing, Modifier.fillMaxSize()) }
            Canvas(
                Modifier.fillMaxSize().pointerInput(scale) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val p = down.position / scale
                        val hit = layers.lastOrNull { it.t.contains(it.width, it.height, p.x, p.y) }
                        if (hit != null) vm.select(hit.id)
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val rotation = event.calculateRotation()
                            if (pan != Offset.Zero || zoom != 1f || rotation != 0f) {
                                moved = true
                                vm.transformSelected(pan.x / scale, pan.y / scale, zoom, rotation)
                                event.changes.forEach { it.consume() }
                            }
                        }
                        // A tap on empty canvas deselects; a pinch there still resizes the selection.
                        if (!moved && hit == null) vm.select(null)
                    }
                },
            ) {
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.save()
                    c.scale(scale, scale)
                    project.layers.forEach { c.drawLayer(it) }
                    selected?.let { c.drawSelection(it, scale) }
                    c.restore()
                }
            }
        }
    }
}

private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = 0xFF40C4FF.toInt()
}

private fun android.graphics.Canvas.drawSelection(layer: Layer, viewScale: Float) {
    save()
    translate(layer.t.x, layer.t.y)
    rotate(layer.t.rotation)
    scale(layer.t.scale, layer.t.scale)
    selectionPaint.strokeWidth = 2.dp.value * 2 / (viewScale * layer.t.scale)
    drawRect(-layer.width / 2, -layer.height / 2, layer.width / 2, layer.height / 2, selectionPaint)
    restore()
}

/** Plays the same composition the export uses (trim, speed, music), minus the layers, which the stage draws itself. */
@Composable
private fun VideoPreview(clip: VideoClip, playing: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember { CompositionPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ALL } }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(clip) {
        player.setComposition(clip.composition(null))
        player.prepare()
    }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    AndroidView(factory = { SurfaceView(it).also(player::setVideoSurfaceView) }, modifier)
}
