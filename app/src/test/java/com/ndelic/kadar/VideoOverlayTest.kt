package com.ndelic.kadar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoOverlayTest {

    @Test
    fun placementMapsCanvasPixelsToDeviceCoordinates() {
        assertPlacement(OverlayPlacement(0f, 0f, 1f, 0f), overlayPlacement(Transform(540f, 960f), 1080, 1920))
        // Top-left corner is (-1, 1): the compositor's y points up.
        assertPlacement(OverlayPlacement(-1f, 1f, 1f, 0f), overlayPlacement(Transform(0f, 0f), 1080, 1920))
        // Lower-right quarter point, half size, turned 30° clockwise on the canvas = -30° counter-clockwise.
        assertPlacement(OverlayPlacement(0.5f, -0.5f, 0.5f, -30f), overlayPlacement(Transform(810f, 1440f, 0.5f, 30f), 1080, 1920))
    }

    // Component-wise, since data class equality tells 0f from -0f.
    private fun assertPlacement(expected: OverlayPlacement, actual: OverlayPlacement) {
        assertEquals(expected.x, actual.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
        assertEquals(expected.scale, actual.scale, 1e-4f)
        assertEquals(expected.rotation, actual.rotation, 1e-4f)
    }

    @Test
    fun spanStopsAtTheLayerTheClipOrTheOutputEnd() {
        assertEquals(1_000L to 4_000L, overlaySpan(1_000, 9_000, 3_000, 10_000))
        assertEquals(1_000L to 2_000L, overlaySpan(1_000, 2_000, 3_000, 10_000))
        assertEquals(8_000L to 10_000L, overlaySpan(8_000, Long.MAX_VALUE, 5_000, 10_000))
    }

    @Test
    fun spanIsNullWhenNothingWouldShow() {
        assertNull(overlaySpan(10_000, 12_000, 3_000, 10_000))
        assertNull(overlaySpan(9_950, 12_000, 3_000, 10_000))
    }
}
