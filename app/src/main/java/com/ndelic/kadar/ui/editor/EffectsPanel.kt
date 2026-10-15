package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.DUOTONES
import com.ndelic.kadar.model.EffectKind
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.TextTabs

/** The selected picture's one effect, its strength (applied on release), and a duotone's pair of colors. */
@Composable
fun EffectsPanel(layer: ImageLayer, vm: EditorViewModel) {
    val effect = layer.effect
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextTabs(EffectKind.entries, effect.kind, label = { stringResource(it.label) }) { k -> vm.editImage { copy(effect = this.effect.copy(kind = k)) } }
        if (effect.kind == EffectKind.Duotone) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                itemsIndexed(DUOTONES) { i, (dark, light) ->
                    val active = effect.dark == dark && effect.light == light
                    val label = stringResource(R.string.effect_duotone_colors, i + 1)
                    Box(
                        Modifier
                            .size(40.dp)
                            .border(2.dp, if (active) Ink.accent else Color.Transparent, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(0f to Color(dark), 0.5f to Color(dark), 0.5f to Color(light), 1f to Color(light)))
                            .clickable { vm.editImage { copy(effect = this.effect.copy(dark = dark, light = light)) } }
                            .semantics { contentDescription = label },
                    )
                }
            }
        }
        if (effect.kind != EffectKind.None) {
            KSlider(null, effect.amount, 0f..1f, ::percent) { v -> vm.editImage { copy(effect = this.effect.copy(amount = v)) } }
        }
    }
}
