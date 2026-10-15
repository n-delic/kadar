package com.ndelic.kadar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class ImageOutlineTest {

    private fun bruteForce(inside: BooleanArray, w: Int, h: Int, sx: Float) = FloatArray(w * h) { i ->
        var best = Float.MAX_VALUE
        for (j in inside.indices) if (inside[j]) best = minOf(best, hypot((i % w - j % w) * sx, (i / w - j / w).toFloat()))
        best
    }

    @Test
    fun distanceFieldIsExact() {
        val rnd = Random(7)
        for (sx in floatArrayOf(1f, 0.5f, 2.3f)) {
            val w = 23
            val h = 17
            val inside = BooleanArray(w * h) { rnd.nextInt(30) == 0 }
            val d = distanceField(inside, w, h, sx)
            val want = bruteForce(inside, w, h, sx)
            for (i in d.indices) assertEquals(want[i], d[i], 1e-3f)
        }
    }

    @Test
    fun oneDotGrowsARoundSpot() {
        val inside = BooleanArray(9 * 9).also { it[4 * 9 + 4] = true }
        val d = distanceField(inside, 9, 9)
        assertEquals(0f, d[4 * 9 + 4], 0f)
        assertEquals(3f, d[4 * 9 + 7], 1e-4f)
        assertEquals(hypot(3f, 4f), d[0 * 9 + 1], 1e-4f)
    }

    @Test
    fun aSolidOutlineHasAHardEdgeAtItsWidth() {
        assertEquals(1f, outlineAlpha(OutlineStyle.Solid, 0f, 5f), 0f)
        assertEquals(1f, outlineAlpha(OutlineStyle.Solid, 4.5f, 5f), 0f)
        assertEquals(0.5f, outlineAlpha(OutlineStyle.Solid, 5f, 5f), 0f)
        assertEquals(0f, outlineAlpha(OutlineStyle.Solid, 6f, 5f), 0f)
    }

    @Test
    fun aGlowFadesOut() {
        val near = outlineAlpha(OutlineStyle.Glow, 1f, 10f)
        val far = outlineAlpha(OutlineStyle.Glow, 8f, 10f)
        assertTrue(near < 1f && far in 0.001f..near)
        assertEquals(0f, outlineAlpha(OutlineStyle.Shadow, 10f, 10f), 0f)
    }

    @Test
    fun stretchIsRoundedToSteps() {
        assertEquals(1f, quantizedStretch(1f), 0f)
        assertEquals(1f, quantizedStretch(1.04f), 0f)
        assertEquals(1.1f, quantizedStretch(1.08f), 1e-5f)
        assertEquals(1 / 1.1f, quantizedStretch(0.92f), 1e-5f)
    }
}
