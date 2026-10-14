package com.ndelic.kadar.video

import android.media.MediaMetadataRetriever
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A GIF export's frame rate: 10 fps is a whole 10 cs per frame, the unit GIF delays are counted in. */
const val GIF_FPS = 10

/** Long side of a GIF export, in pixels: plenty for a chat or a post, and it keeps the file to a few MB. */
const val GIF_LONG_SIDE = 480

/** Longest video a GIF export takes, in ms: past that a GIF gets too big to share (about 1 MB a second). */
const val GIF_MAX_MS = 15_000L

/** The short side a [width]×[height] video is scaled to so its long side is [GIF_LONG_SIDE] (never upscaled). */
fun gifShortSide(width: Int, height: Int) =
    minOf(minOf(width, height), (GIF_LONG_SIDE * minOf(width, height) / maxOf(width, height).toFloat()).roundToInt())

/**
 * Writes every frame of the MP4 [video], made at [GIF_FPS], as a GIF to [out]; [onProgress] gets the share done (0..1).
 * Stops when the coroutine is cancelled.
 */
suspend fun mp4ToGif(video: File, out: OutputStream, onProgress: (Float) -> Unit) = withContext(Dispatchers.Default) {
    MediaMetadataRetriever().use { r ->
        r.setDataSource(video.path)
        val count = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
        var writer: GifWriter? = null
        var pixels = IntArray(0)
        var i = 0
        while (i < count) {
            ensureActive()
            // A few at a time: one decoder pass for the batch, and only a few frames in memory.
            r.getFramesAtIndex(i, minOf(FRAME_BATCH, count - i)).forEach { b ->
                val w = writer ?: GifWriter(out, b.width, b.height).also {
                    writer = it
                    pixels = IntArray(b.width * b.height)
                }
                b.getPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
                b.recycle()
                w.frame(pixels, 100 / GIF_FPS)
                onProgress(++i / count.toFloat())
            }
        }
        checkNotNull(writer) { "No frames" }.finish()
    }
}

private const val FRAME_BATCH = 10

/**
 * Writes an endlessly looping GIF89a of [width]×[height] frames to [out]: [frame] adds one (ARGB pixels, alpha
 * ignored) with its own 256-color palette, [finish] ends the file. Doesn't close [out].
 */
class GifWriter(private val out: OutputStream, private val width: Int, private val height: Int) {
    private val lzw = LzwEncoder()

    init {
        out.write("GIF89a".toByteArray())
        short(width)
        short(height)
        // No global color table: each frame has its own.
        out.write(byteArrayOf(0, 0, 0))
        // NETSCAPE2.0 application extension: loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 11) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))
    }

    fun frame(argb: IntArray, delayCs: Int) {
        val (palette, indices) = quantize(argb)
        // Graphic control extension: keep the frame in place (disposal 1), no transparency.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0x04))
        short(delayCs)
        out.write(byteArrayOf(0, 0))
        // Image descriptor at 0,0 with a local color table of 2^(7+1) entries.
        out.write(0x2C)
        short(0)
        short(0)
        short(width)
        short(height)
        out.write(0x87)
        for (i in 0 until 256) {
            val c = palette.getOrElse(i) { 0 }
            out.write(c shr 16 and 0xFF)
            out.write(c shr 8 and 0xFF)
            out.write(c and 0xFF)
        }
        lzw.encode(indices, out)
    }

    fun finish() = out.write(0x3B)

    private fun short(v: Int) {
        out.write(v and 0xFF)
        out.write(v shr 8 and 0xFF)
    }
}

/**
 * Up to 256 colors for [argb] by median cut over 5-bit-per-channel buckets, and each pixel's palette index. Each
 * bucket's color is the average of its pixels, so a picture with few colors keeps them exactly.
 * shortcut: no dithering, so smooth gradients band a little; add Floyd-Steinberg if that shows in real GIFs.
 */
