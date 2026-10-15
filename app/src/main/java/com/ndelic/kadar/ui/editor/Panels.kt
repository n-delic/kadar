package com.ndelic.kadar.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.annotation.StringRes
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.ndelic.kadar.ui.common.AspectTile
import com.ndelic.kadar.ui.common.CANVAS_RATIOS
import com.ndelic.kadar.ui.common.CircleButton
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KDialog
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.KToggle
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.video.AudioRef
import com.ndelic.kadar.video.MAX_FADE_MS
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.audioPart
import com.ndelic.kadar.video.speedAt
import com.ndelic.kadar.video.speedPosition
import kotlin.math.abs
import kotlin.math.roundToInt

private val EMOJIS = listOf(
    "😂", "🤣", "💀", "😭", "🔥", "👀", "🤡", "🗿", "😎", "🥶", "😳", "🤔", "🤯", "😐", "🥲",
    "😈", "😡", "🙏", "👍", "👎", "💯", "❤️", "✨", "🎉", "⚡", "❌", "✅", "⬆️", "➡️", "⭕",
)

// A null label is the localized "Original".
private val ASPECTS = listOf<Pair<String?, Float?>>(null to null) + CANVAS_RATIOS

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (project.layers.any { it is ImageLayer }) {
                BlurDot(project.blur, vm::setBlur)
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f)) {
                // Transparent matches no dot (the crossed-out one is null), so with the blur on only its dot shows picked.
                val picked = if (project.blur) android.graphics.Color.TRANSPARENT else project.background.takeIf { it != android.graphics.Color.TRANSPARENT }
                ColorRow(picked, allowNone = true) { vm.setBackground(it ?: android.graphics.Color.TRANSPARENT) }
            }
        }
        ExportSizeRow(project, defaultExport, vm)
    }
}

/** The blurred-photo background, picked like a color dot. */
@Composable
private fun BlurDot(active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).border(2.dp, if (active) Ink.accent else Color.Transparent, CircleShape).padding(4.dp)
            .clip(CircleShape).background(Ink.raised).border(1.dp, Ink.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(R.drawable.ic_blur_on, Modifier.size(20.dp), if (active) Ink.accent else Ink.text, stringResource(R.string.canvas_blur))
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

/** The sliders on [Adjust], picked one at a time from tabs so the panel stays short. */
private enum class AdjustSlider(@StringRes val label: Int, val value: (Adjust) -> Float, val set: Adjust.(Float) -> Adjust, val range: ClosedFloatingPointRange<Float> = -1f..1f) {
    Brightness(R.string.brightness, { it.brightness }, { copy(brightness = it) }),
    Contrast(R.string.contrast, { it.contrast }, { copy(contrast = it) }),
    Saturation(R.string.saturation, { it.saturation }, { copy(saturation = it) }),
    Warmth(R.string.warmth, { it.warmth }, { copy(warmth = it) }),
    Tint(R.string.tint, { it.tint }, { copy(tint = it) }),
    Hue(R.string.hue, { it.hue }, { copy(hue = it) }),
    Fade(R.string.fade, { it.fade }, { copy(fade = it) }, 0f..1f),
    Vignette(R.string.vignette, { it.vignette }, { copy(vignette = it) }, 0f..1f),
    Grain(R.string.grain, { it.grain }, { copy(grain = it) }, 0f..1f),
}

/**
 * Brightness, contrast, saturation, warmth, tint, hue, fade, vignette and grain, one slider at a time; [onChange] gets the whole new
 * value, [onDrag] the value while a slider moves, [onApplyAll] (clips) copies it to every clip, [onCompare] is held to
 * see the picture without any of it.
 */
@Composable
fun AdjustPanel(adjust: Adjust, onApplyAll: (() -> Unit)? = null, onCompare: (Boolean) -> Unit, onDrag: (Adjust) -> Unit, onChange: (Adjust) -> Unit) {
    var slider by rememberSaveable { mutableStateOf(AdjustSlider.Brightness) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelLabel(stringResource(R.string.tool_adjust))
            Spacer(Modifier.weight(1f))
            onApplyAll?.let { ApplyAll(it) }
            // Resets the sliders only; the look has its own panel.
            val reset = Adjust(look = adjust.look, amount = adjust.amount)
            if (adjust != reset) {
                Text(stringResource(R.string.reset), color = Ink.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange(reset) }.padding(6.dp))
            }
            CompareButton(onCompare)
        }
        // A dot marks the sliders that are moved, since only one shows at a time.
        TextTabs(AdjustSlider.entries, slider, label = { stringResource(it.label) + if (it.value(adjust) != 0f) " •" else "" }) { slider = it }
        key(slider) {
            KSlider(null, slider.value(adjust), slider.range, { "${(it * 100).roundToInt()}" }, onDrag = { onDrag(adjust.(slider.set)(it)) }) { onChange(adjust.(slider.set)(it)) }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    valueLabel: (Float) -> String = { "${(it * 100).roundToInt()}" },
    onCommit: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink.dim, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f)) { KSlider(null, value, range, valueLabel, onCommit = onCommit) }
    }
}

