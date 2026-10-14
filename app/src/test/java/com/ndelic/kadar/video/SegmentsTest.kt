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
}
