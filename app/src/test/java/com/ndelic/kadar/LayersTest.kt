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
}
