package com.ndelic.kadar.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class ShapesTest {

    @Test
    fun circleStartsRound() {
        val (w, h) = shapeSize(ShapeKind.Ellipse, 100f)
        assertEquals(100f, w)
        assertEquals(100f, h)
    }

    @Test
    fun starStartsAtTheTopAndFillsItsBox() {
        val p = starPoints(100f, 200f)
        assertEquals(20, p.size)
        assertEquals(0f, p[0], 0.001f)
        assertEquals(-100f, p[1], 0.001f)
        val b = polygonBounds(p)
        assertTrue(b[0] >= -50.001f && b[2] <= 50.001f && b[1] >= -100.001f && b[3] <= 100.001f)
        // Inner corners sit closer to the center than the points.
        assertTrue(hypot(p[2], p[3] / 2) < hypot(p[0], p[1] / 2))
    }

    @Test
    fun cutShapesFillTheirBox() {
        for (p in listOf(trianglePoints(100f, 60f), diamondPoints(100f, 60f), heartPoints(100f, 60f))) {
            assertArrayEquals(floatArrayOf(-50f, -30f, 50f, 30f), polygonBounds(p), 0.001f)
        }
    }

    @Test
    fun heartPointsDownAndHasTwoLobes() {
        val p = heartPoints(100f, 100f)
        // The first point (t = 0) is the dip between the lobes, on the middle line, below the top.
        assertEquals(0f, p[0], 0.001f)
        assertTrue(p[1] > -50f + 1f)
        // The lowest point is the tip, in the middle.
        val tip = (0 until p.size / 2).maxBy { p[2 * it + 1] }
        assertEquals(0f, p[2 * tip], 0.5f)
        assertEquals(50f, p[2 * tip + 1], 0.001f)
    }

    @Test
    fun arrowSpansItsBoxAndPointsRight() {
        val p = arrowPoints(300f, 100f)
        assertArrayEquals(floatArrayOf(-150f, -50f, 150f, 50f), polygonBounds(p), 0.001f)
        assertEquals(150f, p[6], 0.001f)
        assertEquals(0f, p[7], 0.001f)
    }

    @Test
    fun bubbleTextWrapsIntoARounderBlockWithoutBreakingWords() {
        // A long line wraps; a short one stays as it is; a long word is never split.
        assertEquals(400f, bubbleWrapWidth(1000f, 100f, 50f), 0.001f)
        assertEquals(120f, bubbleWrapWidth(120f, 100f, 50f), 0.001f)
        assertEquals(600f, bubbleWrapWidth(1000f, 100f, 600f), 0.001f)
    }

    @Test
    fun ellipseAroundPassesThroughTheBoxCorners() {
        val (rx, ry) = ellipseAround(200f, 100f).let { it[0] to it[1] }
        assertEquals(1f, (100f / rx) * (100f / rx) + (50f / ry) * (50f / ry), 0.001f)
    }

    @Test
    fun tailReachesTheTipAndHidesWhenTheTipIsInside() {
        val t = tailPoints(100f, 50f, 0f, 200f)
        assertEquals(6, t.size)
        assertEquals(0f, t[2], 0.001f)
        assertEquals(200f, t[3], 0.001f)
        // The base runs across the center, at right angles to the tail.
        assertEquals(0f, t[1], 0.001f)
        assertEquals(-t[0], t[4], 0.001f)
        assertEquals(0, tailPoints(100f, 50f, 10f, 10f).size)
        assertEquals(0, thoughtTrail(100f, 50f, 10f, 10f).size)
    }

    @Test
    fun thoughtTrailShrinksTowardTheTip() {
        val c = thoughtTrail(100f, 50f, 0f, 250f)
        assertEquals(9, c.size)
        assertTrue(c[2] > c[5] && c[5] > c[8])
        assertTrue(c[1] > 50f && c[1] < c[4] && c[4] < c[7])
        assertEquals(250f, c[7], 0.001f)
    }

    @Test
    fun cloudBumpsSitOnTheEllipseEvenlySpaced() {
        val c = cloudBumps(200f, 100f)
        val n = c.size / 3
        assertTrue(n in 8..18)
        for (i in 0 until n) assertEquals(1f, (c[i * 3] / 200f).let { it * it } + (c[i * 3 + 1] / 100f).let { it * it }, 0.02f)
        val gaps = (0 until n).map { i -> hypot(c[(i + 1) % n * 3] - c[i * 3], c[(i + 1) % n * 3 + 1] - c[i * 3 + 1]) }
        assertTrue(gaps.max() - gaps.min() < gaps.min() * 0.2f)
        // Neighbors overlap, so the outline is one bumpy line.
        assertTrue(gaps.max() < 2 * c[2])
    }

    @Test
    fun shoutSpikesReachOutsideTheEllipse() {
        val p = spikyPoints(100f, 60f)
        assertEquals(56, p.size)
        // Every other point is on the ellipse, the rest outside it.
        for (k in 0 until p.size / 2) {
            val e = (p[2 * k] / 100f).let { it * it } + (p[2 * k + 1] / 60f).let { it * it }
            if (k % 2 == 0) assertEquals(1f, e, 0.001f) else assertTrue(e > 1f)
        }
    }

    @Test
    fun bubbleSizeCoversItsBody() {
        assertArrayEquals(floatArrayOf(100f, 60f), bubbleHalfSize(Bubble.Speech, 100f, 60f), 0.001f)
        assertArrayEquals(floatArrayOf(100f, 60f), bubbleHalfSize(Bubble.Double, 100f, 60f), 0.001f)
        // The pill's round ends reach past the text box's corners: half width = box half width + box half height.
        assertArrayEquals(floatArrayOf(160f / 1.4142135f, 60f / 1.4142135f), bubbleHalfSize(Bubble.Pill, 100f, 60f), 0.001f)
        val cloud = bubbleHalfSize(Bubble.Thought, 100f, 60f)
        val c = cloudBumps(100f, 60f)
        assertTrue(cloud[0] > 100f && cloud[1] > 60f)
        for (i in 0 until c.size / 3) assertTrue(abs(c[i * 3]) + c[i * 3 + 2] <= cloud[0] + 0.001f)
        val shout = bubbleHalfSize(Bubble.Shout, 100f, 60f)
        assertTrue(shout[0] > 100f && shout[1] > 60f)
    }

    @Test
    fun toLocalUndoesShiftedLocal() {
        val t = Transform(300f, 200f, scale = 1.5f, rotation = 30f)
        val moved = t.shiftedLocal(40f, -70f)
        val (x, y) = t.toLocal(moved.x, moved.y)
        assertEquals(40f, x, 0.001f)
        assertEquals(-70f, y, 0.001f)
    }

    @Test
    fun aFullCurveBendsALineIntoHalfACircle() {
        // At 1 the line is half the circle's circumference: radius = width / π, its ends drop a whole radius.
        assertEquals(100f / Math.PI.toFloat(), curveRadius(100f, 1f), 0.001f)
        assertEquals(100f / Math.PI.toFloat(), curveRise(100f, -1f), 0.001f)
        assertEquals(0f, curveRise(100f, 0f), 0f)
        // A gentle curve barely rises.
        assertEquals(true, curveRise(100f, 0.1f) < 5f)
    }
}
