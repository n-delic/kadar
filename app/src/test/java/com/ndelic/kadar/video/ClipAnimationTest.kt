package com.ndelic.kadar.video

import com.ndelic.kadar.video.ClipAnimKind.Fade
import com.ndelic.kadar.video.ClipAnimKind.None
import com.ndelic.kadar.video.ClipAnimKind.SlideLeft
import com.ndelic.kadar.video.ClipAnimKind.Spin
import com.ndelic.kadar.video.ClipAnimKind.ZoomIn
import com.ndelic.kadar.video.ClipAnimKind.ZoomOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipAnimationTest {

    private val none = ClipAnimation()

    @Test
    fun entrancesAndExitsSitAtTheClipsEdgesInOutputTime() {
        val spans = clipAnimSpans(
            listOf(3000L, 2000L),
            listOf(Transition(), Transition()),
            listOf(ClipAnimation(ZoomIn, 500), ClipAnimation(Fade, 400)),
            listOf(ClipAnimation(SlideLeft, 600), ClipAnimation(Spin, 300)),
        )
        assertEquals(
            listOf(
                ClipAnimSpan(0, ZoomIn, false, 0, 500),
                ClipAnimSpan(0, SlideLeft, true, 2400, 3000),
                ClipAnimSpan(1, Fade, false, 3000, 3400),
                ClipAnimSpan(1, Spin, true, 4700, 5000),
            ),
            spans,
        )
    }

    @Test
    fun aTransitionAtTheCutPlaysInstead() {
        val lengths = listOf(3000L, 2000L, 2000L)
        val ins = List(3) { ClipAnimation(Fade, 500) }
        val outs = List(3) { ClipAnimation(Fade, 500) }
        // A dissolve into clip 1 and a dip to black into clip 2: only clip 0's entrance and clip 2's exit are left.
        val spans = clipAnimSpans(lengths, listOf(Transition(), Transition(TransitionKind.Dissolve, 500), Transition(TransitionKind.Black, 500)), ins, outs)
        // The dissolve's head is cut from clip 1, so the video is 6500 long.
        assertEquals(listOf(ClipAnimSpan(0, Fade, false, 0, 500), ClipAnimSpan(2, Fade, true, 6000, 6500)), spans)
        // A transition too long for its clips is none, so the animations play.
        assertEquals(4, clipAnimSpans(listOf(3000L, 100L), listOf(Transition(), Transition(TransitionKind.Black, 500)), ins.take(2), outs.take(2)).size)
    }

    @Test
    fun lengthIsCappedAtHalfThePlayedClip() {
        val spans = clipAnimSpans(listOf(800L), listOf(Transition()), listOf(ClipAnimation(ZoomOut, 3000)), listOf(none))
        assertEquals(listOf(ClipAnimSpan(0, ZoomOut, false, 0, 400)), spans)
    }

    @Test
    fun anEntranceComesIntoPlaceAndAnExitLeaves() {
        val enter = ClipAnimSpan(0, Fade, false, 1000, 1500)
        assertEquals(0f, enter.presence(1000), 0f)
        assertEquals(1f, enter.presence(1500), 0f)
        val leave = ClipAnimSpan(0, Fade, true, 1000, 1500)
        assertEquals(1f, leave.presence(1000), 0f)
        assertEquals(0f, leave.presence(1500), 0f)
        assertTrue(leave.presence(1250) in 0f..1f)
    }

    @Test
    fun looksAreInPlaceAtFullPresence() {
        ClipAnimKind.entries.forEach { k ->
            assertEquals(ClipLook(), k.look(1f, exit = false))
            assertEquals(ClipLook(), k.look(1f, exit = true))
        }
    }

    @Test
    fun outOfViewLooks() {
        assertEquals(0f, Fade.look(0f, false).alpha, 0f)
        assertEquals(0f, ZoomIn.look(0f, false).scale, 0f)
        assertEquals(2f, ZoomOut.look(0f, true).scale, 0f)
        // A slide left comes in from the right and goes out to the left.
        assertEquals(1f, SlideLeft.look(0f, false).dx, 0f)
        assertEquals(-1f, SlideLeft.look(0f, true).dx, 0f)
        // A spin turns clockwise both ways: from a turn back to none, then from none on to a turn.
        assertEquals(-360f, Spin.look(0f, false).degrees, 0f)
        assertEquals(360f, Spin.look(0f, true).degrees, 0f)
        assertEquals(ClipLook(), None.look(0f, false))
    }
}
