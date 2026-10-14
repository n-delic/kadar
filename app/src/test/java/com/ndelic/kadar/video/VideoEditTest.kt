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

    @Test
    fun standardBitrateIsHalfOfHighUncapped() {
        // Media3's DefaultEncoderFactory: width × height × fps × 0.07 × 2.
        assertEquals(8_709_120, standardBitrate(1080, 1920, null, 30f), 100)
        assertEquals(standardBitrate(720, 1280, null, 24f), standardBitrate(1080, 1920, 720, 24f), 100)
        // Not capped like High: the encoder clamps it.
        assertEquals(69_672_960, standardBitrate(3840, 2160, null, 60f), 100)
    }

    @Test
    fun estimatedBytesAddsVideoAndSound() {
        // 10 s at 8 Mbps is 10 MB; AAC adds 128 KiB/s worth of bits.
        assertEquals(10_000_000L, estimatedBytes(10_000, 8_000_000, sound = false))
        assertEquals(10_000_000L + 163_840, estimatedBytes(10_000, 8_000_000, sound = true))
        assertEquals(0L, estimatedBytes(0, 8_000_000, sound = true))
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) = assertEquals(expected.toDouble(), actual.toDouble(), delta.toDouble())
}
