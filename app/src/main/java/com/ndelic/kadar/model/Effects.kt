package com.ndelic.kadar.model

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.ndelic.kadar.R
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** What an image layer's [Effect] does to its picture. */
enum class EffectKind(@StringRes val label: Int) {
    None(R.string.look_none),
    Blur(R.string.effect_blur),
    Pixelate(R.string.effect_pixelate),
    Posterize(R.string.effect_posterize),
    Duotone(R.string.effect_duotone),
    Sharpen(R.string.effect_sharpen),
    Glitch(R.string.effect_glitch),
    PopArt(R.string.effect_pop_art),
}

/** The duotone effect's color pairs, shadows then highlights. */
val DUOTONES = listOf(
    0xFF1D2B6B.toInt() to 0xFFFF8FB1.toInt(),
    0xFF3A0D4F.toInt() to 0xFFFFD23F.toInt(),
    0xFF0B3C3D.toInt() to 0xFFFFA45B.toInt(),
    0xFF4A0E0E.toInt() to 0xFFF6E7C8.toInt(),
    0xFF101010.toInt() to 0xFFC6F432.toInt(),
)

/**
 * One effect on an image layer's picture, [amount] (0 to 1) strong; [dark] and [light] are the duotone's colors. It changes
 * the picture's own pixels, under the layer's look, sliders, vignette, grain, outline and blend.
 */
data class Effect(
    val kind: EffectKind = EffectKind.None,
    val amount: Float = 0.5f,
    val dark: Int = DUOTONES[0].first,
    val light: Int = DUOTONES[0].second,
)

/** The duotone as a color matrix (it's linear, so it goes on the paint at full size, no pixels redone); null for the others. */
fun Effect.matrix(): FloatArray? = if (kind == EffectKind.Duotone) duotoneMatrix(dark, light, amount) else null

/** Each pixel's luma ([LUMA]) mapped from [dark] (black) to [light] (white), blended into the picture by [amount]; alpha kept. */
fun duotoneMatrix(dark: Int, light: Int, amount: Float): FloatArray {
    val m = Look.None.matrix()
    for (row in 0..2) {
        val shift = 16 - 8 * row
        val d = (dark shr shift and 0xFF).toFloat()
        val l = (light shr shift and 0xFF).toFloat()
        for (k in 0..2) m[row * 5 + k] = (1 - amount) * m[row * 5 + k] + amount * (l - d) / 255 * LUMA[k]
        m[row * 5 + 4] = amount * d
    }
    return m
}

// Per picture, not per layer: a drag and the video export make new copies of a layer, which find the picture already done.
private val done = WeakHashMap<Bitmap, LinkedHashMap<Effect, Bitmap>>()

/**
 * [picture] with [effect] on its pixels, drawn over the picture's own bounds: built once and kept while [picture] lives (the
 * last 2 effects of it). The picture itself when the effect needs no pixels redone. Pictures are at most a photo's
 * decode size (`MAX_PHOTO`), which keeps the work bounded; a blur works at a small size and is drawn scaled up.
 */
fun effected(picture: Bitmap, effect: Effect): Bitmap {
    if (effect.kind == EffectKind.None || effect.kind == EffectKind.Duotone || effect.amount <= 0f) return picture
    return synchronized(done) {
        val built = done.getOrPut(picture) { LinkedHashMap() }
        // A result that is the picture itself isn't kept: a WeakHashMap value pointing at its own key never lets it go.
        built[effect] ?: build(picture, effect).also { if (it !== picture) built[effect] = it; if (built.size > 2) built.remove(built.keys.first()) }
    }
}

private fun build(picture: Bitmap, effect: Effect): Bitmap {
    val long = max(picture.width, picture.height)
    if (effect.kind == EffectKind.Blur) {
        val r = effect.amount * BLUR * long
        if (r < 0.5f) return picture
        // The blur's radius is BLUR_STEPS pixels at a size that much smaller, so a strong blur costs no more than a light one.
        val k = minOf(1f, BLUR_STEPS / r)
        val small = Bitmap.createScaledBitmap(picture, max(1, (picture.width * k).roundToInt()), max(1, (picture.height * k).roundToInt()), true)
        return small.edited { px, w, h -> boxBlur(px, w, h, max(1, (r * k).roundToInt())); px }
    }
    return picture.edited { px, w, h ->
        when (effect.kind) {
            EffectKind.Pixelate -> pixelate(px, w, h, max(1, (effect.amount * PIXEL * long).roundToInt()))
            EffectKind.Posterize -> px.also { posterize(it, (2 + (1 - effect.amount) * 6).roundToInt()) }
            EffectKind.Sharpen -> sharpen(px, w, h, effect.amount * SHARPEN)
            EffectKind.Glitch -> glitch(px, w, h, effect.amount)
            EffectKind.PopArt -> px.also { popArt(it, effect.amount) }
            else -> px
        }
    }
}

/** A new bitmap of [change] applied to this one's pixels (unpremultiplied ARGB). */
private fun Bitmap.edited(change: (IntArray, Int, Int) -> IntArray): Bitmap {
    val px = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    return Bitmap.createBitmap(change(px, width, height), width, height, Bitmap.Config.ARGB_8888)
}

/** A full blur's radius, a pixelate's full cell size, and a full sharpen's gain; parts of the picture's longer side. */
private const val BLUR = 0.03f
private const val PIXEL = 0.05f
private const val SHARPEN = 2.5f

/** How many pixels a blur's radius is at the size it's worked out at. */
private const val BLUR_STEPS = 4f

