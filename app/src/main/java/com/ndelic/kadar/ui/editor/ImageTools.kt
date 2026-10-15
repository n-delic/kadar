package com.ndelic.kadar.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.R
import com.ndelic.kadar.model.CutShape
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.cutShapePath
import com.ndelic.kadar.model.straightenedFit
import com.ndelic.kadar.ui.common.CircleButton
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KDialog
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.ui.common.blockTouches
import com.ndelic.kadar.ui.common.fullWidthPanel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Brush(@StringRes val label: Int) { Erase(R.string.brush_erase), Restore(R.string.brush_restore) }

/**
 * Eraser: one finger paints, two fingers zoom and pan. Restore paints the original back
 * (only after a cut-out or crop, when [source] exists). Hardness below 100% gives the brush a soft edge.
 * Strokes are kept, so undo replays the rest onto the layer's bitmap and redo paints one again.
 */
@Composable
fun EraseScreen(layer: ImageLayer, source: Bitmap?, onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val work = remember { editable(layer.bitmap) }
    // The picture before the stroke in progress: each move repaints that whole stroke over it, so a soft edge doesn't
    // build up where the stroke's own segments overlap.
    val before = remember { editable(layer.bitmap) }
    val canvas = remember { android.graphics.Canvas(work) }
    val beforeCanvas = remember { android.graphics.Canvas(before) }
    var frame by remember { mutableIntStateOf(0) } // bumped after every stroke segment to redraw
    var mode by remember { mutableStateOf(Brush.Erase) }
    var brushDp by remember { mutableFloatStateOf(36f) }
    var hardness by remember { mutableFloatStateOf(1f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var done by remember { mutableStateOf(emptyList<BrushStroke>()) }
    var undone by remember { mutableStateOf(emptyList<BrushStroke>()) }
    val restoreShader = remember(source) {
        source?.let { src ->
            BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(-layer.sourceX.toFloat(), -layer.sourceY.toFloat()) })
            }
        }
    }
    fun paint(s: BrushStroke) = canvas.drawPath(s.path, s.paint(restoreShader))
    fun keep(s: BrushStroke) = s.bounds(work).let { beforeCanvas.drawBitmap(work, it, it, copyPaint) }
    fun unpaint(s: BrushStroke) = s.bounds(work).let { canvas.drawBitmap(before, it, it, copyPaint) }
    fun replay() {
        canvas.drawBitmap(layer.bitmap, 0f, 0f, copyPaint)
        done.forEach(::paint)
        beforeCanvas.drawBitmap(work, 0f, 0f, copyPaint)
        frame++
    }
    val density = LocalDensity.current
    DiscardGuard(done.isNotEmpty(), onCancel)

    Column(Modifier.fillMaxSize().background(Ink.bg).blockTouches()) {
        ToolTopBar(onCancel = onCancel, onDone = { onDone(work) }, doneEnabled = done.isNotEmpty()) {
            CircleButton(R.drawable.ic_undo, stringResource(R.string.undo_stroke), enabled = done.isNotEmpty()) {
                undone = undone + done.last()
                done = done.dropLast(1)
                replay()
            }
            Spacer(Modifier.width(12.dp))
            CircleButton(R.drawable.ic_redo, stringResource(R.string.redo_stroke), enabled = undone.isNotEmpty()) {
                val s = undone.last()
                undone = undone.dropLast(1)
                done = done + s
                paint(s)
                keep(s)
                frame++
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val fit = min(constraints.maxWidth / work.width.toFloat(), constraints.maxHeight / work.height.toFloat()) * 0.92f
            val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
            // Shown flipped like on the canvas, so the brush lands where it looks like it does.
            val fx = if (layer.t.flipX) -1f else 1f
            val fy = if (layer.t.flipY) -1f else 1f
            fun toBitmap(p: Offset): Offset {
                val s = fit * zoom
                return Offset((p.x - center.x - pan.x) / (s * fx) + work.width / 2f, (p.y - center.y - pan.y) / (s * fy) + work.height / 2f)
            }
            Canvas(
                // Strokes near the screen edge must paint, not trigger the system back gesture.
                Modifier.fillMaxSize().systemGestureExclusion().pointerInput(fit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = toBitmap(down.position)
                        val stroke = BrushStroke(
                            if (restoreShader == null) Brush.Erase else mode,
                            with(density) { brushDp.dp.toPx() } / (fit * zoom),
                            hardness,
                        ).apply { path.moveTo(start.x, start.y) }
                        var painting = true
                        var moved = false
                        cursor = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) {
                                // A second finger turns the gesture into zoom/pan; the stroke its first finger started isn't wanted.
                                if (painting && moved) {
                                    unpaint(stroke)
                                    frame++
                                }
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
                                stroke.path.lineTo(b.x, b.y)
                                unpaint(stroke)
                                paint(stroke)
                                moved = true
                                frame++
                                cursor = p
                            }
                            event.changes.forEach { it.consume() }
                        }
                        cursor = null
                        if (painting) {
                            // A tap paints a dot.
                            if (!moved) {
                                stroke.path.lineTo(start.x, start.y)
                                paint(stroke)
                                frame++
                            }
                            keep(stroke)
                            done = done + stroke
                            undone = emptyList()
                        }
                    }
                },
            ) {
                frame // read so each stroke segment redraws
                withTransform({
                    translate(center.x + pan.x, center.y + pan.y)
                    scale(fit * zoom * fx, fit * zoom * fy, Offset.Zero)
                    translate(-work.width / 2f, -work.height / 2f)
                }) {
                    checker(Size(work.width.toFloat(), work.height.toFloat()), 24f)
                    drawImage(work.asImageBitmap())
                }
                cursor?.let { drawCircle(Color.White, with(density) { brushDp.dp.toPx() } / 2, it, style = Stroke(2.dp.toPx())) }
            }
        }
        Column(Modifier.fullWidthPanel()) {
            TextTabs(if (restoreShader != null) Brush.entries else listOf(Brush.Erase), mode, label = { stringResource(it.label) }) { mode = it }
            KSlider(R.drawable.ic_ink_eraser, brushDp, 8f..120f, { "${it.roundToInt()}" }) { brushDp = it }
            KSlider(R.drawable.ic_blur_on, hardness, 0f..1f, { "${(it * 100).roundToInt()}%" }) { hardness = it }
        }
    }
}

