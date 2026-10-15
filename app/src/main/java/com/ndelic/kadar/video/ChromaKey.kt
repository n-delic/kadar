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
import com.ndelic.kadar.model.VideoLayer
import kotlin.math.hypot

/** The chroma distance keyed out entirely at full strength. */
private const val KEY_MAX_LOW = 0.4f

/** The widest soft edge, in chroma distance. */
private const val KEY_MAX_SOFT = 0.2f

/** Past the soft edge, how far the key's tint is still taken out of a kept pixel (spill). */
private const val KEY_SPILL = 0.1f

/** [color]'s chroma (BT.601 Cb and Cr, -0.5..0.5): brightness left out, so a shadow on the screen keys like the rest. */
fun keyChroma(color: Int): Pair<Float, Float> {
    val r = (color shr 16 and 0xFF) / 255f
    val g = (color shr 8 and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    return (-0.168736f * r - 0.331264f * g + 0.5f * b) to (0.5f * r - 0.418688f * g - 0.081312f * b)
}

/** The chroma distances from the key under which a pixel is gone and over which it is whole; the soft edge is between. */
fun keyRange(strength: Float, softness: Float): Pair<Float, Float> {
    val low = strength.coerceIn(0f, 1f) * KEY_MAX_LOW
    // Never zero wide: smoothstep is undefined for equal edges.
    return low to low + softness.coerceIn(0f, 1f) * KEY_MAX_SOFT + 0.001f
}

/** How much of [pixel] the key [key] leaves (0 gone, 1 whole), the shader's alpha. */
fun keyAlpha(pixel: Int, key: Int, strength: Float, softness: Float): Float {
    val (pb, pr) = keyChroma(pixel)
    val (kb, kr) = keyChroma(key)
    val (low, high) = keyRange(strength, softness)
    val t = ((hypot(pb - kb, pr - kr) - low) / (high - low)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * Removes [VideoLayer.key] from a video layer's frames as [layer] says (read on every frame, so the preview's sliders are
 * live): pixels near the key's chroma get transparent, with a soft edge, and the key's tint is taken out of the pixels
 * close to it so no green fringe stays. Straight alpha for Media3's compositor; [premultiply] for a `TextureView`, which
 * blends premultiplied. Without a key, frames pass unchanged.
 */
class ChromaKey(private val premultiply: Boolean, val layer: () -> VideoLayer) : GlEffect {
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
            val l = layer()
            val (kb, kr) = keyChroma(l.key ?: 0)
            // No key: every distance is past the edge, so nothing is keyed and no spill is taken.
            val (low, high) = if (l.key == null) -2f to -1.5f else keyRange(l.keyStrength, l.keySoftness)
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatsUniform("uKey", floatArrayOf(kb, kr))
                program.setFloatUniform("uLow", low)
                program.setFloatUniform("uHigh", high)
                program.setFloatUniform("uPremultiply", if (premultiply) 1f else 0f)
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

// The same chroma, range and smoothstep as keyAlpha; the spill takes out the part of a pixel's chroma that points the key's way.
private val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uKey;
uniform float uLow;
uniform float uHigh;
uniform float uPremultiply;
varying vec2 vTexCoord;

void main() {
  vec4 c = texture2D(uTexSampler, vTexCoord);
  float y = dot(c.rgb, vec3(0.299, 0.587, 0.114));
  vec2 cc = vec2(dot(c.rgb, vec3(-0.168736, -0.331264, 0.5)), dot(c.rgb, vec3(0.5, -0.418688, -0.081312)));
  float d = distance(cc, uKey);
  float a = smoothstep(uLow, uHigh, d);
  float k = length(uKey);
  if (k > 0.0) {
    vec2 dir = uKey / k;
    cc -= dir * max(dot(cc, dir), 0.0) * (1.0 - smoothstep(uLow, uHigh + $KEY_SPILL, d));
  }
  vec3 rgb = clamp(vec3(y + 1.402 * cc.y, y - 0.344136 * cc.x - 0.714136 * cc.y, y + 1.772 * cc.x), 0.0, 1.0);
  a *= c.a;
  gl_FragColor = vec4(rgb * mix(1.0, a, uPremultiply), a);
}
"""
