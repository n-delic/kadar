package com.ndelic.kadar.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}
