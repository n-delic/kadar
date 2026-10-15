package com.ndelic.kadar.video

import androidx.annotation.StringRes
import com.ndelic.kadar.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * How one clip turns into the next. Kinds that [overlaps] show both clips at once: the incoming clip's head plays over
 * the end of the clip before (on top, faded or moved in), which makes the video that much shorter. The others pass
 * through a look centered on the cut (black, white, a zoom, a blur, a spin, a flip, a glitch, split colors), one clip at
 * a time. [dx] and [dy] are the way a
 * wipe, slide or push moves, in canvas directions (y down).
 */
enum class TransitionKind(@StringRes val label: Int, val overlaps: Boolean, val dx: Int = 0, val dy: Int = 0) {
    None(R.string.transition_none, false),
    Dissolve(R.string.transition_dissolve, true),
    Black(R.string.transition_black, false),
    White(R.string.transition_white, false),
    Zoom(R.string.transition_zoom, false),
    Blur(R.string.transition_blur, false),
    Spin(R.string.transition_spin, false),
    Flip(R.string.transition_flip, false),
    Glitch(R.string.transition_glitch, false),
    ColorSplit(R.string.transition_color_split, false),
    WipeLeft(R.string.transition_wipe_left, true, dx = -1),
    WipeUp(R.string.transition_wipe_up, true, dy = -1),
    SlideLeft(R.string.transition_slide_left, true, dx = -1),
    SlideUp(R.string.transition_slide_up, true, dy = -1),
    PushLeft(R.string.transition_push_left, true, dx = -1),
    PushUp(R.string.transition_push_up, true, dy = -1),
    ;

    val wipes get() = this == WipeLeft || this == WipeUp
    val pushes get() = this == PushLeft || this == PushUp
    val slides get() = this == SlideLeft || this == SlideUp || pushes
}

/** The transition into a clip from the one before it: its kind and how long it should run (output time). */
data class Transition(val kind: TransitionKind = TransitionKind.None, val durationMs: Long = DEFAULT_TRANSITION_MS)

/** The longest transition between clips [prevMs] and [nextMs] long (output time): half the shorter, so a clip's two transitions never meet. */
fun maxTransitionMs(prevMs: Long, nextMs: Long) = minOf(prevMs, nextMs) / 2

/** How long [t] between clips [prevMs] and [nextMs] long really runs: 0 for none, or when the clips are too short for one. */
fun transitionMs(t: Transition, prevMs: Long, nextMs: Long): Long {
    if (t.kind == TransitionKind.None) return 0
    val ms = minOf(t.durationMs, maxTransitionMs(prevMs, nextMs))
    return if (ms < MIN_TRANSITION_MS) 0 else ms
}

/**
 * For clips [lengthsMs] long (output time) with [transitions] into them: how much of each clip's start plays over the
 * clip before it, under an overlapping transition. The main track leaves those heads out.
 */
fun headsMs(lengthsMs: List<Long>, transitions: List<Transition>) = lengthsMs.indices.map { i ->
    if (i == 0 || !transitions[i].kind.overlaps) 0L else transitionMs(transitions[i], lengthsMs[i - 1], lengthsMs[i])
}

/** Transition [kind] into clip [clip], playing from [startMs] to [endMs] of the output. */
data class TransitionSpan(val clip: Int, val kind: TransitionKind, val startMs: Long, val endMs: Long) {
    operator fun contains(ms: Long) = ms in startMs until endMs

