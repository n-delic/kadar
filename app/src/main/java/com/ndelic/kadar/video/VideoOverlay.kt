package com.ndelic.kadar.video

import com.ndelic.kadar.model.CropRect
import com.ndelic.kadar.model.Transform
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

/** [c] as Media3's `Crop` arguments: left, right, bottom and top in normalized device coordinates (-1..1, y pointing up). */
fun cropNdc(c: CropRect) = floatArrayOf(2 * c.left - 1, 2 * c.right - 1, 1 - 2 * c.bottom, 1 - 2 * c.top)

/**
 * When a video layer actually plays in a [totalMs] long output, as `start to end` in output time: from its [startMs]
 * until its [endMs], the end of its own clip ([durationMs]) or the end of the output, whichever comes first.
 * Null when that leaves too little to show.
 */
fun overlaySpan(startMs: Long, endMs: Long, durationMs: Long, totalMs: Long): Pair<Long, Long>? {
    val end = minOf(endMs, startMs + durationMs, totalMs)
    return if (end - startMs < MIN_OVERLAY_MS) null else startMs to end
}

/** A video layer's alpha at [timeUs]: its [opacity] while it plays ([span], in ms), else 0 to hide the gap frames around it. */
fun overlayAlpha(opacity: Float, span: Pair<Long, Long>, timeUs: Long) = if (timeUs in span.first * 1000 until span.second * 1000) opacity else 0f

/**
 * Frames per second of the export (and preview) once video layers are on: the main clip's own rate, rounded, at most
 * 60 so phones can encode it. 30 when the rate is unknown.
 */
fun outputFrameRate(clipFrameRate: Float) = if (clipFrameRate > 0) clipFrameRate.roundToInt().coerceIn(1, 60) else 30

/**
 * The output frame (at [fps], from 0) that a compositor input's frame at [timeUs] is drawn into: Media3's compositor
 * pairs every output frame with the nearest frame of each other input.
 */
fun outputFrameUs(timeUs: Long, fps: Int) = (timeUs * fps / 1_000_000.0).roundToLong() * 1_000_000 / fps

/**
 * How layers stack around video layers: [layers] (bottom first) cut at every one [isVideo] picks, giving the other
 * layers under the first video layer, between each pair and above the last, so one more group than video layers.
 * Each group is drawn as one picture at its place in the stack, in the preview and the export alike.
 */
fun <T> drawnGroups(layers: List<T>, isVideo: (T) -> Boolean): List<List<T>> {
    val groups = mutableListOf(mutableListOf<T>())
    layers.forEach { if (isVideo(it)) groups += mutableListOf<T>() else groups.last() += it }
    return groups
}

/**
 * Where in its own clip a video layer is at output time [positionMs]: it plays at normal speed from [clipStartMs] once
 * its [span] starts, whatever the main clip's speed. Null outside the span, where it isn't shown.
 */
fun overlaySourceMs(clipStartMs: Long, span: Pair<Long, Long>?, positionMs: Long) =
    if (span == null || positionMs !in span.first until span.second) null else clipStartMs + positionMs - span.first

/**
 * Whether a preview player at [actualMs] must seek to [targetMs]: paused, to show the exact frame; playing, only once
 * it drifts past [MAX_DRIFT_MS], since every seek stalls it for a moment, and at most once per [SEEK_GAP_MS] (counted
 * by [sinceSeekMs]), so a decoder slower than real time still plays between corrections instead of seeking forever.
 */
fun overlayNeedsSeek(actualMs: Long, targetMs: Long, playing: Boolean, sinceSeekMs: Long) =
    if (playing) sinceSeekMs >= SEEK_GAP_MS && abs(actualMs - targetMs) > MAX_DRIFT_MS else actualMs != targetMs

private const val MIN_OVERLAY_MS = 100L
private const val MAX_DRIFT_MS = 80L
private const val SEEK_GAP_MS = 1_000L
