package com.ndelic.kadar.video

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifTest {
    private val w = 160
    private val h = 120

    private fun decode(bytes: ByteArray): List<IntArray> {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        return (0 until reader.getNumImages(true)).map { i ->
            val img = reader.read(i)
            assertEquals(w, img.width)
            assertEquals(h, img.height)
            img.getRGB(0, 0, w, h, null, 0, w)
        }
    }

    private fun gif(vararg frames: IntArray) = ByteArrayOutputStream().also { out ->
        val writer = GifWriter(out, w, h)
        frames.forEach { writer.frame(it, 10) }
        writer.finish()
    }.toByteArray()

    @Test
    fun fewColorsComeBackExactly() {
        val colors = intArrayOf(0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFF12AB34.toInt(), 0xFFFFFFFF.toInt())
        val stripes = IntArray(w * h) { colors[(it % w) / 40] }
        val frames = decode(gif(stripes, stripes.reversedArray()))
        assertEquals(2, frames.size)
        assertTrue(stripes.contentEquals(frames[0]))
        assertTrue(stripes.reversedArray().contentEquals(frames[1]))
    }

    @Test
    fun noiseAndGradientsStayClose() {
        // Noise fills the LZW table many times over (clear codes and every code size); the gradient needs a real palette.
        val random = Random(7)
        val noise = IntArray(w * h) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val gradient = IntArray(w * h) { i -> 0xFF000000.toInt() or ((i % w) * 255 / w shl 16) or ((i / w) * 255 / h shl 8) or 128 }
        val (decodedNoise, decodedGradient) = decode(gif(noise, gradient))
        fun error(a: IntArray, b: IntArray) = a.indices.maxOf { i -> (0..2).maxOf { ch -> abs((a[i] shr 8 * ch and 0xFF) - (b[i] shr 8 * ch and 0xFF)) } }
        // 1024 buckets in 256 colors: each color covers about two buckets (8 levels each) per channel.
        assertTrue(error(gradient, decodedGradient) <= 16)
        // 256 colors for 19200 random ones: each channel is still in the right neighbourhood on average.
        val mean = noise.indices.sumOf { i -> (0..2).sumOf { ch -> abs((noise[i] shr 8 * ch and 0xFF) - (decodedNoise[i] shr 8 * ch and 0xFF)) } } / (3.0 * noise.size)
        assertTrue("mean error $mean", mean < 24)
    }

    @Test
    fun shortSideKeepsTheLongSideAtMost480() {
        assertEquals(270, gifShortSide(1920, 1080))
        assertEquals(270, gifShortSide(1080, 1920))
        assertEquals(480, gifShortSide(1080, 1080))
        assertEquals(200, gifShortSide(300, 200))
    }
}
