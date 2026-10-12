package com.ndelic.kadar.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CropTest {

    @Test
    fun freeCropFollowsTheFingerInsideTheImage() {
        assertEquals(Rect(100f, 100f, 300f, 250f), cropFrom(Offset(100f, 100f), Offset(300f, 250f), null, 1000f, 800f))
        // Dragging past the edge stops at the edge; dragging up-left of the anchor flips the rectangle.
        assertEquals(Rect(100f, 100f, 1000f, 800f), cropFrom(Offset(100f, 100f), Offset(5000f, 5000f), null, 1000f, 800f))
        assertEquals(Rect(50f, 20f, 100f, 100f), cropFrom(Offset(100f, 100f), Offset(50f, 20f), null, 1000f, 800f))
    }

    @Test
    fun ratioCropKeepsShapeAndFits() {
        val r = cropFrom(Offset(0f, 0f), Offset(400f, 100f), 1f, 1000f, 800f)
        assertEquals(400f, r.width, 0.01f)
        assertEquals(400f, r.height, 0.01f)
        // A square from (0,0) can't be taller than the 800 px image.
        val big = cropFrom(Offset(0f, 0f), Offset(1000f, 100f), 1f, 1000f, 800f)
        assertEquals(800f, big.width, 0.01f)
        assertEquals(800f, big.height, 0.01f)
    }
}
