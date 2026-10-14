package com.ndelic.kadar.video

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Output length of [startMs]..[endMs] of a source played at [speed]. */
fun outputMs(startMs: Long, endMs: Long, speed: Float) = ((endMs - startMs) / speed).toLong()

/**
 * Which of clips [lengthsMs] long (output time, end to end) plays at output time [positionMs], and how far into it.
 * A clip owns its start, not its end; the end of the output belongs to the last clip.
 */
fun clipAt(lengthsMs: List<Long>, positionMs: Long): Pair<Int, Long> {
    var start = 0L
    lengthsMs.forEachIndexed { i, length ->
        if (positionMs < start + length || i == lengthsMs.lastIndex) return i to (positionMs - start).coerceIn(0, length)
        start += length
    }
    error("No clips")
}

/** Source time [intoMs] (output time) into a clip trimmed to start at [startMs] and played at [speed]. */
fun sourceMs(startMs: Long, speed: Float, intoMs: Long) = startMs + (intoMs * speed).toLong()

/**
 * Where to cut a clip trimmed to [startMs]..[endMs] at [speed], [intoMs] (output time) in: the source time both pieces
 * meet at, or null when either would be shorter than [MIN_CLIP_MS].
 */
fun splitPoint(startMs: Long, endMs: Long, speed: Float, intoMs: Long): Long? =
    sourceMs(startMs, speed, intoMs).takeIf { it - startMs >= MIN_CLIP_MS && endMs - it >= MIN_CLIP_MS }

/**
 * A clip trimmed to [startMs]..[endMs] of a [durationMs] source (a photo's longest showing) at [speed], with its left
 * ([left]) or right edge dragged [dMs] of output time: the new trim, inside the source and at least [MIN_CLIP_MS] long.
 */
fun edgeTrim(startMs: Long, endMs: Long, durationMs: Long, speed: Float, left: Boolean, dMs: Long): Pair<Long, Long> {
    val d = (dMs * speed).toLong()
    // Bounds can cross for a source shorter than MIN_CLIP_MS; coerceIn would throw.
    return if (left) (startMs + d).coerceIn(0, maxOf(0, endMs - MIN_CLIP_MS)) to endMs
    else startMs to (endMs + d).coerceIn(minOf(startMs + MIN_CLIP_MS, durationMs), durationMs)
}

/**
 * The speed at [p] (0 to 1) along the speed slider: [MIN_SPEED] to [MAX_SPEED] by equal ratios, so 1x sits in the
 * middle-ish and slow and fast get the same room; rounded to 0.01 below 1x and 0.1 above, and caught by 0.5x, 1x and 2x
 * when within 8%.
 */
fun speedAt(p: Float): Float {
    val s = MIN_SPEED * exp(p.coerceIn(0f, 1f) * ln(MAX_SPEED / MIN_SPEED))
    SPEED_SNAPS.firstOrNull { abs(ln(s / it)) < 0.08f }?.let { return it }
    return if (s < 1) (s * 100).roundToInt() / 100f else (s * 10).roundToInt() / 10f
}

/** Where [speed] sits on the speed slider, 0 to 1: the inverse of [speedAt]. */
fun speedPosition(speed: Float) = (ln(speed / MIN_SPEED) / ln(MAX_SPEED / MIN_SPEED)).coerceIn(0f, 1f)

const val MIN_SPEED = 0.2f
const val MAX_SPEED = 10f
private val SPEED_SNAPS = listOf(0.5f, 1f, 2f)

/**
 * Where to cut a layer shown [startMs]..[endMs] (output time) at [atMs]: [atMs], or null when either part would be shorter
 * than [MIN_CLIP_MS]. A layer that runs to the end ([Long.MAX_VALUE]) ends at [totalMs], the output's length.
 */
fun layerSplitPoint(startMs: Long, endMs: Long, totalMs: Long, atMs: Long): Long? =
    atMs.takeIf { it - startMs >= MIN_CLIP_MS && minOf(endMs, totalMs) - it >= MIN_CLIP_MS }

/** A layer shown [startMs]..[endMs] moved to start at [toMs], as long as before; one that runs to the end still does. */
fun layerMovedTo(startMs: Long, endMs: Long, toMs: Long): Pair<Long, Long> =
    toMs to if (endMs == Long.MAX_VALUE) endMs else toMs + (endMs - startMs)

/** This list with the item at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int) = toMutableList().apply { add(to, removeAt(from)) }

/** The shortest a clip or a layer can be trimmed to; the encoder can't do an empty one. */
const val MIN_CLIP_MS = 300L
