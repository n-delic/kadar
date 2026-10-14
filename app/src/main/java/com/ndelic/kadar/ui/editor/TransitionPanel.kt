package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.video.TransitionKind
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.maxTransitionMs

/** The transition into clip [index] from the one before it: its kind, its length, and the same for every cut. */
@Composable
fun TransitionPanel(clip: VideoClip, index: Int, vm: EditorViewModel) {
    val t = clip.segments[index].transition
    val longest = minOf(MAX_MS, maxTransitionMs(clip.segments[index - 1].outputDurationMs, clip.segments[index].outputDurationMs))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextTabs(TransitionKind.entries, t.kind, label = { stringResource(it.label) }) { k -> vm.setTransition(index, t.copy(kind = k)) }
        if (t.kind != TransitionKind.None) {
            if (longest > MIN_MS) {
                // KSlider's value label is a plain function, so it fills in the format itself.
                val seconds = stringResource(R.string.transition_seconds)
                PanelLabel(stringResource(R.string.transition_length))
                KSlider(null, t.durationMs.coerceIn(MIN_MS, longest).toFloat(), MIN_MS.toFloat()..longest.toFloat(), { seconds.format(it / 1000) }) { v ->
                    vm.setTransition(index, t.copy(durationMs = v.toLong()))
                }
            } else {
                Text(stringResource(R.string.transition_too_short), color = Ink.dim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (clip.segments.size > 2) ApplyAll { vm.setTransition(index, t, all = true) }
    }
}

private const val MIN_MS = 200L
private const val MAX_MS = 1500L
