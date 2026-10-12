package com.ndelic.kadar.model

import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Order of the home screen's project grid. [Type] puts videos first, the rarer kind, so they're easy to find. */
enum class ProjectSort(val label: String) {
    Recent("Recent"),
    Name("Name"),
    Type("Type"),
}

/** Label for a project that was never named: its type and the day it was made, e.g. "Photo · Oct 12". */
fun defaultName(isVideo: Boolean, created: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    (if (isVideo) "Video" else "Photo") + " · " + DateTimeFormatter.ofPattern("MMM d", locale).format(Instant.ofEpochMilli(created).atZone(zone))

/** The projects whose label contains [query] (any case), in [sort] order; ties keep the most recent first. */
fun List<SavedProject>.arrange(query: String, sort: ProjectSort): List<SavedProject> {
    val recent = sortedByDescending { it.modified }
    val found = query.trim().let { q -> if (q.isEmpty()) recent else recent.filter { it.label.contains(q, ignoreCase = true) } }
    return when (sort) {
        ProjectSort.Recent -> found
        ProjectSort.Name -> found.sortedWith(compareBy(Collator.getInstance()) { it.label })
        ProjectSort.Type -> found.sortedByDescending { it.isVideo }
    }
}
