package com.ndelic.kadar.model

import android.graphics.Canvas
import androidx.annotation.StringRes
import com.ndelic.kadar.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How a text or sticker layer enters or leaves a video, over [ms]. [perLetter] ones (text entrances only) animate each
 * letter: [Typewriter] shows them one by one, [LettersPop] pops them in one after another.
 */
enum class Anim(@StringRes val label: Int, val ms: Long = ANIM_MS, val perLetter: Boolean = false) {
    None(R.string.anim_none), Fade(R.string.anim_fade), Pop(R.string.anim_pop), SlideUp(R.string.anim_slide_up),
    Typewriter(R.string.anim_typewriter, LETTERS_MS, true), LettersPop(R.string.anim_letters_pop, LETTERS_MS, true),
}

/** Motion that repeats for as long as a layer shows, on top of its entrance and exit. */
enum class Loop(@StringRes val label: Int) { None(R.string.anim_none), Wiggle(R.string.loop_wiggle), Pulse(R.string.loop_pulse), Bounce(R.string.loop_bounce) }

/** A per-letter entrance [p] (0 to 1) of the way in; [letterScale] says how each letter looks. */
data class Reveal(val anim: Anim, val p: Float)

/**
 * A layer's animated look at one moment: [dy] is a fraction of the canvas height added to its y, [rotation] degrees added
 * to its own, and [reveal] a per-letter entrance in progress (text only).
 */
data class Motion(val alpha: Float = 1f, val scale: Float = 1f, val dy: Float = 0f, val rotation: Float = 0f, val reveal: Reveal? = null)

val Steady = Motion()

const val ANIM_MS = 300L
const val LETTERS_MS = 1200L

/** How long [anim] runs for a layer shown from [start] to [end]: at most half that, so short layers still settle. */
fun animMs(anim: Anim, start: Long, end: Long) = minOf(anim.ms, (end - start) / 2)

/**
 * The look at output time [ms] of a layer shown from [start] to [end] (the real end, not [Long.MAX_VALUE]):
 * [animIn] plays over its first [animMs], [animOut] over its last, and [loop] all through. Outside its time it's [Steady];
 * whether it shows at all is up to the caller (the stage keeps the selected layer on screen).
 */
fun motionAt(animIn: Anim, animOut: Anim, start: Long, end: Long, ms: Long, loop: Loop = Loop.None): Motion {
    if (ms < start || ms >= end) return Steady
    val dIn = animMs(animIn, start, end)
    val dOut = animMs(animOut, start, end)
    val ends = when {
        dIn > 0 && ms - start < dIn -> animIn.entering((ms - start).toFloat() / dIn)
        dOut > 0 && end - ms < dOut -> animOut.leaving((end - ms).toFloat() / dOut)
        else -> Steady
    }
    if (loop == Loop.None) return ends
    val l = loop.at(ms - start)
    return ends.copy(scale = ends.scale * l.scale, dy = ends.dy + l.dy, rotation = ends.rotation + l.rotation)
}

fun Layer.motionAt(ms: Long, end: Long) = motionAt(animIn, animOut, startMs, end, ms, animLoop)

/** The stretches of time in which a layer animates, so a timeline split at them is animated all through a piece or not at all. */
fun animationSpans(animIn: Anim, animOut: Anim, start: Long, end: Long, loop: Loop = Loop.None): List<Pair<Long, Long>> {
    if (end <= start) return emptyList()
    if (loop != Loop.None) return listOf(start to end)
    val dIn = animMs(animIn, start, end)
    val dOut = animMs(animOut, start, end)
    return listOfNotNull((start to start + dIn).takeIf { animIn != Anim.None && dIn > 0 }, (end - dOut to end).takeIf { animOut != Anim.None && dOut > 0 })
}

/** [t] ms after the layer shows up; every loop is at rest at 0, so it starts without a jump. */
private fun Loop.at(t: Long): Motion = when (this) {
    Loop.None -> Steady
    Loop.Wiggle -> Motion(rotation = 6f * sin(2 * PI * t / 700).toFloat())
    Loop.Pulse -> Motion(scale = 1 + 0.08f * (1 - cos(2 * PI * t / 900).toFloat()) / 2)
    Loop.Bounce -> Motion(dy = -0.03f * abs(sin(PI * t / 650).toFloat()))
}

/**
 * How big letter [i] of [n] draws (1 is normal, 0 is not drawn) [p] (0 to 1) of the way into a per-letter entrance:
 * a typewriter shows the first `p * n` letters; letters pop in one after another, each over [POP_SHARE] of the time.
 */
fun letterScale(anim: Anim, p: Float, i: Int, n: Int): Float = when (anim) {
    Anim.Typewriter -> if (i < p * n) 1f else 0f
    Anim.LettersPop -> {
        val from = if (n <= 1) 0f else i.toFloat() / (n - 1) * (1 - POP_SHARE)
        val q = ((p - from) / POP_SHARE).coerceIn(0f, 1f)
        if (q <= 0f) 0f else backOut(q)
    }
    else -> 1f
}

private const val POP_SHARE = 0.4f

/** [p] runs 0 to 1 as the layer comes in. */
private fun Anim.entering(p: Float) = when (this) {
    Anim.None -> Steady
    Anim.Fade -> Motion(alpha = easeOut(p))
    Anim.Pop -> Motion(alpha = easeOut(p), scale = 0.6f + 0.4f * backOut(p))
    Anim.SlideUp -> Motion(alpha = easeOut(p), dy = 0.08f * (1 - easeOut(p)))
    Anim.Typewriter, Anim.LettersPop -> Motion(reveal = Reveal(this, p))
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

/**
 * [drawLayer] with [m] applied around the layer's center; [canvasHeight] turns its slide into canvas pixels. A per-letter
 * reveal is handed to the layer's own drawing, so the letters still go through [TextLayer]'s one text path.
 */
fun Canvas.drawLayer(layer: Layer, m: Motion, canvasHeight: Int) {
    if (m.alpha <= 0f) return
    val x = layer.t.x
    val y = layer.t.y + m.dy * canvasHeight
    // A fade goes through one offscreen layer, so an outline doesn't show through its fill. Its bounds cover any
    // rotation, with room for a text shadow.
    val r = layer.reach * layer.t.scale * m.scale * 1.2f
    val count = if (m.alpha < 1f) saveLayerAlpha(x - r, y - r, x + r, y + r, (m.alpha * 255).roundToInt()) else save()
    translate(x, y)
    rotate(m.rotation)
    scale(m.scale, m.scale)
    translate(-layer.t.x, -layer.t.y)
    drawLayer(layer, m.reveal)
    restoreToCount(count)
}
