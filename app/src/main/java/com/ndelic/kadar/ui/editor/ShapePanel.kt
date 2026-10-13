package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.ShapeKind
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.shapeSize
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.PanelLabel
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

val ShapeKind.icon
    get() = when (this) {
        ShapeKind.Rect -> R.drawable.ic_rectangle
        ShapeKind.RoundRect -> R.drawable.ic_crop_square
        ShapeKind.Ellipse -> R.drawable.ic_circle
        ShapeKind.Arrow -> R.drawable.ic_arrow_right_alt
        ShapeKind.Star -> R.drawable.ic_star
        ShapeKind.Line -> R.drawable.ic_horizontal_rule
    }

/** The shape kinds: adds one, or with a [shape] selected changes its kind and offers its stretch and opacity. */
@Composable
fun ShapePanel(shape: ShapeLayer?, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(ShapeKind.entries) { k ->
                val color = if (k == shape?.kind) Ink.accent else Ink.text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(68.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        // A new kind starts from its own proportions, so a line turned into a box isn't a sliver.
                        if (shape == null) vm.addShape(k) else vm.editShape { copy(kind = k, h = shapeSize(k, w).second) }
                    }.padding(vertical = 6.dp),
                ) {
                    KIcon(k.icon, Modifier.size(28.dp), color)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(k.label), color = if (k == shape?.kind) Ink.accent else Ink.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (shape != null) {
            if (shape.kind != ShapeKind.Line) {
                PanelLabel(stringResource(R.string.stretch))
                // Width to height, from 1:4 to 4:1 on a log scale so square sits in the middle.
                KSlider(null, log2(shape.w / shape.h), -2f..2f, { ratioLabel(2f.pow(it)) }) { v -> vm.editShape { copy(h = w / 2f.pow(v)) } }
            }
            PanelLabel(stringResource(R.string.opacity))
            KSlider(null, shape.opacity, 0f..1f, ::percent) { v -> vm.editShape { copy(opacity = v) } }
        }
    }
}

private fun ratioLabel(r: Float) = if (r >= 1) "${(r * 10).roundToInt() / 10f}:1" else "1:${(10 / r).roundToInt() / 10f}"

@Composable
fun ShapeFillPanel(shape: ShapeLayer, vm: EditorViewModel) {
    ColorRow(shape.fill, allowNone = true) { vm.editShape { copy(fill = it) } }
}

@Composable
fun ShapeOutlinePanel(shape: ShapeLayer, vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorRow(shape.outline, allowNone = shape.kind != ShapeKind.Line) { vm.editShape { copy(outline = it) } }
        PanelLabel(stringResource(R.string.outline_width))
        val max = maxOf(shape.w, shape.h) * 0.15f
        KSlider(null, shape.stroke.coerceAtMost(max), 1f..max, { "${it.roundToInt()}" }) { v -> vm.editShape { copy(stroke = v) } }
    }
}
