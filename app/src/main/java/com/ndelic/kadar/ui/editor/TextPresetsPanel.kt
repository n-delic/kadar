package com.ndelic.kadar.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.model.Font
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.model.TextPreset
import com.ndelic.kadar.model.Transform
import com.ndelic.kadar.model.drawLayer
import com.ndelic.kadar.model.styled
import com.ndelic.kadar.ui.common.Ink

/** One tile per preset, each a sample drawn in its look by the same code as the canvas; a tap restyles the selected text. */
@Composable
fun TextPresetsPanel(fonts: List<Font>, vm: EditorViewModel) {
    val samples = remember(fonts) {
        val plain = TextLayer(0, Transform(0f, 0f), "Aa", fonts.first(), 64f, android.graphics.Color.WHITE, null)
        TextPreset.entries.map { it to plain.styled(it, fonts) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(samples) { (preset, sample) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.applyPreset(preset) }.padding(4.dp),
            ) {
                Canvas(Modifier.size(72.dp, 56.dp).clip(RoundedCornerShape(10.dp)).background(Ink.raised)) {
                    val k = minOf(size.width, size.height) * 0.46f / sample.reach
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        nc.save()
                        nc.translate(size.width / 2, size.height / 2)
                        nc.scale(k, k)
                        nc.drawLayer(sample)
                        nc.restore()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(preset.label), color = Ink.dim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
