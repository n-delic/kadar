package com.ndelic.kadar.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ndelic.kadar.model.LayerBlend
import com.ndelic.kadar.ui.common.TextTabs

/** How the selected layer (a picture, text, shape or drawing) mixes with what's under it. */
@Composable
fun BlendPanel(blend: LayerBlend, set: (LayerBlend) -> Unit) {
    TextTabs(LayerBlend.entries, blend, label = { stringResource(it.label) }, onSelect = set)
}
