package com.ndelic.kadar.model

import android.graphics.Canvas
import androidx.annotation.StringRes
import com.ndelic.kadar.R
import kotlin.math.hypot
import kotlin.math.roundToInt

/** How a text or sticker layer enters or leaves a video. */
enum class Anim(@StringRes val label: Int) { None(R.string.anim_none), Fade(R.string.anim_fade), Pop(R.string.anim_pop), SlideUp(R.string.anim_slide_up) }

/** A layer's animated look at one moment: [dy] is a fraction of the canvas height added to its y. */
data class Motion(val alpha: Float = 1f, val scale: Float = 1f, val dy: Float = 0f)

val Steady = Motion()

const val ANIM_MS = 300L

/** How long each animation runs for a layer shown from [start] to [end]: at most half that, so short layers still settle. */
fun animMs(start: Long, end: Long) = minOf(ANIM_MS, (end - start) / 2)

/**
 * The look at output time [ms] of a layer shown from [start] to [end] (the real end, not [Long.MAX_VALUE]):
 * [animIn] plays over its first [animMs], [animOut] over its last. Outside its time it's [Steady]; whether it shows at all
 * is up to the caller (the stage keeps the selected layer on screen).
 */
fun motionAt(animIn: Anim, animOut: Anim, start: Long, end: Long, ms: Long): Motion {
    val d = animMs(start, end)
    if (d <= 0 || ms < start || ms >= end) return Steady
    return when {
        ms - start < d -> animIn.entering((ms - start).toFloat() / d)
        end - ms < d -> animOut.leaving((end - ms).toFloat() / d)
        else -> Steady
    }
}

fun Layer.motionAt(ms: Long, end: Long) = motionAt(animIn, animOut, startMs, end, ms)

/** The stretches of time in which a layer animates, so a timeline split at them is animated all through a piece or not at all. */
fun animationSpans(animIn: Anim, animOut: Anim, start: Long, end: Long): List<Pair<Long, Long>> {
    val d = animMs(start, end)
    if (d <= 0) return emptyList()
    return listOfNotNull((start to start + d).takeIf { animIn != Anim.None }, (end - d to end).takeIf { animOut != Anim.None })
}

/** [p] runs 0 to 1 as the layer comes in. */
private fun Anim.entering(p: Float) = when (this) {
    Anim.None -> Steady
    Anim.Fade -> Motion(alpha = easeOut(p))
    Anim.Pop -> Motion(alpha = easeOut(p), scale = 0.6f + 0.4f * backOut(p))
    Anim.SlideUp -> Motion(alpha = easeOut(p), dy = 0.08f * (1 - easeOut(p)))
}

/** [p] runs 1 to 0 as the layer goes: the entrance played backwards, except a slide keeps going up. */
private fun Anim.leaving(p: Float) = when (this) {
    Anim.SlideUp -> Motion(alpha = easeOut(p), dy = -0.08f * (1 - easeOut(p)))
    else -> entering(p)
}

private fun easeOut(p: Float) = 1 - (1 - p) * (1 - p) * (1 - p)

/** Overshoots to about 1.1 before settling at 1. */
private fun backOut(p: Float): Float {
    val q = p - 1
    return 1 + 2.70158f * q * q * q + 1.70158f * q * q
}

/** [drawLayer] with [m] applied around the layer's center; [canvasHeight] turns its slide into canvas pixels. */
fun Canvas.drawLayer(layer: Layer, m: Motion, canvasHeight: Int) {
    if (m.alpha <= 0f) return
    val x = layer.t.x
    val y = layer.t.y + m.dy * canvasHeight
    // A fade goes through one offscreen layer, so an outline doesn't show through its fill. Its bounds cover any
    // rotation, with room for a text shadow.
    val r = hypot(layer.width, layer.height) * layer.t.scale * m.scale * 0.6f
    val count = if (m.alpha < 1f) saveLayerAlpha(x - r, y - r, x + r, y + r, (m.alpha * 255).roundToInt()) else save()
    translate(x, y)
    scale(m.scale, m.scale)
    translate(-layer.t.x, -layer.t.y)
    drawLayer(layer)
    restoreToCount(count)
}
