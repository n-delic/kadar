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
import com.ndelic.kadar.video.TransitionKind.ColorSplit
import com.ndelic.kadar.video.TransitionKind.Flip
import com.ndelic.kadar.video.TransitionKind.Glitch
import com.ndelic.kadar.video.TransitionKind.Spin
import com.ndelic.kadar.video.TransitionKind.White
import com.ndelic.kadar.video.TransitionKind.Zoom

/**
 * The effects that play the centered transitions [spans] into and out of a [width]×[height] clip of the main track. Media3
 * hands an item's effects its frames at their time in the sequence, which for the main track is output time, in the
 * preview and the export alike, so the single-input preview plays these as they export. Only the effects the spans need,
 * since each runs on every frame of the clip.
 */
fun dipEffects(spans: List<TransitionSpan>, width: Int, height: Int): List<Effect> = buildList {
    fun progress(us: Long, kind: TransitionKind) = spans.firstOrNull { it.kind == kind && us / 1000 in it }?.progress(us / 1000)
    fun at(us: Long, kind: TransitionKind) = progress(us, kind)?.let(::dipAt) ?: 0f
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
    if (Spin in kinds) {
        // Matrices act on normalized coordinates, so a turn is done in the frame's own proportions to keep it rigid.
        val aspect = width.toFloat() / height
        add(MatrixTransformation { us ->
            Matrix().apply {
                progress(us, Spin)?.let { p ->
                    setScale(aspect, 1f)
                    postRotate(spinDegrees(p))
                    spinScale(p).let { s -> postScale(s / aspect, s) }
                }
            }
        })
    }
    if (Flip in kinds) {
        add(MatrixTransformation { us ->
            // Never quite edge-on: a frame squashed to nothing has no area to draw.
            Matrix().apply { setScale(maxOf(MIN_FLIP_WIDTH, progress(us, Flip)?.let(::flipWidth) ?: 1f), 1f) }
        })
    }
    if (Glitch in kinds || ColorSplit in kinds) {
        add(ChannelShift { us ->
            val glitch = at(us, Glitch)
            floatArrayOf(maxOf(SPLIT_PEAK * at(us, ColorSplit), GLITCH_SPLIT * glitch), GLITCH_SLIDE * glitch)
        })
    }
}

/** At the cut a zoom shows the frame this much bigger (2x). */
private const val ZOOM_PEAK = 1f
/** A blur's sigma at the cut, as a share of the clip's short side. */
private const val BLUR_PEAK = 0.03f
/** Next to nothing: a sigma of 0 is not a Gaussian. */
private const val MIN_SIGMA = 0.01f
private const val MIN_FLIP_WIDTH = 0.01f
/** How far a color split moves red and blue apart at the cut, each way, as a share of the width. */
private const val SPLIT_PEAK = 0.04f
/** A glitch's color split and how far its rows slide at most at the cut, as shares of the width. */
private const val GLITCH_SPLIT = 0.02f
private const val GLITCH_SLIDE = 0.15f

/**
 * Moves the red channel right and the blue one left by the first of [shift] at a frame's time, and slides some bands of
 * rows sideways by up to its second (both shares of the width). Which bands move, and how far, comes from [glitchSeed]
 * of the frame's time, so the preview and the export show the same.
 */
class ChannelShift(private val shift: (Long) -> FloatArray) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(useHdr)

    private inner class Program(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val program = try {
            GlProgram(VERTEX, SHIFT_FRAGMENT).apply {
                setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val (split, slide) = shift(presentationTimeUs)
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uSplit", split)
                program.setFloatUniform("uSlide", slide)
                program.setFloatUniform("uSeed", glitchSeed(presentationTimeUs / 1000))
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

// Between 8 and 40 bands a frame; about a third of them slide, each its own way and distance.
private const val SHIFT_FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uSplit;
uniform float uSlide;
uniform float uSeed;
varying vec2 vTexCoord;

float hash(vec2 p) {
  vec3 q = fract(vec3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

void main() {
  float band = floor(vTexCoord.y * mix(8.0, 40.0, hash(vec2(uSeed, 1.0))));
  float dx = hash(vec2(band, uSeed)) > 0.66 ? (hash(vec2(band + 57.0, uSeed)) * 2.0 - 1.0) * uSlide : 0.0;
  vec2 uv = vec2(vTexCoord.x + dx, vTexCoord.y);
  vec4 c = texture2D(uTexSampler, uv);
  c.r = texture2D(uTexSampler, uv - vec2(uSplit, 0.0)).r;
  c.b = texture2D(uTexSampler, uv + vec2(uSplit, 0.0)).b;
  gl_FragColor = c;
}
"""
