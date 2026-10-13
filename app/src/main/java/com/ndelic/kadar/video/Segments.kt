package com.ndelic.kadar.video

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

/** This list with the item at [from] moved to [to]. */
fun <T> List<T>.moved(from: Int, to: Int) = toMutableList().apply { add(to, removeAt(from)) }

/** The shortest a clip or a layer can be trimmed to; the encoder can't do an empty one. */
const val MIN_CLIP_MS = 300L
