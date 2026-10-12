package com.ndelic.kadar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.Project
import com.ndelic.kadar.R
import com.ndelic.kadar.Sticker
import com.ndelic.kadar.TextLayer
import com.ndelic.kadar.VideoClip
import kotlin.math.abs
import kotlin.math.roundToInt

private val EMOJIS = listOf(
    "😂", "🤣", "💀", "😭", "🔥", "👀", "🤡", "🗿", "😎", "🥶", "😳", "🤔", "🤯", "😐", "🥲",
    "😈", "😡", "🙏", "👍", "👎", "💯", "❤️", "✨", "🎉", "⚡", "❌", "✅", "⬆️", "➡️", "⭕",
)

private val ASPECTS = listOf("Original" to null, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16, "16:9" to 16f / 9)

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerPanel(stickers: List<Sticker>, vm: EditorViewModel) {
    var deleting by remember { mutableStateOf<Sticker?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyHorizontalGrid(GridCells.Fixed(2), Modifier.height(104.dp)) {
            items(EMOJIS) { e -> Text(e, fontSize = 30.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.addEmoji(e) }.padding(6.dp)) }
        }
        PanelLabel("My stickers")
        if (stickers.isEmpty()) {
            Text("Remove the background of an image, then tap Save sticker to keep it here.", color = Ink.dim, fontSize = 13.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stickers, key = { it.file.path }) { s ->
                    Image(
                        s.thumb.asImageBitmap(),
                        contentDescription = "Sticker",
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = { vm.addSticker(s) }, onLongClick = { deleting = s }),
                    )
                }
            }
        }
    }
    deleting?.let { s ->
        KDialog(
            title = "Delete sticker?",
            text = "Projects that already use it keep their copy.",
            confirm = "Delete",
            onConfirm = {
                vm.deleteSticker(s)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun CanvasPanel(project: Project, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(ASPECTS) { (label, ratio) ->
                val active = if (ratio == null) project.height == project.originalHeight else abs(project.width / project.height.toFloat() - ratio) < 0.01f
                AspectTile(label, ratio ?: (project.width / project.originalHeight.toFloat()), active) { vm.setAspect(ratio) }
            }
        }
        PanelLabel("Background")
        ColorRow(project.background.takeIf { it != android.graphics.Color.TRANSPARENT }, allowNone = true) { vm.setBackground(it ?: android.graphics.Color.TRANSPARENT) }
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
        Text("Length after speed: ${formatMs(clip.outputDurationMs)}", color = Ink.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
    }
}

@Composable
fun AudioPanel(clip: VideoClip, vm: EditorViewModel, onAddMusic: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelLabel("Video sound")
        KSlider(R.drawable.ic_volume_up, clip.volume, 0f..1f, ::percent) { v -> vm.updateClip { copy(volume = v) } }
        PanelLabel("Music")
        val music = clip.music
        if (music == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAddMusic).padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                KIcon(R.drawable.ic_add, Modifier.size(22.dp), Ink.accent)
                Spacer(Modifier.size(8.dp))
                Text("Add music", color = Ink.accent, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KIcon(R.drawable.ic_music_note, Modifier.size(22.dp), Ink.dim)
                Spacer(Modifier.size(12.dp))
                Text(music.name, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.updateClip { copy(music = null) } }.padding(8.dp)) {
                    KIcon(R.drawable.ic_delete, Modifier.size(22.dp), description = "Remove music")
                }
            }
            KSlider(R.drawable.ic_volume_up, music.volume, 0f..1f, ::percent) { v -> vm.updateClip { copy(music = music.copy(volume = v)) } }
            KSlider(R.drawable.ic_play_arrow, music.startMs.toFloat(), 0f..music.durationMs.toFloat(), { formatMs(it.toLong()) }) { v ->
                vm.updateClip { copy(music = music.copy(startMs = v.toLong())) }
            }
        }
    }
}

@Composable
fun FontPanel(layer: TextLayer, vm: EditorViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        items(vm.fonts) { f ->
            val active = layer.font.name == f.name
            Text(
                f.name,
                fontFamily = FontFamily(f.typeface),
                fontSize = 22.sp,
                color = if (active) Ink.text else Ink.dim,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.editText { copy(font = f) } }.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

private fun percent(v: Float) = "${(v * 100).roundToInt()}%"
