package com.ndelic.kadar.video

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicFadeTest {

    @Test
    fun gainRampsUpAtTheStartAndDownBeforeTheEnd() {
        // An old project's music: the whole 10 s video, both 1.5 s fades on.
        assertEquals(0f, fadeGain(0, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS), 1e-4f)
        assertEquals(0.5f, fadeGain(750, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS), 1e-4f)
        assertEquals(1f, fadeGain(5_000, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS), 1e-4f)
        assertEquals(0.5f, fadeGain(9_250, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS), 1e-4f)
        assertEquals(0f, fadeGain(10_000, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS), 1e-4f)
        // A length of 0 is no fade.
        assertEquals(1f, fadeGain(0, 0, 10_000, 0, MUSIC_FADE_MS), 1e-4f)
        assertEquals(1f, fadeGain(9_900, 0, 10_000, MUSIC_FADE_MS, 0), 1e-4f)
    }

    @Test
    fun gainFollowsAPartThatStartsLater() {
        // 2 s to 8 s, a 4 s fade in (capped at half the part, 3 s) and a 1 s fade out.
        assertEquals(0f, fadeGain(2_000, 2_000, 8_000, 4_000, 1_000), 1e-4f)
        assertEquals(0.5f, fadeGain(3_500, 2_000, 8_000, 4_000, 1_000), 1e-4f)
        assertEquals(1f, fadeGain(5_000, 2_000, 8_000, 4_000, 1_000), 1e-4f)
        assertEquals(0.5f, fadeGain(7_500, 2_000, 8_000, 4_000, 1_000), 1e-4f)
        assertEquals(listOf(0L to 3_000L, 5_000L to 6_000L), fadeWindows(2_000, 8_000, 2_000, 8_000, 4_000, 1_000))
    }

    @Test
    fun shortVideoFadesMeetInTheMiddle() {
        assertEquals(1f, fadeGain(1_000, 0, 2_000, MUSIC_FADE_MS, MUSIC_FADE_MS), 1e-4f)
        assertEquals(0.5f, fadeGain(500, 0, 2_000, MUSIC_FADE_MS, MUSIC_FADE_MS), 1e-4f)
        assertEquals(listOf(0L to 1_000L, 1_000L to 2_000L), fadeWindows(0, 2_000, 0, 2_000, MUSIC_FADE_MS, MUSIC_FADE_MS))
    }

    @Test
    fun repeatsLoopTheSongAndCutTheLastOne() {
        // An old project's music: from 0, at the song's start.
        assertEquals(listOf(Repeat(0, 4_000, 0), Repeat(4_000, 8_000, 0), Repeat(8_000, 10_000, 0)), repeats(0, 10_000, 0, 4_000))
        assertEquals(listOf(Repeat(0, 10_000, 0)), repeats(0, 10_000, 0, 60_000))
        // A part from 2 s, 3 s into the loop: the first repeat plays the loop's last second.
        assertEquals(listOf(Repeat(2_000, 3_000, 3_000), Repeat(3_000, 7_000, 0), Repeat(7_000, 9_000, 0)), repeats(2_000, 9_000, 3_000, 4_000))
        // An offset past a whole loop wraps around.
        assertEquals(listOf(Repeat(0, 1_000, 3_000)), repeats(0, 1_000, 7_000, 4_000))
        assertEquals(emptyList<Repeat>(), repeats(5_000, 5_000, 0, 4_000))
    }

    @Test
    fun fadeWindowsAreOnEachRepeatsOwnClock() {
        // A 10 s video over a 4 s loop: the fade-out (8.5 s to 10 s) falls in the third repeat, which starts at 8 s.
        assertEquals(listOf(0L to 1_500L), fadeWindows(0, 4_000, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS))
        assertEquals(emptyList<Pair<Long, Long>>(), fadeWindows(4_000, 8_000, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS))
        assertEquals(listOf(500L to 2_000L), fadeWindows(8_000, 10_000, 0, 10_000, MUSIC_FADE_MS, MUSIC_FADE_MS))
        // A fade split across two repeats: each gets its part.
        assertEquals(listOf(3_500L to 4_000L), fadeWindows(5_000, 9_000, 0, 10_000, 0, MUSIC_FADE_MS))
        assertEquals(listOf(0L to 1_000L), fadeWindows(9_000, 10_000, 0, 10_000, 0, MUSIC_FADE_MS))
    }
}
