package com.ndelic.kadar.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsTest {

    /** The matrix applied to an opaque (r, g, b), each channel rounded and clamped. */
    private fun apply(m: FloatArray, r: Int, g: Int, b: Int) = IntArray(3) { row ->
        (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * 255 + m[row * 5 + 4]).toInt().coerceIn(0, 255)
    }

    @Test
    fun duotoneMapsBlackAndWhiteToItsColors() {
        val m = duotoneMatrix(0xFF102030.toInt(), 0xFFF0E0D0.toInt(), 1f)
        assertArrayEquals(intArrayOf(0x10, 0x20, 0x30), apply(m, 0, 0, 0))
        assertArrayEquals(intArrayOf(0xF0, 0xE0, 0xD0), apply(m, 255, 255, 255))
        // Alpha passes through.
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f), m.copyOfRange(15, 20), 0f)
    }

    @Test
    fun noDuotoneAmountIsTheIdentity() {
        assertArrayEquals(Look.None.matrix(), duotoneMatrix(0xFF102030.toInt(), 0xFFF0E0D0.toInt(), 0f), 1e-6f)
    }

    @Test
    fun posterizeToTwoLevelsKeepsAlpha() {
        val px = intArrayOf(0x80102030.toInt(), 0x00F0E080, 0xFF7F8081.toInt())
        posterize(px, 2)
        assertArrayEquals(intArrayOf(0x80000000.toInt(), 0x00FFFFFF, 0xFF00FFFF.toInt()), px)
    }

    @Test
    fun pixelateAveragesEachCellIgnoringSeeThroughPixels() {
        // A 4x2 picture in two 2x2 cells: red and transparent, then black and white.
        val px = intArrayOf(0xFFFF0000.toInt(), 0, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0, 0xFFFF0000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        val out = pixelate(px, 4, 2, 2)
        assertEquals(0x7FFF0000, out[0])
        assertTrue(listOf(0, 1, 4, 5).all { out[it] == out[0] })
        assertEquals(0xFF7F7F7F.toInt(), out[2])
        assertTrue(listOf(2, 3, 6, 7).all { out[it] == out[2] })
    }

    @Test
    fun sharpenLeavesFlatColorAndRaisesAnEdge() {
        val flat = IntArray(9) { 0xFF336699.toInt() }
        assertArrayEquals(flat, sharpen(flat, 3, 3, 2f))
        // A dark middle column between light ones gets darker, the light ones lighter.
        val edge = IntArray(9) { if (it % 3 == 1) 0xFF404040.toInt() else 0xFFA0A0A0.toInt() }
        val out = sharpen(edge, 3, 3, 1f)
        assertTrue((out[4] and 0xFF) < 0x40)
        assertTrue((out[3] and 0xFF) > 0xA0)
    }

    @Test
    fun noGlitchIsThePicture() {
        val px = IntArray(12 * 10) { it * 0x010203 or (0xFF shl 24) }
        assertArrayEquals(px, glitch(px, 12, 10, 0f))
    }

    @Test
    fun glitchSplitsChannelsButKeepsAlpha() {
        // Opaque columns of different reds and blues: a strong glitch changes the colors, never the alpha.
        val w = 100
        val px = IntArray(w * 50) { (0xFF shl 24) or ((it % w * 2) shl 16) or (255 - it % w * 2) }
        val out = glitch(px, w, 50, 1f)
        assertTrue(out.all { it ushr 24 == 0xFF })
        assertNotEquals(px.toList(), out.toList())
    }

    @Test
    fun popArtKeepsAlphaAndFlattensColor() {
        val px = IntArray(256) { (it shl 24) or (it shl 16) or (it / 2 shl 8) or 40 }
        popArt(px, 1f)
        assertTrue(px.indices.all { px[it] ushr 24 == it })
        // Two levels per channel: every channel ends at 0 or 255.
        assertTrue(px.all { p -> listOf(16, 8, 0).all { (p shr it and 0xFF) in setOf(0, 255) } })
    }
}
