package com.ndelic.kadar.video

import androidx.annotation.StringRes
import com.ndelic.kadar.R
import com.ndelic.kadar.model.easeOut

/**
 * How a clip of the main track comes in or goes out. A fade shows the canvas background through it (the bars' color or the
 * blurred canvas); a slide moves it a whole canvas [dx], [dy] (y down), the way it moves; zooms and spins scale it about
 * its center.
 */
enum class ClipAnimKind(@StringRes val label: Int, val dx: Int = 0, val dy: Int = 0) {
    None(R.string.anim_none),
    Fade(R.string.anim_fade),
    ZoomIn(R.string.anim_zoom_in),
    ZoomOut(R.string.anim_zoom_out),
    SlideLeft(R.string.anim_slide_left, dx = -1),
    SlideRight(R.string.anim_slide_right, dx = 1),
    SlideUp(R.string.anim_slide_up, dy = -1),
    SlideDown(R.string.anim_slide_down, dy = 1),
    Spin(R.string.transition_spin),
}

/** A clip's entrance or exit: its kind and how long it should run (output time). */
data class ClipAnimation(val kind: ClipAnimKind = ClipAnimKind.None, val durationMs: Long = DEFAULT_CLIP_ANIM_MS)

/** Clip [clip]'s entrance (or [exit]) [kind], playing from [startMs] to [endMs] of the output. */
data class ClipAnimSpan(val clip: Int, val kind: ClipAnimKind, val exit: Boolean, val startMs: Long, val endMs: Long) {
    operator fun contains(ms: Long) = ms in startMs until endMs

    /** How far in place the clip is at output time [ms]: 0 out of view, 1 in place; an exit is its entrance played backwards. */
    fun presence(ms: Long) = ((ms - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f).let { easeOut(if (exit) 1 - it else it) }
}

/** How long a clip's entrance or exit runs at most: half its [playedMs] (without the head a transition plays over the clip before). */
fun maxClipAnimMs(playedMs: Long) = playedMs / 2

/**
 * Where each entrance ([ins]) and exit ([outs]) of clips [lengthsMs] long (output time) with [transitions] into them plays
 * in the output: over the first or last [ClipAnimation.durationMs] of the clip as the main track plays it, at most
 * [maxClipAnimMs]. A transition at that cut plays instead, so the clips don't come and go twice.
 */
fun clipAnimSpans(lengthsMs: List<Long>, transitions: List<Transition>, ins: List<ClipAnimation>, outs: List<ClipAnimation>): List<ClipAnimSpan> {
    val heads = headsMs(lengthsMs, transitions)
    fun cut(i: Int) = i in 1..lengthsMs.lastIndex && transitionMs(transitions[i], lengthsMs[i - 1], lengthsMs[i]) > 0
    var start = 0L
    return buildList {
        lengthsMs.indices.forEach { i ->
            val played = lengthsMs[i] - heads[i]
            val end = start + played
            fun ms(a: ClipAnimation) = if (a.kind == ClipAnimKind.None) 0 else minOf(a.durationMs, maxClipAnimMs(played))
            ms(ins[i]).takeIf { it > 0 && !cut(i) }?.let { add(ClipAnimSpan(i, ins[i].kind, false, start, start + it)) }
            ms(outs[i]).takeIf { it > 0 && !cut(i + 1) }?.let { add(ClipAnimSpan(i, outs[i].kind, true, end - it, end)) }
            start = end
        }
    }
}

/** A clip's animated look: [scale] about its center, moved [dx], [dy] (shares of the canvas, y down), turned [degrees] clockwise, at [alpha]. */
data class ClipLook(val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f, val degrees: Float = 0f, val alpha: Float = 1f)

/**
 * The look of [kind] at presence [q] (0 out of view, 1 in place). An exit is the entrance played backwards, except that a
 * slide or a spin keeps going its way.
 */
fun ClipAnimKind.look(q: Float, exit: Boolean): ClipLook {
    if (q >= 1f) return ClipLook()
    val away = (1 - q) * if (exit) -1 else 1
    return when (this) {
        ClipAnimKind.None -> ClipLook()
        ClipAnimKind.Fade -> ClipLook(alpha = q)
        ClipAnimKind.ZoomIn -> ClipLook(scale = q)
        ClipAnimKind.ZoomOut -> ClipLook(scale = 1 + ZOOM_OUT_FROM * (1 - q))
        ClipAnimKind.Spin -> ClipLook(scale = q, degrees = -360f * away)
        else -> ClipLook(dx = -dx * away, dy = -dy * away)
    }
}

const val DEFAULT_CLIP_ANIM_MS = 500L
/** A zoom out comes in from this much bigger (2x). */
private const val ZOOM_OUT_FROM = 1f
