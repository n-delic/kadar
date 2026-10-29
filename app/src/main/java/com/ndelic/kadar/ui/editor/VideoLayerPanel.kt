package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.PanelLabel

/** The selected video layer's opacity, sound and where in its clip it starts. */
@Composable
fun VideoLayerPanel(layer: VideoLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelLabel(stringResource(R.string.opacity))
        KSlider(null, layer.opacity, 0f..1f, ::percent, onDrag = { v, first -> vm.editVideo(record = first) { copy(opacity = v) } }) { v -> vm.editVideo { copy(opacity = v) } }
        PanelLabel(stringResource(R.string.sound))
        KSlider(R.drawable.ic_volume_up, layer.volume, 0f..1f, ::percent, onDrag = { v, first -> vm.editVideo(record = first) { copy(volume = v) } }) { v -> vm.editVideo { copy(volume = v) } }
        // At least a second of the clip stays playable.
        val lastStart = layer.durationMs - 1000
        if (lastStart > 0) {
            PanelLabel(stringResource(R.string.starts_at))
            KSlider(R.drawable.ic_play_arrow, layer.clipStartMs.toFloat(), 0f..lastStart.toFloat(), { formatMs(it.toLong()) }) { v ->
                vm.editVideo { withClipStart(v.toLong()) }
            }
        }
    }
}
