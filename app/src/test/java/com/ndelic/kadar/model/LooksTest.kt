package com.ndelic.kadar.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksTest {

    private val identity = FloatArray(20).also { for (i in 0..3) it[i * 6] = 1f }

    @Test
    fun everyLookIsIdentityAtAmountZero() {
        for (look in Look.entries) {
            val a = Adjust(look = look, amount = 0f)
            assertTrue(a.isNone)
            assertArrayEquals(look.name, identity, a.filterMatrix(), 0.0001f)
        }
    }

    @Test
    fun defaultsComposeToIdentity() {
        assertArrayEquals(identity, Adjust().filterMatrix(), 0.0001f)
        assertArrayEquals(identity, concat(Adjust().colorMatrix(), Look.None.matrix()), 0.0001f)
    }

    @Test
    fun monoRowsAreEqual() {
        val m = Look.Mono.matrix()
        for (col in 0..4) {
            assertEquals(m[col], m[5 + col], 0.0001f)
            assertEquals(m[col], m[10 + col], 0.0001f)
        }
    }

    @Test
    fun lookComesBeforeTheSliders() {
        // Saturation after Mono has no color left to boost, so the rows stay equal.
        val m = Adjust(saturation = 1f, look = Look.Mono).filterMatrix()
        for (col in 0..4) assertEquals(m[col], m[5 + col], 0.0001f)
        // Brightness after Fade adds on top of the look's own lift.
        assertEquals(Look.Fade.matrix()[4] + 64f, Adjust(brightness = 0.5f, look = Look.Fade).filterMatrix()[4], 0.001f)
    }

    @Test
    fun glMatrixIsColumnMajorWithOffsetsInZeroToOne() {
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f), glRgbMatrix(identity), 0f)
        val gl = glRgbMatrix(Adjust(brightness = 0.5f).colorMatrix())
        for (row in 0..2) assertEquals(64f / 255, gl[12 + row], 0.0001f)
        // Green's input into red: row 0, column 1 of the 4x5 matrix lands at column 1, row 0 of the 4x4.
        val warmish = identity.copyOf().also { it[1] = 0.3f }
        assertEquals(0.3f, glRgbMatrix(warmish)[4], 0f)
    }

    /** [m] applied to the color (r, g, b) in 0..255. */
    private fun apply(m: FloatArray, r: Float, g: Float, b: Float) = FloatArray(3) { row -> m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4] }

    @Test
    fun untouchedColorSlidersKeepTheOldMatrixExactly() {
        val a = Adjust(brightness = 0.3f, contrast = -0.2f, saturation = 0.4f, look = Look.Sepia, amount = 0.7f)
        // filterMatrix() as it was before warmth, tint, hue and fade.
        val old = concat(a.colorMatrix(), FloatArray(20) { identity[it] + a.amount * (Look.Sepia.matrix()[it] - identity[it]) })
        assertArrayEquals(old, a.filterMatrix(), 0f)
    }

    @Test
    fun anyColorSliderCounts() {
        listOf(Adjust(warmth = 0.1f), Adjust(tint = -0.1f), Adjust(hue = 0.5f), Adjust(fade = 0.2f)).forEach { assertFalse(it.toString(), it.isNone) }
    }

    @Test
    fun warmthAndTintMoveTheChannels() {
        val warm = apply(Adjust(warmth = 1f).filterMatrix(), 100f, 100f, 100f)
        assertTrue(warm[0] > 100f && warm[2] < 100f)
        assertEquals(100f, warm[1], 0.001f)
        val magenta = apply(Adjust(tint = 1f).filterMatrix(), 100f, 100f, 100f)
        assertTrue(magenta[1] < 100f)
        assertEquals(100f, magenta[0], 0.001f)
    }

    @Test
    fun hueTurnsColorsButKeepsGray() {
        val m = Adjust(hue = 0.5f).filterMatrix()
        apply(m, 128f, 128f, 128f).forEach { assertEquals(128f, it, 0.01f) }
        val red = apply(m, 200f, 0f, 0f)
        assertTrue(red[0] < 200f)
    }

    @Test
    fun fadeLiftsBlackAndDimsWhite() {
        val m = Adjust(fade = 1f).filterMatrix()
        assertTrue(apply(m, 0f, 0f, 0f)[0] > 30f)
        assertTrue(apply(m, 255f, 255f, 255f)[0] < 240f)
    }
}
