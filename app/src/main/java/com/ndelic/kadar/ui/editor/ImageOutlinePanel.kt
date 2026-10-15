package com.ndelic.kadar.ui.editor

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.OutlineStyle
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.TextTabs

/** The selected picture's outline around its shape: solid, glow or shadow, its color (or none) and width. */
@Composable
fun ImageOutlinePanel(layer: ImageLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextTabs(OutlineStyle.entries, layer.outlineStyle.takeIf { layer.outline != null }, label = { stringResource(it.label) }) { s ->
            // Without an outline yet, a style starts white like a sticker cut, or black for a shadow.
            vm.editImage { copy(outlineStyle = s, outline = outline ?: if (s == OutlineStyle.Shadow) Color.BLACK else Color.WHITE) }
        }
        ColorRow(layer.outline, allowNone = true) { vm.editImage { copy(outline = it) } }
        if (layer.outline != null) {
            LabeledSlider(stringResource(R.string.outline_width), layer.outlineWidth, 0.01f..0.1f) { v -> vm.editImage { copy(outlineWidth = v) } }
        }
    }
}
