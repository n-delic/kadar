package com.ndelic.kadar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/** Where a layer's center sits on the canvas, in canvas pixels. Rotation is in degrees, clockwise. */
data class Transform(val x: Float, val y: Float, val scale: Float = 1f, val rotation: Float = 0f) {

    /** True if canvas point ([px], [py]) falls inside a [width]×[height] box placed by this transform. */
    fun contains(width: Float, height: Float, px: Float, py: Float): Boolean {
        val r = Math.toRadians(-rotation.toDouble())
        val dx = px - x
        val dy = py - y
        val lx = (dx * cos(r) - dy * sin(r)) / scale
        val ly = (dx * sin(r) + dy * cos(r)) / scale
        return abs(lx) <= width / 2 && abs(ly) <= height / 2
    }

    /** Pulls the center onto the canvas center lines ([cx], [cy]) and the angle onto a multiple of 90° when they're close. */
    fun snapped(cx: Float, cy: Float, distance: Float, degrees: Float = 4f): Transform {
        val r = (rotation % 360 + 360) % 360
        val off = r - (r / 90f).roundToInt() * 90f
        return copy(
            x = if (abs(x - cx) < distance) cx else x,
            y = if (abs(y - cy) < distance) cy else y,
            rotation = if (abs(off) < degrees) rotation - off else rotation,
        )
    }

    /** Moves the center by ([dx], [dy]) measured in the layer's own unscaled, unrotated pixels. */
    fun shiftedLocal(dx: Float, dy: Float): Transform {
        val r = Math.toRadians(rotation.toDouble())
        return copy(
            x = x + ((dx * cos(r) - dy * sin(r)) * scale).toFloat(),
            y = y + ((dx * sin(r) + dy * cos(r)) * scale).toFloat(),
        )
    }
}

/** [name] is what projects store, so fonts can change without breaking saved files. */
class Font(val name: String, val typeface: Typeface)

sealed interface Layer {
    val id: Long
    val t: Transform
    val width: Float
    val height: Float

    /** When the layer shows in a video, in output time (after trim and speed). [Long.MAX_VALUE] means "to the end". Photos ignore it. */
    val startMs: Long
    val endMs: Long

    /** Hidden layers aren't drawn or exported; locked ones can't be grabbed on the canvas (only picked from the Layers list). */
    val hidden: Boolean
    val locked: Boolean

    fun visibleAt(ms: Long) = ms >= startMs && ms < endMs
    fun withTime(startMs: Long, endMs: Long): Layer
    fun withFlags(hidden: Boolean, locked: Boolean): Layer

    /** Draws the content centered on the origin; [drawLayer] applies [t] around it. */
    fun drawContent(canvas: Canvas)
    fun withTransform(t: Transform): Layer
}

/**
 * [file] is the image's name inside the project folder, so the project can be reopened.
 * After a cut-out or crop, [source] is the untouched original and ([sourceX], [sourceY]) where this bitmap's
 * top-left sits in it, so the restore brush can paint the original back.
 */
