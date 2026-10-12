package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.R
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.ui.common.CircleButton
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.ui.common.blockTouches
import com.ndelic.kadar.ui.common.fullWidthPanel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Brush { Erase, Restore }

/**
 * Eraser: one finger paints, two fingers zoom and pan. Restore paints the original back
 * (only after a cut-out or crop, when [source] exists).
 */
@Composable
fun EraseScreen(layer: ImageLayer, source: Bitmap?, onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val work = remember {
        layer.bitmap.copy(Bitmap.Config.ARGB_8888, true).apply {
            // A copy of a JPEG photo says it has no alpha; erased pixels would come out black.
            setHasAlpha(true)
        }
    }
    val canvas = remember { android.graphics.Canvas(work) }
    var frame by remember { mutableIntStateOf(0) } // bumped after every stroke segment to redraw
    var mode by remember { mutableStateOf(Brush.Erase) }
    var brushDp by remember { mutableFloatStateOf(36f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var changed by remember { mutableStateOf(false) }
    val erase = remember { strokePaint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) } }
    val restore = remember(source) {
        source?.let { src ->
            strokePaint().apply {
                shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply { setTranslate(-layer.sourceX.toFloat(), -layer.sourceY.toFloat()) })
                }
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            }
        }
    }
    val density = LocalDensity.current
    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize().background(Ink.bg).blockTouches()) {
        ToolTopBar(onCancel = onCancel, onDone = { onDone(work) }, doneEnabled = changed)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val fit = min(constraints.maxWidth / work.width.toFloat(), constraints.maxHeight / work.height.toFloat()) * 0.92f
            val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
            fun toBitmap(p: Offset): Offset {
                val s = fit * zoom
                return Offset((p.x - center.x - pan.x) / s + work.width / 2f, (p.y - center.y - pan.y) / s + work.height / 2f)
            }
            Canvas(
                Modifier.fillMaxSize().pointerInput(fit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var last = toBitmap(down.position)
                        var painting = true
                        cursor = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) {
                                // A second finger turns the gesture into zoom/pan; strokes so far stay.
                                painting = false
                                cursor = null
                                val c = event.calculateCentroid()
                                val z = event.calculateZoom()
                                val newZoom = (zoom * z).coerceIn(1f, 12f)
                                pan = (pan + event.calculatePan()) + (c - center - pan) * (1 - newZoom / zoom)
                                zoom = newZoom
                            } else if (painting) {
                                val p = pressed.first().position
                                val b = toBitmap(p)
                                val paint = if (mode == Brush.Restore) restore else erase
                                if (paint != null) {
                                    paint.strokeWidth = with(density) { brushDp.dp.toPx() } / (fit * zoom)
                                    canvas.drawLine(last.x, last.y, b.x, b.y, paint)
                                    changed = true
                                    frame++
                                }
                                last = b
                                cursor = p
                            }
                            event.changes.forEach { it.consume() }
                        }
                        cursor = null
                    }
                },
            ) {
                frame // read so each stroke segment redraws
                withTransform({
                    translate(center.x + pan.x, center.y + pan.y)
                    scale(fit * zoom, fit * zoom, Offset.Zero)
                    translate(-work.width / 2f, -work.height / 2f)
                }) {
                    checker(Size(work.width.toFloat(), work.height.toFloat()), 24f)
                    drawImage(work.asImageBitmap())
                }
                cursor?.let { drawCircle(Color.White, with(density) { brushDp.dp.toPx() } / 2, it, style = Stroke(2.dp.toPx())) }
            }
        }
        Column(Modifier.fullWidthPanel()) {
            TextTabs(if (restore != null) Brush.entries else listOf(Brush.Erase), mode, label = { it.name }) { mode = it }
            KSlider(R.drawable.ic_ink_eraser, brushDp, 8f..120f, { "${it.roundToInt()}" }) { brushDp = it }
        }
    }
}

private fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

