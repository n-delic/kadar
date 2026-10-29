package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.CensorKind
import com.ndelic.kadar.model.CensorLayer
import com.ndelic.kadar.model.CensorShape
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.TextTabs

/** The selected blur area's kind, shape, strength and opacity (applied on release). */
@Composable
fun CensorPanel(layer: CensorLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextTabs(CensorKind.entries, layer.kind, label = { stringResource(it.label) }) { k -> vm.editCensor { copy(kind = k) } }
        TextTabs(CensorShape.entries, layer.shape, label = { stringResource(it.label) }) { s -> vm.editCensor { copy(shape = s) } }
        KSlider(R.drawable.ic_blur_on, layer.strength, 0f..1f, ::percent, onDrag = { v, first -> vm.editCensor(record = first) { copy(strength = v) } }) { v -> vm.editCensor { copy(strength = v) } }
        KSlider(R.drawable.ic_opacity, layer.opacity, 0f..1f, ::percent, onDrag = { v, first -> vm.editCensor(record = first) { copy(opacity = v) } }) { v -> vm.editCensor { copy(opacity = v) } }
    }
}
