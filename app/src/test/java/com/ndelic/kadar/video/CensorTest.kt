package com.ndelic.kadar.video

import com.ndelic.kadar.model.CensorKind
import com.ndelic.kadar.model.CensorLayer
import com.ndelic.kadar.model.CensorShape
import com.ndelic.kadar.model.Transform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CensorTest {

    @Test
    fun cellGrowsWithStrengthOnTheShortSide() {
        assertEquals(10.8f, censorCell(0f, 1920, 1080), 0.01f)
        assertEquals(86.4f, censorCell(1f, 1080, 1920), 0.01f)
        assertEquals(censorCell(1f, 1080, 1920), censorCell(2f, 1080, 1920))
    }

    @Test
    fun onlyAreasShownAtTheTimeTopmostFirst() {
        val a = CensorLayer(1, Transform(100f, 200f), 50f, 40f, startMs = 0, endMs = 1000)
        val b = CensorLayer(2, Transform(300f, 400f, scale = 2f, rotation = 90f, stretch = 1.5f), 50f, 40f, CensorKind.Pixelate, CensorShape.Heart)
        val hidden = b.copy(id = 3, hidden = true)
        val u = CensorUniforms(listOf(a, b, hidden), 500, 1080, 1920)
        assertEquals(2, u.count)
        // b is on top: its center, rotation, half size (scale and stretch applied), shape and a positive (pixelate) cell.
        assertArrayEquals(floatArrayOf(300f, 400f, 0f, 1f), u.place.copyOf(4), 1e-5f)
        assertArrayEquals(floatArrayOf(75f, 40f, 2f, censorCell(0.5f, 1080, 1920)), u.shape.copyOf(4), 1e-4f)
        assertArrayEquals(floatArrayOf(100f, 200f, 1f, 0f), u.place.copyOfRange(4, 8), 1e-5f)
        assertEquals(-censorCell(0.5f, 1080, 1920), u.shape[7], 1e-4f)
        assertEquals(1, CensorUniforms(listOf(a, b), 1000, 1080, 1920).count)
    }

    @Test
    fun atMostEightAtOnce() {
        val many = List(10) { CensorLayer(it.toLong(), Transform(0f, 0f), 10f, 10f) }
        assertEquals(MAX_CENSORS, CensorUniforms(many, 0, 100, 100).count)
    }
}
