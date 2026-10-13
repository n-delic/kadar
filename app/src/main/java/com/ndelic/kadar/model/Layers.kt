package com.ndelic.kadar.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.StringRes
import com.ndelic.kadar.R
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.composition
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where a layer's center sits on the canvas, in canvas pixels. Rotation is in degrees, clockwise. */
data class Transform(val x: Float, val y: Float, val scale: Float = 1f, val rotation: Float = 0f) {

    /** True if canvas point ([px], [py]) falls inside a [width]×[height] box placed by this transform. */
    fun contains(width: Float, height: Float, px: Float, py: Float): Boolean {
        val (lx, ly) = toLocal(px, py)
        return abs(lx) <= width / 2 && abs(ly) <= height / 2
    }

    /** Canvas point ([px], [py]) in the layer's own unscaled, unrotated pixels from its center; the inverse of [shiftedLocal]. */
    fun toLocal(px: Float, py: Float): Pair<Float, Float> {
        val r = Math.toRadians(-rotation.toDouble())
        val dx = px - x
        val dy = py - y
        return ((dx * cos(r) - dy * sin(r)) / scale).toFloat() to ((dx * sin(r) + dy * cos(r)) / scale).toFloat()
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

    /** Entrance and exit in a video (text, stickers and shapes); photos ignore them. */
    val animIn: Anim get() = Anim.None
    val animOut: Anim get() = Anim.None
    val animLoop: Loop get() = Loop.None

    /** How far from its center it draws, unscaled: its box's half diagonal, or more for a bubble's tail. */
    val reach: Float get() = hypot(width, height) / 2

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
    override val animIn: Anim = Anim.None,
    override val animOut: Anim = Anim.None,
    override val animLoop: Loop = Loop.None,
) : Layer {
    private val paint = if (adjust.isNone) bitmapPaint else Paint(bitmapPaint).apply { colorFilter = ColorMatrixColorFilter(adjust.filterMatrix()) }
    override val width get() = bitmap.width.toFloat()
    override val height get() = bitmap.height.toFloat()
    override fun drawContent(canvas: Canvas) = canvas.drawBitmap(bitmap, -width / 2, -height / 2, paint)
    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

/** Part of a frame, as fractions (0 to 1) of its width and height. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width get() = right - left
    val height get() = bottom - top
}

/** This transform moved so that a [width]×[height] frame showing [to] instead of [from] keeps the part it still shows in place. */
fun Transform.recropped(from: CropRect, to: CropRect, width: Int, height: Int) =
    shiftedLocal((to.left + to.right - from.left - from.right) / 2 * width, (to.top + to.bottom - from.top - from.bottom) / 2 * height)

/**
 * Another video on top of the main clip (picture-in-picture), [videoWidth]×[videoHeight] being its display size
 * (rotation applied), of which it shows only [crop]. It plays its clip from [clipStartMs] on, so it shows for at most [playableMs].
 * Its pixels come only from the Media3 compositor (`VideoClip.composition`), in the preview and the export alike, so it draws
 * nothing here (thumbnails and the bitmap overlay skip it); the stage shows its selection box like any layer.
 */
data class VideoLayer(
    override val id: Long,
    override val t: Transform,
    val uri: Uri,
    val durationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    /** Its own sound, 0 to 1; muted by default so it doesn't talk over the main clip. */
    val volume: Float = 0f,
    override val startMs: Long = 0,
    override val endMs: Long = startMs + durationMs,
    override val hidden: Boolean = false,
    override val locked: Boolean = false,
    /** 0 to 1. */
    val opacity: Float = 1f,
    /** Where in its own clip it starts playing. */
    val clipStartMs: Long = 0,
    val crop: CropRect = CropRect(),
) : Layer {
    override val width get() = videoWidth * crop.width
    override val height get() = videoHeight * crop.height
    val playableMs get() = durationMs - clipStartMs
    override fun drawContent(canvas: Canvas) = Unit
    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = minOf(endMs, startMs + playableMs))

    /** Plays the clip from [ms] on; the track gets shorter if less is left than it was long. */
    fun withClipStart(ms: Long) = copy(clipStartMs = ms).let { it.withTime(it.startMs, it.endMs) }

    /** Shows only [c] of the frame; the part still shown stays where it was on the canvas. */
    fun withCrop(c: CropRect) = copy(crop = c, t = t.recropped(crop, c, videoWidth, videoHeight))
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

/**
 * Freehand drawing: [strokes] in the layer's own pixels, centered on its origin (see [withStrokes]), so it moves,
 * scales, rotates and times like any other layer.
 */
data class DrawLayer(
    override val id: Long,
    override val t: Transform,
    val strokes: List<PenStroke>,
    override val startMs: Long = 0,
    override val endMs: Long = Long.MAX_VALUE,
    override val hidden: Boolean = false,
    override val locked: Boolean = false,
) : Layer {
    private val bounds = strokeBounds(strokes)
    private val paths by lazy { strokes.map { it.path() } }
    // Per layer, not shared: animated video frames are drawn on the GL thread while the stage draws on the main thread.
    private val penPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
    override val width get() = bounds?.let { it[2] - it[0] } ?: 0f
    override val height get() = bounds?.let { it[3] - it[1] } ?: 0f
    override fun drawContent(canvas: Canvas) = strokes.forEachIndexed { i, s ->
        penPaint.color = s.color
        penPaint.strokeWidth = s.width
        canvas.drawPath(paths[i], penPaint)
    }
    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

private fun PenStroke.path() = Path().apply {
    val s = smoothSegments(points)
    if (s.isEmpty()) return@apply
    moveTo(s[0], s[1])
    for (i in 2 until s.size step 4) quadTo(s[i], s[i + 1], s[i + 2], s[i + 3])
}

/**
 * Brightness, contrast and saturation, each -1..1 with 0 meaning unchanged, plus a [Look] blended in by [amount] (0..1).
 * Used by image layers and the video.
 */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val look: Look = Look.None,
    val amount: Float = 1f,
) {
    val isNone get() = brightness == 0f && contrast == 0f && saturation == 0f && (look == Look.None || amount == 0f)

    /** 4x5 color matrix for [android.graphics.ColorMatrix]: saturate, then contrast around mid-gray, then brighten. */
    fun colorMatrix(): FloatArray {
        val s = 1 + saturation
        val k = 1 + contrast
        val offset = 128 * (1 - k) + 128 * brightness
        val m = FloatArray(20)
        for (row in 0..2) {
            for (col in 0..2) m[row * 5 + col] = k * (LUMA[col] * (1 - s) + if (row == col) s else 0f)
            m[row * 5 + 4] = offset
        }
        m[18] = 1f
        return m
    }
}

enum class TextAlign(@StringRes val label: Int) { Left(R.string.align_left), Center(R.string.align_center), Right(R.string.align_right) }

/** The shape of a text layer's box: the rounded caption box, or a bubble with a tail. */
enum class Bubble(@StringRes val label: Int) { Box(R.string.bubble_box), Speech(R.string.bubble_speech), Thought(R.string.bubble_thought), Shout(R.string.bubble_shout) }

data class TextLayer(
    override val id: Long,
    override val t: Transform,
    val text: String,
    val font: Font,
    val size: Float,
    val color: Int,
    /** Outline color, or null for no outline. */
    val outline: Int?,
    /** Fill of the box or bubble behind the text, or null for none. */
    val box: Int? = null,
    val bubble: Bubble = Bubble.Box,
    /** Outline of the box or bubble, or null for none. */
    val boxOutline: Int? = null,
    /** Where a bubble's tail points, in the layer's own pixels from its center; null for the default, down and to the left. */
    val tail: Pair<Float, Float>? = null,
    val shadow: Boolean = false,
    /** Extra space between letters, in ems. */
    val spacing: Float = 0f,
    val align: TextAlign = TextAlign.Center,
    /** Glow color around the letters, or null for none; [glowSize] (0 to 1) is how far it spreads. */
    val glow: Int? = null,
    val glowSize: Float = 0.5f,
    /** 0 to 1, the whole layer (box included). */
    val opacity: Float = 1f,
    /** Line height as a multiple of the font's. */
    val lineSpacing: Float = 1f,
    /** Where the text wraps, in the layer's own pixels; null keeps the lines as typed (bubbles wrap to a round block). */
    val wrap: Float? = null,
    override val startMs: Long = 0,
    override val endMs: Long = Long.MAX_VALUE,
    override val hidden: Boolean = false,
    override val locked: Boolean = false,
    override val animIn: Anim = Anim.None,
    override val animOut: Anim = Anim.None,
    override val animLoop: Loop = Loop.None,
) : Layer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.typeface = font.typeface
        it.textSize = size
        it.letterSpacing = spacing
    }
    // Per layer, like [paint]: the video export draws copies of animated layers off the main thread.
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val isBubble get() = box != null && bubble != Bubble.Box
    private val layout: StaticLayout = run {
        val line = text.lines().maxOf { paint.measureText(it) }
        // Bubbles wrap long text into a rounder block; the caption box keeps the lines as typed.
        val word = if (isBubble && wrap == null) text.split(' ', '\n').maxOf { paint.measureText(it) } else 0f
        val w = wrap?.roundToInt()?.coerceAtLeast(1) ?: ((if (isBubble) bubbleWrapWidth(line, paint.fontSpacing, word) else line).toInt() + 1)
        val alignment = when (align) {
            TextAlign.Left -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
        }
        StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(alignment)
            .setIncludePad(true)
            .setLineSpacing(0f, lineSpacing)
            .build()
    }
    /** The width the text wraps at now, [wrap] if set. */
    val wrapWidth get() = layout.width.toFloat()

