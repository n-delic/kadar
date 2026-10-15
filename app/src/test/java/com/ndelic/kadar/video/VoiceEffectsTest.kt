package com.ndelic.kadar.video

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEffectsTest {

    @Test
    fun robotMovesATone() {
        val rate = 44_100
        val robot = RingModulation(ROBOT_HZ)
        val out = FloatArray(rate) { i -> sin(2 * PI * 440 * i / rate).toFloat() * robot.getGainFactorAtSamplePosition(i.toLong(), rate) }
        // The tone is gone, a sideband on each side of it.
        assertTrue(level(out, 440.0, rate) < 0.01)
        assertTrue(level(out, 440.0 - ROBOT_HZ, rate) > 0.4)
        assertTrue(level(out, 440.0 + ROBOT_HZ, rate) > 0.4)
    }

    @Test
    fun robotGainStaysInRangeOverLongPositions() {
        val robot = RingModulation(ROBOT_HZ)
        for (p in listOf(0L, 1L, 10_000_000_000L, Long.MAX_VALUE / 2)) {
            assertTrue(robot.getGainFactorAtSamplePosition(p, 48_000) in -1f..1f)
            assertTrue(robot.isUnityUntil(p, 48_000) > p)
        }
    }

    /** The amplitude of [hz] in [x] (Goertzel). */
    private fun level(x: FloatArray, hz: Double, rate: Int): Double {
        val w = 2 * PI * hz / rate
        var s1 = 0.0
        var s2 = 0.0
        for (v in x) {
            val s = v + 2 * cos(w) * s1 - s2
            s2 = s1
            s1 = s
        }
        return 2 * sqrt(s1 * s1 + s2 * s2 - 2 * cos(w) * s1 * s2) / x.size
    }
}
