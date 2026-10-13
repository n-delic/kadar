package com.ndelic.kadar.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.Sticker
import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.DrawLayer
import com.ndelic.kadar.model.ExportSize
import com.ndelic.kadar.model.Font
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.Project
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.TextAlign
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KDialog
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.KToggle
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.video.Music
import com.ndelic.kadar.video.VideoClip
import kotlin.math.abs
import kotlin.math.roundToInt

private val EMOJIS = listOf(
    "😂", "🤣", "💀", "😭", "🔥", "👀", "🤡", "🗿", "😎", "🥶", "😳", "🤔", "🤯", "😐", "🥲",
    "😈", "😡", "🙏", "👍", "👎", "💯", "❤️", "✨", "🎉", "⚡", "❌", "✅", "⬆️", "➡️", "⭕",
)

// A null label is the localized "Original".
private val ASPECTS = listOf(null to null, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16, "16:9" to 16f / 9)

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerPanel(stickers: List<Sticker>, vm: EditorViewModel) {
    var deleting by remember { mutableStateOf<Sticker?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyHorizontalGrid(GridCells.Fixed(2), Modifier.height(104.dp)) {
            items(EMOJIS) { e -> Text(e, fontSize = 30.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.addEmoji(e) }.padding(6.dp)) }
        }
        PanelLabel(stringResource(R.string.my_stickers))
        if (stickers.isEmpty()) {
            Text(stringResource(R.string.my_stickers_empty), color = Ink.dim, fontSize = 13.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stickers, key = { it.file.path }) { s ->
                    Image(
                        s.thumb.asImageBitmap(),
                        contentDescription = stringResource(R.string.sticker),
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = { vm.addSticker(s) }, onLongClick = { deleting = s }),
                    )
                }
            }
        }
    }
    deleting?.let { s ->
        KDialog(
            title = stringResource(R.string.delete_sticker_title),
            text = stringResource(R.string.delete_sticker_text),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteSticker(s)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun CanvasPanel(project: Project, defaultExport: ExportSize, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(ASPECTS) { (label, ratio) ->
                val active = if (ratio == null) project.height == project.originalHeight else abs(project.width / project.height.toFloat() - ratio) < 0.01f
                AspectTile(label ?: stringResource(R.string.original), ratio ?: (project.width / project.originalHeight.toFloat()), active) { vm.setAspect(ratio) }
            }
        }
        PanelLabel(stringResource(R.string.background))
        ColorRow(project.background.takeIf { it != android.graphics.Color.TRANSPARENT }, allowNone = true) { vm.setBackground(it ?: android.graphics.Color.TRANSPARENT) }
        ExportSizeRow(project, defaultExport, vm)
    }
}

/** Video canvas: reshape to a social-media ratio (9:16 for reels and stories) with black bars. */
@Composable
fun VideoCanvasPanel(project: Project, clip: VideoClip, defaultExport: ExportSize, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(ASPECTS) { (label, ratio) ->
                val active = clip.canvas?.let { ratio != null && abs(it - ratio) < 0.01f } ?: (ratio == null)
                AspectTile(label ?: stringResource(R.string.original), ratio ?: (clip.width / clip.height.toFloat()), active) { vm.setVideoCanvas(ratio) }
            }
        }
        if (clip.canvas != null) TextTabs(listOf(false, true), clip.blur, label = { stringResource(if (it) R.string.canvas_blur else R.string.canvas_black) }) { b -> vm.updateClip { copy(blur = b) } }
        ExportSizeRow(project, defaultExport, vm)
    }
}

/** Brightness, contrast and saturation sliders; [onChange] gets the whole new value. */
@Composable
fun AdjustPanel(adjust: Adjust, onChange: (Adjust) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelLabel(stringResource(R.string.tool_adjust))
            Spacer(Modifier.weight(1f))
            // Resets the sliders only; the look has its own panel.
            val reset = Adjust(look = adjust.look, amount = adjust.amount)
            if (adjust != reset) {
                Text(stringResource(R.string.reset), color = Ink.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange(reset) }.padding(6.dp))
            }
        }
        LabeledSlider(stringResource(R.string.brightness), adjust.brightness) { onChange(adjust.copy(brightness = it)) }
        LabeledSlider(stringResource(R.string.contrast), adjust.contrast) { onChange(adjust.copy(contrast = it)) }
        LabeledSlider(stringResource(R.string.saturation), adjust.saturation) { onChange(adjust.copy(saturation = it)) }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    valueLabel: (Float) -> String = { "${(it * 100).roundToInt()}" },
    onCommit: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink.dim, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f)) { KSlider(null, value, range, valueLabel, onCommit) }
    }
}