    /** Wrapping where dragging its [side] edge (-1 left, 1 right) by [dx] own pixels puts it, the other edge staying put. */
    fun wrapDragged(side: Float, dx: Float): TextLayer {
        val w = (wrapWidth + side * dx).coerceAtLeast(size)
        return copy(wrap = w, t = t.shiftedLocal(side * (w - wrapWidth) / 2, 0f))
    }
    private val glowRadius get() = if (glow == null) 0f else size * glowSize * 0.5f
    private val stroke get() = if (outline == null) 0f else size * 0.16f
    private val boxPadX get() = if (box == null) 0f else size * 0.3f
    private val boxPadY get() = if (box == null) 0f else size * 0.12f

    private val boxStroke get() = if (boxOutline == null) 0f else size * 0.07f
    /** Half the size of the ellipse a bubble's text sits in. */
    private val oval = ellipseAround(layout.width + stroke + size * 0.5f, layout.height + stroke + size * 0.4f)
    private val bubbleHalf by lazy { bubbleHalfSize(bubble, oval[0], oval[1]) }
    private val bubblePath by lazy { bubblePath(bubble, oval[0], oval[1], bubbleHalf, tailTip) }

    /** Where a bubble's tail points, or null when there's no bubble. */
    val tailTip get() = if (isBubble) tail ?: (-bubbleHalf[0] * 0.4f to bubbleHalf[1] + size * 1.2f) else null