@Composable
fun TextStylePanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextTabs(TextAlign.entries, layer.align, label = { stringResource(it.label) }) { a -> vm.editText { copy(align = a) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            CircleButton(R.drawable.ic_format_bold, stringResource(R.string.text_bold), active = layer.bold) { vm.editText { copy(bold = !bold) } }
            CircleButton(R.drawable.ic_format_italic, stringResource(R.string.text_italic), active = layer.italic) { vm.editText { copy(italic = !italic) } }
            CircleButton(R.drawable.ic_format_underlined, stringResource(R.string.text_underline), active = layer.underline) { vm.editText { copy(underline = !underline) } }
            CircleButton(R.drawable.ic_uppercase, stringResource(R.string.text_caps), active = layer.caps) { vm.editText { copy(caps = !caps) } }
        }
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

/** Clip [index]'s speed on a slider from 0.2x to 10x, its pitch fix, and both for every video clip. */
@Composable
fun SpeedPanel(video: VideoClip, index: Int, vm: EditorViewModel) {
    val clip = video.segments[index]
    Column {
        // KSlider's value label is a plain function, so it fills in the format itself.
        val format = stringResource(R.string.speed_value)
        fun label(speed: Float) = format.format(speed.toString().removeSuffix(".0"))
        KSlider(R.drawable.ic_speed, speedPosition(clip.speed), 0f..1f, { label(speedAt(it)) }) { p -> vm.updateSegment(index) { copy(speed = speedAt(p)) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.speed_length, formatMs(clip.outputDurationMs)), color = Ink.dim, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            // Photos keep their length: speed on a still only shortens it.
            if (video.segments.size > 1) ApplyAll { vm.updateAllSegments { if (isImage) this else copy(speed = clip.speed, keepPitch = clip.keepPitch) } }
        }
        KToggle(stringResource(R.string.keep_pitch), stringResource(R.string.keep_pitch_hint), clip.keepPitch) { on -> vm.updateSegment(index) { copy(keepPitch = on) } }
    }
}

/** "Apply to all": gives every clip (or cut) the setting the panel shows, in one undo step. */
@Composable
fun ApplyAll(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        KIcon(R.drawable.ic_select_all, Modifier.size(22.dp), Ink.accent)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.apply_all), color = Ink.accent, fontWeight = FontWeight.SemiBold)
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

/**
 * The selected music or voiceover track's [audio] settings; with none selected, clip [index]'s sound, adding music and
 * recording a voiceover.
 */
@Composable
fun AudioPanel(clip: VideoClip, index: Int, audio: AudioRef?, vm: EditorViewModel, onAddMusic: () -> Unit, voiceover: @Composable () -> Unit) {
    // Scrolls if it gets taller than the others, so the stage keeps its room.
    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val part = audio?.let { clip.audioPart(it) }
        if (audio != null && part != null) {
            PanelLabel(stringResource(if (audio.voice) R.string.voiceover else R.string.music))
            KSlider(R.drawable.ic_volume_up, part.volume, 0f..1f, ::percent) { v -> vm.editAudio { copy(volume = v) } }
            if (!audio.voice) {
                val music = clip.music[audio.index]
                // Leave at least a second of song, so a repeat is never empty.
                KSlider(R.drawable.ic_play_arrow, music.startMs.toFloat(), 0f..maxOf(0f, music.durationMs - 1000f), { formatMs(it.toLong()) }) { v -> vm.setSongStart(v.toLong()) }
            }
            PanelLabel(stringResource(R.string.audio_fades))
            val seconds = stringResource(R.string.transition_seconds)
            fun fadeMs(v: Float) = (v / 100).roundToInt() * 100L
            // Side by side, so the panel stays as tall as the others.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    KSlider(R.drawable.ic_trending_up, part.fadeInMs.toFloat(), 0f..MAX_FADE_MS.toFloat(), { seconds.format(fadeMs(it) / 1000f) }) { v -> vm.editAudio { copy(fadeInMs = fadeMs(v)) } }
                }
                Box(Modifier.weight(1f)) {
                    KSlider(R.drawable.ic_trending_down, part.fadeOutMs.toFloat(), 0f..MAX_FADE_MS.toFloat(), { seconds.format(fadeMs(it) / 1000f) }) { v -> vm.editAudio { copy(fadeOutMs = fadeMs(v)) } }
                }
            }
            return@Column
        }
        PanelLabel(stringResource(R.string.video_sound))
        KSlider(R.drawable.ic_volume_up, clip.segments[index].volume, 0f..1f, ::percent) { v -> vm.updateSegment(index) { copy(volume = v) } }
        PanelLabel(stringResource(R.string.music))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAddMusic).padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            KIcon(R.drawable.ic_add, Modifier.size(22.dp), Ink.accent)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.music_add), color = Ink.accent, fontWeight = FontWeight.SemiBold)
        }
        voiceover()
        if (clip.music.isNotEmpty() || clip.voices.isNotEmpty()) Text(stringResource(R.string.audio_hint), color = Ink.dim, fontSize = 12.sp)
    }
}

/** Font files the picker offers: the font types, and the generic one some file managers give `.ttf` files. */
private val FONT_TYPES = arrayOf("font/*", "application/font-sfnt", "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype", "application/octet-stream")

/** [fonts] are the bundled ones, then the imported ones; the last tile imports a font file. */
@Composable
fun FontPanel(layer: TextLayer, fonts: List<Font>, defaultSize: Float, vm: EditorViewModel) {
    val pick = rememberLauncherForActivityResult(OpenDocument()) { it?.let(vm::importFont) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        // Size is the layer's placement scale, like a pinch, so the wrap and the bubble tail keep their shape.
        LabeledSlider(stringResource(R.string.text_size), layer.size * layer.t.scale / defaultSize, 0.2f..4f, ::percent) { v ->
            vm.editText { copy(t = t.copy(scale = v * defaultSize / size)) }
        }
    }
}

fun percent(v: Float) = "${(v * 100).roundToInt()}%"