/** A mutable copy that keeps transparency: a copy of a JPEG photo says it has no alpha, and erased pixels would come out black. */
private fun editable(bitmap: Bitmap) = bitmap.copy(Bitmap.Config.ARGB_8888, true).apply { setHasAlpha(true) }

/** One eraser or restore stroke in bitmap pixels; [hardness] 1 is a hard edge, lower fades the edge over more of the brush. */
private class BrushStroke(val mode: Brush, val width: Float, hardness: Float) {
    val path = Path()
    // Sigma of the edge's fade: at hardness 0 the fade takes the outer half of the brush, and the core (two sigmas in
    // from the edge's middle) still erases fully.
    private val sigma = (1 - hardness) * width / 8

    fun paint(restore: Shader?) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = max(1f, width - 4 * sigma)
        // Android turns a blur radius into sigma = 0.57735 * radius + 0.5.
        val radius = (sigma - 0.5f) / 0.57735f
        if (radius >= 0.5f) maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        if (mode == Brush.Restore && restore != null) {
            shader = restore
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        } else {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    /** The pixels this stroke can touch, soft edge included, inside [bitmap]. */
    fun bounds(bitmap: Bitmap): android.graphics.Rect {
        val r = RectF().also { path.computeBounds(it, false) }
        val pad = width / 2 + sigma * 2 + 2
        return android.graphics.Rect(
            (r.left - pad).toInt().coerceIn(0, bitmap.width), (r.top - pad).toInt().coerceIn(0, bitmap.height),
            (r.right + pad).toInt().coerceIn(0, bitmap.width), (r.bottom + pad).toInt().coerceIn(0, bitmap.height),
        )
    }
}

/** Copies pixels as they are, transparent ones included. Lazy, so JVM tests of this file's math don't need Android. */
private val copyPaint by lazy { Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) } }

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

// A null label is the localized "Free".
private val CROP_RATIOS = listOf(
    null to null, "1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "2:3" to 2f / 3, "9:16" to 9f / 16,
    "16:9" to 16f / 9, "4:3" to 4f / 3, "3:2" to 1.5f,
)

/**
 * Crop [bitmap] (an image layer's, or a video layer's frame), starting from [initial] or the whole of it: drag the
 * corners or the middle; ratios lock the shape. A layer flipped on the canvas ([flipX], [flipY]) shows flipped here too.
 * With [turnable], the picture also turns in quarter turns and straightens by up to 45° either way. [onDone] gets
 * `[left, top, right, bottom)`, the quarter turns and the straightening angle. Unturned, the rectangle is in [bitmap]'s
 * pixels (a flipped view mirrored back); turned, it's in the frame as shown (the flipped picture's box after the
 * quarter turns, same center). With [shapes], the crop can be cut to a [CutShape] filling the rectangle, as shown.
 */
