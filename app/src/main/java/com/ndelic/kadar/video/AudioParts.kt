package com.ndelic.kadar.video

/**
 * Where a music or voiceover track plays and how: from output time [atMs] for [lengthMs] ([Long.MAX_VALUE]: to the end of
 * the video), [offsetMs] into its source (into the looped song, for music), at [volume], fading in over [fadeInMs] and out
 * over [fadeOutMs].
 */
data class AudioPart(
    val atMs: Long = 0,
    val offsetMs: Long = 0,
    val lengthMs: Long = Long.MAX_VALUE,
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
) {
    /** Where it stops on a [totalMs] output: at its own end or the output's, whichever comes first. */
    fun endMs(totalMs: Long) = if (lengthMs == Long.MAX_VALUE) totalMs else minOf(atMs + lengthMs, totalMs)
}

/** Which audio track: music part [index], or voiceover [index] when [voice]. */
data class AudioRef(val voice: Boolean, val index: Int)

enum class AudioDrag { Start, End, Move }

/**
 * This part with its [edge] dragged [dMs] (output time) on a [totalMs] output. [sourceMs] is how long the source is, or
 * null for music, which loops: dragging its end to the end of the video makes it run to the end, as a new song does.
 */
fun AudioPart.dragged(edge: AudioDrag, dMs: Long, totalMs: Long, sourceMs: Long?): AudioPart {
    val end = endMs(totalMs)
    return when (edge) {
        AudioDrag.Move -> copy(atMs = (atMs + dMs).coerceIn(0, maxOf(0, totalMs - MIN_CLIP_MS)))
        AudioDrag.Start -> {
            // Not before the source's own start. Bounds can cross on a part shorter than MIN_CLIP_MS; coerceIn would throw.
            val low = maxOf(0, atMs - offsetMs)
            val at = (atMs + dMs).coerceIn(low, maxOf(low, end - MIN_CLIP_MS))
            val d = at - atMs
            copy(atMs = at, offsetMs = offsetMs + d, lengthMs = if (lengthMs == Long.MAX_VALUE) lengthMs else lengthMs - d)
        }
        AudioDrag.End -> {
            val max = if (sourceMs == null) totalMs else minOf(totalMs, atMs + sourceMs - offsetMs)
            val e = (end + dMs).coerceIn(minOf(atMs + MIN_CLIP_MS, max), max)
            copy(lengthMs = if (sourceMs == null && e >= totalMs) Long.MAX_VALUE else maxOf(e - atMs, MIN_CLIP_MS))
        }
    }
}

/**
 * This part cut at output time [cutMs] into two that sound as it did: no fade out and in at the cut. Null when either
 * would be shorter than [MIN_CLIP_MS].
 */
fun AudioPart.splitAt(cutMs: Long, totalMs: Long): Pair<AudioPart, AudioPart>? {
    val at = layerSplitPoint(atMs, endMs(totalMs), totalMs, cutMs) ?: return null
    val d = at - atMs
    val rest = if (lengthMs == Long.MAX_VALUE) lengthMs else lengthMs - d
    return copy(lengthMs = d, fadeOutMs = 0) to copy(atMs = at, offsetMs = offsetMs + d, lengthMs = rest, fadeInMs = 0)
}

/** Where new audio starts on a [totalMs] output: at the playhead, or from the beginning when under a second is left. */
fun audioStartMs(playheadMs: Long, totalMs: Long) = if (playheadMs < totalMs - MIN_AUDIO_ROOM_MS) playheadMs else 0L

private const val MIN_AUDIO_ROOM_MS = 1000L

/** The longest fade in or out the Audio panel offers. */
const val MAX_FADE_MS = 5000L

fun VideoClip.audioPart(ref: AudioRef): AudioPart? = if (ref.voice) voices.getOrNull(ref.index)?.part else music.getOrNull(ref.index)?.part

/** This clip with audio track [ref] replaced by the parts [change] makes of it: one to edit it, two to split it, none to delete it. */
fun VideoClip.withAudio(ref: AudioRef, change: (AudioPart) -> List<AudioPart>): VideoClip =
    if (ref.voice) {
        copy(voices = voices.flatMapIndexed { i, v -> if (i == ref.index) change(v.part).map { v.copy(part = it) } else listOf(v) })
    } else {
        copy(music = music.flatMapIndexed { i, m -> if (i == ref.index) change(m.part).map { m.copy(part = it) } else listOf(m) })
    }
