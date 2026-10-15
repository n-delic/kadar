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
import com.ndelic.kadar.ui.common.PanelLabel

/** The selected text's color and, if picked, the color it fades into towards the bottom. */
@Composable
fun TextColorPanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorRow(layer.color, allowNone = false) { c -> c?.let { vm.editText { copy(color = it) } } }
        PanelLabel(stringResource(R.string.gradient))
        ColorRow(layer.gradient, allowNone = true) { vm.editText { copy(gradient = it) } }
    }
}
