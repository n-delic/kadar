package com.ndelic.kadar.video

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFadeTest {

    @Test
    fun gainRampsUpAtTheStartAndDownBeforeTheEnd() {
        assertEquals(0f, fadeGain(0, 10_000, fadeIn = true, fadeOut = true), 1e-4f)
        assertEquals(0.5f, fadeGain(750, 10_000, fadeIn = true, fadeOut = true), 1e-4f)
        assertEquals(1f, fadeGain(5_000, 10_000, fadeIn = true, fadeOut = true), 1e-4f)
        assertEquals(0.5f, fadeGain(9_250, 10_000, fadeIn = true, fadeOut = true), 1e-4f)
        assertEquals(0f, fadeGain(10_000, 10_000, fadeIn = true, fadeOut = true), 1e-4f)
        // Each fade is its own switch.
        assertEquals(1f, fadeGain(0, 10_000, fadeIn = false, fadeOut = true), 1e-4f)
        assertEquals(1f, fadeGain(9_900, 10_000, fadeIn = true, fadeOut = false), 1e-4f)
    }

    @Test
    fun shortVideoFadesMeetInTheMiddle() {
        assertEquals(1f, fadeGain(1_000, 2_000, fadeIn = true, fadeOut = true), 1e-4f)
        assertEquals(0.5f, fadeGain(500, 2_000, fadeIn = true, fadeOut = true), 1e-4f)
        assertEquals(listOf(0L to 1_000L, 1_000L to 2_000L), fadeWindows(0, 2_000, 2_000, fadeIn = true, fadeOut = true))
    }

    @Test
    fun loopSpansRepeatTheSongAndCutTheLastOne() {
        assertEquals(listOf(0L to 4_000L, 4_000L to 8_000L, 8_000L to 10_000L), loopSpans(4_000, 10_000))
        assertEquals(listOf(0L to 10_000L), loopSpans(60_000, 10_000))
    }

    @Test
    fun fadeWindowsAreOnEachRepeatsOwnClock() {
        // A 10 s video over a 4 s loop: the fade-out (8.5 s to 10 s) falls in the third repeat, which starts at 8 s.
        assertEquals(listOf(0L to 1_500L), fadeWindows(0, 4_000, 10_000, fadeIn = true, fadeOut = true))
        assertEquals(emptyList<Pair<Long, Long>>(), fadeWindows(4_000, 8_000, 10_000, fadeIn = true, fadeOut = true))
        assertEquals(listOf(500L to 2_000L), fadeWindows(8_000, 10_000, 10_000, fadeIn = true, fadeOut = true))
        // A fade split across two repeats: each gets its part.
        assertEquals(listOf(3_500L to 4_000L), fadeWindows(5_000, 9_000, 10_000, fadeIn = false, fadeOut = true))
        assertEquals(listOf(0L to 1_000L), fadeWindows(9_000, 10_000, 10_000, fadeIn = false, fadeOut = true))
    }
}
