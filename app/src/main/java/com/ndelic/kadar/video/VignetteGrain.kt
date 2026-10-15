@file:OptIn(UnstableApi::class)

package com.ndelic.kadar.video

import android.content.Context
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
import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.GRAIN_MAX
import com.ndelic.kadar.model.VIGNETTE_INNER
import com.ndelic.kadar.model.VIGNETTE_MAX
import com.ndelic.kadar.model.VIGNETTE_OUTER
import com.ndelic.kadar.model.grainCell

/**
 * The vignette and grain of [adjust], read on every frame so the preview's change live. The same falloff
 * (`vignetteDarkness`), speck size and mix as a photo's `texturePaints`, but new specks every frame, like film.
 */
class VignetteGrain(val adjust: () -> Adjust) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(useHdr)

    private inner class Program(useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val program = try {
            GlProgram(VERTEX, FRAGMENT).apply {
                setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        private var size = Size(1, 1)

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight).also { size = it }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val a = adjust()
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uVignette", a.vignette)
                program.setFloatUniform("uGrain", a.grain * GRAIN_MAX)
                program.setFloatsUniform("uSize", floatArrayOf(size.width.toFloat(), size.height.toFloat()))
                program.setFloatUniform("uCell", grainCell(size.width.toFloat(), size.height.toFloat()))
                // Another pattern on every frame (frames are tens of milliseconds apart).
                program.setFloatUniform("uSeed", (presentationTimeUs / 1000 % 1000).toFloat())
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

// A speck is premultiplied like the photo's grain tile (white or black by its sign, as opaque as it is strong), and
// both are mixed in like SRC_ATOP over the frame, so photo and video match.
private val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uVignette;
uniform float uGrain;
uniform vec2 uSize;
uniform float uCell;
uniform float uSeed;
varying vec2 vTexCoord;

float hash(vec3 p) {
  p = fract(p * 0.1031);
  p += dot(p, p.zyx + 31.32);
  return fract((p.x + p.y) * p.z);
}

vec4 speck(vec2 cell) {
  float n = hash(vec3(cell, uSeed)) + hash(vec3(cell + 57.0, uSeed)) - 1.0;
  return n > 0.0 ? vec4(n) : vec4(0.0, 0.0, 0.0, -n);
}

void main() {
  vec4 c = texture2D(uTexSampler, vTexCoord);
  float t = clamp((length(vTexCoord * 2.0 - 1.0) - $VIGNETTE_INNER) / ($VIGNETTE_OUTER - $VIGNETTE_INNER), 0.0, 1.0);
  c.rgb *= 1.0 - uVignette * $VIGNETTE_MAX * t * t * (3.0 - 2.0 * t);
  vec2 g = vTexCoord * uSize / uCell - 0.5;
  vec2 i = floor(g);
  vec2 f = g - i;
  vec4 s = mix(mix(speck(i), speck(i + vec2(1.0, 0.0)), f.x), mix(speck(i + vec2(0.0, 1.0)), speck(i + vec2(1.0, 1.0)), f.x), f.y) * uGrain;
  c.rgb = s.rgb * c.a + c.rgb * (1.0 - s.a);
  gl_FragColor = c;
}
"""
