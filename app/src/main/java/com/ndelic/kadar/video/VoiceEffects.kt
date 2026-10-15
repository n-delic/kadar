@file:OptIn(UnstableApi::class)

package com.ndelic.kadar.video

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ndelic.kadar.R
import kotlin.math.PI
import kotlin.math.sin

/** How a voiceover track sounds: as recorded, pitched up or down (same length), or a robot. */
enum class VoiceEffect(@StringRes val label: Int) {
    Normal(R.string.voice_effect_normal),
    Higher(R.string.voice_effect_higher),
    Deeper(R.string.voice_effect_deeper),
    Chipmunk(R.string.voice_effect_chipmunk),
    Robot(R.string.voice_effect_robot),
    ;

    /** New processors for one item (they keep state, so never share them between items). */
    fun processors(): List<AudioProcessor> = when (this) {
        Normal -> emptyList()
        Higher -> listOf(pitched(1.4f))
        Deeper -> listOf(pitched(0.7f))
        Chipmunk -> listOf(pitched(2f))
        Robot -> listOf(GainProcessor(RingModulation(ROBOT_HZ)))
    }
}

private fun pitched(pitch: Float) = SonicAudioProcessor().apply { setPitch(pitch) }

/** The robot's carrier: low enough that the voice stays understandable, high enough to buzz. Whole hertz: the phase restarts every second. */
const val ROBOT_HZ = 50f

/**
 * Ring modulation as a gain: every sample times a [hz] sine, so each frequency f in the voice becomes f - [hz] and
 * f + [hz]. GainProcessor multiplies without clamping, so negative gains are fine.
 */
internal class RingModulation(private val hz: Float) : GainProcessor.GainProvider {
    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int) =
        sin(2 * PI * hz * (samplePosition % sampleRate) / sampleRate).toFloat()

    // Asked only where the gain is exactly 1: one sample at a time, never "for good" (that would make it inactive).
    override fun isUnityUntil(samplePosition: Long, sampleRate: Int) = samplePosition + 1
}
