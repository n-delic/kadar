package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.Anim
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.Loop
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.ui.common.TextTabs

/** Entrance, exit and loop of the selected text, sticker or shape in a video; per-letter entrances are for text only. */
@Composable
fun AnimationPanel(layer: Layer, vm: EditorViewModel) {
    fun set(animIn: Anim = layer.animIn, animOut: Anim = layer.animOut, loop: Loop = layer.animLoop) = when (layer) {
        is TextLayer -> vm.editText { copy(animIn = animIn, animOut = animOut, animLoop = loop) }
        is ImageLayer -> vm.editImage { copy(animIn = animIn, animOut = animOut, animLoop = loop) }
        is ShapeLayer -> vm.editShape { copy(animIn = animIn, animOut = animOut, animLoop = loop) }
        else -> {}
    }
    val whole = Anim.entries.filter { !it.perLetter }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelLabel(stringResource(R.string.anim_in))
        TextTabs(if (layer is TextLayer) Anim.entries else whole, layer.animIn, label = { stringResource(it.label) }) { set(animIn = it) }
        PanelLabel(stringResource(R.string.anim_out))
        TextTabs(whole, layer.animOut, label = { stringResource(it.label) }) { set(animOut = it) }
        PanelLabel(stringResource(R.string.anim_loop))
        TextTabs(Loop.entries, layer.animLoop, label = { stringResource(it.label) }) { set(loop = it) }
    }
}
