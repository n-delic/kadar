package com.ndelic.kadar

import android.graphics.Bitmap
import android.graphics.Canvas
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

    fun visibleAt(ms: Long) = ms >= startMs && ms < endMs
    fun withTime(startMs: Long, endMs: Long): Layer

    /** Draws the content centered on the origin; [drawLayer] applies [t] around it. */
    fun drawContent(canvas: Canvas)
    fun withTransform(t: Transform): Layer
}

/** [file] is the image's name inside the project folder, so the project can be reopened. */
data class ImageLayer(
    override val id: Long,
    override val t: Transform,
    val bitmap: Bitmap,
    val file: String,
    override val startMs: Long = 0,
    override val endMs: Long = Long.MAX_VALUE,
) : Layer {
    override val width get() = bitmap.width.toFloat()
    override val height get() = bitmap.height.toFloat()
    override fun drawContent(canvas: Canvas) = canvas.drawBitmap(bitmap, -width / 2, -height / 2, bitmapPaint)
    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
}

data class TextLayer(
    override val id: Long,
    override val t: Transform,
    val text: String,
    val font: Font,
    val size: Float,
    val color: Int,
    /** Outline color, or null for no outline. */
    val outline: Int?,
    override val startMs: Long = 0,
    override val endMs: Long = Long.MAX_VALUE,
) : Layer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.typeface = font.typeface
        it.textSize = size
    }
    private val layout: StaticLayout = run {
        val w = text.lines().maxOf { paint.measureText(it) }.toInt() + 1
        StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()
    }
    private val stroke get() = if (outline == null) 0f else size * 0.16f

    override val width get() = layout.width + stroke
    override val height get() = layout.height + stroke

    override fun drawContent(canvas: Canvas) {
        canvas.translate(-layout.width / 2f, -layout.height / 2f)
        if (outline != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = outline
            layout.draw(canvas)
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        layout.draw(canvas)
    }

    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
}

private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

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
            under?.let { drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint) }
            layers.forEach { drawLayer(it) }
        }
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
