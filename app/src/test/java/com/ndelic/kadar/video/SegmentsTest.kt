package com.ndelic.kadar.video

import com.ndelic.kadar.model.CropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentsTest {

    @Test
    fun outputLengthFollowsSpeed() {
        assertEquals(4000, outputMs(1000, 5000, 1f))
        assertEquals(2000, outputMs(1000, 5000, 2f))
        assertEquals(8000, outputMs(1000, 5000, 0.5f))
    }

    @Test
    fun clipAtFindsTheClipAndTheTimeIntoIt() {
        val lengths = listOf(3000L, 1000L, 2000L)
        assertEquals(0 to 0L, clipAt(lengths, 0))
        assertEquals(0 to 2999L, clipAt(lengths, 2999))
        // A boundary belongs to the clip that starts there.
        assertEquals(1 to 0L, clipAt(lengths, 3000))
        assertEquals(2 to 500L, clipAt(lengths, 4500))
        // The very end, and past it, is the last clip's end.
        assertEquals(2 to 2000L, clipAt(lengths, 6000))
        assertEquals(2 to 2000L, clipAt(lengths, 9000))
        assertEquals(0 to 0L, clipAt(listOf(1000L), -5))
    }

    @Test
    fun splitPointIsInSourceTimeAndKeepsBothPiecesLongEnough() {
        // 2 s into a clip trimmed from 1 s, played at 2x: 4 s of source later.
        assertEquals(5000L, splitPoint(1000, 9000, 2f, 2000))
        assertEquals(1500L, splitPoint(0, 3000, 1f, 1500))
        assertNull(splitPoint(0, 3000, 1f, MIN_CLIP_MS - 1))
        assertNull(splitPoint(0, 3000, 1f, 3000 - MIN_CLIP_MS + 1))
    }

    @Test
    fun layerSplitPointKeepsBothPartsLongEnough() {
        assertEquals(2000L, layerSplitPoint(1000, 4000, 10_000, 2000))
        assertNull(layerSplitPoint(1000, 4000, 10_000, 1000 + MIN_CLIP_MS - 1))
        assertNull(layerSplitPoint(1000, 4000, 10_000, 4000 - MIN_CLIP_MS + 1))
        assertNull(layerSplitPoint(1000, 4000, 10_000, 5000))
        // A layer to the end ends with the output.
        assertEquals(9000L, layerSplitPoint(0, Long.MAX_VALUE, 10_000, 9000))
        assertNull(layerSplitPoint(0, Long.MAX_VALUE, 10_000, 9900))
    }

    @Test
    fun layerMovedToKeepsItsLength() {
        assertEquals(5000L to 7500L, layerMovedTo(1000, 3500, 5000))
        assertEquals(0L to 2500L, layerMovedTo(1000, 3500, 0))
        assertEquals(4000L to Long.MAX_VALUE, layerMovedTo(1000, Long.MAX_VALUE, 4000))
    }

    @Test
    fun movedReordersOneItem() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(1, 1))
    }

    @Test
    fun edgeTrimMovesOneEdgeInSourceTimeWithinLimits() {
        // 1 s of output at 2x is 2 s of source.
        assertEquals(3000L to 9000L, edgeTrim(1000, 9000, 10_000, 2f, left = true, dMs = 1000))
        assertEquals(1000L to 7000L, edgeTrim(1000, 9000, 10_000, 2f, left = false, dMs = -1000))
        // Not past the source's ends, a photo's longest showing included.
        assertEquals(0L to 9000L, edgeTrim(1000, 9000, 10_000, 1f, left = true, dMs = -5000))
        assertEquals(1000L to 30_000L, edgeTrim(1000, 3000, 30_000, 1f, left = false, dMs = 60_000))
        // Never shorter than MIN_CLIP_MS, from either side.
        assertEquals(9000L - MIN_CLIP_MS to 9000L, edgeTrim(1000, 9000, 10_000, 1f, left = true, dMs = 20_000))
        assertEquals(1000L to 1000L + MIN_CLIP_MS, edgeTrim(1000, 9000, 10_000, 1f, left = false, dMs = -20_000))
        // A source shorter than that doesn't throw.
        assertEquals(0L to 200L, edgeTrim(0, 200, 200, 1f, left = true, dMs = 100))
        assertEquals(0L to 200L, edgeTrim(0, 200, 200, 1f, left = false, dMs = -100))
    }

    @Test
    fun aTransitionIsCappedByAClipTrimmedShorterUnderIt() {
        val t = listOf(Transition(), Transition(TransitionKind.Dissolve, 1500))
        val (start, end) = edgeTrim(0, 10_000, 10_000, 1f, left = false, dMs = -9000)
        val lengths = listOf(10_000L, outputMs(start, end, 1f))
        assertEquals(500L, transitionSpans(lengths, t).single().let { it.endMs - it.startMs })
    }

    @Test
    fun speedSliderRunsFromMinToMaxAndSnaps() {
        assertEquals(MIN_SPEED, speedAt(0f))
        assertEquals(MAX_SPEED, speedAt(1f))
        listOf(0.5f, 1f, 2f).forEach { assertEquals(it, speedAt(speedPosition(it))) }
        // Near a snap point lands on it; away from them, the value is rounded.
        assertEquals(1f, speedAt(speedPosition(1.06f)))
        assertEquals(3.5f, speedAt(speedPosition(3.52f)))
        assertEquals(0.3f, speedAt(speedPosition(0.301f)))
        // Old projects' fixed steps keep their place.
        listOf(0.25f, 0.75f, 1.5f, 3f, 4f).forEach { assertEquals(it, speedAt(speedPosition(it)), 0.001f) }
    }

    @Test
    fun aQuarterTurnSwapsTheSize() {
        assertEquals(1920 to 1080, turnedSize(1920, 1080, 0))
        assertEquals(1080 to 1920, turnedSize(1920, 1080, 1))
        assertEquals(1920 to 1080, turnedSize(1920, 1080, 2))
        assertEquals(1080 to 1920, turnedSize(1920, 1080, 3))
    }

    @Test
    fun cropsTurnWithThePicture() {
        // The left half of the source: turned clockwise, it's the top half; upside down, the right half.
        val left = CropRect(0f, 0f, 0.5f, 1f)
        assertCrop(CropRect(0f, 0f, 1f, 0.5f), turnedCrop(left, 1, flipX = false, flipY = false))
        assertCrop(CropRect(0.5f, 0f, 1f, 1f), turnedCrop(left, 2, flipX = false, flipY = false))
        assertCrop(CropRect(0f, 0.5f, 1f, 1f), turnedCrop(left, 3, flipX = false, flipY = false))
        // Mirrored first, it's the right half, which a clockwise turn puts at the bottom.
        assertCrop(CropRect(0f, 0.5f, 1f, 1f), turnedCrop(left, 1, flipX = true, flipY = false))
        // The top-left quarter's corner goes to the top right after a clockwise turn.
        assertCrop(CropRect(0.75f, 0f, 1f, 0.25f), turnedCrop(CropRect(0f, 0f, 0.25f, 0.25f), 1, flipX = false, flipY = false))
    }

    @Test
    fun unturnedCropUndoesTurnedCrop() {
        val c = CropRect(0.1f, 0.2f, 0.6f, 0.9f)
        for (turns in 0..3) for (fx in listOf(false, true)) for (fy in listOf(false, true)) {
            assertCrop(c, unturnedCrop(turnedCrop(c, turns, fx, fy), turns, fx, fy))
        }
    }

    private fun assertCrop(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, 1e-6f)
        assertEquals(expected.top, actual.top, 1e-6f)
        assertEquals(expected.right, actual.right, 1e-6f)
        assertEquals(expected.bottom, actual.bottom, 1e-6f)
    }
}