@Composable
fun TextStylePanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextTabs(TextAlign.entries, layer.align, label = { stringResource(it.label) }) { a -> vm.editText { copy(align = a) } }
        KToggle(stringResource(R.string.shadow), null, layer.shadow) { on -> vm.editText { copy(shadow = on) } }
        LabeledSlider(stringResource(R.string.spacing), layer.spacing * 2) { v -> vm.editText { copy(spacing = v / 2) } }
        LabeledSlider(stringResource(R.string.line_height), layer.lineSpacing, 0.6f..2f, { "${(it * 10).roundToInt() / 10f}" }) { v -> vm.editText { copy(lineSpacing = v) } }
        LabeledSlider(stringResource(R.string.opacity), layer.opacity, 0.05f..1f, ::percent) { v -> vm.editText { copy(opacity = v) } }
    }
}

/** Per-project export size; "Default" follows Settings. */
@Composable
fun ExportSizeRow(project: Project, default: ExportSize, vm: EditorViewModel) {
    Column {
        PanelLabel(stringResource(R.string.export_size))
        TextTabs(listOf<ExportSize?>(null) + ExportSize.entries, project.exportSize, label = { it?.let { s -> stringResource(s.label) } ?: stringResource(R.string.export_size_default, stringResource(default.label)) }) { vm.setProjectExportSize(it) }
    }
}

/** A box drawn in the target aspect ratio, so the choice reads at a glance. */
@Composable
private fun AspectTile(label: String, ratio: Float, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Ink.accent else Ink.dim
    val (w, h) = if (ratio >= 1) 36f to 36f / ratio else 36f * ratio to 36f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(w.dp, h.dp).border(2.dp, color, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 11.sp)
    }
}

@Composable
fun SpeedPanel(clip: VideoClip, vm: EditorViewModel) {
    Column {
        TextTabs(SPEEDS, clip.speed, label = { "${it}x".replace(".0x", "x") }) { s -> vm.updateClip { copy(speed = s) } }
        Text(stringResource(R.string.speed_length, formatMs(clip.outputDurationMs)), color = Ink.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        KToggle(stringResource(R.string.keep_pitch), stringResource(R.string.keep_pitch_hint), clip.keepPitch) { on -> vm.updateClip { copy(keepPitch = on) } }
    }
}

/** The stack, top layer first: tap to select, arrows to move up or down. */
@Composable
fun LayersPanel(layers: List<Layer>, selectedId: Long?, vm: EditorViewModel) {
    LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(layers.asReversed(), key = { it.id }) { l ->
            val active = l.id == selectedId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Ink.raised else Color.Transparent)
                    .clickable { vm.select(l.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Ink.line), contentAlignment = Alignment.Center) {
                    when (l) {
                        is ImageLayer -> Image(l.bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        is TextLayer -> Text("T", color = Ink.text, fontFamily = FontFamily(l.font.typeface), fontSize = 18.sp)
                        is VideoLayer -> KIcon(R.drawable.ic_movie, Modifier.size(22.dp), Ink.violet)
                        is DrawLayer -> KIcon(R.drawable.ic_draw, Modifier.size(22.dp), Ink.coral)
                        is ShapeLayer -> KIcon(l.kind.icon, Modifier.size(22.dp), Ink.sky)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    l.label,
                    color = Ink.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SmallIcon(if (l.hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, stringResource(if (l.hidden) R.string.show else R.string.hide)) { vm.setFlags(l.id, !l.hidden, l.locked) }
                SmallIcon(if (l.locked) R.drawable.ic_lock else R.drawable.ic_lock_open, stringResource(if (l.locked) R.string.unlock else R.string.lock)) { vm.setFlags(l.id, l.hidden, !l.locked) }
                SmallIcon(R.drawable.ic_arrow_upward, stringResource(R.string.move_up)) { vm.moveLayer(l.id, +1) }
                SmallIcon(R.drawable.ic_arrow_downward, stringResource(R.string.move_down)) { vm.moveLayer(l.id, -1) }
            }
        }
    }
}

/** What the Layers panel and the timeline tracks call a layer. */
val Layer.label: String
    @Composable get() = when (this) {
        is TextLayer -> text.lines().first()
        is ImageLayer -> stringResource(R.string.image)
        is VideoLayer -> stringResource(R.string.video)
        is DrawLayer -> stringResource(R.string.drawing)
        is ShapeLayer -> stringResource(kind.label)
    }

@Composable
fun SmallIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        KIcon(icon, Modifier.size(22.dp), description = description)
    }
}