    override val width get() = if (isBubble) 2 * bubbleHalf[0] + boxStroke else layout.width + stroke + 2 * boxPadX
    override val height get() = if (isBubble) 2 * bubbleHalf[1] + boxStroke else layout.height + stroke + 2 * boxPadY
    override val reach get() = maxOf(hypot(width, height) / 2, tailTip?.let { hypot(it.first, it.second) + boxStroke } ?: 0f) + glowRadius

    override fun drawContent(canvas: Canvas) = drawContent(canvas, null)

    /** With [reveal] (a per-letter entrance in progress) the letters are drawn one by one, each as [letterScale] says. */
    fun drawContent(canvas: Canvas, reveal: Reveal?) {
        // Like a fade: one offscreen layer, so the outline and box don't show through the letters. Room for a shadow.
        val r = reach * 1.2f
        val count = if (opacity < 1f) canvas.saveLayerAlpha(-r, -r, r, r, (opacity * 255).roundToInt()) else canvas.save()
        if (box != null) {
            boxPaint.style = Paint.Style.FILL
            boxPaint.color = box
            if (isBubble) canvas.drawPath(bubblePath, boxPaint)
            else canvas.drawRoundRect(-width / 2, -height / 2, width / 2, height / 2, size * 0.25f, size * 0.25f, boxPaint)
        }
        if (box != null && boxOutline != null) {
            boxPaint.style = Paint.Style.STROKE
            boxPaint.strokeJoin = Paint.Join.ROUND
            boxPaint.strokeWidth = boxStroke
            boxPaint.color = boxOutline
            if (isBubble) {
                canvas.drawPath(bubblePath, boxPaint)
            } else {
                // Inside the caption box, so adding an outline doesn't change its size.
                val i = boxStroke / 2
                canvas.drawRoundRect(-width / 2 + i, -height / 2 + i, width / 2 - i, height / 2 - i, size * 0.25f, size * 0.25f, boxPaint)
            }
        }
        canvas.translate(-layout.width / 2f, -layout.height / 2f)
        fun draw() = if (reveal == null) layout.draw(canvas) else drawLetters(canvas, reveal)
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = stroke
        if (glow != null) {
            // The letters (and outline) in the glow color, blurred around; the passes below cover the letters themselves.
            paint.style = if (outline != null) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            paint.color = glow
            paint.setShadowLayer(glowRadius, 0f, 0f, glow)
            // Twice: one blurred pass spreads thin and reads as a soft shadow, not a glow.
            repeat(2) { draw() }
            paint.clearShadowLayer()
        }
        // The shadow goes on the first pass only, so it sits under the outline instead of over it.
        if (shadow) paint.setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0xAA000000.toInt())
        if (outline != null) {
            paint.style = Paint.Style.STROKE
            paint.color = outline
            draw()
            paint.clearShadowLayer()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        draw()
        paint.clearShadowLayer()
        canvas.restoreToCount(count)
    }