internal fun DrawScope.checker(area: Size, cell: Float) {
    drawRect(Color(0xFF3A3A3A), Offset.Zero, area)
    clipRect(0f, 0f, area.width, area.height) {
        var y = 0f
        var row = 0
        while (y < area.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < area.width) {
                drawRect(Color(0xFF4A4A4A), Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

private val CROP_RATIOS = listOf("Free" to null, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16, "16:9" to 16f / 9)

/** Crop one image layer: drag the corners or the middle; ratios lock the shape. Returns `[left, top, right, bottom)`. */
@Composable
fun CropScreen(layer: ImageLayer, onDone: (IntArray) -> Unit, onCancel: () -> Unit) {
    val bw = layer.bitmap.width.toFloat()
    val bh = layer.bitmap.height.toFloat()
    var rect by remember { mutableStateOf(Rect(0f, 0f, bw, bh)) }
    var ratio by remember { mutableStateOf<Pair<String, Float?>>(CROP_RATIOS.first()) }
    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize().background(Ink.bg).blockTouches()) {
        ToolTopBar(onCancel = onCancel, doneEnabled = true, onDone = {
            onDone(intArrayOf(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt()))
        })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            val fit = min(constraints.maxWidth / bw, constraints.maxHeight / bh)
            val origin = Offset((constraints.maxWidth - bw * fit) / 2, (constraints.maxHeight - bh * fit) / 2)
            val grab = with(LocalDensity.current) { 28.dp.toPx() }
            Canvas(
                Modifier.fillMaxSize().pointerInput(fit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun toBitmap(p: Offset) = (p - origin) / fit
                        val start = rect
                        val corners = listOf(start.topLeft, start.topRight, start.bottomLeft, start.bottomRight)
                        val corner = corners.indexOfFirst { ((it * fit + origin) - down.position).getDistance() < grab }
                        while (true) {
                            val change = awaitPointerEvent().changes.first()
                            if (!change.pressed) break
                            val p = toBitmap(change.position)
                            rect = if (corner >= 0) {
                                // The opposite corner stays put.
                                cropFrom(corners[3 - corner], p, ratio.second, bw, bh)
                            } else {
                                val d = (change.position - down.position) / fit
                                val dx = d.x.coerceIn(-start.left, bw - start.right)
                                val dy = d.y.coerceIn(-start.top, bh - start.bottom)
                                start.translate(dx, dy)
                            }
                            change.consume()
                        }
                    }
                },
            ) {
                translate(origin.x, origin.y) { checker(Size(bw * fit, bh * fit), 12.dp.toPx()) }
                drawImage(
                    layer.bitmap.asImageBitmap(),
                    dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                    dstSize = IntSize((bw * fit).roundToInt(), (bh * fit).roundToInt()),
                )
                val r = Rect(rect.topLeft * fit + origin, rect.bottomRight * fit + origin)
                val dim = Color.Black.copy(alpha = 0.6f)
                val full = Rect(origin, Offset(origin.x + bw * fit, origin.y + bh * fit))
                drawRect(dim, full.topLeft, Size(full.width, r.top - full.top))
                drawRect(dim, Offset(full.left, r.bottom), Size(full.width, full.bottom - r.bottom))
                drawRect(dim, Offset(full.left, r.top), Size(r.left - full.left, r.height))
                drawRect(dim, Offset(r.right, r.top), Size(full.right - r.right, r.height))
                drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.5.dp.toPx()))
                for (i in 1..2) {
                    val x = r.left + r.width * i / 3
                    val y = r.top + r.height * i / 3
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
                }
                val arm = 18.dp.toPx()
                val w = 4.dp.toPx()
                listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomLeft to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f)).forEach { (c, dir) ->
                    drawLine(Color.White, c, c + Offset(arm * dir.x, 0f), w)
                    drawLine(Color.White, c, c + Offset(0f, arm * dir.y), w)
                }
            }
        }
        Box(Modifier.fullWidthPanel(), contentAlignment = Alignment.Center) {
            TextTabs(CROP_RATIOS, ratio, label = { it.first }) { r ->
                ratio = r
                rect = r.second?.let { centered(it, bw, bh) } ?: Rect(0f, 0f, bw, bh)
            }
        }
    }
}

/** The rectangle spanned by fixed corner [anchor] and the finger at [p], kept inside the bitmap and at [ratio] if set. */
internal fun cropFrom(anchor: Offset, p: Offset, ratio: Float?, bw: Float, bh: Float): Rect {
    val minSize = 32f
    val sx = if (p.x >= anchor.x) 1f else -1f
    val sy = if (p.y >= anchor.y) 1f else -1f
    val roomX = if (sx > 0) bw - anchor.x else anchor.x
    val roomY = if (sy > 0) bh - anchor.y else anchor.y
    var w = abs(p.x - anchor.x).coerceIn(minSize, roomX)
    var h = abs(p.y - anchor.y).coerceIn(minSize, roomY)
    if (ratio != null) {
        // Follow whichever side the finger moved further, then shrink both if that runs out of room.
        if (w / h > ratio) h = w / ratio else w = h * ratio
        val shrink = min(1f, min(roomX / w, roomY / h))
        w *= shrink
        h *= shrink
    }
    val x2 = anchor.x + sx * w
    val y2 = anchor.y + sy * h
    return Rect(min(anchor.x, x2), min(anchor.y, y2), max(anchor.x, x2), max(anchor.y, y2))
}

private fun centered(ratio: Float, bw: Float, bh: Float): Rect {
    val w = min(bw, bh * ratio)
    val h = w / ratio
    return Rect((bw - w) / 2, (bh - h) / 2, (bw + w) / 2, (bh + h) / 2)
}

/** Cancel and apply for the full-screen tools; [middle] holds tool buttons between them. */
@Composable
internal fun ToolTopBar(onCancel: () -> Unit, onDone: () -> Unit, doneEnabled: Boolean, middle: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(R.drawable.ic_close, "Cancel", onClick = onCancel)
        Spacer(Modifier.weight(1f))
        middle()
        Spacer(Modifier.weight(1f))
        CircleButton(R.drawable.ic_check, "Apply", enabled = doneEnabled, onClick = onDone)
    }
}