    /** 0 to 1 through the transition at output time [ms]. */
    fun progress(ms: Long) = ((ms - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}

/**
 * Where each transition plays in the output of clips [lengthsMs] long with [transitions] into them: an overlapping one
 * over the end of the clip before (the cut is where the main track switches clips), any other centered on the cut.
 */
fun transitionSpans(lengthsMs: List<Long>, transitions: List<Transition>): List<TransitionSpan> {
    val heads = headsMs(lengthsMs, transitions)
    var cut = 0L
    return buildList {
        lengthsMs.indices.forEach { i ->
            val ms = if (i == 0) 0 else transitionMs(transitions[i], lengthsMs[i - 1], lengthsMs[i])
            val kind = transitions[i].kind
            if (ms > 0) add(if (kind.overlaps) TransitionSpan(i, kind, cut - ms, cut) else TransitionSpan(i, kind, cut - ms / 2, cut + ms - ms / 2))
            cut += lengthsMs[i] - heads[i]
        }
    }
}

/**
 * How long each head of the overlapping [spans] plays on past its cut, at its final place: [tailMs], or until the next
 * one starts. Media3's compositor pairs every output frame with the nearest frame of each input, which for the frame
 * just before a cut can be the first of the gap after a head, and that frame is hidden. The preview's tail is longer
 * ([PREVIEW_TAIL_MS]): its main player shows the clip before for a frame or two after switching clips.
 */
fun headTailsMs(spans: List<TransitionSpan>, tailMs: Long = HEAD_TAIL_MS) =
    spans.mapIndexed { k, s -> minOf(tailMs, (spans.getOrNull(k + 1)?.startMs ?: Long.MAX_VALUE) - s.endMs) }

/** How far into the cut a centered transition is at [p] (0 to 1 through it): 0 at its ends, 1 at the cut. */
fun dipAt(p: Float) = 1 - abs(1 - 2 * p)

/** How far into a Black and a White transition among [spans] output time [ms] is, each 0 outside it and 1 at its cut. */
fun colorDips(spans: List<TransitionSpan>, ms: Long): Pair<Float, Float> {
    fun at(kind: TransitionKind) = spans.firstOrNull { it.kind == kind && ms in it }?.let { dipAt(it.progress(ms)) } ?: 0f
    return at(TransitionKind.Black) to at(TransitionKind.White)
}

/**
 * The incoming clip's sound at [p] (0 to 1 through an overlapping transition), the outgoing one's at 1 - [p]: an
 * equal-power crossfade, so the two together stay as loud as either alone instead of dipping in the middle.
 */
fun crossfadeGain(p: Float) = sin(p * PI / 2).toFloat()

/** The incoming clip's alpha at [p]: a dissolve fades it in, the others show it whole (moved or wiped in). */
fun TransitionKind.incomingAlpha(p: Float) = if (this == TransitionKind.Dissolve) p else 1f

/** Where a sliding or pushed-in incoming clip is at [p], as a fraction of the canvas (x, y; y down): it comes from the side it moves away from. */
fun TransitionKind.incomingOffset(p: Float) = if (slides) (1 - ease(p)).let { -dx * it to -dy * it } else 0f to 0f

/** Where a pushed-out outgoing clip is at [p], as a fraction of the canvas (x, y; y down). */
fun TransitionKind.outgoingOffset(p: Float) = if (pushes) ease(p).let { dx * it to dy * it } else 0f to 0f

/**
 * The part of the canvas a wipe shows of the incoming clip at [p]: left, top, right and bottom as fractions (y down),
 * growing from the side it moves away from. Null for kinds that don't wipe.
 */
fun TransitionKind.wipeRect(p: Float): FloatArray? {
    if (!wipes) return null
    val e = ease(p)
    return floatArrayOf(if (dx < 0) 1 - e else 0f, if (dy < 0) 1 - e else 0f, if (dx > 0) e else 1f, if (dy > 0) e else 1f)
}

/** How far a spin has turned the frame at [p], in degrees: a whole turn, half of it by the cut, fastest there. */
fun spinDegrees(p: Float) = 360f * ease(p)

/** How much bigger a spin shows the frame at [p]: up to [SPIN_ZOOM] at the cut, so the turned frame's corners stay mostly filled. */
fun spinScale(p: Float) = 1 + SPIN_ZOOM * dipAt(p).let { it * it }

/** How wide a flip shows the frame at [p], as a share of it: a card turning at an even pace, edge-on at the cut. */
fun flipWidth(p: Float) = cos(dipAt(p) * PI / 2).toFloat()

/**
 * Which pattern of shifted rows a glitch shows at output time [ms]: a new one every [GLITCH_STEP_MS], from the frame's
 * time and nothing else, so the preview and the export glitch alike. Kept small for the GPU's float precision.
 */
fun glitchSeed(ms: Long) = (ms / GLITCH_STEP_MS % 1000).toFloat()

/** Slow at both ends. */
private fun ease(p: Float) = p * p * (3 - 2 * p)

const val DEFAULT_TRANSITION_MS = 500L
/** At the cut a spin shows the frame this much bigger (1.5x). */
const val SPIN_ZOOM = 0.5f
const val GLITCH_STEP_MS = 60L
/** The shortest a transition runs; capped below this by short clips, there is none. */
const val MIN_TRANSITION_MS = 100L
/** At least one frame of a clip down to 20 fps. */
const val HEAD_TAIL_MS = 50L
const val PREVIEW_TAIL_MS = 250L