/** Each [cell]×[cell] square's average color (weighted by alpha, so see-through pixels don't darken it) and alpha. */
fun pixelate(px: IntArray, w: Int, h: Int, cell: Int): IntArray {
    val out = IntArray(px.size)
    for (cy in 0 until h step cell) for (cx in 0 until w step cell) {
        var a = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        val ys = cy until minOf(h, cy + cell)
        val xs = cx until minOf(w, cx + cell)
        for (y in ys) for (x in xs) {
            val p = px[y * w + x]
            val pa = (p ushr 24).toLong()
            a += pa
            r += (p shr 16 and 0xFF) * pa
            g += (p shr 8 and 0xFF) * pa
            b += (p and 0xFF) * pa
        }
        val n = ys.count() * xs.count()
        val c = if (a == 0L) 0 else ((a / n).toInt() shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (b / a).toInt()
        for (y in ys) for (x in xs) out[y * w + x] = c
    }
    return out
}

/** Each color channel rounded to one of [levels] evenly spaced values; alpha kept. */
fun posterize(px: IntArray, levels: Int) {
    val steps = (levels - 1).coerceAtLeast(1)
    val table = IntArray(256) { ((it * steps / 255f).roundToInt() * 255f / steps).roundToInt() }
    for (i in px.indices) {
        val p = px[i]
        px[i] = (p and 0xFF000000.toInt()) or (table[p shr 16 and 0xFF] shl 16) or (table[p shr 8 and 0xFF] shl 8) or table[p and 0xFF]
    }
}

/** An unsharp mask: each channel pushed away from its 3×3 neighbourhood's average by [gain]; alpha kept. */
fun sharpen(px: IntArray, w: Int, h: Int, gain: Float): IntArray {
    val out = IntArray(px.size)
    val own = 1 + gain
    val each = gain / 9
    fun channel(v: Int, sum: Int) = (own * v - each * sum + 0.5f).toInt().coerceIn(0, 255)
    // The channels 16 bits apart in a Long, so one add sums all three of a 3×3 neighbourhood.
    fun packed(p: Int) = ((p shr 16 and 0xFF).toLong() shl 32) or ((p shr 8 and 0xFF).toLong() shl 16) or (p and 0xFF).toLong()
    for (y in 0 until h) {
        // Past the border the edge pixel repeats.
        val rows = intArrayOf(maxOf(y - 1, 0) * w, y * w, minOf(y + 1, h - 1) * w)
        for (x in 0 until w) {
            val left = maxOf(x - 1, 0)
            val right = minOf(x + 1, w - 1)
            var sum = 0L
            for (row in rows) sum += packed(px[row + left]) + packed(px[row + x]) + packed(px[row + right])
            val p = px[y * w + x]
            out[y * w + x] = (p and 0xFF000000.toInt()) or (channel(p shr 16 and 0xFF, (sum shr 32).toInt()) shl 16) or
                (channel(p shr 8 and 0xFF, (sum shr 16).toInt() and 0xFFFF) shl 8) or channel(p and 0xFF, sum.toInt() and 0xFFFF)
        }
    }
    return out
}

/**
 * Bands of rows slid sideways and the red and blue channels pulled apart, both further at a higher [amount]. The bands
 * come from a fixed seed, so the same picture always glitches the same way. A see-through neighbour lends no color.
 */
fun glitch(px: IntArray, w: Int, h: Int, amount: Float): IntArray {
    val random = Random(GLITCH_SEED)
    val rowShift = IntArray(h)
    var y = 0
    while (y < h) {
        val band = max(1, (h * (0.02f + 0.08f * random.nextFloat())).roundToInt())
        val shift = if (random.nextFloat() < 0.4f) ((random.nextFloat() * 2 - 1) * GLITCH_SLIDE * amount * w).roundToInt() else 0
        for (r in y until minOf(h, y + band)) rowShift[r] = shift
        y += band
    }
    val split = (GLITCH_SPLIT * amount * w).roundToInt()
    val out = IntArray(px.size)
    for (row in 0 until h) {
        val base = row * w
        for (i in 0 until w) {
            val x = i - rowShift[row]
            val p = px[base + x.coerceIn(0, w - 1)]
            var red = px[base + (x + split).coerceIn(0, w - 1)]
            var blue = px[base + (x - split).coerceIn(0, w - 1)]
            if (red ushr 24 == 0) red = p
            if (blue ushr 24 == 0) blue = p
            out[base + i] = (p and 0xFF00FF00.toInt()) or (red and 0xFF0000) or (blue and 0xFF)
        }
    }
    return out
}

private const val GLITCH_SEED = 7
private const val GLITCH_SLIDE = 0.08f
private const val GLITCH_SPLIT = 0.02f

/** Bold, flat color: saturation raised, then posterized to fewer levels the higher the [amount]; alpha kept. */
fun popArt(px: IntArray, amount: Float) {
    val sat = 1 + 2 * amount
    for (i in px.indices) {
        val p = px[i]
        val r = p shr 16 and 0xFF
        val g = p shr 8 and 0xFF
        val b = p and 0xFF
        val l = LUMA[0] * r + LUMA[1] * g + LUMA[2] * b
        fun boost(v: Int) = (l + sat * (v - l)).roundToInt().coerceIn(0, 255)
        px[i] = (p and 0xFF000000.toInt()) or (boost(r) shl 16) or (boost(g) shl 8) or boost(b)
    }
    posterize(px, (5 - 3 * amount).roundToInt())
}
