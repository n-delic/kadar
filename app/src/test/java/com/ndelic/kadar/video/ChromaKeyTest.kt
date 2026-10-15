package com.ndelic.kadar.video

import com.ndelic.kadar.model.KEY_GREEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyTest {

    @Test
    fun grayHasNoChroma() {
        val (cb, cr) = keyChroma(0xFF808080.toInt())
        assertEquals(0f, cb, 1e-3f)
        assertEquals(0f, cr, 1e-3f)
    }

    @Test
    fun keyGoesAndOtherColorsStay() {
        assertEquals(0f, keyAlpha(KEY_GREEN, KEY_GREEN, 0.5f, 0.3f), 0f)
        // A darker, duller green screen still goes at the default strength: brightness doesn't count.
        assertEquals(0f, keyAlpha(0xFF008000.toInt(), KEY_GREEN, 0.5f, 0.3f), 0f)
        for (c in listOf(0xFFFFFFFF, 0xFF000000, 0xFF808080, 0xFFE0AC8C, 0xFFFF0000, 0xFF2979FF)) {
            assertEquals(1f, keyAlpha(c.toInt(), KEY_GREEN, 0.5f, 0.3f), 0f)
        }
    }

    @Test
    fun strengthWidensAndSoftnessFeathers() {
        val pixel = 0xFF40A060.toInt()
        assertEquals(1f, keyAlpha(pixel, KEY_GREEN, 0f, 0f), 0f)
        assertEquals(0f, keyAlpha(pixel, KEY_GREEN, 1f, 0f), 0f)
        val soft = (0..100).map { keyAlpha(pixel, KEY_GREEN, it / 100f, 1f) }
        // Partly kept somewhere along the way, and never more as the strength grows.
        assertTrue(soft.any { it in 0.1f..0.9f })
        assertTrue(soft.zipWithNext().all { (a, b) -> b <= a })
        val (low, high) = keyRange(0.5f, 0f)
        assertTrue(high > low && high - low < 0.01f)
    }
}
