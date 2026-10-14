package com.ndelic.kadar.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Width and height of the box around a [w]×[h] picture turned by [degrees] about its center. */
fun turnedBounds(w: Float, h: Float, degrees: Float): Pair<Float, Float> {
    val r = Math.toRadians(degrees.toDouble())
    val c = abs(cos(r)).toFloat()
    val s = abs(sin(r)).toFloat()
    return w * c + h * s to w * s + h * c
}

/**
 * Width and height of the largest rectangle of [ratio] (width / height) centered on a [w]×[h] picture turned by
 * [degrees], so a straightened crop starts without empty corners.
 */
fun straightenedFit(w: Float, h: Float, degrees: Float, ratio: Float): Pair<Float, Float> {
    val r = Math.toRadians(degrees.toDouble())
    val c = abs(cos(r)).toFloat()
    val s = abs(sin(r)).toFloat()
    // Every corner of the rectangle, turned back into the picture's own axes, has to stay inside it.
    val width = min(w / (c + s / ratio), h / (s + c / ratio))
    return width to width / ratio
}

/** [x], [y] turned by [degrees] (clockwise on screen, y down, like Canvas.rotate). */
fun turned(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return x * c - y * s to x * s + y * c
}

/**
 * Where a crop of a turned layer starts in its turned source, the new `sourceX`, `sourceY`. The layer's [w]×[h] bitmap sat
 * at ([sourceX], [sourceY]) in a [sourceW]×[sourceH] source; both are mirrored ([flipX], [flipY]) and turned by [degrees]
 * about their centers, the source into a box of [turnedW]×[turnedH]. The crop's top left is ([left], [top]) from the
 * turned layer's center.
 */
fun turnedSourceOffset(
    w: Int, h: Int, sourceX: Int, sourceY: Int, sourceW: Int, sourceH: Int,
    degrees: Float, turnedW: Int, turnedH: Int, left: Float, top: Float, flipX: Boolean = false, flipY: Boolean = false,
): Pair<Float, Float> {
    // The layer's center, from the source's center, mirrored and turned with it.
    val cx = sourceX + w / 2f - sourceW / 2f
    val cy = sourceY + h / 2f - sourceH / 2f
    val (ux, uy) = turned(if (flipX) -cx else cx, if (flipY) -cy else cy, degrees)
    return turnedW / 2f + ux + left to turnedH / 2f + uy + top
}

/**
 * The canvas fitted to a [w]×[h] picture placed by [photo]: the picture's box (turned with it) in the picture's own
 * pixels, so a scaled photo keeps its resolution.
 */
fun fittedCanvas(w: Float, h: Float, photo: Transform): Pair<Int, Int> =
    turnedBounds(w, h, photo.rotation).let { (bw, bh) -> max(1, bw.roundToInt()) to max(1, bh.roundToInt()) }

/** Where this layer goes on a [width]×[height] canvas fitted to the picture placed by [photo], to stay on the same spot of it. */
fun Transform.onFittedCanvas(photo: Transform, width: Int, height: Int) =
    copy(x = (x - photo.x) / photo.scale + width / 2f, y = (y - photo.y) / photo.scale + height / 2f, scale = scale / photo.scale)
