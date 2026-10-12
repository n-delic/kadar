package com.ndelic.kadar.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {

    @Test
    fun reorderTargetSnapsToTheNearestRowAndStaysInTheList() {
        assertEquals(1, reorderTarget(1, 19f, 40f, 4))
        assertEquals(2, reorderTarget(1, 21f, 40f, 4))
        assertEquals(0, reorderTarget(1, -30f, 40f, 4))
        assertEquals(0, reorderTarget(1, -500f, 40f, 4))
        assertEquals(3, reorderTarget(1, 500f, 40f, 4))
    }

    @Test
    fun tracksBetweenTheOldAndNewRowMakeRoom() {
        // Dragged down from row 1 to 3: rows 2 and 3 move up, the others stay.
        assertEquals(listOf(0, 0, -1, -1), (0..3).map { makeRoom(it, 1, 3) })
        // Dragged up from row 3 to 1: rows 1 and 2 move down.
        assertEquals(listOf(0, 1, 1, 0), (0..3).map { makeRoom(it, 3, 1) })
        // Nothing lifted.
        assertEquals(listOf(0, 0, 0, 0), (0..3).map { makeRoom(it, -1, -1) })
    }
}
