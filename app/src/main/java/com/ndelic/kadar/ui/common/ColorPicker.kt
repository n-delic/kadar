package com.ndelic.kadar.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.R

/**
 * Starts the eyedropper on the stage picture; the color touched goes to the callback. Provided by the editor around its
 * panels; null where there is no picture to pick from, which hides the eyedropper.
 */
val LocalEyedropper = compositionLocalOf<(((Int) -> Unit) -> Unit)?> { null }

/** Custom colors picked lately, newest first, shown after the custom dot. Kept until the app closes. */
internal val recentColors = mutableStateListOf<Int>()

internal fun rememberColor(color: Int) {
    if (color in SWATCHES) return
    recentColors.remove(color)
    recentColors.add(0, color)
    if (recentColors.size > MAX_RECENT) recentColors.removeAt(MAX_RECENT)
}

private const val MAX_RECENT = 6

private val HUES = List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) }

/** "RRGGBB" (a leading # is fine) as an opaque color, or null if it isn't six hex digits. */
fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || !hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
    return hex.toInt(16) or 0xFF000000.toInt()
}

fun hexOf(color: Int) = "%06X".format(color and 0xFFFFFF)

/** The dot that opens the custom color picker: a hue ring around [color] when the selection is a custom color, else a plus. */
@Composable
internal fun CustomColorDot(color: Int?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .border(2.dp, if (color != null) Ink.accent else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(HUES))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(color?.let { Color(it) } ?: Ink.raised), contentAlignment = Alignment.Center) {
            if (color == null) KIcon(R.drawable.ic_add, Modifier.size(16.dp), description = stringResource(R.string.color_custom))
        }
    }
}

/** The eyedropper's dot in a color row. */
@Composable
internal fun EyedropperDot(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).padding(4.dp).clip(CircleShape).background(Ink.raised).border(1.dp, Ink.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(R.drawable.ic_colorize, Modifier.size(18.dp), description = stringResource(R.string.eyedropper))
    }
}

/** Any color: saturation and brightness in a square, hue on a bar, and its hex code to read or type. */
@Composable
internal fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember { mutableStateOf(TextFieldValue(hexOf(initial))) }
    fun syncHex() {
        hex = TextFieldValue(hexOf(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
    }
    KDialog(stringResource(R.string.color_custom), stringResource(R.string.done), { onPick(color) }, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val shadeLabel = stringResource(R.string.color_shade)
            Canvas(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = shadeLabel }
                    .dragAt { x, y ->
                        sat = x
                        value = 1 - y
                        syncHex()
                    },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val at = Offset(sat * size.width, (1 - value) * size.height)
                drawCircle(Color.White, 9.dp.toPx(), at, style = Stroke(2.5.dp.toPx()))
            }
            val hueLabel = stringResource(R.string.color_hue)
            Canvas(
                Modifier.fillMaxWidth().height(22.dp).clip(CircleShape)
                    .semantics { contentDescription = hueLabel }
                    .dragAt { x, _ ->
                        hue = x * 360f
                        syncHex()
                    },
            ) {
                drawRect(Brush.horizontalGradient(HUES))
                drawCircle(Color.White, size.height / 2 - 1.dp.toPx(), Offset(hue / 360f * size.width, size.height / 2), style = Stroke(2.5.dp.toPx()))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(color)).border(1.dp, Ink.line, CircleShape))
                KTextField(
                    hex,
                    { field ->
                        hex = field
                        parseHexColor(field.text)?.let { c ->
                            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }
                            hue = hsv[0]
                            sat = hsv[1]
                            value = hsv[2]
                        }
                    },
                    stringResource(R.string.color_hex),
                    Modifier.weight(1f),
                    background = Ink.panel,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        }
    }
}

/** Reports where a finger touches and drags, as fractions (0 to 1) of the width and height. */
private fun Modifier.dragAt(onMove: (Float, Float) -> Unit) = pointerInput(Unit) {
    fun report(at: Offset) = onMove((at.x / size.width).coerceIn(0f, 1f), (at.y / size.height).coerceIn(0f, 1f))
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        report(down.position)
        while (true) {
            val change = awaitPointerEvent().changes.first()
            if (!change.pressed) break
            report(change.position)
            change.consume()
        }
    }
}
