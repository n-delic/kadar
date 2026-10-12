package com.ndelic.kadar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayersTest {

    @Test
    fun containsFollowsScaleAndRotation() {
        // A 100×20 bar centered at (200, 200), doubled and turned upright.
        val t = Transform(200f, 200f, scale = 2f, rotation = 90f)
        assertTrue(t.contains(100f, 20f, 200f, 300f))
        assertFalse(t.contains(100f, 20f, 300f, 200f))
        assertTrue(Transform(0f, 0f).contains(100f, 20f, 49f, 9f))
        assertFalse(Transform(0f, 0f).contains(100f, 20f, 51f, 0f))
    }

    @Test
    fun shiftedLocalMovesAlongTheLayersOwnAxes() {
        val t = Transform(0f, 0f, scale = 2f, rotation = 90f).shiftedLocal(10f, 0f)
        assertEquals(0f, t.x, 0.001f)
        assertEquals(20f, t.y, 0.001f)
    }

    @Test
    fun opaqueBoundsFindsVisiblePixels() {
        val px = IntArray(5 * 4)
        px[1 * 5 + 2] = 0xFF000000.toInt()
        px[2 * 5 + 3] = 0x80FFFFFF.toInt()
        px[3 * 5 + 0] = 0x05FFFFFF // nearly invisible edge noise is ignored
        assertArrayEquals(intArrayOf(2, 1, 4, 3), opaqueBounds(px, 5, 4))
        assertNull(opaqueBounds(IntArray(4), 2, 2))
    }

    @Test
    fun segmentBoundsCutAtEveryLayerEdge() {
        // A whole-video layer plus one from 1 s to 2 s, in a 3 s video; an end past the video is clamped.
        val cuts = segmentBounds(listOf(0L to Long.MAX_VALUE, 1000L to 2000L, 2500L to 9000L), 3000)
        assertEquals(listOf(0L to 1000L, 1000L to 2000L, 2000L to 2500L, 2500L to 3000L), cuts)
    }

    @Test
    fun snappedPullsOntoCenterAndRightAngles() {
        val t = Transform(503f, 300f, rotation = 92.5f).snapped(cx = 500f, cy = 500f, distance = 5f)
        assertEquals(500f, t.x, 0.001f)
        assertEquals(300f, t.y, 0.001f) // too far from the center line to snap
        assertEquals(90f, t.rotation, 0.001f)
        // Keeps the winding: -358° is 2° off 0°, so it snaps to -360°, not to 0°.
        assertEquals(-360f, Transform(0f, 0f, rotation = -358f).snapped(1000f, 1000f, 5f).rotation, 0.001f)
        assertEquals(45f, Transform(0f, 0f, rotation = 45f).snapped(1000f, 1000f, 5f).rotation, 0.001f)
    }

    @Test
    fun adjustMatrixIsIdentityAtZeroAndGrayAtNoSaturation() {
        val identity = Adjust().colorMatrix()
        for (i in 0..19) assertEquals(if (i in setOf(0, 6, 12, 18)) 1f else 0f, identity[i], 0.0001f)
        val gray = Adjust(saturation = -1f).colorMatrix()
        // Every color row is the same luma mix.
        for (col in 0..2) {
            assertEquals(gray[col], gray[5 + col], 0.0001f)
            assertEquals(gray[col], gray[10 + col], 0.0001f)
        }
        assertEquals(64f, Adjust(brightness = 0.5f).colorMatrix()[4], 0.0001f)
    }

    @Test
    fun canvasSizeKeepsTheLongSideEven() {
        assertEquals(1080 to 1920, canvasSize(1920, 1080, 9f / 16))
        assertEquals(1920 to 1080, canvasSize(1920, 1080, null))
        assertEquals(1920 to 1920, canvasSize(1080, 1920, 1f))
        // 4K is capped so the phone's encoder can keep up.
        assertEquals(1080 to 1920, canvasSize(3840, 2160, 9f / 16))
    }
}
