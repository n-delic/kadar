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
import com.ndelic.kadar.model.CensorLayer
import kotlin.math.cos
import kotlin.math.sin

/** How many blur areas show at once; more at the same time are left out. */
const val MAX_CENSORS = 8

private const val CELL_MIN = 0.01f
private const val CELL_MAX = 0.08f

/** How big a censor's blocks are (pixelate), or the cells its blur is worked out from, on a [w]×[h] canvas: a share of its short side. */
fun censorCell(strength: Float, w: Int, h: Int) = minOf(w, h) * (CELL_MIN + (CELL_MAX - CELL_MIN) * strength.coerceIn(0f, 1f))

/** How much the magnifier kind zooms into its area, from [strength] (0 to 1): 2x to 6x, so a face's middle fills the area. */
fun censorZoom(strength: Float) = 2f + 4f * strength.coerceIn(0f, 1f)

/**
 * The blur areas of [censors] that show at [ms] (output time) on a [w]×[h] canvas, topmost first, as the shader takes them:
 * per area, in [place] its center (canvas pixels, y down) and its rotation's cosine and sine, in [shape] its half width and
 * height (canvas pixels) and its cell size, and in [shape]'s z and [extra] its kind (the [CensorKind] ordinal times 10 plus
 * the [com.ndelic.kadar.model.CensorShape] ordinal) and its opacity and magnifier zoom.
 */
class CensorUniforms(censors: List<CensorLayer>, ms: Long, w: Int, h: Int) {
    private val shown = censors.filter { !it.hidden && it.visibleAt(ms) }.asReversed().take(MAX_CENSORS)
    val count = shown.size
    val place = FloatArray(MAX_CENSORS * 4)
    val shape = FloatArray(MAX_CENSORS * 4)
    val extra = FloatArray(MAX_CENSORS * 4)

    init {
        shown.forEachIndexed { i, c ->
            val r = Math.toRadians(c.t.rotation.toDouble())
            val cell = censorCell(c.strength, w, h)
            floatArrayOf(c.t.x, c.t.y, cos(r).toFloat(), sin(r).toFloat()).copyInto(place, i * 4)
            floatArrayOf(c.w * c.t.scaleX / 2, c.h * c.t.scale / 2, (c.kind.ordinal * 10 + c.shape.ordinal).toFloat(), cell).copyInto(shape, i * 4)
            floatArrayOf(c.opacity, censorZoom(c.strength), 0f, 0f).copyInto(extra, i * 4)
        }
    }
}

/**
 * Censors the clip under the blur areas [censors] (read on every frame, so the preview's change live) shown at the frame's
 * time: blur, pixelate, frosted glass (the blur mixed toward white) or a magnifier that zooms into the area's middle, each
 * mixed over the frame by the area's opacity. Goes last on a clip, after its fit and placement, so its frame is the canvas
 * and its time output time. Pixelate is the average of each block; blur blends the averages of the four nearest cells
 * smoothly, like a picture worked out small and scaled up.
 */
