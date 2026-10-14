package com.ndelic.kadar.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrokesTest {

    @Test
    fun smoothSegmentsCurveThroughMidpoints() {
        // Start, then a curve via (10, 0) to the midpoint (10, 5), then a straight line to the last sample.
        val s = smoothSegments(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f))
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 5f, 10f, 10f, 10f, 10f), s, 0.0001f)
        // A tap is a zero-length segment (a dot), two samples a straight line.
        assertArrayEquals(floatArrayOf(3f, 4f, 3f, 4f, 3f, 4f), smoothSegments(floatArrayOf(3f, 4f)), 0.0001f)
        assertArrayEquals(floatArrayOf(0f, 0f, 5f, 5f, 5f, 5f), smoothSegments(floatArrayOf(0f, 0f, 5f, 5f)), 0.0001f)
        assertEquals(0, smoothSegments(FloatArray(0)).size)
    }

    @Test
    fun boundsIncludeHalfTheWidth() {
        val b = strokeBounds(listOf(PenStroke(0, 10f, floatArrayOf(0f, 0f, 100f, 50f)), PenStroke(0, 2f, floatArrayOf(-20f, 0f))))
        assertArrayEquals(floatArrayOf(-21f, -5f, 105f, 55f), b, 0.0001f)
        assertNull(strokeBounds(emptyList()))
    }

    @Test
    fun newDrawingIsCenteredWhereItWasDrawn() {
        val layer = DrawLayer(1, Transform(0f, 0f), emptyList()).withStrokes(listOf(PenStroke(0, 10f, floatArrayOf(100f, 200f, 300f, 400f))))
        assertEquals(200f, layer.t.x, 0.001f)
        assertEquals(300f, layer.t.y, 0.001f)
        assertEquals(210f, layer.width, 0.001f)
        assertEquals(210f, layer.height, 0.001f)
        assertArrayEquals(floatArrayOf(-100f, -100f, 100f, 100f), layer.strokes.single().points, 0.001f)
    }

    @Test
    fun addedStrokesLandInTheLayersMovedScaledRotatedSpace() {
        // A drawing doubled and turned 90°: a canvas point 40 px below its center is 20 px right of it in its own space.
        val layer = DrawLayer(1, Transform(500f, 500f, scale = 2f, rotation = 90f), listOf(PenStroke(0, 2f, floatArrayOf(-10f, 0f))))
        val more = layer.withStrokes(listOf(PenStroke(0, 4f, floatArrayOf(500f, 540f))))
        // Re-centered on both dots (local -10 and 20, so 5 right), which on the canvas is 10 px down.
        assertEquals(500f, more.t.x, 0.001f)
        assertEquals(510f, more.t.y, 0.001f)
        assertArrayEquals(floatArrayOf(-15f, 0f), more.strokes[0].points, 0.001f)
        assertArrayEquals(floatArrayOf(15f, 0f), more.strokes[1].points, 0.001f)
        assertEquals(2f, more.strokes[1].width, 0.001f)
    }

    @Test
    fun addedStrokesLandWhereTheyShowOnAMirroredDrawing() {
        val layer = DrawLayer(1, Transform(500f, 500f, flipX = true), listOf(PenStroke(0, 2f, floatArrayOf(-10f, 0f))))
        // 30 px right on the canvas is 30 px left in a mirrored drawing's own space.
        val more = layer.withStrokes(listOf(PenStroke(0, 2f, floatArrayOf(530f, 500f))))
        assertArrayEquals(floatArrayOf(10f, 0f), more.strokes[0].points, 0.001f)
        assertArrayEquals(floatArrayOf(-10f, 0f), more.strokes[1].points, 0.001f)
        // Re-centered on local -20, which is 20 px right on the canvas.
        assertEquals(520f, more.t.x, 0.001f)
    }
}
