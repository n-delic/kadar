package com.ndelic.kadar.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorPickerTest {

    @Test
    fun parsesSixHexDigitsAsAnOpaqueColor() {
        assertEquals(0xFFC6F432.toInt(), parseHexColor("C6F432"))
        assertEquals(0xFFC6F432.toInt(), parseHexColor(" #c6f432 "))
        assertEquals(0xFF000000.toInt(), parseHexColor("000000"))
    }

    @Test
    fun rejectsAnythingElse() {
        for (text in listOf("", "#FFF", "1234567", "GGGGGG", "-12345", "+12345", "red")) assertNull(text, parseHexColor(text))
    }

    @Test
    fun hexDropsAlphaAndRoundTrips() {
        assertEquals("C6F432", hexOf(0x80C6F432.toInt()))
        assertEquals(0xFF00B0FF.toInt(), parseHexColor(hexOf(0xFF00B0FF.toInt())))
    }
}