fun quantize(argb: IntArray): Pair<IntArray, ByteArray> {
    val count = IntArray(BUCKETS)
    val sumR = LongArray(BUCKETS)
    val sumG = LongArray(BUCKETS)
    val sumB = LongArray(BUCKETS)
    for (c in argb) {
        val k = bucket(c)
        count[k]++
        sumR[k] += (c shr 16 and 0xFF).toLong()
        sumG[k] += (c shr 8 and 0xFF).toLong()
        sumB[k] += (c and 0xFF).toLong()
    }
    val boxes = mutableListOf(Box((0 until BUCKETS).filter { count[it] > 0 }.toIntArray()))
    while (boxes.size < 256) {
        // Split the box with the widest spread in one channel, at the median of its pixels along that channel.
        val i = boxes.indices.filter { boxes[it].keys.size > 1 }.maxByOrNull { boxes[it].spread } ?: break
        val box = boxes[i].keys.sortedBy { channelOf(it, boxes[i].channel) }.toIntArray()
        val half = box.sumOf { count[it].toLong() } / 2
        var seen = 0L
        var cut = 0
        while (cut < box.size - 1 && seen < half) seen += count[box[cut++]]
        boxes[i] = Box(box.copyOfRange(0, cut))
        boxes += Box(box.copyOfRange(cut, box.size))
    }
    val index = IntArray(BUCKETS)
    val palette = IntArray(boxes.size) { b ->
        var n = 0L
        var r = 0L
        var g = 0L
        var bl = 0L
        for (k in boxes[b].keys) {
            index[k] = b
            n += count[k]
            r += sumR[k]
            g += sumG[k]
            bl += sumB[k]
        }
        ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }
    return palette to ByteArray(argb.size) { index[bucket(argb[it])].toByte() }
}

private const val BUCKETS = 1 shl 15

private fun bucket(c: Int) = (c shr 9 and 0x7C00) or (c shr 6 and 0x3E0) or (c shr 3 and 0x1F)

private fun channelOf(bucket: Int, channel: Int) = bucket shr (10 - 5 * channel) and 0x1F

/** Buckets of [quantize] that become one palette color; [channel] is the one they spread most in. */
private class Box(val keys: IntArray) {
    val channel: Int
    val spread: Int

    init {
        val ranges = (0..2).map { ch -> keys.maxOf { channelOf(it, ch) } - keys.minOf { channelOf(it, ch) } }
        spread = ranges.max()
        channel = ranges.indexOf(spread)
    }
}

/** GIF's variable-length LZW for 8-bit indices, written as data sub-blocks. One per GIF: its tables are reused. */
private class LzwEncoder {
    // Codes by prefix code and next index; an entry counts only if its stamp is the current generation, so a clear is O(1).
    private val codes = IntArray(MAX_CODES shl 8)
    private val stamps = IntArray(MAX_CODES shl 8)
    private var generation = 0
    private val block = ByteArray(255)
    private var blockSize = 0
    private var bits = 0
    private var bitCount = 0

    fun encode(indices: ByteArray, out: OutputStream) {
        out.write(MIN_CODE_SIZE)
        var codeSize = MIN_CODE_SIZE + 1
        var next = EOI + 1
        generation++
        write(CLEAR, codeSize, out)
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val c = indices[i].toInt() and 0xFF
            val key = prefix shl 8 or c
            if (stamps[key] == generation) {
                prefix = codes[key]
                continue
            }
            write(prefix, codeSize, out)
            if (next < MAX_CODES) {
                // The decoder reads the code after this one with one more bit once the table reaches 2^codeSize.
                if (next == 1 shl codeSize) codeSize++
                stamps[key] = generation
                codes[key] = next++
            } else {
                write(CLEAR, codeSize, out)
                codeSize = MIN_CODE_SIZE + 1
                next = EOI + 1
                generation++
            }
            prefix = c
        }
        write(prefix, codeSize, out)
        write(EOI, codeSize, out)
        if (bitCount > 0) byte(bits and 0xFF, out)
        bits = 0
        bitCount = 0
        flush(out)
        out.write(0)
    }

    private fun write(code: Int, size: Int, out: OutputStream) {
        bits = bits or (code shl bitCount)
        bitCount += size
        while (bitCount >= 8) {
            byte(bits and 0xFF, out)
            bits = bits ushr 8
            bitCount -= 8
        }
    }

    private fun byte(b: Int, out: OutputStream) {
        block[blockSize++] = b.toByte()
        if (blockSize == block.size) flush(out)
    }

    private fun flush(out: OutputStream) {
        if (blockSize == 0) return
        out.write(blockSize)
        out.write(block, 0, blockSize)
        blockSize = 0
    }

    private companion object {
        const val MIN_CODE_SIZE = 8
        const val CLEAR = 256
        const val EOI = 257
        const val MAX_CODES = 4096
    }
}