    /** Where each letter (grapheme, so an emoji stays whole) starts in [text], plus the text's end. */
    private val letters by lazy {
        val b = BreakIterator.getCharacterInstance()
        b.setText(text)
        buildList {
            var i = b.first()
            while (i != BreakIterator.DONE) {
                add(i)
                i = b.next()
            }
        }
    }

    /**
     * The layout's letters one at a time at the layout's own positions, so they land where [StaticLayout.draw] puts them.
     * shortcut: joined scripts (Arabic, Devanagari) lose their joins while the letters come in; shape whole runs if users need them.
     */
    private fun drawLetters(canvas: Canvas, reveal: Reveal) {
        val n = letters.size - 1
        for (i in 0 until n) {
            val s = letters[i]
            val e = letters[i + 1]
            val k = letterScale(reveal.anim, reveal.p, i, n)
            if (k <= 0f || text[s].isWhitespace()) continue
            val x = layout.getPrimaryHorizontal(s)
            val y = layout.getLineBaseline(layout.getLineForOffset(s)).toFloat()
            canvas.save()
            canvas.scale(k, k, x + paint.measureText(text, s, e) / 2, y - size * 0.35f)
            canvas.drawText(text, s, e, x, y, paint)
            canvas.restore()
        }
    }

    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

/**
 * One outline for a bubble around the ellipse ([rx], [ry]) that is [half] wide and tall without its tail, with the tail
 * pointing at [tip]: everything is merged, so the outline never runs across the inside.
 */
private fun bubblePath(bubble: Bubble, rx: Float, ry: Float, half: FloatArray, tip: Pair<Float, Float>?): Path {
    val oval = Path().apply { addOval(-rx, -ry, rx, ry, Path.Direction.CW) }
    val body = when (bubble) {
        Bubble.Thought -> oval.apply { op(circles(cloudBumps(rx, ry)), Path.Op.UNION) }
        Bubble.Shout -> polygon(spikyPoints(rx, ry))
        else -> oval
    }
    val tail = when {
        tip == null -> return body
        bubble == Bubble.Thought -> circles(thoughtTrail(half[0], half[1], tip.first, tip.second))
        else -> polygon(tailPoints(rx, ry, tip.first, tip.second))
    }
    return body.apply { op(tail, Path.Op.UNION) }
}

private fun polygon(p: FloatArray) = Path().apply {
    if (p.isEmpty()) return@apply
    moveTo(p[0], p[1])
    for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
    close()
}

/** Overlapping circles (x, y, radius triples) merged into one outline. */
private fun circles(c: FloatArray) = Path().apply {
    for (i in 0 until c.size / 3) op(Path().apply { addCircle(c[i * 3], c[i * 3 + 1], c[i * 3 + 2], Path.Direction.CW) }, Path.Op.UNION)
}

enum class ShapeKind(@StringRes val label: Int) {
    Rect(R.string.shape_rect), RoundRect(R.string.shape_round_rect), Ellipse(R.string.shape_ellipse),
    Arrow(R.string.shape_arrow), Star(R.string.shape_star), Line(R.string.shape_line),
}

/**
 * A [kind] of shape [w]×[h] in its own pixels, with a [fill] and an [outline] [stroke] wide (either null for none) and an
 * [opacity] (0 to 1). A line runs across [w] in the outline color (the fill if it has none); [h] doesn't apply to it.
 */
data class ShapeLayer(
    override val id: Long,
    override val t: Transform,
    val kind: ShapeKind,
    val w: Float,
    val h: Float,
    val fill: Int?,
    val outline: Int?,
    val stroke: Float,
    val opacity: Float = 1f,
    override val startMs: Long = 0,
    override val endMs: Long = Long.MAX_VALUE,
    override val hidden: Boolean = false,
    override val locked: Boolean = false,
    override val animIn: Anim = Anim.None,
    override val animOut: Anim = Anim.None,
    override val animLoop: Loop = Loop.None,
) : Layer {
    // Per layer, like the text paints: the video export draws copies of animated layers off the main thread.
    private val paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val path by lazy {
        when (kind) {
            ShapeKind.Arrow -> polygon(arrowPoints(w, h))
            ShapeKind.Star -> polygon(starPoints(w, h))
            else -> null
        }
    }
    private val lineColor get() = outline ?: fill
    private val pad get() = if (kind == ShapeKind.Line || outline != null) stroke else 0f
    override val width get() = w + pad
    // A line's box is a little taller than the line, so it can still be grabbed.
    override val height get() = if (kind == ShapeKind.Line) stroke * 3 else h + pad

    override fun drawContent(canvas: Canvas) {
        val count = if (opacity < 1f) canvas.saveLayerAlpha(-width / 2, -height / 2, width / 2, height / 2, (opacity * 255).roundToInt()) else canvas.save()
        if (kind == ShapeKind.Line) {
            lineColor?.let {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.color = it
                canvas.drawLine(-w / 2, 0f, w / 2, 0f, paint)
            }
        } else {
            fill?.let {
                paint.style = Paint.Style.FILL
                paint.color = it
                drawShape(canvas)
            }
            outline?.let {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.color = it
                drawShape(canvas)
            }
        }
        canvas.restoreToCount(count)
    }

    private fun drawShape(canvas: Canvas) {
        when (kind) {
            ShapeKind.Rect -> canvas.drawRect(-w / 2, -h / 2, w / 2, h / 2, paint)
            ShapeKind.RoundRect -> canvas.drawRoundRect(-w / 2, -h / 2, w / 2, h / 2, minOf(w, h) * 0.2f, minOf(w, h) * 0.2f, paint)
            ShapeKind.Ellipse -> canvas.drawOval(-w / 2, -h / 2, w / 2, h / 2, paint)
            else -> path?.let { canvas.drawPath(it, paint) }
        }
    }

    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

/** A new [kind] of shape about [size] canvas pixels big: outlined in red, or filled red for an arrow or a star. */
fun newShape(id: Long, kind: ShapeKind, at: Transform, size: Float): ShapeLayer {
    val red = 0xFFFF1744.toInt()
    val solid = kind == ShapeKind.Arrow || kind == ShapeKind.Star
    val (w, h) = shapeSize(kind, size)
    return ShapeLayer(id, at, kind, w, h, fill = if (solid) red else null, outline = if (solid) null else red, stroke = size * 0.04f)
}

/** A [kind]'s starting proportions at about [size] wide. */
fun shapeSize(kind: ShapeKind, size: Float) = when (kind) {
    ShapeKind.Arrow -> size to size * 0.45f
    ShapeKind.Star -> size to size
    ShapeKind.Line -> size to size * 0.04f
    else -> size to size * 0.7f
}

private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/** [reveal] is a per-letter entrance in progress, which only text has. */
fun Canvas.drawLayer(layer: Layer, reveal: Reveal? = null) {
    save()
    translate(layer.t.x, layer.t.y)
    rotate(layer.t.rotation)
    scale(layer.t.scale, layer.t.scale)
    if (layer is TextLayer) layer.drawContent(this, reveal) else layer.drawContent(this)
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
