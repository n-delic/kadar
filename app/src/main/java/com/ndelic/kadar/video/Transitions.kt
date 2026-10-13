package com.ndelic.kadar.video

import androidx.annotation.StringRes
import com.ndelic.kadar.R
import kotlin.math.abs

/**
 * How one clip turns into the next. Kinds that [overlaps] show both clips at once: the incoming clip's head plays over
 * the end of the clip before (on top, faded or moved in), which makes the video that much shorter. The others pass
 * through a look centered on the cut (black, white, a zoom, a blur), one clip at a time. [dx] and [dy] are the way a
 * wipe, slide or push moves, in canvas directions (y down).
 */
enum class TransitionKind(@StringRes val label: Int, val overlaps: Boolean, val dx: Int = 0, val dy: Int = 0) {
    None(R.string.transition_none, false),
    Dissolve(R.string.transition_dissolve, true),
    Black(R.string.transition_black, false),
    White(R.string.transition_white, false),
    Zoom(R.string.transition_zoom, false),
    Blur(R.string.transition_blur, false),
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
 * How long each head of the overlapping [spans] plays on past its cut, at its final place: [HEAD_TAIL_MS], or until
 * the next one starts. Media3's compositor pairs every output frame with the nearest frame of each input, which for
 * the frame just before a cut can be the first of the gap after a head, and that frame is hidden.
 */
fun headTailsMs(spans: List<TransitionSpan>) =
    spans.mapIndexed { k, s -> minOf(HEAD_TAIL_MS, (spans.getOrNull(k + 1)?.startMs ?: Long.MAX_VALUE) - s.endMs) }

/** How far into the cut a centered transition is at [p] (0 to 1 through it): 0 at its ends, 1 at the cut. */
fun dipAt(p: Float) = 1 - abs(1 - 2 * p)

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

/** Slow at both ends. */
private fun ease(p: Float) = p * p * (3 - 2 * p)

const val DEFAULT_TRANSITION_MS = 500L
/** The shortest a transition runs; capped below this by short clips, there is none. */
const val MIN_TRANSITION_MS = 100L
/** At least one frame of a clip down to 20 fps. */
const val HEAD_TAIL_MS = 50L
