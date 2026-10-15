package com.ndelic.kadar.video

import androidx.media3.common.C
import androidx.media3.common.audio.GainProcessor
import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.Look
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.model.glRgbMatrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import java.io.File
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

    @Test
    fun previewStructureLeavesOutWhatThePreviewTakesLive() {
        // android.net.Uri can't be made in a JVM test, so no clips here: their volumes are checked on the device.
        val video = VideoClip(emptyList(), voices = listOf(VoiceClip(File("v.m4a"), 1000, AudioPart(atMs = 200, volume = 0.3f))), canvas = 1f, blur = true)
        val structure = video.previewStructure()
        assertEquals(video.copy(voices = listOf(video.voices[0].copy(part = AudioPart(atMs = 200))), blur = false), structure)
        // A new volume or the blurred canvas keeps the structure, so the preview doesn't reload; moving the part doesn't.
        assertEquals(structure, video.withAudio(AudioRef(voice = true, index = 0)) { listOf(it.copy(volume = 0f)) }.copy(blur = false).previewStructure())
        assertNotEquals(structure, video.withAudio(AudioRef(voice = true, index = 0)) { listOf(it.copy(atMs = 300)) }.previewStructure())
    }

    @Test
    fun clipPlacementStaysInRangeAndPlacesTheFittedBox() {
        // No zooming out past the fit or in past the cap; the center stays on the canvas.
        assertEquals(ClipPlacement(1f, 0.5f, -0.5f), ClipPlacement().moved(0.8f, -0.9f, 0.5f))
        assertEquals(MAX_CLIP_ZOOM, ClipPlacement(4f).moved(0f, 0f, 2f).zoom)
        // A 1080×608 frame fitted into a 1080×1920 canvas, zoomed twice about the center, moved a quarter canvas down.
        val placed = ClipPlacement(2f, 0f, 0.25f).place(floatArrayOf(0f, 656f, 1080f, 1264f), 1080, 1920)
        assertArrayEquals(floatArrayOf(-540f, 832f, 1620f, 2048f), placed, 0.01f)
    }

    @Test
    fun placementBlurStrengthAndBarsKeepThePreviewStructure() {
        val video = VideoClip(emptyList(), canvas = 0.5f)
        assertEquals(video.previewStructure(), video.copy(placement = ClipPlacement(2f, 0.1f, 0f), blurStrength = 2f).previewStructure())
        // Black bars need no fill; under the blurred canvas they stay see-through.
        assertEquals(null, video.barFill(android.graphics.Color.BLACK))
        assertEquals(0xFFFF0000.toInt(), video.barFill(0xFFFF0000.toInt()))
        assertEquals(null, video.copy(blur = true).barFill(0xFFFF0000.toInt()))
    }

    @Test
    fun volumeGainReadsALiveVolumeOverTheFades() {
        val volumes = LiveVolumes()
        // A fade from 1 s on at a quarter, unity before it (48 kHz).
        val fades = object : GainProcessor.GainProvider {
            override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int) = if (samplePosition < 48_000) 1f else 0.25f
            override fun isUnityUntil(samplePosition: Long, sampleRate: Int) = if (samplePosition < 48_000) 48_000L else C.TIME_UNSET
        }
        val gain = VolumeGain(fades, volumes.voice(0))
        // Nothing shown yet: full volume, unity only up to a tenth of a second ahead or to the fade, never for good.
        assertEquals(1f, gain.getGainFactorAtSamplePosition(0, 48_000))
        assertEquals(4_800L, gain.isUnityUntil(0, 48_000))
        assertEquals(48_000L, gain.isUnityUntil(45_000, 48_000))
        assertEquals(50_001L, gain.isUnityUntil(50_000, 48_000))
        volumes.show(VideoClip(emptyList(), voices = listOf(VoiceClip(File("v.m4a"), 1000, AudioPart(volume = 0.5f)))))
        // The provider already in the composition hears the new volume, over the fade too.
        assertEquals(0.5f, gain.getGainFactorAtSamplePosition(0, 48_000))
        assertEquals(0.125f, gain.getGainFactorAtSamplePosition(50_000, 48_000))
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) = assertEquals(expected.toDouble(), actual.toDouble(), delta.toDouble())
}
