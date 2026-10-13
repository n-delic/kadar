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
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.ui.common.TextTabs

/** Entrance and exit of the selected text, sticker or shape in a video. */
@Composable
fun AnimationPanel(layer: Layer, vm: EditorViewModel) {
    fun set(animIn: Anim, animOut: Anim) = when (layer) {
        is TextLayer -> vm.editText { copy(animIn = animIn, animOut = animOut) }
        is ImageLayer -> vm.editImage { copy(animIn = animIn, animOut = animOut) }
        is ShapeLayer -> vm.editShape { copy(animIn = animIn, animOut = animOut) }
        else -> {}
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelLabel(stringResource(R.string.anim_in))
        TextTabs(Anim.entries, layer.animIn, label = { stringResource(it.label) }) { set(it, layer.animOut) }
        PanelLabel(stringResource(R.string.anim_out))
        TextTabs(Anim.entries, layer.animOut, label = { stringResource(it.label) }) { set(layer.animIn, it) }
    }
}
