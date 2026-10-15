package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.KEY_GREEN
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.KToggle

/** The selected video layer's green screen: on or off, the color it removes, how much around it goes and how soft the edge is. */
@Composable
fun ChromaKeyPanel(layer: VideoLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KToggle(stringResource(R.string.key_remove_color), stringResource(R.string.key_hint), layer.key != null) { on ->
            vm.editVideo { copy(key = if (on) KEY_GREEN else null) }
        }
        layer.key?.let { key ->
            ColorRow(key, allowNone = false) { c -> c?.let { vm.editVideo { copy(key = it) } } }
            LabeledSlider(stringResource(R.string.key_strength), layer.keyStrength, 0f..1f) { v -> vm.editVideo { copy(keyStrength = v) } }
            LabeledSlider(stringResource(R.string.key_softness), layer.keySoftness, 0f..1f) { v -> vm.editVideo { copy(keySoftness = v) } }
        }
    }
}
