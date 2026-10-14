package com.ndelic.kadar.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPartsTest {

    private val song = AudioPart(atMs = 2_000, offsetMs = 1_000, lengthMs = 5_000, fadeInMs = 500, fadeOutMs = 500)

    @Test
    fun endIsTheVideosEndForAPartThatRunsToIt() {
        assertEquals(10_000L, AudioPart().endMs(10_000))
        assertEquals(7_000L, song.endMs(10_000))
        assertEquals(6_000L, song.endMs(6_000))
    }

    @Test
    fun movingKeepsTheLengthAndStaysInTheVideo() {
        assertEquals(song.copy(atMs = 4_000), song.dragged(AudioDrag.Move, 2_000, 10_000, null))
        assertEquals(song.copy(atMs = 0), song.dragged(AudioDrag.Move, -5_000, 10_000, null))
        assertEquals(song.copy(atMs = 10_000 - MIN_CLIP_MS), song.dragged(AudioDrag.Move, 20_000, 10_000, null))
    }

    @Test
    fun draggingTheStartTrimsTheSourceToo() {
        assertEquals(song.copy(atMs = 3_000, offsetMs = 2_000, lengthMs = 4_000), song.dragged(AudioDrag.Start, 1_000, 10_000, null))
        // Not before the source's own start (1 s back from 2 s), nor closer than MIN_CLIP_MS to the end.
        assertEquals(song.copy(atMs = 1_000, offsetMs = 0, lengthMs = 6_000), song.dragged(AudioDrag.Start, -5_000, 10_000, null))
        assertEquals(7_000 - MIN_CLIP_MS, song.dragged(AudioDrag.Start, 9_000, 10_000, null).atMs)
    }

    @Test
    fun draggingTheEndOfASongCanLoopItToTheEnd() {
        assertEquals(song.copy(lengthMs = 3_000), song.dragged(AudioDrag.End, -2_000, 10_000, null))
        assertEquals(song.copy(lengthMs = Long.MAX_VALUE), song.dragged(AudioDrag.End, 5_000, 10_000, null))
        assertEquals(song.copy(lengthMs = MIN_CLIP_MS), song.dragged(AudioDrag.End, -9_000, 10_000, null))
    }

    @Test
    fun aVoiceEndsWithItsRecording() {
        val voice = AudioPart(atMs = 1_000, lengthMs = 2_000)
        assertEquals(voice.copy(lengthMs = 3_000), voice.dragged(AudioDrag.End, 5_000, 10_000, sourceMs = 3_000))
    }

    @Test
    fun splitPartsPlayOnWithoutAFadeAtTheCut() {
        val (first, second) = song.splitAt(4_000, 10_000)!!
        assertEquals(song.copy(lengthMs = 2_000, fadeOutMs = 0), first)
        assertEquals(song.copy(atMs = 4_000, offsetMs = 3_000, lengthMs = 3_000, fadeInMs = 0), second)
        // A song to the end: the second part still runs to the end.
        assertEquals(Long.MAX_VALUE, AudioPart().splitAt(4_000, 10_000)!!.second.lengthMs)
        assertNull(song.splitAt(2_100, 10_000))
        assertNull(song.splitAt(8_000, 10_000))
    }

    @Test
    fun newAudioStartsAtThePlayheadUnlessTheVideoIsAlmostOver() {
        assertEquals(3_000L, audioStartMs(3_000, 10_000))
        assertEquals(0L, audioStartMs(9_500, 10_000))
    }
}
