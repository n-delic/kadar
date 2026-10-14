package com.ndelic.kadar.video

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
}
