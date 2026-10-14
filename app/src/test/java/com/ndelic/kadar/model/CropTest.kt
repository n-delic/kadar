package com.ndelic.kadar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Test

class CropTest {

    @Test
    fun turnedBoundsSwapAtAQuarterTurn() {
        val (w, h) = turnedBounds(400f, 300f, 90f)
        assertEquals(300f, w, 0.01f)
        assertEquals(400f, h, 0.01f)
        // 45°: both sides become (w + h) / sqrt 2.
        val (w45, h45) = turnedBounds(400f, 300f, 45f)
        assertEquals(700 / 1.41421f, w45, 0.1f)
        assertEquals(700 / 1.41421f, h45, 0.1f)
    }

    @Test
    fun straightenedFitIsTheWholePictureWhenLevel() {
        assertEquals(400f to 300f, straightenedFit(400f, 300f, 0f, 4f / 3))
        // A square in a level 400×300 picture is limited by the height.
        assertEquals(300f to 300f, straightenedFit(400f, 300f, 0f, 1f))
    }

    @Test
    fun straightenedFitKeepsEveryCornerInsideTheTurnedPicture() {
        val angle = 10f
        val (w, h) = straightenedFit(400f, 300f, angle, 4f / 3)
        assertEquals(4f / 3, w / h, 0.001f)
        // Each corner, turned back into the picture's axes, lies inside it; at least one touches an edge.
        var touches = false
        for ((cx, cy) in listOf(w / 2 to h / 2, -w / 2 to h / 2)) {
            val (x, y) = turned(cx, cy, -angle)
            assertTrue(abs(x) <= 200.01f && abs(y) <= 150.01f)
            if (abs(abs(x) - 200f) < 0.05f || abs(abs(y) - 150f) < 0.05f) touches = true
        }
        assertTrue(touches)
    }

    @Test
    fun turnedIsClockwiseOnScreen() {
        // y points down, so a quarter turn takes right to down.
        val (x, y) = turned(1f, 0f, 90f)
        assertEquals(0f, x, 1e-5f)
        assertEquals(1f, y, 1e-5f)
    }

    @Test
    fun turnedSourceOffsetOfAWholeLayerTurnedAQuarter() {
        // A 400×300 layer that is its whole source, turned 90° and kept whole: the crop starts at the turned box's corner.
        val (x, y) = turnedSourceOffset(400, 300, 0, 0, 400, 300, 90f, 300, 400, -150f, -200f)
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun turnedSourceOffsetFollowsTheLayerInsideItsSource() {
        // A 100×100 cut-out at (300, 0) of a 400×200 source, turned 90°. Its center (350, 50) is (150, -50) from the
        // source's center; turned that is (50, 150), and the turned source is 200×400, so the center lands on (150, 350).
        val (x, y) = turnedSourceOffset(100, 100, 300, 0, 400, 200, 90f, 200, 400, -50f, -50f)
        assertEquals(100f, x, 0.01f)
        assertEquals(300f, y, 0.01f)
    }
}
