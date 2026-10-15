package com.ndelic.kadar.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.Anim
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.Loop
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.video.ClipAnimKind
import com.ndelic.kadar.video.ClipAnimation
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.maxClipAnimMs

private enum class Part(@StringRes val label: Int) { In(R.string.anim_in), Out(R.string.anim_out), Loop(R.string.anim_loop) }

/**
 * Entrance, exit and loop of the selected text, sticker or shape in a video, one part at a time so the panel stays short;
 * entrances and exits get a length. Per-letter entrances are for text only.
 */
@Composable
fun AnimationPanel(layer: Layer, vm: EditorViewModel) {
    fun set(animIn: Anim = layer.animIn, animOut: Anim = layer.animOut, loop: Loop = layer.animLoop, inMs: Long? = layer.animInMs, outMs: Long? = layer.animOutMs) = when (layer) {
        is TextLayer -> vm.editText { copy(animIn = animIn, animOut = animOut, animLoop = loop, animInMs = inMs, animOutMs = outMs) }
        is ImageLayer -> vm.editImage { copy(animIn = animIn, animOut = animOut, animLoop = loop, animInMs = inMs, animOutMs = outMs) }
        is ShapeLayer -> vm.editShape { copy(animIn = animIn, animOut = animOut, animLoop = loop, animInMs = inMs, animOutMs = outMs) }
        else -> {}
    }
    var part by rememberSaveable { mutableStateOf(Part.In) }
    val whole = Anim.entries.filter { !it.perLetter }
    // KSlider's value label is a plain function, so it fills in the format itself.
    val seconds = stringResource(R.string.transition_seconds)
    @Composable
    fun Length(anim: Anim, ms: Long?, onCommit: (Long) -> Unit) {
        if (anim != Anim.None) KSlider(null, (ms ?: anim.ms).toFloat(), MIN_MS.toFloat()..MAX_MS.toFloat(), { seconds.format(it / 1000) }) { onCommit(it.toLong()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PartTabs(Part.entries, part) { part = it }
        when (part) {
            Part.In -> {
                TextTabs(if (layer is TextLayer) Anim.entries else whole, layer.animIn, label = { stringResource(it.label) }) { set(animIn = it) }
                Length(layer.animIn, layer.animInMs) { set(inMs = it) }
            }
            Part.Out -> {
                TextTabs(whole, layer.animOut, label = { stringResource(it.label) }) { set(animOut = it) }
                Length(layer.animOut, layer.animOutMs) { set(outMs = it) }
            }
            Part.Loop -> TextTabs(Loop.entries, layer.animLoop, label = { stringResource(it.label) }) { set(loop = it) }
        }
    }
}

/**
 * Entrance and exit of clip [index] of the main track, one at a time, each with a length up to half the clip as it plays.
 * A transition at that cut plays instead, which the panel says.
 */
@Composable
fun ClipAnimationPanel(clip: VideoClip, index: Int, vm: EditorViewModel) {
    val s = clip.segments[index]
    var part by rememberSaveable { mutableStateOf(Part.In) }
    val exit = part == Part.Out
    val a = if (exit) s.animOut else s.animIn
    fun set(new: ClipAnimation) = vm.updateSegment(index) { if (exit) copy(animOut = new) else copy(animIn = new) }
    val longest = minOf(MAX_MS, maxClipAnimMs(clip.played[index].outputDurationMs))
    val cut = if (exit) index + 1 else index
    val seconds = stringResource(R.string.transition_seconds)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PartTabs(listOf(Part.In, Part.Out), part) { part = it }
        TextTabs(ClipAnimKind.entries, a.kind, label = { stringResource(it.label) }) { set(a.copy(kind = it)) }
        if (a.kind != ClipAnimKind.None) {
            if (clip.transitionSpans.any { it.clip == cut }) {
                Text(stringResource(R.string.clip_anim_transition), color = Ink.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
            } else if (longest > MIN_MS) {
                KSlider(null, a.durationMs.coerceIn(MIN_MS, longest).toFloat(), MIN_MS.toFloat()..longest.toFloat(), { seconds.format(it / 1000) }) { set(a.copy(durationMs = it.toLong())) }
            }
        }
    }
}

@Composable
private fun PartTabs(parts: List<Part>, part: Part, onPick: (Part) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { p ->
            Text(
                stringResource(p.label).uppercase(),
                color = if (p == part) Ink.accent else Ink.dim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onPick(p) }.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

private const val MIN_MS = 100L
private const val MAX_MS = 3000L
