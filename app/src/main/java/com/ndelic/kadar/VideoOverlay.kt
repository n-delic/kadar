package com.ndelic.kadar

/**
 * A video layer's placement in Media3's compositor: its center in normalized device coordinates of the output frame
 * (-1..1, y pointing up), its scale, and its rotation in degrees counter-clockwise.
 */
data class OverlayPlacement(val x: Float, val y: Float, val scale: Float, val rotation: Float)

/**
 * Converts a layer [t] on a [width]×[height] canvas. The scale carries over as is because the overlay frame is
 * presented at the layer's own size; the canvas's y points down and its rotation is clockwise, so both flip.
 */
fun overlayPlacement(t: Transform, width: Int, height: Int) =
    OverlayPlacement(2 * t.x / width - 1, 1 - 2 * t.y / height, t.scale, -t.rotation)

/**
 * When a video layer actually plays in a [totalMs] long output, as `start to end` in output time: from its [startMs]
 * until its [endMs], the end of its own clip ([durationMs]) or the end of the output, whichever comes first.
 * Null when that leaves too little to show.
 */
fun overlaySpan(startMs: Long, endMs: Long, durationMs: Long, totalMs: Long): Pair<Long, Long>? {
    val end = minOf(endMs, startMs + durationMs, totalMs)
    return if (end - startMs < MIN_OVERLAY_MS) null else startMs to end
}

private const val MIN_OVERLAY_MS = 100L
