package com.ndelic.kadar.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Geometry for shape layers and speech bubbles, in a layer's own pixels around its center. Polygons are flat x, y lists;
// circles are flat x, y, radius lists.

/** A [points]-pointed star filling a [w]×[h] box, first point straight up; inner corners at [inner] of the outer radius. */
fun starPoints(w: Float, h: Float, points: Int = 5, inner: Float = 0.42f): FloatArray = FloatArray(points * 4) { i ->
    val k = i / 2
    val r = if (k % 2 == 0) 1f else inner
    val a = -PI / 2 + PI * k / points
    if (i % 2 == 0) (cos(a) * r * w / 2).toFloat() else (sin(a) * r * h / 2).toFloat()
}

/** An arrow pointing right across a [w]×[h] box: a shaft a third of the height and a head as tall as the box. */
fun arrowPoints(w: Float, h: Float): FloatArray {
    val head = minOf(h, w * 0.5f)
    val neck = w / 2 - head
    val shaft = h / 6
    return floatArrayOf(-w / 2, -shaft, neck, -shaft, neck, -h / 2, w / 2, 0f, neck, h / 2, neck, shaft, -w / 2, shaft)
}

/** A triangle filling a [w]×[h] box, pointing up. */
fun trianglePoints(w: Float, h: Float) = floatArrayOf(0f, -h / 2, w / 2, h / 2, -w / 2, h / 2)

/** A diamond touching the middle of each side of a [w]×[h] box. */
fun diamondPoints(w: Float, h: Float) = floatArrayOf(0f, -h / 2, w / 2, 0f, 0f, h / 2, -w / 2, 0f)

/** A heart filling a [w]×[h] box, point down: the curve x = 16 sin³t, y = 13 cos t - 5 cos 2t - 2 cos 3t - cos 4t, sampled. */
fun heartPoints(w: Float, h: Float, steps: Int = 120): FloatArray {
    val raw = FloatArray(steps * 2) { i ->
        val t = 2 * PI * (i / 2) / steps
        if (i % 2 == 0) (16 * sin(t).pow(3)).toFloat() else (-(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))).toFloat()
    }
    val b = polygonBounds(raw)
    return FloatArray(raw.size) { i ->
        if (i % 2 == 0) (raw[i] - b[0]) / (b[2] - b[0]) * w - w / 2 else (raw[i] - b[1]) / (b[3] - b[1]) * h - h / 2
    }
}

/**
 * How wide to wrap a bubble's text that is [lineWidth] long on one line: about 1.6 times wider than the block is tall,
 * never narrower than its [longestWord], so words don't break.
 */
fun bubbleWrapWidth(lineWidth: Float, lineHeight: Float, longestWord: Float): Float =
    minOf(lineWidth, maxOf(longestWord, sqrt(1.6f * lineWidth * lineHeight)))

/** Half the width and height of the ellipse through the corners of a [w]×[h] box with the same shape. */
fun ellipseAround(w: Float, h: Float) = floatArrayOf(w / 2 * SQRT2, h / 2 * SQRT2)

private const val SQRT2 = 1.4142135f

/** Distance from the center to the edge of the ellipse ([rx], [ry]) in the direction of ([dx], [dy]). */
private fun ellipseReach(rx: Float, ry: Float, dx: Float, dy: Float): Float {
    val d = hypot(dx, dy)
    if (d == 0f) return 0f
    return 1 / sqrt((dx / d / rx) * (dx / d / rx) + (dy / d / ry) * (dy / d / ry))
}

/**
 * A speech bubble's tail: a triangle from a base across the center (so it grows out of the ellipse ([rx], [ry]) wherever it
 * points) to the tip ([tipX], [tipY]). Empty when the tip is inside the ellipse.
 */
fun tailPoints(rx: Float, ry: Float, tipX: Float, tipY: Float): FloatArray {
    val d = hypot(tipX, tipY)
    if (d <= ellipseReach(rx, ry, tipX, tipY)) return FloatArray(0)
    val half = minOf(rx, ry) * 0.4f
    val px = -tipY / d * half
    val py = tipX / d * half
    return floatArrayOf(px, py, tipX, tipY, -px, -py)
}

