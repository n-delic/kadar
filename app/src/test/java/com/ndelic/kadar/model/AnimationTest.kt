package com.ndelic.kadar.model

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
    fun spansCoverOnlyTheAnimatedEnds() {
        assertEquals(listOf(1000L to 1300L, 2700L to 3000L), animationSpans(Anim.Fade, Anim.Pop, 1000, 3000))
        assertEquals(listOf(2700L to 3000L), animationSpans(Anim.None, Anim.Pop, 1000, 3000))
        assertEquals(emptyList<Pair<Long, Long>>(), animationSpans(Anim.Fade, Anim.Fade, 3000, 3000))
    }
}
