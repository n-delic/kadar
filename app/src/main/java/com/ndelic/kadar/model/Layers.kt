package com.ndelic.kadar.model

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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

/**
 * Where a layer's center sits on the canvas, in canvas pixels. Rotation is in degrees, clockwise. [flipX] mirrors the
 * content left to right and [flipY] top to bottom inside its box; the box itself (handles, hit test) doesn't flip.
 * [stretch] scales the width on top of [scale]: a picture or drawing pulled wider (above 1) or narrower by a side handle.
 */
data class Transform(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val stretch: Float = 1f,
) {
    /** How many canvas pixels one of the layer's own pixels is across. */
    val scaleX get() = scale * stretch

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
        return ((dx * cos(r) - dy * sin(r)) / scaleX).toFloat() to ((dx * sin(r) + dy * cos(r)) / scale).toFloat()
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
            x = x + (dx * scaleX * cos(r) - dy * scale * sin(r)).toFloat(),
            y = y + (dx * scaleX * sin(r) + dy * scale * cos(r)).toFloat(),
        )
    }

    /** [shiftedLocal] by ([dx], [dy]) in the content's own pixels, which a flip mirrors. */
    fun shiftedContent(dx: Float, dy: Float) = shiftedLocal(if (flipX) -dx else dx, if (flipY) -dy else dy)

    /**
     * A [width]×[height] box (own pixels) with its [side] edge (-1 left or top, 1 right or bottom) across ([vertical] false)
     * or up and down dragged by [d] own pixels: stretched that way only, the opposite edge staying put on the canvas.
     */
    fun sideDragged(width: Float, height: Float, vertical: Boolean, side: Float, d: Float): Transform {
        val size = if (vertical) height else width
        val k = (size + side * d).coerceAtLeast(MIN_SIDE / if (vertical) scale else scaleX) / size
        val shift = side * size * (k - 1) / 2
        return if (vertical) shiftedLocal(0f, shift).copy(scale = scale * k, stretch = stretch / k)
        else shiftedLocal(shift, 0f).copy(stretch = stretch * k)
    }
}

/** The narrowest a side handle squeezes a layer, in canvas pixels. */
private const val MIN_SIDE = 16f

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

    /** How long the entrance and exit run, or null for their kind's own length ([Anim.ms]). */
    val animInMs: Long? get() = null
    val animOutMs: Long? get() = null

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
    /** 0 to 1. */
    val opacity: Float = 1f,
    override val animInMs: Long? = null,
    override val animOutMs: Long? = null,
    /** Drawn around the picture's shape as [outlineStyle] says, or null for none. */
    val outline: Int? = null,
    val outlineStyle: OutlineStyle = OutlineStyle.Solid,
    /** How thick the outline is (or how far a glow or shadow spreads), as a part of the picture's longer side. */
    val outlineWidth: Float = DEFAULT_OUTLINE_WIDTH,
) : Layer {
    // With an outline the opacity goes on both at once (one offscreen layer), so the outline doesn't show through the picture.
    private val pictureAlpha = if (outline == null) opacity else 1f
    private val paint = if (adjust.isNone && pictureAlpha >= 1f) bitmapPaint else Paint(bitmapPaint).apply {
        if (!adjust.isNone) colorFilter = ColorMatrixColorFilter(adjust.filterMatrix())
        alpha = (pictureAlpha * 255).roundToInt()
    }
    private val texture = adjust.texturePaints(width, height)
    private val outlinePaint = outline?.let { c ->
        Paint(bitmapPaint).apply {
            color = c
            if (outlineStyle == OutlineStyle.Shadow) alpha = alpha * 3 / 5
        }
    }
    private val mask by lazy { outline?.let { outlineMask(bitmap, outlineStyle, outlineWidth, t.stretch) } }
    private val outlineSize get() = if (outline == null) 0f else outlineWidth * maxOf(width, height)
    override val width get() = bitmap.width.toFloat()
    override val height get() = bitmap.height.toFloat()
    override val reach get() = hypot(width, height) / 2 + outlineSize * if (outlineStyle == OutlineStyle.Shadow) 2.5f else 1f
    override fun drawContent(canvas: Canvas) {
        val m = mask
        // Down and right on the canvas whichever way the picture is flipped, as far across as down when it's stretched.
        val dx = if (outlineStyle != OutlineStyle.Shadow) 0f else outlineSize / t.stretch * if (t.flipX) -1 else 1
        val dy = if (outlineStyle != OutlineStyle.Shadow) 0f else outlineSize * if (t.flipY) -1 else 1
        val count = if (m == null || opacity >= 1f) canvas.save() else {
            val r = RectF(m.bounds).apply { union(left + dx, top + dy, right + dx, bottom + dy) }
            canvas.saveLayerAlpha(r, (opacity * 255).roundToInt())
        }
        if (m != null) {
            canvas.translate(dx, dy)
            canvas.drawBitmap(m.bitmap, null, m.bounds, outlinePaint)
            canvas.translate(-dx, -dy)
        }
        if (texture.isEmpty()) {
            canvas.drawBitmap(bitmap, -width / 2, -height / 2, paint)
        } else {
            // A layer of its own, so the vignette and grain (SRC_ATOP) land only on the picture's pixels, not its outline.
            canvas.saveLayer(-width / 2, -height / 2, width / 2, height / 2, null)
            canvas.drawBitmap(bitmap, -width / 2, -height / 2, paint)
            texture.forEach { canvas.drawRect(-width / 2, -height / 2, width / 2, height / 2, it) }
        }
        canvas.restoreToCount(count)
    }
    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = endMs)
    override fun withFlags(hidden: Boolean, locked: Boolean) = copy(hidden = hidden, locked = locked)
}

