package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.PanelLabel

/** The selected text's glow: its color (or none) and how far it spreads. */
@Composable
fun GlowPanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorRow(layer.glow, allowNone = true) { vm.editText { copy(glow = it) } }
        if (layer.glow != null) {
            PanelLabel(stringResource(R.string.glow_size))
            KSlider(null, layer.glowSize, 0.1f..1f, ::percent, onDrag = { v, first -> vm.editText(record = first) { copy(glowSize = v) } }) { v -> vm.editText { copy(glowSize = v) } }
        }
    }
}
