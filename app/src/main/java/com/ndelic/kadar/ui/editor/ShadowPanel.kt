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

/** The selected text's shadow: its color (or none), how soft it is and how far below the letters it falls. */
@Composable
fun ShadowPanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorRow(layer.shadow, allowNone = true) { vm.editText { copy(shadow = it) } }
        if (layer.shadow != null) {
            LabeledSlider(stringResource(R.string.shadow_blur), layer.shadowBlur, 0f..0.3f) { v -> vm.editText { copy(shadowBlur = v) } }
            LabeledSlider(stringResource(R.string.shadow_distance), layer.shadowDistance, 0f..0.3f) { v -> vm.editText { copy(shadowDistance = v) } }
        }
    }
}
