package com.ndelic.kadar.video

/** How long the music takes to fade in at the start of the output and out before its end. */
const val MUSIC_FADE_MS = 1500L

/** The fades' length on a [totalMs] output: on a video shorter than two fades they meet in the middle. */
private fun fadeLength(totalMs: Long) = minOf(MUSIC_FADE_MS, totalMs / 2)

/** The music's fade factor (0 silent to 1 full) at output time [t] of a [totalMs] output. */
fun fadeGain(t: Long, totalMs: Long, fadeIn: Boolean, fadeOut: Boolean): Float {
    val f = fadeLength(totalMs).coerceAtLeast(1).toFloat()
    val rising = if (fadeIn) t / f else 1f
    val falling = if (fadeOut) (totalMs - t) / f else 1f
    return minOf(rising, falling, 1f).coerceAtLeast(0f)
}

/** Output spans `[start, end)` of the song's repeats, [loopMs] of song each, the last one cut at [totalMs]. */
fun loopSpans(loopMs: Long, totalMs: Long): List<Pair<Long, Long>> = (0 until totalMs step loopMs).map { it to minOf(it + loopMs, totalMs) }

/**
 * Where the music fades within the output span `[from, to)` of one repeat, as `[start, end)` relative to [from], so it
 * can be set on that repeat's own clock. Empty when the repeat plays at full volume throughout.
 */
fun fadeWindows(from: Long, to: Long, totalMs: Long, fadeIn: Boolean, fadeOut: Boolean): List<Pair<Long, Long>> {
    val f = fadeLength(totalMs)
    return listOfNotNull((0L to f).takeIf { fadeIn }, (totalMs - f to totalMs).takeIf { fadeOut })
        .mapNotNull { (start, end) ->
            val a = maxOf(start, from)
            val b = minOf(end, to)
            (a - from to b - from).takeIf { b > a }
        }
}
