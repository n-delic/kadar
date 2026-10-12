package com.ndelic.kadar.ui.editor

import kotlin.math.min

/**
 * The stretch of output time the timeline shows: [spanMs] of it from [fromMs], across a track `width` px wide.
 * View-only, never saved and not undoable. Starts as the whole video; always used through [fit].
 */
data class TimelineViewport(val fromMs: Float = 0f, val spanMs: Float = Float.MAX_VALUE) {
    fun x(ms: Float, width: Float) = (ms - fromMs) / spanMs * width

    fun x(ms: Long, width: Float) = x(ms.toFloat(), width)

    fun time(x: Float, width: Float, totalMs: Long) = (fromMs + x / width * spanMs).toLong().coerceIn(0, totalMs)

    /** Kept inside a video [totalMs] long: zoomed out to the whole video at most, in until [MIN_VISIBLE_MS] fills it. */
    fun fit(totalMs: Long): TimelineViewport {
        val span = spanIn(spanMs, totalMs)
        return TimelineViewport(fromMs.coerceIn(0f, totalMs - span), span)
    }

    /** Pinched by [zoom] while the fingers' center moved from [fromX] to [toX]: the time under the fingers stays under them. */
    fun pinch(zoom: Float, fromX: Float, toX: Float, width: Float, totalMs: Long): TimelineViewport {
        val span = spanIn(spanMs / zoom, totalMs)
        return TimelineViewport(fromMs + fromX / width * spanMs - toX / width * span, span).fit(totalMs)
    }

    /** Pages so [positionMs] is in view, a little in from the left; unchanged while it's already in view. */
    fun follow(positionMs: Long, totalMs: Long) =
        if (positionMs < fromMs || positionMs > fromMs + spanMs) copy(fromMs = positionMs - spanMs * 0.1f).fit(totalMs) else this

    /** The smallest round tick step that puts ticks at least [minGapPx] apart. */
    fun rulerStepMs(width: Float, minGapPx: Float) = RULER_STEPS.firstOrNull { it / spanMs * width >= minGapPx } ?: RULER_STEPS.last()
}

/** A ruler label: whole seconds when the ticks are, tenths otherwise. */
fun rulerLabel(ms: Long, stepMs: Long): String {
    val seconds = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
    return if (stepMs % 1000 == 0L) seconds else "$seconds.${ms / 100 % 10}"
}

/** Which of [count] filmstrip frames, sampled evenly over a [durationMs] source, shows source time [sourceMs]. */
fun filmstripIndex(sourceMs: Float, durationMs: Long, count: Int) = (sourceMs / durationMs * count).toInt().coerceIn(0, count - 1)

private fun spanIn(spanMs: Float, totalMs: Long) = spanMs.coerceIn(min(MIN_VISIBLE_MS, totalMs.toFloat()), totalMs.toFloat())

/** Fully zoomed in, about a second fills the track width: enough to place a 300 ms animation by eye. */
private const val MIN_VISIBLE_MS = 1000f
private val RULER_STEPS = longArrayOf(100, 200, 500, 1000, 2000, 5000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000, 3_600_000)
