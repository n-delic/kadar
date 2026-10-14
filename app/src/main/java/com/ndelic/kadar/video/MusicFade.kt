package com.ndelic.kadar.video

/** How long a fade took in projects from before fade lengths, where music faded in or out or didn't. */
const val MUSIC_FADE_MS = 1500L

/** A fade's length on a part [lengthMs] long: on a part shorter than two fades they meet in the middle. */
private fun fadeLength(fadeMs: Long, lengthMs: Long) = minOf(fadeMs, lengthMs / 2)

/** The fade factor (0 silent to 1 full) at output time [t] of a part playing `[from, to)` that fades in and out over [fadeInMs] and [fadeOutMs]. */
fun fadeGain(t: Long, from: Long, to: Long, fadeInMs: Long, fadeOutMs: Long): Float {
    val fadeIn = fadeLength(fadeInMs, to - from)
    val fadeOut = fadeLength(fadeOutMs, to - from)
    val rising = if (fadeIn > 0) (t - from) / fadeIn.toFloat() else 1f
    val falling = if (fadeOut > 0) (to - t) / fadeOut.toFloat() else 1f
    return minOf(rising, falling, 1f).coerceAtLeast(0f)
}

/** One repeat of a looping source: plays output `[fromMs, toMs)`, starting [loopMs] into the loop. */
data class Repeat(val fromMs: Long, val toMs: Long, val loopMs: Long)

/**
 * The repeats of a source that loops every [loopMs] while it plays output `[fromMs, toMs)`, starting [offsetMs] into the
 * loop: the first from there to the loop's end, then whole loops, the last one cut at [toMs].
 */
fun repeats(fromMs: Long, toMs: Long, offsetMs: Long, loopMs: Long): List<Repeat> {
    if (loopMs <= 0) return emptyList()
    val spans = mutableListOf<Repeat>()
    var t = fromMs
    var into = offsetMs % loopMs
    while (t < toMs) {
        val end = minOf(toMs, t + loopMs - into)
        spans += Repeat(t, end, into)
        t = end
        into = 0
    }
    return spans
}

/**
 * Where a part playing `[from, to)` fades within the output span `[spanFrom, spanTo)` of one repeat, as `[start, end)`
 * relative to [spanFrom], so it can be set on that repeat's own clock. Empty when the repeat plays at full volume throughout.
 */
fun fadeWindows(spanFrom: Long, spanTo: Long, from: Long, to: Long, fadeInMs: Long, fadeOutMs: Long): List<Pair<Long, Long>> =
    listOf(from to from + fadeLength(fadeInMs, to - from), to - fadeLength(fadeOutMs, to - from) to to)
        .mapNotNull { (start, end) ->
            val a = maxOf(start, spanFrom)
            val b = minOf(end, spanTo)
            (a - spanFrom to b - spanFrom).takeIf { b > a }
        }
