@file:OptIn(UnstableApi::class)

package com.ndelic.kadar.video

import android.content.Context
import android.graphics.Matrix
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GaussianFunction
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SeparableConvolution
import com.ndelic.kadar.video.TransitionKind.Black
import com.ndelic.kadar.video.TransitionKind.Blur
import com.ndelic.kadar.video.TransitionKind.White
import com.ndelic.kadar.video.TransitionKind.Zoom

/**
 * The effects that play the centered transitions [spans] into and out of a [width]×[height] clip of the main track. Media3
 * hands an item's effects its frames at their time in the sequence, which for the main track is output time, in the
 * preview and the export alike, so the single-input preview plays these as they export. Only the effects the spans need,
 * since each runs on every frame of the clip.
 */
fun dipEffects(spans: List<TransitionSpan>, width: Int, height: Int): List<Effect> = buildList {
    fun at(us: Long, kind: TransitionKind) = spans.firstOrNull { it.kind == kind && us / 1000 in it }?.let { dipAt(it.progress(us / 1000)) } ?: 0f
    val kinds = spans.map { it.kind }.toSet()
    if (Black in kinds || White in kinds) {
        add(RgbMatrix { us, _ ->
            val white = at(us, White)
            val q = maxOf(at(us, Black), white)
            // Column-major, multiplying (r, g, b, 1): every color scaled down, then white added for White.
            FloatArray(16).apply {
                this[0] = 1 - q
                this[5] = 1 - q
                this[10] = 1 - q
                if (white > 0) for (i in 12..14) this[i] = q
                this[15] = 1f
            }
        })
    }
    if (Zoom in kinds) {
        add(MatrixTransformation { us ->
            val s = 1 + ZOOM_PEAK * at(us, Zoom).let { it * it }
            Matrix().apply { setScale(s, s) }
        })
    }
    if (Blur in kinds) {
        val sigma = BLUR_PEAK * minOf(width, height)
        add(object : SeparableConvolution() {
            override fun getConvolution(presentationTimeUs: Long) = GaussianFunction(maxOf(MIN_SIGMA, sigma * at(presentationTimeUs, Blur)), 2f)
        })
    }
}

/** At the cut a zoom shows the frame this much bigger (2x). */
private const val ZOOM_PEAK = 1f
/** A blur's sigma at the cut, as a share of the clip's short side. */
private const val BLUR_PEAK = 0.03f
/** Next to nothing: a sigma of 0 is not a Gaussian. */
private const val MIN_SIGMA = 0.01f

/**
 * Shows only the part of a canvas-sized frame of a head that [span] has wiped in by the output frame (at [fps]) it
 * lands in, like the head's placement; the rest is transparent, so the compositor shows the clip under it there.
 */
class WipeMask(private val span: TransitionSpan, private val fps: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(useHdr)

    private inner class Program(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val program = try {
            GlProgram(VERTEX, FRAGMENT).apply {
                setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val (l, t, r, b) = span.kind.wipeRect(span.progress(outputFrameUs(presentationTimeUs, fps) / 1000))!!
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                // Texture coordinates have y up.
                program.setFloatsUniform("uRect", floatArrayOf(l, 1 - b, r, 1 - t))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }
}

private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexCoord;
void main() {
  gl_Position = aFramePosition;
  vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

private const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec4 uRect;
varying vec2 vTexCoord;
void main() {
  bool inside = vTexCoord.x >= uRect.x && vTexCoord.x <= uRect.z && vTexCoord.y >= uRect.y && vTexCoord.y <= uRect.w;
  gl_FragColor = inside ? texture2D(uTexSampler, vTexCoord) : vec4(0.0);
}
"""
