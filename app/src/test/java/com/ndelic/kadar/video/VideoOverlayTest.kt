package com.ndelic.kadar.video

import com.ndelic.kadar.model.CropRect
import com.ndelic.kadar.model.Transform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun blurSizeKeepsTheCanvasShapeAtASmallSize() {
        assertEquals(180 to 320, blurSize(1080, 1920))
        assertEquals(320 to 320, blurSize(1920, 1920))
        assertEquals(320 to 180, blurSize(1920, 1080))
        // A very thin canvas still gets a usable frame.
        assertEquals(320 to 2, blurSize(1920, 4))
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

    @Test
    fun alphaIsTheOpacityOnlyWhileTheLayerPlays() {
        assertEquals(0f, overlayAlpha(0.5f, 1_000L to 2_000L, 999_999), 0f)
        assertEquals(0.5f, overlayAlpha(0.5f, 1_000L to 2_000L, 1_000_000), 0f)
        assertEquals(0f, overlayAlpha(0.5f, 1_000L to 2_000L, 2_000_000), 0f)
    }

    @Test
    fun frameRateFollowsTheClipWithinWhatPhonesEncode() {
        assertEquals(30, outputFrameRate(29.97f))
        assertEquals(60, outputFrameRate(59.6f))
        assertEquals(60, outputFrameRate(240f))
        assertEquals(30, outputFrameRate(0f))
    }

    @Test
    fun drawnGroupsCutAtEveryVideoLayer() {
        fun groups(vararg layers: String) = drawnGroups(layers.toList()) { it.startsWith("v") }
        assertEquals(listOf(listOf("a", "b")), groups("a", "b"))
        // Video layers at the bottom leave everything in the top group, which stays the composition's own overlay.
        assertEquals(listOf(emptyList(), listOf("a")), groups("v1", "a"))
        assertEquals(listOf(listOf("a"), listOf("b", "c"), emptyList()), groups("a", "v1", "b", "c", "v2"))
        assertEquals(listOf(emptyList(), emptyList(), listOf("a")), groups("v1", "v2", "a"))
    }

    @Test
    fun sourceTimeRunsFromTheInPointOnceTheSpanStarts() {
        assertEquals(500L, overlaySourceMs(500, 1_000L to 4_000L, 1_000))
        assertEquals(2_500L, overlaySourceMs(500, 1_000L to 4_000L, 3_000))
        assertNull(overlaySourceMs(500, 1_000L to 4_000L, 999))
        assertNull(overlaySourceMs(500, 1_000L to 4_000L, 4_000))
        assertNull(overlaySourceMs(500, null, 2_000))
    }

    @Test
    fun seeksOnAnyOffsetWhenPausedButOnlyOnDriftWhilePlaying() {
        assertFalse(overlayNeedsSeek(1_000, 1_000, playing = false, sinceSeekMs = 0))
        assertTrue(overlayNeedsSeek(1_000, 1_001, playing = false, sinceSeekMs = 0))
        assertFalse(overlayNeedsSeek(1_000, 1_080, playing = true, sinceSeekMs = 5_000))
        assertTrue(overlayNeedsSeek(1_000, 1_081, playing = true, sinceSeekMs = 5_000))
        assertTrue(overlayNeedsSeek(1_100, 1_000, playing = true, sinceSeekMs = 5_000))
        // Right after a correction it gets a second to play before the next one.
        assertFalse(overlayNeedsSeek(1_000, 2_000, playing = true, sinceSeekMs = 999))
    }

    @Test
    fun cropMapsToDeviceCoordinatesWithYUp() {
        assertArrayEquals(floatArrayOf(-1f, 1f, -1f, 1f), cropNdc(CropRect()), 0.0001f)
        // The right half's lower half: x 0.5..1, y 0.5..1 down = -1..0 up.
        assertArrayEquals(floatArrayOf(0f, 1f, -1f, 0f), cropNdc(CropRect(0.5f, 0.5f, 1f, 1f)), 0.0001f)
    }
}