@Composable
fun AudioPanel(clip: VideoClip, vm: EditorViewModel, onAddMusic: () -> Unit, voiceover: @Composable () -> Unit) {
    // Scrolls once a few recordings are listed, so the stage keeps its room.
    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelLabel(stringResource(R.string.video_sound))
        KSlider(R.drawable.ic_volume_up, clip.volume, 0f..1f, ::percent) { v -> vm.updateClip { copy(volume = v) } }
        PanelLabel(stringResource(R.string.music))
        val music = clip.music
        if (music == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAddMusic).padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                KIcon(R.drawable.ic_add, Modifier.size(22.dp), Ink.accent)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.music_add), color = Ink.accent, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KIcon(R.drawable.ic_music_note, Modifier.size(22.dp), Ink.dim)
                Spacer(Modifier.size(12.dp))
                Text(music.name, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.updateClip { copy(music = null) } }.padding(8.dp)) {
                    KIcon(R.drawable.ic_delete, Modifier.size(22.dp), description = stringResource(R.string.music_remove))
                }
            }
            KSlider(R.drawable.ic_volume_up, music.volume, 0f..1f, ::percent) { v -> vm.updateClip { copy(music = music.copy(volume = v)) } }
            // Leave at least a second of song, so the looping music item is never empty.
            KSlider(R.drawable.ic_play_arrow, music.startMs.toFloat(), 0f..maxOf(0f, music.durationMs - 1000f), { formatMs(it.toLong()) }) { v ->
                vm.updateClip { copy(music = music.copy(startMs = v.toLong())) }
            }
            Row {
                Box(Modifier.weight(1f)) {
                    KToggle(stringResource(R.string.music_fade_in), null, music.fadeIn) { on -> vm.updateClip { copy(music = music.copy(fadeIn = on)) } }
                }
                Box(Modifier.weight(1f)) {
                    KToggle(stringResource(R.string.music_fade_out), null, music.fadeOut) { on -> vm.updateClip { copy(music = music.copy(fadeOut = on)) } }
                }
            }
        }
        voiceover()
    }
}

/** Font files the picker offers: the font types, and the generic one some file managers give `.ttf` files. */
private val FONT_TYPES = arrayOf("font/*", "application/font-sfnt", "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype", "application/octet-stream")

/** [fonts] are the bundled ones, then the imported ones; the last tile imports a font file. */
@Composable
fun FontPanel(layer: TextLayer, fonts: List<Font>, vm: EditorViewModel) {
    val pick = rememberLauncherForActivityResult(OpenDocument()) { it?.let(vm::importFont) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(fonts, key = { it.name }) { f ->
            val active = layer.font.name == f.name
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.heightIn(min = 44.dp).widthIn(max = 120.dp).clip(shape)
                    .border(1.5.dp, if (active) Ink.accent else Color.Transparent, shape)
                    .clickable { vm.editText { copy(font = f) } }.padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    f.name,
                    fontFamily = FontFamily(f.typeface),
                    fontSize = 22.sp,
                    color = if (active) Ink.accent else Ink.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable { pick.launch(FONT_TYPES) }.padding(horizontal = 10.dp),
            ) {
                KIcon(R.drawable.ic_add, Modifier.size(22.dp), Ink.accent)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.font_import), color = Ink.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

fun percent(v: Float) = "${(v * 100).roundToInt()}%"
