package com.ndelic.kadar.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * One freehand line: [points] are x, y pairs and [width] is in the same pixels. [color] is ARGB, so the
 * highlighter's transparency is part of it. Never changed after it's made, so layers can share it.
 */
class PenStroke(val color: Int, val width: Float, val points: FloatArray)

/**
 * Quadratic segments through the midpoints between the finger's samples, so strokes come out smooth instead of jagged.
 * Returns the start x, y followed by control x, y and end x, y for each segment; a single sample gives one zero-length
 * segment (a dot with round caps).
 */
fun smoothSegments(points: FloatArray): FloatArray {
    val n = points.size / 2
    if (n == 0) return FloatArray(0)
    val out = FloatArray(2 + 4 * max(1, n - 1))
    out[0] = points[0]
    out[1] = points[1]
    var o = 2
    for (i in 1 until n - 1) {
        out[o++] = points[2 * i]
        out[o++] = points[2 * i + 1]
        out[o++] = (points[2 * i] + points[2 * i + 2]) / 2
        out[o++] = (points[2 * i + 1] + points[2 * i + 3]) / 2
    }
    // The last stretch is a straight line to the final sample (control point = end point).
    val lx = points[2 * n - 2]
    val ly = points[2 * n - 1]
    out[o++] = lx
    out[o++] = ly
    out[o++] = lx
    out[o] = ly
    return out
}

/** `[left, top, right, bottom]` around every stroke, widened by half its width; null when there's nothing to bound. */
fun strokeBounds(strokes: List<PenStroke>): FloatArray? {
    var l = Float.POSITIVE_INFINITY
    var t = Float.POSITIVE_INFINITY
    var r = Float.NEGATIVE_INFINITY
    var b = Float.NEGATIVE_INFINITY
    for (s in strokes) {
        val half = s.width / 2
        for (i in 0 until s.points.size / 2) {
            l = minOf(l, s.points[2 * i] - half)
            t = minOf(t, s.points[2 * i + 1] - half)
            r = maxOf(r, s.points[2 * i] + half)
            b = maxOf(b, s.points[2 * i + 1] + half)
        }
    }
    return if (r < l) null else floatArrayOf(l, t, r, b)
}

/**
 * [strokes] less what an eraser of [radius] dragged from ([ax], [ay]) to ([bx], [by]) touches: the points it reaches go,
 * so a stroke cut in the middle becomes two and leftover single points are dropped. Strokes it misses stay as they are.
 */
fun erased(strokes: List<PenStroke>, ax: Float, ay: Float, bx: Float, by: Float, radius: Float): List<PenStroke> = strokes.flatMap { s ->
    val reach = radius + s.width / 2
    val n = s.points.size / 2
    val keep = BooleanArray(n) { i -> distanceToSegment(s.points[2 * i], s.points[2 * i + 1], ax, ay, bx, by) > reach }
    if (keep.all { it }) return@flatMap listOf(s)
    val parts = ArrayList<PenStroke>()
    var i = 0
    while (i < n) {
        if (!keep[i]) {
            i++
            continue
        }
        val from = i
        while (i < n && keep[i]) i++
        if (i - from > 1) parts += PenStroke(s.color, s.width, s.points.copyOfRange(2 * from, 2 * i))
    }
    parts
}

private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/** This layer's strokes in canvas pixels (moved, scaled and turned like the layer), the inverse of [withStrokes]. */
fun DrawLayer.canvasStrokes(): List<PenStroke> = strokes.map { s ->
    val p = s.points
    PenStroke(s.color, s.width * t.scale, FloatArray(p.size).also { out ->
        for (i in 0 until p.size / 2) {
            val (x, y) = turned(p[2 * i] * t.scale, p[2 * i + 1] * t.scale, t.rotation)
            out[2 * i] = x + t.x
            out[2 * i + 1] = y + t.y
        }
    })
}

/**
 * Adds [strokes] drawn in canvas pixels on top of this layer's own, mapped into its moved, scaled and rotated space,
 * then re-centers everything on the layer's origin, so it still hit-tests, scales and rotates around its middle.
 * A new drawing is an empty layer at the canvas origin plus its strokes.
 */
fun DrawLayer.withStrokes(strokes: List<PenStroke>): DrawLayer {
    val r = Math.toRadians(-t.rotation.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val local = strokes.map { stroke ->
        val p = stroke.points
        PenStroke(stroke.color, stroke.width / t.scale, FloatArray(p.size) { i ->
            val dx = p[i - i % 2] - t.x
            val dy = p[i - i % 2 + 1] - t.y
            (if (i % 2 == 0) dx * c - dy * s else dx * s + dy * c) / t.scale
        })
    }
    val all = this.strokes + local
    val b = strokeBounds(all) ?: return copy(strokes = all)
    val cx = (b[0] + b[2]) / 2
    val cy = (b[1] + b[3]) / 2
    return copy(
        t = t.shiftedLocal(cx, cy),
        strokes = all.map { PenStroke(it.color, it.width, FloatArray(it.points.size) { i -> it.points[i] - if (i % 2 == 0) cx else cy }) },
    )
}
