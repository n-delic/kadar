package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.ndelic.kadar.video.Segment

/**
 * Clip [s] on the stage as the composition shows it before its fit (VideoEdit's shapeEffects): [content] draws the whole
 * source frame into the modifier it gets, which crops, mirrors and turns it, centered in a box of the clip's shown size
 * at [k] screen pixels per source pixel. [layer] adds to the box's graphics layer (a look, the clips' placement).
 */
@Composable
fun ShapedClip(s: Segment, k: Float, layer: GraphicsLayerScope.() -> Unit = {}, content: @Composable (Modifier) -> Unit) {
    val (sw, sh) = s.shownSize
    val box = with(LocalDensity.current) { DpSize((sw * k).toDp(), (sh * k).toDp()) }
    val full = with(LocalDensity.current) { DpSize((s.width * k).toDp(), (s.height * k).toDp()) }
    val cx = (s.crop.left + s.crop.right) / 2
    val cy = (s.crop.top + s.crop.bottom) / 2
    Box(
        Modifier.fillMaxSize().wrapContentSize(Alignment.Center, unbounded = true).requiredSize(box).graphicsLayer {
            layer()
            clip = true
        },
    ) {
        // The crop's center on the box's center, mirrored and then turned about it, like Media3's scale before rotate.
        content(
            Modifier.fillMaxSize().wrapContentSize(Alignment.Center, unbounded = true).requiredSize(full).graphicsLayer {
                transformOrigin = TransformOrigin(cx, cy)
                translationX = (0.5f - cx) * size.width
                translationY = (0.5f - cy) * size.height
                scaleX = if (s.flipX) -1f else 1f
                scaleY = if (s.flipY) -1f else 1f
                rotationZ = 90f * s.turns
            },
        )
    }
}
