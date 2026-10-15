@file:OptIn(UnstableApi::class)

package com.ndelic.kadar.video

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Paints [color] (read on every frame) under a clip fitted into the canvas: Media3 leaves the letterbox bars
 * transparent. A transparent color leaves the frame as it is, so the blurred background shows through. The frame's own
 * alpha is first scaled by [alpha] at its time (a clip's fade, [clipAnimEffects]). Left see-through, the colors come out
 * [premultiplied] by alpha if asked: the preview's TextureView blends them that way, Media3's compositor straight.
 */
class BarFill(val alpha: (Long) -> Float = { 1f }, val premultiplied: Boolean = false, val color: () -> Int) : GlEffect {
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
            val c = color()
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uAlpha", alpha(presentationTimeUs))
                program.setFloatUniform("uPremultiply", if (premultiplied) 1f else 0f)
                program.setFloatsUniform("uFill", floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f, Color.alpha(c) / 255f))
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

// The frame over an opaque fill: straight alpha, like the rest of Media3's pipeline.
private const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec4 uFill;
uniform float uAlpha;
uniform float uPremultiply;
varying vec2 vTexCoord;
void main() {
  vec4 c = texture2D(uTexSampler, vTexCoord);
  c.a *= uAlpha;
  gl_FragColor = uFill.a > 0.0 ? vec4(mix(uFill.rgb, c.rgb, c.a), 1.0) : vec4(c.rgb * mix(1.0, c.a, uPremultiply), c.a);
}
"""