data class ImageLayer(
    override val id: Long,
    override val t: Transform,
    val bitmap: Bitmap,
    val file: String,
    val source: String? = null,
    val sourceX: Int = 0,
    val sourceY: Int = 0,
    val adjust: Adjust = Adjust(),
    override val startMs: Long = 0,
    override val endMs: Long = Long.MAX_VALUE,
    override val hidden: Boolean = false,
    override val locked: Boolean = false,
) : Layer {
    private val paint = if (adjust.isNone) bitmapPaint else Paint(bitmapPaint).apply { colorFilter = ColorMatrixColorFilter(adjust.colorMatrix()) }
    override val width get() = bitmap.width.toFloat()
    override val height get() = bitmap.height.toFloat()
    override fun drawContent(canvas: Canvas) = canvas.drawBitmap(bitmap, -width / 2, -height / 2, paint)
    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

/** Brightness, contrast and saturation, each -1..1 with 0 meaning unchanged. Used by image layers and the video. */
data class Adjust(val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f) {
    val isNone get() = brightness == 0f && contrast == 0f && saturation == 0f

    /** 4x5 color matrix for [android.graphics.ColorMatrix]: saturate, then contrast around mid-gray, then brighten. */
    fun colorMatrix(): FloatArray {
        val s = 1 + saturation
        val k = 1 + contrast
        val offset = 128 * (1 - k) + 128 * brightness
        val luma = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        val m = FloatArray(20)
        for (row in 0..2) {
            for (col in 0..2) m[row * 5 + col] = k * (luma[col] * (1 - s) + if (row == col) s else 0f)
            m[row * 5 + 4] = offset
        }
        m[18] = 1f
        return m
    }
}

enum class TextAlign { Left, Center, Right }

data class TextLayer(
    override val id: Long,
    override val t: Transform,
    val text: String,
    val font: Font,
    val size: Float,
    val color: Int,
    /** Outline color, or null for no outline. */
    val outline: Int?,
    /** Rounded box behind the text (caption style), or null for none. */
    val box: Int? = null,
    val shadow: Boolean = false,
    /** Extra space between letters, in ems. */
    val spacing: Float = 0f,
    val align: TextAlign = TextAlign.Center,
    override val startMs: Long = 0,
    override val endMs: Long = Long.MAX_VALUE,
    override val hidden: Boolean = false,
    override val locked: Boolean = false,
) : Layer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.typeface = font.typeface
        it.textSize = size
        it.letterSpacing = spacing
    }
    private val layout: StaticLayout = run {
        val w = text.lines().maxOf { paint.measureText(it) }.toInt() + 1
        val alignment = when (align) {
            TextAlign.Left -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
        }
        StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(alignment)
            .setIncludePad(true)
            .build()
    }
    private val stroke get() = if (outline == null) 0f else size * 0.16f
    private val boxPadX get() = if (box == null) 0f else size * 0.3f
    private val boxPadY get() = if (box == null) 0f else size * 0.12f

    override val width get() = layout.width + stroke + 2 * boxPadX
    override val height get() = layout.height + stroke + 2 * boxPadY

    override fun drawContent(canvas: Canvas) {
        if (box != null) {
            boxPaint.color = box
            canvas.drawRoundRect(-width / 2, -height / 2, width / 2, height / 2, size * 0.25f, size * 0.25f, boxPaint)
        }
        canvas.translate(-layout.width / 2f, -layout.height / 2f)
        // The shadow goes on the first pass only, so it sits under the outline instead of over it.
        if (shadow) paint.setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0xAA000000.toInt())
        if (outline != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = outline
            layout.draw(canvas)
            paint.clearShadowLayer()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        layout.draw(canvas)
        paint.clearShadowLayer()
    }

    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)

fun Canvas.drawLayer(layer: Layer) {
    save()
    translate(layer.t.x, layer.t.y)
    rotate(layer.t.rotation)
    scale(layer.t.scale, layer.t.scale)
    layer.drawContent(this)
    restore()
}

/**
 * Renders the layers, [scale] times the canvas size, over [under] stretched to the canvas (a video frame for thumbnails).
 * The same drawing code backs the on-screen editor, so exports match it.
 */
fun renderLayers(width: Int, height: Int, background: Int, layers: List<Layer>, scale: Float = 1f, under: Bitmap? = null): Bitmap =
    Bitmap.createBitmap((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawColor(background)
            scale(scale, scale)
            under?.let { drawBitmap(it, null, fitRect(it.width, it.height, width, height), bitmapPaint) }
            layers.forEach { if (!it.hidden) drawLayer(it) }
        }
    }

/** [w]×[h] scaled to fit inside [boxW]×[boxH], centered (letterboxed). */
fun fitRect(w: Int, h: Int, boxW: Int, boxH: Int): RectF {
    val f = minOf(boxW / w.toFloat(), boxH / h.toFloat())
    val dw = w * f
    val dh = h * f
    return RectF((boxW - dw) / 2, (boxH - dh) / 2, (boxW + dw) / 2, (boxH + dh) / 2)
}

/** Splits `0..durationMs` at every layer start and end, so the set of visible layers is constant inside each piece. */
fun segmentBounds(ranges: List<Pair<Long, Long>>, durationMs: Long): List<Pair<Long, Long>> =
    (ranges.flatMap { listOf(it.first, it.second) } + 0L + durationMs)
        .map { it.coerceIn(0, durationMs) }
        .distinct()
        .sorted()
        .zipWithNext()

/** Bounding box `[left, top, right, bottom)` of the pixels that are not (almost) fully transparent, or null if none are. */
fun opaqueBounds(pixels: IntArray, width: Int, height: Int): IntArray? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (pixels[y * width + x] ushr 24 > 8) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right < 0) null else intArrayOf(left, top, right + 1, bottom + 1)
}