/** A thought bubble's trail: three shrinking circles from the edge of the ellipse ([rx], [ry]) to the tip; empty when the tip is inside. */
fun thoughtTrail(rx: Float, ry: Float, tipX: Float, tipY: Float): FloatArray {
    val d = hypot(tipX, tipY)
    val edge = ellipseReach(rx, ry, tipX, tipY)
    if (d <= edge) return FloatArray(0)
    val r = minOf(rx, ry) * 0.16f
    return floatArrayOf(0.2f, 0.6f, 1f).zip(floatArrayOf(r, r * 0.65f, r * 0.4f)).flatMap { (f, cr) ->
        val at = (edge + (d - edge) * f) / d
        listOf(tipX * at, tipY * at, cr)
    }.toFloatArray()
}

/** A cloud's bumps: overlapping circles centered on the ellipse ([rx], [ry]), evenly spaced along it. */
fun cloudBumps(rx: Float, ry: Float): FloatArray {
    val steps = 360
    val px = FloatArray(steps + 1) { (cos(2 * PI * it / steps) * rx).toFloat() }
    val py = FloatArray(steps + 1) { (sin(2 * PI * it / steps) * ry).toFloat() }
    val along = FloatArray(steps + 1)
    for (i in 1..steps) along[i] = along[i - 1] + hypot(px[i] - px[i - 1], py[i] - py[i - 1])
    val total = along[steps]
    val n = (total / (minOf(rx, ry) * 0.7f)).toInt().coerceIn(8, 18)
    val r = total / n * 0.62f
    val out = FloatArray(n * 3)
    var j = 0
    for (k in 0 until n) {
        while (along[j] < total * k / n) j++
        out[k * 3] = px[j]
        out[k * 3 + 1] = py[j]
        out[k * 3 + 2] = r
    }
    return out
}

/** A shout bubble: [spikes] spikes around the ellipse ([rx], [ry]), every other one shorter. */
fun spikyPoints(rx: Float, ry: Float, spikes: Int = 14): FloatArray = FloatArray(spikes * 4) { i ->
    val k = i / 2
    val a = -PI / 2 + PI * k / spikes
    val depth = minOf(rx, ry) * 0.45f
    val out = when {
        k % 2 == 0 -> 0f
        k % 4 == 1 -> depth
        else -> depth * 0.6f
    }
    if (i % 2 == 0) (cos(a) * (rx + out)).toFloat() else (sin(a) * (ry + out)).toFloat()
}

/** Radius of the circle a line [width] long bends onto at [curve] (-1 to 1, not 0): at ±1 the line is half a circle. */
fun curveRadius(width: Float, curve: Float) = width / (abs(curve) * PI.toFloat())

/** How far the ends of a line [width] long, bent by [curve], drop below (or rise above) its middle. */
fun curveRise(width: Float, curve: Float) = if (curve == 0f) 0f else curveRadius(width, curve) * (1 - cos(abs(curve) * PI.toFloat() / 2))

/** Half the width and height of a [bubble]'s body around the ellipse ([rx], [ry]), without its tail. */
fun bubbleHalfSize(bubble: Bubble, rx: Float, ry: Float): FloatArray = when (bubble) {
    Bubble.Box, Bubble.Speech, Bubble.Double -> floatArrayOf(rx, ry)
    // The text's box (the ellipse is the box times √2), with round ends as wide as it is tall, so its corners stay inside.
    Bubble.Pill -> floatArrayOf((rx + ry) / SQRT2, ry / SQRT2)
    Bubble.Thought -> cloudBumps(rx, ry).let { c ->
        val n = c.size / 3
        floatArrayOf((0 until n).maxOf { abs(c[it * 3]) + c[it * 3 + 2] }, (0 until n).maxOf { abs(c[it * 3 + 1]) + c[it * 3 + 2] })
    }
    Bubble.Shout -> polygonBounds(spikyPoints(rx, ry)).let { b -> floatArrayOf(maxOf(-b[0], b[2]), maxOf(-b[1], b[3])) }
}

/** `[left, top, right, bottom]` around a polygon. */
fun polygonBounds(p: FloatArray): FloatArray {
    var l = Float.POSITIVE_INFINITY
    var t = Float.POSITIVE_INFINITY
    var r = Float.NEGATIVE_INFINITY
    var b = Float.NEGATIVE_INFINITY
    for (i in 0 until p.size / 2) {
        l = minOf(l, p[2 * i])
        r = maxOf(r, p[2 * i])
        t = minOf(t, p[2 * i + 1])
        b = maxOf(b, p[2 * i + 1])
    }
    return floatArrayOf(l, t, r, b)
}