@Composable
fun CropScreen(
    bitmap: Bitmap, onDone: (IntArray, Int, Float, CutShape?) -> Unit, onCancel: () -> Unit, initial: Rect? = null,
    flipX: Boolean = false, flipY: Boolean = false, turnable: Boolean = false, shapes: Boolean = false,
) {
    var turns by remember { mutableIntStateOf(0) }
    var angle by remember { mutableFloatStateOf(0f) }
    val bw = (if (turns % 2 == 0) bitmap.width else bitmap.height).toFloat()
    val bh = (if (turns % 2 == 0) bitmap.height else bitmap.width).toFloat()
    var rect by remember { mutableStateOf(initial ?: Rect(0f, 0f, bw, bh)) }
    var ratio by remember { mutableStateOf<Pair<String?, Float?>>(CROP_RATIOS.first()) }
    var shape by remember { mutableStateOf<CutShape?>(null) }
    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize().background(Ink.bg).blockTouches()) {
        ToolTopBar(onCancel = onCancel, doneEnabled = true, onDone = {
            val r = if (turns != 0 || angle != 0f) rect else {
                // [rect] is in the picture as shown; mirror it back onto the bitmap.
                Rect(if (flipX) bw - rect.right else rect.left, if (flipY) bh - rect.bottom else rect.top, if (flipX) bw - rect.left else rect.right, if (flipY) bh - rect.top else rect.bottom)
            }
            onDone(intArrayOf(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()), turns, angle, shape)
        })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            val fit = min(constraints.maxWidth / bw, constraints.maxHeight / bh)
            val origin = Offset((constraints.maxWidth - bw * fit) / 2, (constraints.maxHeight - bh * fit) / 2)
            val grab = with(LocalDensity.current) { 28.dp.toPx() }
            Canvas(
                Modifier.fillMaxSize().pointerInput(fit, bw, bh) {
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
                val full = Rect(origin, Offset(origin.x + bw * fit, origin.y + bh * fit))
                translate(origin.x, origin.y) { checker(Size(bw * fit, bh * fit), 12.dp.toPx()) }
                clipRect(full.left, full.top, full.right, full.bottom) {
                    withTransform({
                        translate(full.center.x, full.center.y)
                        rotate(turns * 90 + angle, Offset.Zero)
                        scale(if (flipX) -fit else fit, if (flipY) -fit else fit, Offset.Zero)
                        translate(-bitmap.width / 2f, -bitmap.height / 2f)
                    }) { drawImage(bitmap.asImageBitmap()) }
                }
                val r = Rect(rect.topLeft * fit + origin, rect.bottomRight * fit + origin)
                val dim = Color.Black.copy(alpha = 0.6f)
                drawRect(dim, full.topLeft, Size(full.width, r.top - full.top))
                drawRect(dim, Offset(full.left, r.bottom), Size(full.width, full.bottom - r.bottom))
                drawRect(dim, Offset(full.left, r.top), Size(r.left - full.left, r.height))
                drawRect(dim, Offset(r.right, r.top), Size(full.right - r.right, r.height))
                shape?.let {
                    val outline = cutShapePath(it, r.width, r.height).apply { offset(r.center.x, r.center.y) }.asComposePath()
                    clipPath(outline, ClipOp.Difference) { drawRect(dim, r.topLeft, r.size) }
                    drawPath(outline, Color.White, style = Stroke(2.dp.toPx()))
                }
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
        Column(Modifier.fullWidthPanel(), horizontalAlignment = Alignment.CenterHorizontally) {
            TextTabs(CROP_RATIOS, ratio, label = { it.first ?: stringResource(R.string.crop_free) }) { r ->
                ratio = r
                rect = centeredFit(bw, bh, angle, r.second)
            }
            if (shapes) {
                TextTabs(listOf(null) + CutShape.entries, shape, label = { stringResource(it?.label ?: R.string.shape_rect) }) { s ->
                    shape = s
                    // Shapes start even (a true circle); another ratio stretches them.
                    if (s != null && ratio.second == null) {
                        ratio = CROP_RATIOS[1]
                        rect = centeredFit(bw, bh, angle, 1f)
                    }
                }
            }
            if (turnable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AngleDial(angle, Modifier.weight(1f)) { a ->
                        angle = a
                        rect = centeredFit(bw, bh, a, ratio.second)
                    }
                    Spacer(Modifier.width(12.dp))
                    CircleButton(R.drawable.ic_rotate_right, stringResource(R.string.rotate_90)) {
                        turns = (turns + 1) % 4
                        // The frame swaps width and height.
                        rect = centeredFit(bh, bw, angle, ratio.second)
                    }
                }
            }
        }
    }
}