/**
 * A clip placed at [startMs] that plays its source from [clipStartMs], with its start trimmed to [ms]: the new start and
 * in-point. The in-point moves by as much as the start, and the start goes back no further than the source begins.
 */
fun trimmedIn(startMs: Long, clipStartMs: Long, ms: Long): Pair<Long, Long> {
    val s = ms.coerceAtLeast(startMs - clipStartMs)
    return s to clipStartMs + s - startMs
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
    /** Look and color sliders, like a clip's. */
    val adjust: Adjust = Adjust(),
) : Layer {
    override val width get() = videoWidth * crop.width
    override val height get() = videoHeight * crop.height
    val playableMs get() = durationMs - clipStartMs
    override fun drawContent(canvas: Canvas) = Unit
    override fun withTransform(t: Transform) = copy(t = t)
    override fun withTime(startMs: Long, endMs: Long) = copy(startMs = startMs, endMs = minOf(endMs, startMs + playableMs))

    /** Plays the clip from [ms] on; the track gets shorter if less is left than it was long. */
    fun withClipStart(ms: Long) = copy(clipStartMs = ms).let { it.withTime(it.startMs, it.endMs) }

    /** Its start trimmed to [ms] like a clip's: the in-point moves with it, so a later start cuts the frames before it. */
    fun trimmedStart(ms: Long) = trimmedIn(startMs, clipStartMs, ms).let { (s, c) -> copy(startMs = s, clipStartMs = c) }

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
    private val sprays by lazy { strokes.map { if (it.style == PenStyle.Spray) sprayDots(it.points, it.width) else null } }
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
        when (s.style) {
            PenStyle.Solid -> canvas.drawPath(paths[i], penPaint)
            // Zero-length dashes with round caps are dots, one width apart.
            PenStyle.Dotted -> drawWith(penPaint.apply { pathEffect = DashPathEffect(floatArrayOf(0f, s.width * 2), 0f) }) { canvas.drawPath(paths[i], it) }
            PenStyle.Soft -> drawWith(penPaint.apply { maskFilter = BlurMaskFilter(s.width * 0.3f, BlurMaskFilter.Blur.NORMAL) }) { canvas.drawPath(paths[i], it) }
            PenStyle.Neon -> {
                // A wide blurred glow in the color, then a thin core close to white.
                penPaint.strokeWidth = s.width * 1.6f
                drawWith(penPaint.apply { maskFilter = BlurMaskFilter(s.width * 0.8f, BlurMaskFilter.Blur.NORMAL) }) { canvas.drawPath(paths[i], it) }
                penPaint.strokeWidth = s.width * 0.45f
                penPaint.color = towardWhite(s.color, 0.7f)
                canvas.drawPath(paths[i], penPaint)
            }
            PenStyle.Spray -> drawWith(penPaint.apply { style = Paint.Style.FILL }) { p ->
                val d = sprays[i]!!
                for (k in 0 until d.size / 3) canvas.drawCircle(d[3 * k], d[3 * k + 1], d[3 * k + 2], p)
            }
        }
    }

    /** [color] moved [amount] of the way to white, its alpha kept. */
    private fun towardWhite(color: Int, amount: Float): Int {
        fun ch(shift: Int) = ((color shr shift and 0xFF) + ((255 - (color shr shift and 0xFF)) * amount)).roundToInt() shl shift
        return (color and 0xFF000000.toInt()) or ch(16) or ch(8) or ch(0)
    }

        /** Draws with [paint] as set up for one stroke, then puts it back to a plain solid stroke. */
    private inline fun drawWith(paint: Paint, draw: (Paint) -> Unit) {
        draw(paint)
        paint.pathEffect = null
        paint.maskFilter = null
        paint.style = Paint.Style.STROKE
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
 * Brightness, contrast, saturation, warmth, tint and hue, each -1..1, and fade, vignette and grain 0..1, with 0 meaning unchanged, plus a
 * [Look] blended in by [amount] (0..1). Used by image layers, video clips and video layers.
 */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val look: Look = Look.None,
    val amount: Float = 1f,
    /** Toward orange (positive) or blue. */
    val warmth: Float = 0f,
    /** Toward magenta (positive) or green. */
    val tint: Float = 0f,
    /** A turn of the color wheel, -1..1 for -180°..180°. */
    val hue: Float = 0f,
    val fade: Float = 0f,
    /** Darkens toward the edges, 0..1. */
    val vignette: Float = 0f,
    /** Film grain, 0..1. */
    val grain: Float = 0f,
) {
    /** No color change: [vignette] and [grain] aren't colors, see [hasTexture]. */
    val isNone get() = listOf(brightness, contrast, saturation, warmth, tint, hue, fade).all { it == 0f } && (look == Look.None || amount == 0f)

    val hasTexture get() = vignette != 0f || grain != 0f

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
enum class Bubble(@StringRes val label: Int) { Box(R.string.bubble_box), Speech(R.string.bubble_speech), Thought(R.string.bubble_thought), Shout(R.string.bubble_shout), Pill(R.string.bubble_pill), Double(R.string.bubble_double) }

/** A new text's size on a [width]×[height] canvas, and 100% on the size slider. */
fun defaultTextSize(width: Int, height: Int) = minOf(width, height) * 0.1f

/** The one shadow text had before it got a color: black, a little see-through. */
const val DEFAULT_SHADOW = 0xAA000000.toInt()

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
    /** 0 to 1, the box or bubble fill only. */
    val boxOpacity: Float = 1f,
    val bubble: Bubble = Bubble.Box,
    /** Outline of the box or bubble, or null for none. */
    val boxOutline: Int? = null,
    /** Where a bubble's tail points, in the layer's own pixels from its center; null for the default, down and to the left. */
    val tail: Pair<Float, Float>? = null,
    /** Shadow color, or null for none; [shadowBlur] and [shadowDistance] (straight down) are in multiples of [size]. */
    val shadow: Int? = null,
    val shadowBlur: Float = 0.08f,
    val shadowDistance: Float = 0.04f,
    /** Outline thickness, a multiple of [size]. */
    val outlineWidth: Float = 0.16f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** Draws the letters in capitals; [text] keeps what was typed. */
    val caps: Boolean = false,
    /** Extra space between letters, in ems. */
    val spacing: Float = 0f,
    val align: TextAlign = TextAlign.Center,
    /** Glow color around the letters, or null for none; [glowSize] (0 to 1) is how far it spreads. */
    val glow: Int? = null,
    /** The color the letters fade into from [color] at the top to this at the bottom, or null for one color. */
    val gradient: Int? = null,
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
    override val animInMs: Long? = null,
    override val animOutMs: Long? = null,
) : Layer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        // The platform fakes bold and italic for fonts that have no such style.
        it.typeface = when {
            bold && italic -> Typeface.create(font.typeface, Typeface.BOLD_ITALIC)
            bold -> Typeface.create(font.typeface, Typeface.BOLD)
            italic -> Typeface.create(font.typeface, Typeface.ITALIC)
            else -> font.typeface
        }
        it.textSize = size
        it.letterSpacing = spacing
        it.isUnderlineText = underline
    }
    private val shown = if (caps) text.uppercase() else text
    // Per layer, like [paint]: the video export draws copies of animated layers off the main thread.
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val isBubble get() = box != null && bubble != Bubble.Box
    // A pill keeps the lines as typed, like the caption box; the round bubbles wrap into a rounder block.
    private val roundWrap get() = isBubble && bubble != Bubble.Pill
    private val layout: StaticLayout = run {
        // Italic letters lean past their measured width; the room keeps the last one inside the box.
        val line = shown.lines().maxOf { paint.measureText(it) } + if (italic) size * 0.3f else 0f
        // Bubbles wrap long text into a rounder block; the caption box keeps the lines as typed.
        val word = if (roundWrap && wrap == null) shown.split(' ', '\n').maxOf { paint.measureText(it) } else 0f
        val w = wrap?.roundToInt()?.coerceAtLeast(1) ?: ((if (roundWrap) bubbleWrapWidth(line, paint.fontSpacing, word) else line).toInt() + 1)
        val alignment = when (align) {
            TextAlign.Left -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
        }
        StaticLayout.Builder.obtain(shown, 0, shown.length, paint, w)
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
    private val stroke get() = if (outline == null) 0f else size * outlineWidth
    private val shadowReach get() = if (shadow == null) 0f else size * (shadowBlur + shadowDistance)
    private val boxPadX get() = if (box == null) 0f else size * 0.3f
    private val boxPadY get() = if (box == null) 0f else size * 0.12f

    // A double outline is two lines with a gap, drawn as one wide stroke with the fill color down its middle.
    private val boxStroke get() = if (boxOutline == null) 0f else size * if (bubble == Bubble.Double && isBubble) 0.2f else 0.07f
    /** Half the size of the ellipse a bubble's text sits in. */
    private val oval = ellipseAround(layout.width + stroke + size * 0.5f, layout.height + stroke + size * 0.4f)
    private val bubbleHalf by lazy { bubbleHalfSize(bubble, oval[0], oval[1]) }
    private val bubblePath by lazy { bubblePath(bubble, oval[0], oval[1], bubbleHalf, tailTip) }

    /** Where a bubble's tail points, or null when there's no bubble. */
    val tailTip get() = if (isBubble) tail ?: (-bubbleHalf[0] * 0.4f to bubbleHalf[1] + size * 1.2f) else null

    override val width get() = if (isBubble) 2 * bubbleHalf[0] + boxStroke else layout.width + stroke + 2 * boxPadX
    override val height get() = if (isBubble) 2 * bubbleHalf[1] + boxStroke else layout.height + stroke + 2 * boxPadY
    override val reach get() = maxOf(hypot(width, height) / 2, tailTip?.let { hypot(it.first, it.second) + boxStroke } ?: 0f) + maxOf(glowRadius, shadowReach)

    override fun drawContent(canvas: Canvas) = drawContent(canvas, null)

    /** With [reveal] (a per-letter entrance in progress) the letters are drawn one by one, each as [letterScale] says. */
    fun drawContent(canvas: Canvas, reveal: Reveal?) {
        // Like a fade: one offscreen layer, so the outline and box don't show through the letters. Room for a shadow.
        val r = reach * 1.2f
        val count = if (opacity < 1f) canvas.saveLayerAlpha(-r, -r, r, r, (opacity * 255).roundToInt()) else canvas.save()
        if (box != null) {
            boxPaint.style = Paint.Style.FILL
            boxPaint.color = box
            boxPaint.alpha = (boxPaint.alpha * boxOpacity).roundToInt()
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
                if (bubble == Bubble.Double) {
                    boxPaint.strokeWidth = boxStroke * 0.4f
                    boxPaint.color = box
                    canvas.drawPath(bubblePath, boxPaint)
                }
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
        // A zero radius would remove the shadow, so a sharp one still gets a tiny blur.
        if (shadow != null) paint.setShadowLayer(maxOf(size * shadowBlur, 0.5f), 0f, size * shadowDistance, shadow)
        if (outline != null) {
            paint.style = Paint.Style.STROKE
            paint.color = outline
            draw()
            paint.clearShadowLayer()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        if (gradient != null) paint.shader = LinearGradient(0f, 0f, 0f, layout.height.toFloat(), color, gradient, Shader.TileMode.CLAMP)
        draw()
        paint.shader = null
        paint.clearShadowLayer()
        canvas.restoreToCount(count)
    }

    /** Where each letter (grapheme, so an emoji stays whole) starts in the drawn text, plus its end. */
    private val letters by lazy {
        val b = BreakIterator.getCharacterInstance()
        b.setText(shown)
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
            if (k <= 0f || shown[s].isWhitespace()) continue
            val x = layout.getPrimaryHorizontal(s)
            val y = layout.getLineBaseline(layout.getLineForOffset(s)).toFloat()
            canvas.save()
            canvas.scale(k, k, x + paint.measureText(shown, s, e) / 2, y - size * 0.35f)
            canvas.drawText(shown, s, e, x, y, paint)
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
        Bubble.Pill -> Path().apply { addRoundRect(-half[0], -half[1], half[0], half[1], half[1], half[1], Path.Direction.CW) }
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
    override val animInMs: Long? = null,
    override val animOutMs: Long? = null,
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

    /** [Transform.sideDragged] for a shape: it changes [w] or [h] instead of stretching, so its outline and corners keep their size. */
    fun sideDragged(vertical: Boolean, side: Float, d: Float): ShapeLayer {
        val size = if (vertical) h else w
        val next = (size + side * d).coerceAtLeast(MIN_SIDE / if (vertical) t.scale else t.scaleX)
        val shift = side * (next - size) / 2
        return if (vertical) copy(h = next, t = t.shiftedLocal(0f, shift)) else copy(w = next, t = t.shiftedLocal(shift, 0f))
    }
}

/** Whether the stage shows handles on the left and right ([vertical] false) or top and bottom sides; a line has no height. */
fun Layer.hasSideHandles(vertical: Boolean) = when (this) {
    is TextLayer -> !vertical
    is VideoLayer -> false
    is ShapeLayer -> !vertical || kind != ShapeKind.Line
    is ImageLayer, is DrawLayer -> true
}

/** A side handle dragged by [d] own pixels: text wraps, a shape resizes, a picture or drawing stretches. */
fun Layer.sideDragged(vertical: Boolean, side: Float, d: Float): Layer = when {
    !hasSideHandles(vertical) -> this
    this is TextLayer -> wrapDragged(side, d)
    this is ShapeLayer -> sideDragged(vertical, side, d)
    else -> withTransform(t.sideDragged(width, height, vertical, side, d))
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
    scale(if (layer.t.flipX) -layer.t.scaleX else layer.t.scaleX, if (layer.t.flipY) -layer.t.scale else layer.t.scale)
    if (layer is TextLayer) layer.drawContent(this, reveal) else layer.drawContent(this)
    restore()
}

/**
 * Renders the layers, [scale] times the canvas size, over [under] fitted into the canvas (a video frame for thumbnails),
 * over [backdrop] stretched to fill it (a blurred photo canvas, [Project.backdrop]).
 * The same drawing code backs the on-screen editor, so exports match it.
 */
fun renderLayers(width: Int, height: Int, background: Int, layers: List<Layer>, scale: Float = 1f, under: Bitmap? = null, backdrop: Bitmap? = null): Bitmap =
    Bitmap.createBitmap((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawColor(background)
            scale(scale, scale)
            backdrop?.let { drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint) }
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
