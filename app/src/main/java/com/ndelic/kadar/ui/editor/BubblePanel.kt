package com.ndelic.kadar.ui.editor

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.Bubble
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.ui.common.TextTabs

/** The box or bubble behind the selected text: its shape (none, caption box, speech, thought, shout, pill, double outline), fill, its opacity and outline. */
@Composable
fun BubblePanel(layer: TextLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val current = layer.bubble.takeIf { layer.box != null }
        TextTabs(listOf<Bubble?>(null) + Bubble.entries, current, label = { stringResource(it?.label ?: R.string.bubble_none) }) { b ->
            vm.editText {
                when {
                    b == null -> copy(box = null)
                    // A new bubble starts white with a black outline; a caption box keeps the colors it had.
                    b != Bubble.Box && current in listOf(null, Bubble.Box) -> copy(bubble = b, box = Color.WHITE, boxOutline = Color.BLACK)
                    else -> copy(bubble = b, box = box ?: Color.BLACK)
                }
            }
        }
        if (layer.box != null) {
            PanelLabel(stringResource(R.string.fill))
            ColorRow(layer.box, allowNone = false) { c -> c?.let { vm.editText { copy(box = it) } } }
            LabeledSlider(stringResource(R.string.opacity), layer.boxOpacity, 0f..1f, ::percent) { v -> vm.editText { copy(boxOpacity = v) } }
            PanelLabel(stringResource(R.string.outline))
            ColorRow(layer.boxOutline, allowNone = true) { vm.editText { copy(boxOutline = it) } }
        }
    }
}
