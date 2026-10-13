package com.ndelic.kadar.video

import com.ndelic.kadar.video.TransitionKind.Black
import com.ndelic.kadar.video.TransitionKind.Dissolve
import com.ndelic.kadar.video.TransitionKind.None
import com.ndelic.kadar.video.TransitionKind.PushLeft
import com.ndelic.kadar.video.TransitionKind.SlideUp
import com.ndelic.kadar.video.TransitionKind.WipeLeft
import com.ndelic.kadar.video.TransitionKind.WipeUp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransitionsTest {

    @Test
    fun durationIsCappedByTheShorterNeighbour() {
        assertEquals(500L, transitionMs(Transition(Dissolve, 500), 4000, 3000))
        // Half the shorter clip, so a clip's two transitions never meet.
        assertEquals(400L, transitionMs(Transition(Dissolve, 1500), 4000, 800))
        // Too short for any.
        assertEquals(0L, transitionMs(Transition(Black, 1000), 4000, 150))
        assertEquals(0L, transitionMs(Transition(None, 1000), 4000, 4000))
    }

    @Test
    fun overlappingTransitionsCutTheIncomingHeads() {
        val lengths = listOf(3000L, 2000L, 4000L)
        val transitions = listOf(Transition(Dissolve, 500), Transition(Dissolve, 500), Transition(Black, 600))
        // The first clip's transition is unused, a dip overlaps nothing.
        assertEquals(listOf(0L, 500L, 0L), headsMs(lengths, transitions))
    }

    @Test
    fun spansSitOverTheEndOfTheClipBeforeOrAroundTheCut() {
        val lengths = listOf(3000L, 2000L, 4000L)
        val transitions = listOf(Transition(), Transition(Dissolve, 500), Transition(Black, 600))
        // The main track plays 3000, then 1500 of the second clip (its head is on top of the first's end), then 4000.
        assertEquals(
            listOf(TransitionSpan(1, Dissolve, 2500, 3000), TransitionSpan(2, Black, 4200, 4800)),
            transitionSpans(lengths, transitions),
        )
        assertEquals(emptyList<TransitionSpan>(), transitionSpans(listOf(3000L), listOf(Transition(Dissolve, 500))))
    }

    @Test
    fun headsPlayOnPastTheCutUntilTheNextOne() {
        val spans = listOf(TransitionSpan(1, Dissolve, 1000, 1500), TransitionSpan(2, PushLeft, 1520, 2000), TransitionSpan(3, Dissolve, 4000, 4500))
        assertEquals(listOf(20L, HEAD_TAIL_MS, HEAD_TAIL_MS), headTailsMs(spans))
    }

    @Test
    fun outputFrameIsTheNearestOnTheGrid() {
        // 30 fps: frames at 0, 33333, 66666 us.
        assertEquals(33_333L, outputFrameUs(45_000, 30))
        assertEquals(66_666L, outputFrameUs(55_000, 30))
        assertEquals(5_533_333L, outputFrameUs(5_546_000, 30))
    }

    @Test
    fun progressRunsThroughTheSpan() {
        val span = TransitionSpan(1, Dissolve, 1000, 1500)
        assertEquals(0f, span.progress(900), 0f)
        assertEquals(0.5f, span.progress(1250), 0f)
        assertEquals(1f, span.progress(2000), 0f)
        assertEquals(true, 1499L in span)
        assertEquals(false, 1500L in span)
    }

    @Test
    fun dipPeaksAtTheCut() {
        assertEquals(0f, dipAt(0f), 0f)
        assertEquals(1f, dipAt(0.5f), 0f)
        assertEquals(0f, dipAt(1f), 0f)
    }

    @Test
    fun movesStartOffCanvasAndEndInPlace() {
        // Slide up: in from below, y down.
        assertEquals(0f to 1f, SlideUp.incomingOffset(0f))
        assertEquals(0f to 0f, SlideUp.incomingOffset(1f))
        // A push moves the outgoing clip out the way it goes while the incoming comes in from the other side.
        assertEquals(1f to 0f, PushLeft.incomingOffset(0f))
        assertEquals(-1f to 0f, PushLeft.outgoingOffset(1f))
        assertEquals(0f to 0f, SlideUp.outgoingOffset(1f))
        assertEquals(0.5f, Dissolve.incomingAlpha(0.5f), 0f)
        assertEquals(1f, PushLeft.incomingAlpha(0.2f), 0f)
    }

    @Test
    fun wipesRevealFromTheSideTheyMoveAwayFrom() {
        assertArrayEquals(floatArrayOf(1f, 0f, 1f, 1f), WipeLeft.wipeRect(0f), 0f)
        assertArrayEquals(floatArrayOf(0.5f, 0f, 1f, 1f), WipeLeft.wipeRect(0.5f), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f), WipeUp.wipeRect(1f), 0f)
        assertNull(Dissolve.wipeRect(0.5f))
    }
}
