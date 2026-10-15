package com.ndelic.kadar.video

import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.Look
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.model.glRgbMatrix
import org.junit.Assert.assertArrayEquals
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

    @Test
    fun liveLooksSwapTheMatrixAnEffectReads() {
        val looks = LiveLooks()
        val first = looks.effect(0)
        val identity = glRgbMatrix(Adjust().filterMatrix())
        // Before any look, and for a clip past the list: no change to the picture.
        assertArrayEquals(identity, first.getMatrix(0, false), 0f)
        val bw = Adjust(look = Look.Mono)
        looks.show(listOf(bw, Adjust(brightness = 0.5f)))
        // The effect already in the composition reads the new look on its next frame.
        assertArrayEquals(glRgbMatrix(bw.filterMatrix()), first.getMatrix(33_000, false), 0f)
        assertArrayEquals(glRgbMatrix(Adjust(brightness = 0.5f).filterMatrix()), looks.effect(1).getMatrix(0, false), 0f)
        assertArrayEquals(identity, looks.effect(2).getMatrix(0, false), 0f)
    }

    @Test
    fun liveLooksSwapTheVignetteAndGrainAnEffectReads() {
        val looks = LiveLooks()
        val first = looks.texture(0)
        assertEquals(Adjust(), first.adjust())
        looks.show(listOf(Adjust(vignette = 0.4f, grain = 0.2f)))
        assertEquals(Adjust(vignette = 0.4f, grain = 0.2f), first.adjust())
        assertEquals(Adjust(), looks.texture(1).adjust())
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) = assertEquals(expected.toDouble(), actual.toDouble(), delta.toDouble())
}
