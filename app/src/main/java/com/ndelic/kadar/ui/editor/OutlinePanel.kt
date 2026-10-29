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

/** The selected text's outline: its color (or none) and how thick it is. */
@Composable
fun OutlinePanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorRow(layer.outline, allowNone = true) { vm.editText { copy(outline = it) } }
        if (layer.outline != null) {
            LabeledSlider(stringResource(R.string.outline_width), layer.outlineWidth, 0.02f..0.4f, onDrag = { v, first -> vm.editText(record = first) { copy(outlineWidth = v) } }) { v -> vm.editText { copy(outlineWidth = v) } }
        }
    }
}
