package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.R
import com.ndelic.kadar.media.scaleDown
import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.Look
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.KSlider

/**
 * One tile per [Look], previewed on [source] (the image layer or a video frame) with the current sliders, plus its
 * strength; [onApplyAll] (clips) gives every clip this look and adjustment, [onCompare] is held to see the original.
 */
@Composable
fun FiltersPanel(source: Bitmap?, adjust: Adjust, onApplyAll: (() -> Unit)? = null, onCompare: (Boolean) -> Unit, onChange: (Adjust) -> Unit) {
    val thumb = remember(source) { source?.let { scaleDown(it, 160).asImageBitmap() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Look.entries) { look ->
                val active = look == adjust.look
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { if (!active) onChange(adjust.copy(look = look, amount = 1f)) }.padding(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .border(2.dp, if (active) Ink.accent else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(4.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Ink.raised),
                    ) {
                        if (thumb != null) {
                            // The platform filter an image layer's paint uses, so the tile shows what the stage will.
                            val filter = remember(adjust, look) { ColorMatrixColorFilter(adjust.copy(look = look, amount = 1f).filterMatrix()).asComposeColorFilter() }
                            Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = filter)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(look.label), color = if (active) Ink.accent else Ink.dim, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        // On the strength slider's row, so the panel doesn't get taller.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { if (adjust.look != Look.None) KSlider(null, adjust.amount, 0f..1f, ::percent) { onChange(adjust.copy(amount = it)) } }
            onApplyAll?.let { ApplyAll(it) }
            CompareButton(onCompare)
        }
    }
}

/** [onHold] gets true while it's pressed and false on release: the stage shows the picture without its look meanwhile. */
@Composable
fun CompareButton(onHold: (Boolean) -> Unit) {
    val hold by rememberUpdatedState(onHold)
    var held by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (held) Ink.accent else Ink.raised)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    hold(true)
                    // In finally too, so a panel closed mid-press doesn't leave the stage showing the original.
                    try {
                        tryAwaitRelease()
                    } finally {
                        held = false
                        hold(false)
                    }
                })
            },
    ) {
        KIcon(R.drawable.ic_compare, Modifier.size(22.dp), if (held) Ink.onAccent else Ink.text, stringResource(R.string.hold_to_compare))
    }
}