class Censor(val censors: () -> List<CensorLayer>) : GlEffect {
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
            val u = CensorUniforms(censors(), presentationTimeUs / 1000, size.width, size.height)
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatsUniform("uSize", floatArrayOf(size.width.toFloat(), size.height.toFloat()))
                program.setIntUniform("uCount", u.count)
                program.bindAttributesAndUniforms()
                // GlProgram sets one element of a uniform array, so the arrays go in directly, after it.
                GLES20.glUniform4fv(program.getUniformLocation("uPlace"), MAX_CENSORS, u.place, 0)
                GLES20.glUniform4fv(program.getUniformLocation("uShape"), MAX_CENSORS, u.shape, 0)
                GLES20.glUniform4fv(program.getUniformLocation("uExtra"), MAX_CENSORS, u.extra, 0)
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

// Canvas pixels with y down, like the layers; texture coordinates have y up. A heart is the curve
// (x² + y² - 1)³ = x²y³, which spans x ±1.1375 and y -1 to 1.235, fitted into the box.
// uShape's z is the kind (the CensorKind ordinal times 10) plus the shape ordinal; uExtra holds the
// area's opacity and the magnifier's zoom. Frosted is the smooth blur mixed toward white, triangles and
// halftone dots are worked out of the cell averages like pixelate is.
private val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uSize;
uniform int uCount;
uniform vec4 uPlace[$MAX_CENSORS];
uniform vec4 uShape[$MAX_CENSORS];
uniform vec4 uExtra[$MAX_CENSORS];
varying vec2 vTexCoord;

vec4 cellAverage(vec2 cell, float size) {
  vec4 sum = vec4(0.0);
  for (int i = 0; i < 4; i++) {
    for (int j = 0; j < 4; j++) {
      vec2 p = (cell + (vec2(float(i), float(j)) + 0.5) / 4.0) * size;
      sum += texture2D(uTexSampler, vec2(p.x / uSize.x, 1.0 - p.y / uSize.y));
    }
  }
  return sum / 16.0;
}

bool inside(vec2 l, float shape) {
  if (shape < 0.5) return max(abs(l.x), abs(l.y)) <= 1.0;
  if (shape < 1.5) return dot(l, l) <= 1.0;
  float x = l.x * 1.1375;
  float y = 0.1175 - l.y * 1.1175;
  float a = x * x + y * y - 1.0;
  return a * a * a <= x * x * y * y * y;
}

void main() {
  vec2 p = vec2(vTexCoord.x, 1.0 - vTexCoord.y) * uSize;
  vec4 orig = texture2D(uTexSampler, vTexCoord);
  vec4 c = orig;
  for (int k = 0; k < $MAX_CENSORS; k++) {
    if (k >= uCount) break;
    vec2 d = p - uPlace[k].xy;
    vec2 l = vec2(d.x * uPlace[k].z + d.y * uPlace[k].w, d.y * uPlace[k].z - d.x * uPlace[k].w) / uShape[k].xy;
    if (inside(l, mod(uShape[k].z, 10.0))) {
      float kind = floor(uShape[k].z / 10.0 + 0.5);
      float size = uShape[k].w;
      if (kind < 0.5 || (kind > 1.5 && kind < 2.5)) {
        vec2 g = p / size - 0.5;
        vec2 i = floor(g);
        vec2 f = smoothstep(0.0, 1.0, g - i);
        c = mix(mix(cellAverage(i, size), cellAverage(i + vec2(1.0, 0.0), size), f.x),
                mix(cellAverage(i + vec2(0.0, 1.0), size), cellAverage(i + vec2(1.0, 1.0), size), f.x), f.y);
        if (kind > 1.5) c = mix(c, vec4(1.0), 0.25);
      } else if (kind < 1.5) {
        c = cellAverage(floor(p / size), size);
      } else if (kind < 3.5) {
        vec2 src = uPlace[k].xy + d / uExtra[k].y;
        c = texture2D(uTexSampler, vec2(src.x / uSize.x, 1.0 - src.y / uSize.y));
      } else if (kind < 4.5) {
        // Triangles: both halves of each cell along its diagonal, shaded apart with a per-cell jitter, for a low-poly look.
        vec2 cell = floor(p / size);
        bool up = (p.x / size - cell.x) + (p.y / size - cell.y) > 1.0;
        c = cellAverage(cell + (up ? vec2(1.0) : vec2(0.0)), size);
        float jitter = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
        c *= up ? 1.0 : 0.86 + 0.2 * jitter;
      } else {
        // Halftone: a black dot per cell, bigger the darker the cell's average.
        vec2 cell = floor(p / size);
        vec4 avg = cellAverage(cell, size);
        float lum = clamp(dot(avg.rgb, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
        float r = size * 0.55 * sqrt(1.0 - lum);
        c = distance(p, (cell + 0.5) * size) < r ? vec4(0.04, 0.04, 0.04, 1.0) : vec4(1.0);
      }
      c = mix(orig, c, uExtra[k].x);
      break;
    }
  }
  gl_FragColor = c;
}
"""
