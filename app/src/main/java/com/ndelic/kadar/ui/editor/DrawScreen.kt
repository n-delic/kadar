package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withClip
import com.ndelic.kadar.R
import com.ndelic.kadar.model.DrawLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.PenStroke
import com.ndelic.kadar.model.Project
import com.ndelic.kadar.model.Transform
import com.ndelic.kadar.model.canvasStrokes
import com.ndelic.kadar.model.drawLayer
import com.ndelic.kadar.model.erased
import com.ndelic.kadar.model.fitRect
import com.ndelic.kadar.ui.common.CircleButton
import com.ndelic.kadar.ui.common.ColorRow
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.ui.common.blockTouches
import com.ndelic.kadar.ui.common.fullWidthPanel
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Pen(@StringRes val label: Int) { Marker(R.string.pen_marker), Highlighter(R.string.pen_highlighter), Eraser(R.string.pen_eraser) }

/**
 * Freehand drawing over the canvas: one finger draws or erases, two fingers zoom and pan. [layers] and [under] (the
 * video frame at the playhead) are shown as a guide; drawing on [target] edits its strokes too. [onDone] gets all the
 * strokes in canvas pixels, the target's included.
 */
@Composable
fun DrawScreen(project: Project, layers: List<Layer>, under: Bitmap?, target: DrawLayer?, onDone: (List<PenStroke>) -> Unit, onCancel: () -> Unit) {
    val w = project.width.toFloat()
    val h = project.height.toFloat()
    var strokes by remember { mutableStateOf(target?.canvasStrokes().orEmpty()) }
    // Whole stroke lists before each change (undo) and after each undone one (redo); strokes are never changed, so this is cheap.
    var past by remember { mutableStateOf(emptyList<List<PenStroke>>()) }
    var future by remember { mutableStateOf(emptyList<List<PenStroke>>()) }
    var current by remember { mutableStateOf<PenStroke?>(null) }
    var pen by remember { mutableStateOf(Pen.Marker) }
    var color by remember { mutableIntStateOf(0xFFFF1744.toInt()) }
    var sizeDp by remember { mutableFloatStateOf(6f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    // Drawn strokes are a layer at the canvas origin, so they go through the same drawLayer as the result.
    val drawn = remember(strokes) { DrawLayer(0, Transform(0f, 0f), strokes) }
    val density = LocalDensity.current
    val minStep = with(density) { 1.5.dp.toPx() }
    val changed = past.isNotEmpty()
    DiscardGuard(changed, onCancel)

    Column(Modifier.fillMaxSize().background(Ink.bg).blockTouches()) {
        ToolTopBar(onCancel = onCancel, onDone = { onDone(strokes) }, doneEnabled = changed) {
            CircleButton(R.drawable.ic_undo, stringResource(R.string.undo_stroke), enabled = past.isNotEmpty()) {
                future = future + listOf(strokes)
                strokes = past.last()
                past = past.dropLast(1)
            }
            Spacer(Modifier.width(12.dp))
            CircleButton(R.drawable.ic_redo, stringResource(R.string.redo_stroke), enabled = future.isNotEmpty()) {
                past = past + listOf(strokes)
                strokes = future.last()
                future = future.dropLast(1)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val fit = min(constraints.maxWidth / w, constraints.maxHeight / h) * 0.92f
            val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
            fun toCanvas(p: Offset): Offset {
                val s = fit * zoom
                return Offset((p.x - center.x - pan.x) / s + w / 2, (p.y - center.y - pan.y) / s + h / 2)
            }
            Canvas(
                // Strokes near the screen edge must draw, not trigger the system back gesture.
                Modifier.fillMaxSize().systemGestureExclusion().pointerInput(fit, center) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val before = strokes
                        // Width follows the screen, so zooming in draws finer lines; the highlighter is wider and see-through.
                        val eraser = pen == Pen.Eraser
                        val highlighter = pen == Pen.Highlighter
                        val width = with(density) { sizeDp.dp.toPx() } / (fit * zoom) * if (highlighter || eraser) 3 else 1
                        val alpha = (opacity * if (highlighter) 128 else 255).roundToInt()
                        val argb = (color and 0x00FFFFFF) or (alpha shl 24)
                        val points = ArrayList<Float>()
                        var last = down.position
                        fun add(p: Offset) {
                            val c = toCanvas(p)
                            if (eraser) {
                                val from = if (points.isEmpty()) c else Offset(points[points.size - 2], points[points.size - 1])
                                strokes = erased(strokes, from.x, from.y, c.x, c.y, width / 2)
                                cursor = p
                            }
                            points += c.x
                            points += c.y
                            if (!eraser) current = PenStroke(argb, width, points.toFloatArray())
                        }
                        add(down.position)
                        var drawing = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) {
                                // A second finger means zoom/pan; the line (or erasing) its first finger started isn't wanted.
                                drawing = false
                                current = null
                                cursor = null
                                strokes = before
                                val c = event.calculateCentroid()
                                val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, 12f)
                                pan = (pan + event.calculatePan()) + (c - center - pan) * (1 - newZoom / zoom)
                                zoom = newZoom
                            } else if (drawing) {
                                val p = pressed.first().position
                                if ((p - last).getDistance() >= minStep) {
                                    add(p)
                                    last = p
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                        current?.let { strokes = strokes + it }
                        current = null
                        cursor = null
                        if (strokes != before) {
                            past = past + listOf(before)
                            future = emptyList()
                        }
                    }
                },
            ) {
                withTransform({
                    translate(center.x + pan.x, center.y + pan.y)
                    scale(fit * zoom, fit * zoom, Offset.Zero)
                    translate(-w / 2, -h / 2)
                }) {
                    if (project.background == android.graphics.Color.TRANSPARENT) checker(Size(w, h), 24f) else drawRect(Color(project.background), size = Size(w, h))
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.withClip(0f, 0f, w, h) {
                            under?.let { drawBitmap(it, null, fitRect(it.width, it.height, project.width, project.height), framePaint) }
                            layers.forEach { drawLayer(it) }
                            drawLayer(drawn)
                            current?.let { drawLayer(DrawLayer(0, Transform(0f, 0f), listOf(it))) }
                        }
                    }
                }
                cursor?.let { drawCircle(Color.White, with(density) { sizeDp.dp.toPx() } * 1.5f, it, style = Stroke(2.dp.toPx())) }
            }
        }
        Column(Modifier.fullWidthPanel(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextTabs(Pen.entries, pen, label = { stringResource(it.label) }) { pen = it }
            ColorRow(color, allowNone = false) { c -> c?.let { color = it } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { KSlider(R.drawable.ic_draw, sizeDp, 2f..40f, { "${it.roundToInt()}" }) { sizeDp = it } }
                Box(Modifier.weight(1f)) { KSlider(R.drawable.ic_opacity, opacity, 0.1f..1f, { "${(it * 100).roundToInt()}%" }) { opacity = it } }
            }
        }
    }
}
private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
