package com.ndelic.kadar.model

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.StringRes
import com.ndelic.kadar.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Rec. 709 luma weights: how much red, green and blue each add to perceived brightness. */
val LUMA = floatArrayOf(0.2126f, 0.7152f, 0.0722f)

/**
 * Filters, each a 4x5 [android.graphics.ColorMatrix] (offsets in 0..255). Built in-house from the [Adjust] math,
 * channel gains and the luma weights; no third-party presets.
 */
enum class Look(@StringRes val label: Int) {
    None(R.string.look_none),
    Mono(R.string.look_mono),
    Noir(R.string.look_noir),
    Sepia(R.string.look_sepia),
    Warm(R.string.look_warm),
    Cool(R.string.look_cool),
    Vivid(R.string.look_vivid),
    Fade(R.string.look_fade),
    TealOrange(R.string.look_teal_orange);

    fun matrix(): FloatArray = when (this) {
        None -> Adjust().colorMatrix()
        Mono -> Adjust(saturation = -1f).colorMatrix()
        Noir -> Adjust(brightness = -0.08f, contrast = 0.45f, saturation = -1f).colorMatrix()
        // Gray tinted brown: red kept, blue cut, a little lift so blacks turn dark brown.
        Sepia -> tintedGray(floatArrayOf(1.07f, 0.89f, 0.66f), floatArrayOf(12f, 6f, 0f))
        Warm -> floatArrayOf(1.06f, 0f, 0f, 0f, 10f, 0f, 1f, 0f, 0f, 4f, 0f, 0f, 0.86f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        Cool -> floatArrayOf(0.9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 4f, 0f, 0f, 1.08f, 0f, 12f, 0f, 0f, 0f, 1f, 0f)
        Vivid -> Adjust(contrast = 0.1f, saturation = 0.35f).colorMatrix()
        // Lifted blacks, dimmed whites, softer color.
        Fade -> Adjust(brightness = 0.1f, contrast = -0.25f, saturation = -0.3f).colorMatrix()
        // Widens the red-minus-blue difference (skin goes orange, sky and shade go teal) and nudges neutrals teal.
        TealOrange -> floatArrayOf(1.25f, 0f, -0.25f, 0f, -8f, 0f, 1f, 0f, 0f, 0f, -0.25f, 0.1f, 1.15f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)
    }
}

/** Every color row is the luma mix scaled by [gain], plus [offset]. */
private fun tintedGray(gain: FloatArray, offset: FloatArray) = FloatArray(20).also { m ->
    for (row in 0..2) {
        for (col in 0..2) m[row * 5 + col] = gain[row] * LUMA[col]
        m[row * 5 + 4] = offset[row]
    }
    m[18] = 1f
}

/**
 * The look blended in by [Adjust.amount], then the sliders (brightness, contrast and saturation, then warmth and tint,
 * hue, fade): one matrix for the image layer paint and the video effect.
 */
fun Adjust.filterMatrix(): FloatArray {
    val identity = Look.None.matrix()
    val look = look.matrix()
    // A color slider at 0 is left out rather than multiplied in, so old projects get exactly the matrix they had.
    val sliders = listOfNotNull(
        gains(1 + WARMTH * warmth, 1 - TINT * tint, 1 - WARMTH * warmth, 0f).takeIf { warmth != 0f || tint != 0f },
        hueTurn(hue * PI.toFloat()).takeIf { hue != 0f },
        gains(1 - FADE_GAIN * fade, 1 - FADE_GAIN * fade, 1 - FADE_GAIN * fade, FADE_LIFT * fade).takeIf { fade != 0f },
    ).fold(colorMatrix()) { m, next -> concat(next, m) }
    return concat(sliders, FloatArray(20) { identity[it] + amount * (look[it] - identity[it]) })
}

/** How far warmth and tint move a channel's gain at the slider's end. */
private const val WARMTH = 0.2f
private const val TINT = 0.2f

/** Full fade: lifts black to this (0..255) and pulls white down by [FADE_GAIN], a flat, washed-out picture. */
private const val FADE_LIFT = 50f
private const val FADE_GAIN = 0.3f

/** Each channel scaled by its gain, plus [offset] (0..255) on all three. */
private fun gains(r: Float, g: Float, b: Float, offset: Float) =
    floatArrayOf(r, 0f, 0f, 0f, offset, 0f, g, 0f, 0f, offset, 0f, 0f, b, 0f, offset, 0f, 0f, 0f, 1f, 0f)

/**
 * The colors turned [angle] radians around the gray axis, keeping brightness: the hue rotation matrix from the W3C
 * filter effects spec (feColorMatrix hueRotate).
 */
private fun hueTurn(angle: Float): FloatArray {
    val c = cos(angle)
    val s = sin(angle)
    return floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** [a] applied after [b]; both 4x5 with the implied fifth row (0, 0, 0, 0, 1). */
fun concat(a: FloatArray, b: FloatArray) = FloatArray(20) { i ->
    val row = i / 5
    val col = i % 5
    (0..3).fold(if (col == 4) a[row * 5 + 4] else 0f) { sum, k -> sum + a[row * 5 + k] * b[k * 5 + col] }
}

/**
 * A 4x5 Android color matrix (offsets 0..255) as Media3's column-major 4x4 RGB matrix, which multiplies (r, g, b, 1)
 * in 0..1: the offsets become the last column, divided by 255. The alpha input column is dropped (video is opaque).
 */
fun glRgbMatrix(m: FloatArray) = FloatArray(16) { i ->
    val col = i / 4
    val row = i % 4
    when {
        col < 3 -> m[row * 5 + col]
        row < 3 -> m[row * 5 + 4] / 255
        else -> 1f
    }
}

/**
 * How far toward black a vignette of [strength] (0..1) takes a pixel at [d], its distance from the center in the
 * ellipse that fits the picture (1 at the middle of each edge, about 1.41 in the corners): nothing in the middle, then
 * a smooth falloff to the edges. The video shader (`VignetteGrain`) has the same curve.
 */
fun vignetteDarkness(d: Float, strength: Float): Float {
    val t = ((d - VIGNETTE_INNER) / (VIGNETTE_OUTER - VIGNETTE_INNER)).coerceIn(0f, 1f)
    return strength * VIGNETTE_MAX * t * t * (3 - 2 * t)
}

/** A full vignette darkens this much (0..1) where it is strongest, from [VIGNETTE_INNER] out to [VIGNETTE_OUTER] (past the corners). */
const val VIGNETTE_MAX = 0.85f
const val VIGNETTE_INNER = 0.45f
const val VIGNETTE_OUTER = 1.45f

/** Full grain takes a speck this much of the way to white or black, at most. */
const val GRAIN_MAX = 0.3f

/** A grain speck's size in pixels of a [width]×[height] picture: the same share of the picture at any resolution. */
fun grainCell(width: Float, height: Float) = maxOf(1f, minOf(width, height) / GRAIN_ACROSS)

private const val GRAIN_ACROSS = 540f

/**
 * This adjustment's vignette and grain as paints over a [width]×[height] picture centered on the origin, to draw over
 * it: in SRC_ATOP, so its transparent pixels stay transparent. Each mixes the picture toward black (or white, for light
 * specks) by its alpha, the same mix as the video shader. [seed] moves the grain pattern, for a preview that plays.
 */
/** [picture] with this look, adjustment, vignette and grain drawn in, as an image layer draws them: a still of a clip as it plays. */
fun Adjust.appliedTo(picture: Bitmap): Bitmap {
    if (isNone && !hasTexture) return picture
    val out = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(picture, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { if (!isNone) colorFilter = ColorMatrixColorFilter(filterMatrix()) })
    // The texture paints are laid out around the picture's center, like an image layer's.
    val w = picture.width.toFloat()
    val h = picture.height.toFloat()
    canvas.translate(w / 2, h / 2)
    texturePaints(w, h).forEach { canvas.drawRect(-w / 2, -h / 2, w / 2, h / 2, it) }
    return out
}

fun Adjust.texturePaints(width: Float, height: Float, seed: Int = 0): List<Paint> = listOfNotNull(
    if (vignette == 0f) null else Paint().apply {
        val at = FloatArray(VIGNETTE_STOPS) { it / (VIGNETTE_STOPS - 1f) }
        val colors = IntArray(VIGNETTE_STOPS) { Color.argb((vignetteDarkness(at[it] * VIGNETTE_OUTER, vignette) * 255).roundToInt(), 0, 0, 0) }
        shader = RadialGradient(0f, 0f, VIGNETTE_OUTER, colors, at, Shader.TileMode.CLAMP).apply { setLocalMatrix(Matrix().apply { setScale(width / 2, height / 2) }) }
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    },
    if (grain == 0f) null else Paint(Paint.FILTER_BITMAP_FLAG).apply {
        val cell = grainCell(width, height)
        shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(cell, cell); postTranslate(-width / 2 + seed * 37 * cell, -height / 2 + seed * 61 * cell) })
        }
        alpha = (grain * GRAIN_MAX * 255).roundToInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    },
)

private const val VIGNETTE_STOPS = 24

/** Grain specks, white or black, each as strong as two random values added and centered (-1..1), so most are faint. */
private val grainTile by lazy {
    val random = Random(GRAIN_TILE)
    val pixels = IntArray(GRAIN_TILE * GRAIN_TILE) {
        val n = random.nextFloat() + random.nextFloat() - 1
        val c = if (n > 0) 255 else 0
        Color.argb((abs(n) * 255).roundToInt(), c, c, c)
    }
    Bitmap.createBitmap(pixels, GRAIN_TILE, GRAIN_TILE, Bitmap.Config.ARGB_8888)
}

private const val GRAIN_TILE = 256
