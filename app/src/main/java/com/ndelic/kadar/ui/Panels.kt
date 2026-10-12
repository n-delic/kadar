package com.ndelic.kadar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.Project
import com.ndelic.kadar.Sticker
import com.ndelic.kadar.TextLayer
import com.ndelic.kadar.VideoClip
import kotlin.math.abs

private val EMOJIS = listOf(
    "😂", "🤣", "💀", "😭", "🔥", "👀", "🤡", "🗿", "😎", "🥶", "😳", "🤔", "🤯", "😐", "🥲",
    "😈", "😡", "🙏", "👍", "👎", "💯", "❤️", "✨", "🎉", "⚡", "❌", "✅", "⬆️", "➡️", "⭕",
)

private val COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFEB3B, 0xFFFF9800, 0xFFF44336, 0xFFE91E63,
    0xFF9C27B0, 0xFF2196F3, 0xFF00BCD4, 0xFF4CAF50,
).map { it.toInt() }

private val ASPECTS = listOf("Original" to null, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16, "16:9" to 16f / 9)

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerPanel(stickers: List<Sticker>, vm: EditorViewModel) {
    var deleting by remember { mutableStateOf<Sticker?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow {
            items(EMOJIS) { e -> Text(e, fontSize = 32.sp, modifier = Modifier.clickable { vm.addEmoji(e) }.padding(6.dp)) }
        }
        Text("My stickers", style = MaterialTheme.typography.labelLarge)
        if (stickers.isEmpty()) {
            Text("Remove the background of an image, then tap Save sticker to keep it here.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stickers, key = { it.file.path }) { s ->
                    Image(
                        s.thumb.asImageBitmap(),
                        contentDescription = "Sticker",
                        modifier = Modifier.size(64.dp).combinedClickable(onClick = { vm.addSticker(s) }, onLongClick = { deleting = s }),
                    )
                }
            }
        }
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete sticker?") },
            text = { Text("Projects that already use it keep their copy.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSticker(s)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CanvasPanel(project: Project, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Size", style = MaterialTheme.typography.labelLarge)
        ChipRow {
            ASPECTS.forEach { (label, ratio) ->
                val active = if (ratio == null) project.height == project.originalHeight else abs(project.width / project.height.toFloat() - ratio) < 0.01f
                FilterChip(selected = active, onClick = { vm.setAspect(ratio) }, label = { Text(label) })
            }
        }
        Text("Background", style = MaterialTheme.typography.labelLarge)
        ChipRow {
            listOf("White" to android.graphics.Color.WHITE, "Black" to android.graphics.Color.BLACK, "Transparent" to android.graphics.Color.TRANSPARENT).forEach { (label, color) ->
                FilterChip(selected = project.background == color, onClick = { vm.setBackground(color) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun TrimPanel(clip: VideoClip, vm: EditorViewModel) {
    var range by remember(clip.startMs, clip.endMs) { mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat()) }
    Column {
        Text("${formatMs(range.start)} – ${formatMs(range.endInclusive)}   (${formatMs(clip.outputDurationMs.toFloat())} after speed)")
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..clip.durationMs.toFloat(),
            onValueChangeFinished = {
                // Keep at least a tenth of a second, the encoder can't do an empty clip.
                val start = range.start.toLong().coerceAtMost(clip.durationMs - 100)
                vm.updateClip { copy(startMs = start, endMs = range.endInclusive.toLong().coerceAtLeast(start + 100)) }
            },
        )
    }
}

@Composable
fun SpeedPanel(clip: VideoClip, vm: EditorViewModel) {
    ChipRow {
        SPEEDS.forEach { s ->
            FilterChip(selected = clip.speed == s, onClick = { vm.updateClip { copy(speed = s) } }, label = { Text("${s}x".replace(".0x", "x")) })
        }
    }
}

@Composable
fun AudioPanel(clip: VideoClip, vm: EditorViewModel, onAddMusic: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Video sound", style = MaterialTheme.typography.labelLarge)
        CommitSlider(clip.volume, 0f..1f) { v -> vm.updateClip { copy(volume = v) } }
        val music = clip.music
        if (music == null) {
            OutlinedButton(onClick = onAddMusic) { Text("Add music") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(music.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.updateClip { copy(music = null) } }) { Text("Remove") }
            }
            Text("Music volume", style = MaterialTheme.typography.labelLarge)
            CommitSlider(music.volume, 0f..1f) { v -> vm.updateClip { copy(music = music.copy(volume = v)) } }
            Text("Music starts at ${formatMs(music.startMs.toFloat())}", style = MaterialTheme.typography.labelLarge)
            CommitSlider(music.startMs.toFloat(), 0f..music.durationMs.toFloat()) { v -> vm.updateClip { copy(music = music.copy(startMs = v.toLong())) } }
        }
    }
}

@Composable
fun FontPanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipRow {
            vm.fonts.forEach { f ->
                FilterChip(
                    selected = layer.font.name == f.name,
                    onClick = { vm.editText { copy(font = f) } },
                    label = { Text(f.name, fontFamily = FontFamily(f.typeface)) },
                )
            }
        }
        Text("Size", style = MaterialTheme.typography.labelLarge)
        // Pinching scales too; this is for fine control without moving the layer.
        CommitSlider(layer.t.scale, 0.2f..5f) { v -> vm.editText { copy(t = t.copy(scale = v)) } }
    }
}

@Composable
fun ColorPanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fill", style = MaterialTheme.typography.labelLarge)
        ChipRow { COLORS.forEach { c -> Swatch(c, layer.color == c) { vm.editText { copy(color = c) } } } }
        Text("Outline", style = MaterialTheme.typography.labelLarge)
        ChipRow {
            FilterChip(selected = layer.outline == null, onClick = { vm.editText { copy(outline = null) } }, label = { Text("None") })
            COLORS.forEach { c -> Swatch(c, layer.outline == c) { vm.editText { copy(outline = c) } } }
        }
    }
}

@Composable
private fun Swatch(color: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (active) 3.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A slider that only reports when the finger lifts, so the video preview isn't rebuilt on every pixel. */
@Composable
private fun CommitSlider(value: Float, range: ClosedFloatingPointRange<Float>, onCommit: (Float) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Slider(value = current, onValueChange = { current = it }, valueRange = range, onValueChangeFinished = { onCommit(current) })
}

private fun formatMs(ms: Float): String {
    val total = ms.toLong() / 100
    return "%d:%02d.%d".format(total / 600, total / 10 % 60, total % 10)
}
