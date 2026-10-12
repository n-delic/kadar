package com.ndelic.kadar.model

import java.io.File
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectListTest {

    private fun project(created: Long, modified: Long, isVideo: Boolean = false, name: String? = null) =
        SavedProject(File(created.toString()), modified, null, isVideo, name)

    // 2026-10-12 09:00 UTC
    private val oct12 = 1_791_795_600_000L
    private val en = LabelWords("Photo", "Video", "MMM d")

    @Test
    fun defaultNameShowsTypeAndCreationDay() {
        assertEquals("Photo · Oct 12", defaultName(false, oct12, en, ZoneOffset.UTC, Locale.ENGLISH))
        assertEquals("Video · Oct 12", defaultName(true, oct12, en, ZoneOffset.UTC, Locale.ENGLISH))
    }

    @Test
    fun defaultNameUsesTheGivenWordsAndDateOrder() {
        val bs = LabelWords("Foto", "Video", "d. MMM")
        assertEquals("Foto · 12. okt", defaultName(false, oct12, bs, ZoneOffset.UTC, Locale.forLanguageTag("bs")))
    }

    @Test
    fun labelPrefersTheName() {
        assertEquals("Cat meme", project(oct12, oct12, name = "Cat meme").label(en))
        assertEquals(defaultName(true, oct12, en), project(oct12, oct12, isVideo = true).label(en))
    }

    @Test
    fun labelFallsBackToModifiedWhenTheFolderIsNotATimestamp() {
        val p = SavedProject(File("imported"), oct12, null, false)
        assertEquals(defaultName(false, oct12, en), p.label(en))
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesDefaultLabels() {
        val cat = project(1, 30, name = "Cat meme")
        val dog = project(2, 20, name = "dog")
        val clip = project(3, 10, isVideo = true)
        val all = listOf(clip, dog, cat)
        assertEquals(listOf(cat), all.arrange("  cAT ", ProjectSort.Recent, en))
        assertEquals(listOf(clip), all.arrange("video", ProjectSort.Recent, en))
        assertEquals(listOf(cat, dog, clip), all.arrange("", ProjectSort.Recent, en))
        assertEquals(emptyList<SavedProject>(), all.arrange("zebra", ProjectSort.Recent, en))
    }

    @Test
    fun sortsByNameIgnoringCaseThenRecent() {
        val b = project(1, 10, name = "banana")
        val a1 = project(2, 20, name = "Apple")
        val a2 = project(3, 30, name = "Apple")
        val c = project(4, 40, name = "cherry")
        assertEquals(listOf(a2, a1, b, c), listOf(b, a1, c, a2).arrange("", ProjectSort.Name, en))
    }

    @Test
    fun typePutsVideosFirstThenRecent() {
        val photoOld = project(1, 10)
        val photoNew = project(2, 40)
        val videoOld = project(3, 20, isVideo = true)
        val videoNew = project(4, 30, isVideo = true)
        assertEquals(listOf(videoNew, videoOld, photoNew, photoOld), listOf(photoOld, videoOld, photoNew, videoNew).arrange("", ProjectSort.Type, en))
    }
}
