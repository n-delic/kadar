package com.ndelic.kadar.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A blurred photo canvas's background: the bottom visible image layer scaled to cover the canvas, blurred and darkened
 * like the video's blurred canvas. Small (long side [BACKDROP_LONG_SIDE]); the stage, the export and the thumbnail all
 * draw it stretched over the whole canvas. Null without [Project.blur], for videos, or with no picture.
 */
fun Project.backdrop(): Bitmap? {
    val base = blurBase() ?: return null
    val s = BACKDROP_LONG_SIDE / max(width, height).toFloat()
    val w = max(2, (width * s).roundToInt())
    val h = max(2, (height * s).roundToInt())
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val m = base.adjust.filterMatrix().copyOf()
    // Darker, so captions over the background stay readable (the video's blurred canvas does the same).
    for (i in intArrayOf(4, 9, 14)) m[i] -= 0.15f * 255
    Canvas(out).apply {
        val cover = max(w / base.bitmap.width.toFloat(), h / base.bitmap.height.toFloat())
        translate(w / 2f, h / 2f)
        // Mirrored like the picture on the canvas.
        scale(if (base.t.flipX) -cover else cover, if (base.t.flipY) -cover else cover)
        drawBitmap(base.bitmap, -base.bitmap.width / 2f, -base.bitmap.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) })
    }
    val pixels = IntArray(w * h)
    out.getPixels(pixels, 0, w, 0, 0, w, h)
    boxBlur(pixels, w, h, BACKDROP_BLUR)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

/** The picture a blurred photo canvas blurs: its bottom visible image layer; null when there's no blur to draw. */
fun Project.blurBase(): ImageLayer? = if (!blur || video != null) null else layers.firstOrNull { it is ImageLayer && !it.hidden } as? ImageLayer

/** Whether a photo exports with see-through parts (a transparent background, no blurred canvas), so only as a PNG. */
val Project.transparent get() = background == Color.TRANSPARENT && blurBase() == null

/**
 * Blurs ARGB [pixels] in place: three box passes each way, about a Gaussian of sigma 1.1 × [radius]. Colors are weighted
 * by alpha, so see-through pixels don't darken their neighbours; edges repeat the border pixel.
 */
fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
    val tmp = IntArray(pixels.size)
    repeat(3) {
        blurLine(pixels, tmp, w, h, radius, horizontal = true)
        blurLine(tmp, pixels, w, h, radius, horizontal = false)
    }
}

private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    for (y in 0 until h) {
        for (x in 0 until w) {
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (k in -radius..radius) {
                val p = if (horizontal) src[y * w + (x + k).coerceIn(0, w - 1)] else src[(y + k).coerceIn(0, h - 1) * w + x]
                val pa = (p ushr 24).toLong()
                a += pa
                r += (p shr 16 and 0xFF) * pa
                g += (p shr 8 and 0xFF) * pa
                b += (p and 0xFF) * pa
            }
            val n = 2 * radius + 1
            dst[y * w + x] = if (a == 0L) 0 else ((a / n).toInt() shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
        }
    }
}

/** Fine enough for a blur: stretched to the canvas, it looks the same as a full-size one, at a fraction of the work. */
private const val BACKDROP_LONG_SIDE = 128

/** About 5% of the canvas's long side, like the video's blurred canvas. */
private const val BACKDROP_BLUR = 5
