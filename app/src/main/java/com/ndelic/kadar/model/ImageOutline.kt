package com.ndelic.kadar.model

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.annotation.StringRes
import com.ndelic.kadar.R
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How an image layer's outline ([ImageLayer.outline]) follows the picture's shape: a hard edge, a soft glow, or a soft shadow down and right. */
enum class OutlineStyle(@StringRes val label: Int) { Solid(R.string.outline_solid), Glow(R.string.tool_glow), Shadow(R.string.shadow) }

/** A new outline's width, as a part of the picture's longer side. */
const val DEFAULT_OUTLINE_WIDTH = 0.03f

/** The longest side of a mask's picture part, so a big photo doesn't make a big mask; it is drawn scaled up, the edge stays smooth. */
private const val MASK_SIDE = 1024

/** A side-handle stretch is rounded to steps this far apart for the mask, so dragging one doesn't rebuild the mask every frame. */
private const val STRETCH_STEP = 1.1f

/** An outline's alpha-only [bitmap], drawn over [bounds] in the picture's own pixels (centered on its middle). */
class OutlineMask(val bitmap: Bitmap, val bounds: RectF)

private data class MaskKey(val style: OutlineStyle, val width: Float, val stretch: Float)

// Per picture, not per layer: a drag and the video export make new copies of a layer, which find the mask already built.
private val masks = WeakHashMap<Bitmap, LinkedHashMap<MaskKey, OutlineMask>>()

/**
 * The outline of [picture]'s opaque shape in [style], [width] (a part of its longer side) wide, as wide across as down
 * once the picture is drawn [stretch] times wider. Built once and kept while [picture] lives, at most 4 looks of it.
 */
fun outlineMask(picture: Bitmap, style: OutlineStyle, width: Float, stretch: Float): OutlineMask {
    val key = MaskKey(style, width, quantizedStretch(stretch))
    return synchronized(masks) {
        val built = masks.getOrPut(picture) { LinkedHashMap() }
        built.getOrPut(key) { buildMask(picture, key) }.also { if (built.size > 4) built.remove(built.keys.first()) }
    }
}

private fun buildMask(picture: Bitmap, key: MaskKey): OutlineMask {
    val w = picture.width
    val h = picture.height
    val k = minOf(1f, MASK_SIDE / max(w, h).toFloat())
    val mw = max(1, (w * k).roundToInt())
    val mh = max(1, (h * k).roundToInt())
    val small = if (mw == w && mh == h) picture else Bitmap.createScaledBitmap(picture, mw, mh, true)
    val pixels = IntArray(mw * mh).also { small.getPixels(it, 0, mw, 0, 0, mw, mh) }
    if (small !== picture) small.recycle()
    val r = key.width * max(mw, mh)
    val padX = ceil(r / key.stretch).toInt() + 1
    val padY = ceil(r).toInt() + 1
    val gw = mw + 2 * padX
    val gh = mh + 2 * padY
    val inside = BooleanArray(gw * gh)
    for (y in 0 until mh) for (x in 0 until mw) inside[(y + padY) * gw + x + padX] = pixels[y * mw + x] ushr 24 >= 128
    val d = distanceField(inside, gw, gh, key.stretch)
    val argb = Bitmap.createBitmap(IntArray(gw * gh) { (outlineAlpha(key.style, d[it], r) * 255).roundToInt() shl 24 }, gw, gh, Bitmap.Config.ARGB_8888)
    val mask = argb.extractAlpha()
    argb.recycle()
    val sx = w / mw.toFloat()
    val sy = h / mh.toFloat()
    return OutlineMask(mask, RectF(-w / 2f - padX * sx, -h / 2f - padY * sy, w / 2f + padX * sx, h / 2f + padY * sy))
}

/** [stretch] rounded to the nearest power of [STRETCH_STEP]; 1 stays 1. */
fun quantizedStretch(stretch: Float) = STRETCH_STEP.pow((ln(stretch) / ln(STRETCH_STEP)).roundToInt())

/** How opaque an outline [style] [r] wide is [d] away from the shape: a hard, anti-aliased edge, or a soft fade out. */
fun outlineAlpha(style: OutlineStyle, d: Float, r: Float): Float = when (style) {
    OutlineStyle.Solid -> (r + 0.5f - d).coerceIn(0f, 1f)
    else -> if (d >= r) 0f else (1 - d / r).let { it * it }
}

/**
 * How far each cell of a [w]×[h] grid is from the nearest [inside] cell (0 inside), cells being [spacingX] across and 1 down.
 * Exact, in two passes of the lower envelope of parabolas (Felzenszwalb and Huttenlocher), so it costs the same at any distance.
 */
fun distanceField(inside: BooleanArray, w: Int, h: Int, spacingX: Float = 1f): FloatArray {
    val n = max(w, h)
    val f = FloatArray(n)
    val line = FloatArray(n)
    val v = IntArray(n)
    val z = FloatArray(n + 1)
    val sq = FloatArray(w * h)
    for (x in 0 until w) {
        for (y in 0 until h) f[y] = if (inside[y * w + x]) 0f else FAR
        squaredDistances(f, h, line, v, z)
        for (y in 0 until h) sq[y * w + x] = line[y]
    }
    // Across, a step is spacingX: d = s²·min((q−p)² + f/s²).
    val s2 = spacingX * spacingX
    for (y in 0 until h) {
        for (x in 0 until w) f[x] = sq[y * w + x] / s2
        squaredDistances(f, w, line, v, z)
        for (x in 0 until w) sq[y * w + x] = sqrt(line[x] * s2)
    }
    return sq
}

private const val FAR = 1e20f

/** [out] q = min over p of (q − p)² + [f] p, for the first [n] entries; [v] and [z] are scratch space. */
private fun squaredDistances(f: FloatArray, n: Int, out: FloatArray, v: IntArray, z: FloatArray) {
    var k = 0
    v[0] = 0
    z[0] = Float.NEGATIVE_INFINITY
    z[1] = Float.POSITIVE_INFINITY
    for (q in 1 until n) {
        var s = intersection(f, q, v[k])
        // z[0] is minus infinity, so k never goes below 0.
        while (s <= z[k]) s = intersection(f, q, v[--k])
        k++
        v[k] = q
        z[k] = s
        z[k + 1] = Float.POSITIVE_INFINITY
    }
    k = 0
    for (q in 0 until n) {
        while (z[k + 1] < q) k++
        val p = v[k]
        out[q] = (q - p).toFloat() * (q - p) + f[p]
    }
}

/** Where the parabolas from [q] and [p] cross. */
private fun intersection(f: FloatArray, q: Int, p: Int) = ((f[q] + q * q) - (f[p] + p * p)) / (2f * (q - p))
