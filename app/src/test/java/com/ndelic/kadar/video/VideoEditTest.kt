package com.ndelic.kadar.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEditTest {

    @Test
    fun highBitrateFollowsPixelsAndFrameRate() {
        // 1080×1920 at 30 fps: about what a phone camera records.
        assertEquals(17_418_240, highBitrate(1080, 1920, null, 30f), 100)
        // Unknown frame rate counts as 30 fps; 60 fps doubles it.
        assertEquals(highBitrate(1080, 1920, null, 30f), highBitrate(1080, 1920, null, 0f))
        assertEquals(2 * highBitrate(1080, 1920, null, 30f), highBitrate(1080, 1920, null, 60f), 100)
    }

    @Test
    fun highBitrateUsesTheDownscaledSizeAndIsCapped() {
        assertEquals(highBitrate(720, 1280, null, 30f), highBitrate(1080, 1920, 720, 30f), 100)
        // A larger short side never upscales.
        assertEquals(highBitrate(1080, 1920, null, 30f), highBitrate(1080, 1920, 2160, 30f))
        assertEquals(50_000_000, highBitrate(3840, 2160, null, 60f))
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) = assertEquals(expected.toDouble(), actual.toDouble(), delta.toDouble())
}
