package com.ndelic.kadar.model

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTest {

    private fun fade(ms: Long, start: Long = 1000, end: Long = 3000) = motionAt(Anim.Fade, Anim.Fade, start, end, ms)

    @Test
    fun steadyOutsideTheLayersTime() {
        assertEquals(Steady, fade(999))
        assertEquals(Steady, fade(3000))
        assertEquals(Steady, fade(5000))
    }

    @Test
    fun fadesInOverTheFirst300Ms() {
        assertEquals(0f, fade(1000).alpha, 0.001f)
        assertEquals(0.875f, fade(1150).alpha, 0.001f)
        assertEquals(Steady, fade(1300))
    }

    @Test
    fun steadyBetweenInAndOut() {
        assertEquals(Steady, fade(2000))
        assertEquals(Steady, fade(2700))
    }

    @Test
    fun fadesOutOverTheLast300Ms() {
        assertEquals(0.875f, fade(2850).alpha, 0.001f)
        assertTrue(fade(2999).alpha < 0.05f)
    }

    @Test
    fun shortLayerSplitsItsTimeBetweenInAndOut() {
        // 200 ms on screen: 100 ms in, 100 ms out.
        assertEquals(0.875f, fade(1050, end = 1200).alpha, 0.001f)
        assertEquals(Steady, fade(1100, end = 1200))
        assertEquals(0.875f, fade(1150, end = 1200).alpha, 0.001f)
        assertEquals(Steady, fade(1000, end = 1001))
    }

    @Test
    fun popStartsSmallAndOvershoots() {
        assertEquals(0.6f, motionAt(Anim.Pop, Anim.None, 0, 2000, 0).scale, 0.001f)
        assertTrue((0L until 300L).any { motionAt(Anim.Pop, Anim.None, 0, 2000, it).scale > 1.02f })
        assertEquals(Steady, motionAt(Anim.Pop, Anim.None, 0, 2000, 1900))
    }

    @Test
    fun slideComesUpFromBelowAndLeavesUpwards() {
        assertEquals(0.08f, motionAt(Anim.SlideUp, Anim.SlideUp, 0, 2000, 0).dy, 0.001f)
        assertTrue(motionAt(Anim.SlideUp, Anim.SlideUp, 0, 2000, 1999).dy < -0.07f)
    }

    @Test
    fun slidesComeInFromTheOtherSideAndLeaveTheirWay() {
        assertEquals(-0.08f, motionAt(Anim.SlideRight, Anim.SlideRight, 0, 2000, 0).dx, 0.001f)
        assertTrue(motionAt(Anim.SlideRight, Anim.SlideRight, 0, 2000, 1999).dx > 0.07f)
        assertEquals(0.08f, motionAt(Anim.SlideLeft, Anim.None, 0, 2000, 0).dx, 0.001f)
        assertEquals(-0.08f, motionAt(Anim.SlideDown, Anim.None, 0, 2000, 0).dy, 0.001f)
        assertEquals(0f, motionAt(Anim.SlideDown, Anim.None, 0, 2000, 0).dx, 0.001f)
    }

    @Test
    fun zoomOutStartsBigAndSettles() {
        assertEquals(1.5f, motionAt(Anim.ZoomOut, Anim.None, 0, 2000, 0).scale, 0.001f)
        assertEquals(Steady, motionAt(Anim.ZoomOut, Anim.None, 0, 2000, 300))
    }

    @Test
    fun aSetLengthIsStillAtMostHalfTheLayer() {
        assertEquals(0.875f, motionAt(Anim.Fade, Anim.None, 0, 4000, 500, inMs = 1000).alpha, 0.001f)
        assertEquals(listOf(0L to 1000L, 3800L to 4000L), animationSpans(Anim.Fade, Anim.Fade, 0, 4000, inMs = 1000, outMs = 200))
        assertEquals(listOf(0L to 500L), animationSpans(Anim.Fade, Anim.None, 0, 1000, inMs = 3000))
        // Unset, a kind keeps its own length.
        assertEquals(300L, animMs(Anim.Fade, 0, 4000, null))
    }

    @Test
    fun spansCoverOnlyTheAnimatedEnds() {
        assertEquals(listOf(1000L to 1300L, 2700L to 3000L), animationSpans(Anim.Fade, Anim.Pop, 1000, 3000))
        assertEquals(listOf(2700L to 3000L), animationSpans(Anim.None, Anim.Pop, 1000, 3000))
        assertEquals(emptyList<Pair<Long, Long>>(), animationSpans(Anim.Fade, Anim.Fade, 3000, 3000))
    }

    @Test
    fun aLoopAnimatesTheWholeTimeAndStartsAtRest() {
        assertEquals(listOf(1000L to 3000L), animationSpans(Anim.None, Anim.Fade, 1000, 3000, Loop.Pulse))
        assertEquals(Steady, motionAt(Anim.None, Anim.None, 1000, 3000, 1000, Loop.Wiggle))
        assertTrue(abs(motionAt(Anim.None, Anim.None, 1000, 3000, 1175, Loop.Wiggle).rotation) > 5f)
        assertTrue(motionAt(Anim.None, Anim.None, 1000, 3000, 1450, Loop.Pulse).scale > 1.07f)
        assertTrue(motionAt(Anim.None, Anim.None, 1000, 3000, 1325, Loop.Bounce).dy < -0.02f)
        assertEquals(Steady, motionAt(Anim.None, Anim.None, 1000, 3000, 3000, Loop.Bounce))
    }

    @Test
    fun aLoopAddsToTheEntrance() {
        val m = motionAt(Anim.Fade, Anim.None, 0, 3000, 150, Loop.Wiggle)
        assertEquals(0.875f, m.alpha, 0.001f)
        assertTrue(m.rotation != 0f)
    }

    @Test
    fun perLetterEntrancesRunLongerAndCarryTheirProgress() {
        assertEquals(Reveal(Anim.Typewriter, 0.5f), motionAt(Anim.Typewriter, Anim.None, 0, 5000, 600).reveal)
        assertEquals(Steady, motionAt(Anim.Typewriter, Anim.None, 0, 5000, 1200))
        // A short layer still gets half its time.
        assertEquals(Reveal(Anim.LettersPop, 0.5f), motionAt(Anim.LettersPop, Anim.Fade, 0, 1000, 250).reveal)
        assertEquals(listOf(0L to 1200L), animationSpans(Anim.Typewriter, Anim.None, 0, 5000))
    }

    @Test
    fun typewriterShowsTheFirstLettersInTurn() {
        assertEquals(listOf(0f, 0f, 0f, 0f), (0 until 4).map { letterScale(Anim.Typewriter, 0f, it, 4) })
        assertEquals(listOf(1f, 1f, 0f, 0f), (0 until 4).map { letterScale(Anim.Typewriter, 0.5f, it, 4) })
        assertEquals(listOf(1f, 1f, 1f, 1f), (0 until 4).map { letterScale(Anim.Typewriter, 0.99f, it, 4) })
    }

    @Test
    fun lettersPopOneAfterAnother() {
        assertEquals(0f, letterScale(Anim.LettersPop, 0f, 0, 5), 0.001f)
        // Mid-way the first letters have landed, the middle one is popping and the last hasn't started.
        assertEquals(1f, letterScale(Anim.LettersPop, 0.5f, 0, 5), 0.001f)
        assertTrue(letterScale(Anim.LettersPop, 0.5f, 2, 5) in 0.01f..1.2f)
        assertEquals(0f, letterScale(Anim.LettersPop, 0.5f, 4, 5), 0.001f)
        assertEquals(1f, letterScale(Anim.LettersPop, 1f, 4, 5), 0.001f)
        assertTrue((0..100).any { letterScale(Anim.LettersPop, it / 100f, 1, 3) > 1.02f })
        assertEquals(1f, letterScale(Anim.LettersPop, 1f, 0, 1), 0.001f)
    }
}
