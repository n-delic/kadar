package com.ndelic.kadar.model

import androidx.annotation.StringRes
import com.ndelic.kadar.R
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Order of the home screen's project grid. [Type] puts videos first, the rarer kind, so they're easy to find. */
enum class ProjectSort(@StringRes val label: Int) {
    Recent(R.string.sort_recent),
    Name(R.string.sort_name),
    Type(R.string.sort_type),
}

/** The app language's words for an unnamed project's label: "Photo", "Video" and a date pattern such as "MMM d". */
data class LabelWords(val photo: String, val video: String, val datePattern: String)

/** Label for a project that was never named: its type and the day it was made, e.g. "Photo · Oct 12". */
fun defaultName(isVideo: Boolean, created: Long, words: LabelWords, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    (if (isVideo) words.video else words.photo) + " · " + DateTimeFormatter.ofPattern(words.datePattern, locale).format(Instant.ofEpochMilli(created).atZone(zone))

/** The projects whose label contains [query] (any case), in [sort] order; ties keep the most recent first. */
fun List<SavedProject>.arrange(query: String, sort: ProjectSort, words: LabelWords): List<SavedProject> {
    val recent = sortedByDescending { it.modified }
    val found = query.trim().let { q -> if (q.isEmpty()) recent else recent.filter { it.label(words).contains(q, ignoreCase = true) } }
    return when (sort) {
        ProjectSort.Recent -> found
        ProjectSort.Name -> found.sortedWith(compareBy(Collator.getInstance()) { it.label(words) })
        ProjectSort.Type -> found.sortedByDescending { it.isVideo }
    }
}
