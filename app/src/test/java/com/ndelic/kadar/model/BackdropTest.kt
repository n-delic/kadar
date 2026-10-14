package com.ndelic.kadar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTest {

    @Test
    fun aFlatColorStaysTheSame() {
        val px = IntArray(8 * 6) { 0xFF336699.toInt() }
        boxBlur(px, 8, 6, 2)
        assertTrue(px.all { it == 0xFF336699.toInt() })
    }

    @Test
    fun seeThroughNeighboursDontDarkenTheColor() {
        // Red on the left half, transparent black on the right: the blur fades red out, it doesn't turn it brown.
        val px = IntArray(10) { if (it < 5) 0xFFFF0000.toInt() else 0 }
        boxBlur(px, 10, 1, 2)
        val edge = px[5]
        assertTrue(edge ushr 24 in 1..254)
        assertEquals(0xFF0000, edge and 0xFFFFFF)
    }

    @Test
    fun aBrightDotSpreads() {
        val px = IntArray(9 * 9) { 0xFF000000.toInt() }
        px[4 * 9 + 4] = 0xFFFFFFFF.toInt()
        boxBlur(px, 9, 9, 1)
        // The center keeps the most light, and some reaches the next pixels over.
        val center = px[4 * 9 + 4] and 0xFF
        val side = px[4 * 9 + 5] and 0xFF
        assertTrue(center in 1..254 && side in 1 until center)
    }
}
