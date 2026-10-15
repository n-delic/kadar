package com.ndelic.kadar.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.LayerBlend
import com.ndelic.kadar.ui.common.TextTabs

/** How the selected image mixes with what's under it. */
@Composable
fun BlendPanel(layer: ImageLayer, vm: EditorViewModel) {
    TextTabs(LayerBlend.entries, layer.blend, label = { stringResource(it.label) }) { b -> vm.editImage { copy(blend = b) } }
}