/** The largest rectangle of [ratio] (the frame's own when null) centered in the [fw]×[fh] frame, inside the picture turned by [angle]. */
private fun centeredFit(fw: Float, fh: Float, angle: Float, ratio: Float?): Rect {
    val (w, h) = straightenedFit(fw, fh, angle, ratio ?: (fw / fh))
    return Rect((fw - w) / 2, (fh - h) / 2, (fw + w) / 2, (fh + h) / 2)
}

/** A ruler of degrees from -45 to 45 that slides under a fixed mark; drag it sideways to straighten. */
@Composable
private fun AngleDial(angle: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(angle)
    val label = stringResource(R.string.straighten)
    val perDegree = with(LocalDensity.current) { 8.dp.toPx() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.degrees, angle.roundToInt()), color = if (angle == 0f) Ink.dim else Ink.accent, fontSize = 12.sp)
        Canvas(
            Modifier.fillMaxWidth().height(32.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(current, -MAX_STRAIGHTEN..MAX_STRAIGHTEN)
                    setProgress { onChange(snapAngle(it)); true }
                }
                .pointerInput(perDegree) {
                    // Unsnapped during the drag, so a slow drag can leave zero.
                    var raw = 0f
                    detectHorizontalDragGestures(onDragStart = { raw = current }) { _, dx ->
                        raw = (raw - dx / perDegree).coerceIn(-MAX_STRAIGHTEN, MAX_STRAIGHTEN)
                        onChange(snapAngle(raw))
                    }
                },
        ) {
            val mid = size.width / 2
            for (d in -MAX_STRAIGHTEN.toInt()..MAX_STRAIGHTEN.toInt()) {
                val x = mid + (d - angle) * perDegree
                if (x < 0 || x > size.width) continue
                val long = d % 5 == 0
                val h = if (long) size.height * 0.6f else size.height * 0.35f
                drawLine(if (d == 0) Ink.text else Ink.line, Offset(x, size.height - h), Offset(x, size.height), if (long) 2.dp.toPx() else 1.dp.toPx())
            }
            drawLine(Ink.accent, Offset(mid, 0f), Offset(mid, size.height), 2.5.dp.toPx())
        }
    }
}

/** Kept inside the dial, and exactly level near zero so the picture can go back to straight. */
private fun snapAngle(a: Float) = a.coerceIn(-MAX_STRAIGHTEN, MAX_STRAIGHTEN).let { if (abs(it) < 0.4f) 0f else it }

private const val MAX_STRAIGHTEN = 45f

/** The rectangle spanned by fixed corner [anchor] and the finger at [p], kept inside the bitmap and at [ratio] if set. */
internal fun cropFrom(anchor: Offset, p: Offset, ratio: Float?, bw: Float, bh: Float): Rect {
    val minSize = 32f
    val sx = if (p.x >= anchor.x) 1f else -1f
    val sy = if (p.y >= anchor.y) 1f else -1f
    val roomX = if (sx > 0) bw - anchor.x else anchor.x
    val roomY = if (sy > 0) bh - anchor.y else anchor.y
    // Past the image edge the room can be smaller than minSize; coerceIn would throw.
    var w = abs(p.x - anchor.x).coerceIn(min(minSize, roomX), roomX)
    var h = abs(p.y - anchor.y).coerceIn(min(minSize, roomY), roomY)
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

/** Back asks before throwing away unsaved work in a full-screen tool; with nothing changed it just cancels. */
@Composable
internal fun DiscardGuard(changed: Boolean, onCancel: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    BackHandler { if (changed) asking = true else onCancel() }
    if (asking) {
        KDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_text),
            confirm = stringResource(R.string.discard_confirm),
            onConfirm = {
                asking = false
                onCancel()
            },
            onDismiss = { asking = false },
        )
    }
}

/** Cancel and apply for the full-screen tools; [middle] holds tool buttons between them. */
@Composable
internal fun ToolTopBar(onCancel: () -> Unit, onDone: () -> Unit, doneEnabled: Boolean, middle: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(R.drawable.ic_close, stringResource(R.string.cancel), onClick = onCancel)
        Spacer(Modifier.weight(1f))
        middle()
        Spacer(Modifier.weight(1f))
        CircleButton(R.drawable.ic_check, stringResource(R.string.apply), enabled = doneEnabled, onClick = onDone)
    }
}
