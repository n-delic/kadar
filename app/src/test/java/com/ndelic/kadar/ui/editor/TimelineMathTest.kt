package com.ndelic.kadar.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineMathTest {

    private val whole = TimelineViewport().fit(60_000)

    @Test
    fun fitStartsAtTheWholeVideoAndKeepsTheViewInsideIt() {
        assertEquals(TimelineViewport(0f, 60_000f), whole)
        assertEquals(TimelineViewport(59_000f, 1000f), TimelineViewport(70_000f, 10f).fit(60_000))
        // A video shorter than the zoom limit can only show all of itself.
        assertEquals(TimelineViewport(0f, 500f), TimelineViewport(100f, 100f).fit(500))
    }

    @Test
    fun timeAndXAreInverseOnTheVisiblePart() {
        val view = TimelineViewport(10_000f, 5000f)
        assertEquals(0f, view.x(10_000L, 300f), 0.001f)
        assertEquals(300f, view.x(15_000L, 300f), 0.001f)
        assertEquals(12_500L, view.time(150f, 300f, 60_000))
        assertEquals(0L, view.time(-10_000f, 300f, 60_000))
    }

    @Test
    fun pinchKeepsTheTimeUnderTheFingersAndStopsAtTheLimits() {
        val zoomed = whole.pinch(4f, 150f, 150f, 300f, 60_000)
        assertEquals(15_000f, zoomed.spanMs, 0.001f)
        assertEquals(whole.time(150f, 300f, 60_000), zoomed.time(150f, 300f, 60_000))
        // Sliding two fingers right by half the width scrolls back by half the span.
        assertEquals(zoomed.fromMs - 7500f, zoomed.pinch(1f, 100f, 250f, 300f, 60_000).fromMs, 0.001f)
        assertEquals(1000f, whole.pinch(1000f, 0f, 0f, 300f, 60_000).spanMs, 0.001f)
        assertEquals(whole, zoomed.pinch(0.01f, 150f, 150f, 300f, 60_000))
    }

    @Test
    fun followPagesOnlyWhenThePlayheadLeavesTheView() {
        val view = TimelineViewport(10_000f, 5000f)
        assertEquals(view, view.follow(12_000, 60_000))
        assertEquals(15_500f, view.follow(16_000, 60_000).fromMs, 0.001f)
        // Looping back to the start shows the start.
        assertEquals(0f, view.follow(0, 60_000).fromMs, 0.001f)
        assertEquals(whole, whole.follow(60_000, 60_000))
    }

    @Test
    fun rulerStepsAreRoundAndSpacedForTheZoom() {
        assertEquals(10_000L, whole.rulerStepMs(300f, 50f))
        assertEquals(200L, TimelineViewport(0f, 1000f).rulerStepMs(300f, 50f))
        assertEquals("0:10", rulerLabel(10_000, 5000))
        assertEquals("1:05", rulerLabel(65_000, 5000))
        assertEquals("0:01.5", rulerLabel(1500, 500))
    }

    @Test
    fun filmstripIndexPicksTheFrameForASourceTime() {
        assertEquals(0, filmstripIndex(0f, 10_000, 10))
        assertEquals(4, filmstripIndex(4999f, 10_000, 10))
        assertEquals(9, filmstripIndex(10_000f, 10_000, 10))
        assertEquals(0, filmstripIndex(-50f, 10_000, 10))
    }
}
