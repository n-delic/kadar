package com.ndelic.kadar.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// The mark's parts on the app icon's 108-unit grid; the tile is the 66-unit safe zone starting at 21.
private val CORNERS = listOf(
    "M33,45V39a6,6 0,0 1,6 -6h6",
    "M63,33h6a6,6 0,0 1,6 6v6",
    "M75,63v6a6,6 0,0 1,-6 6h-6",
    "M45,75h-6a6,6 0,0 1,-6 -6v-6",
).map { PathParser().parsePathString(it).toPath() }

/** Which way each corner flies in from (top-left, top-right, bottom-right, bottom-left). */
private val CORNER_FROM = listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f))

private val PLAY = PathParser().parsePathString("M48,44.5L64,54L48,63.5Z").toPath()

/** Progress of [p] through the window [start]..[end], eased, 0 before and 1 after. */
private fun window(p: Float, start: Float, end: Float, easing: (Float) -> Float = FastOutSlowInEasing::transform) =
    easing(((p - start) / (end - start)).coerceIn(0f, 1f))

/**
 * Cold-start intro: the tile pops in, the four lime corners fly in one by one, the teal play mark spins in,
 * then "Kadar" rises letter by letter over a soft glow, and everything fades into the home screen.
 * Tap to skip; skipped entirely when the system has animations turned off.
 */
@Composable
fun Intro(ready: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val animationsOff = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    LaunchedEffect(Unit) {
        if (animationsOff) return@LaunchedEffect onDone()
        // Let the home screen underneath load and draw its thumbnails first (the dark launch moment hides it);
        // doing that mid-animation made it stall. Capped, so a slow disk never holds the app.
        withTimeoutOrNull(1200) { snapshotFlow { isReady }.first { it } }
        repeat(3) { withFrameNanos { } }
        progress.animateTo(1f, tween(1500, easing = LinearEasing))
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onDone()
    }
    val p = progress.value
    val out = exit.value

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - out }
            .background(Ink.bg)
            .clickable(interactionSource = null, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                // On the way out the logo leans toward the viewer a little, as if the app opens through it.
                val grow = 1f + 0.12f * out
                scaleX = grow
                scaleY = grow
            },
        ) {
            Canvas(Modifier.size(132.dp)) {
                val unit = size.minDimension / 66f
                val center = Offset(size.width / 2, size.height / 2)

                // Glow: swells as the mark lands, settles once the name is in.
                val glow = window(p, 0.1f, 0.5f) * (1f - 0.5f * window(p, 0.7f, 1f))
                drawCircle(
                    Brush.radialGradient(listOf(Ink.accent.copy(alpha = 0.2f * glow), Color.Transparent), center, size.minDimension * 1.3f),
                    radius = size.minDimension * 1.3f,
                    center = center,
                )

                val tile = window(p, 0f, 0.3f, Overshoot::transform)
                scale(0.55f + 0.45f * tile, center) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0xFF2E3036), Color(0xFF16171A))),
                        cornerRadius = CornerRadius(16 * unit),
                        alpha = window(p, 0f, 0.18f),
                    )
                }

                translate(-21 * unit, -21 * unit) {
                    scale(unit, Offset.Zero) {
                        CORNERS.forEachIndexed { i, corner ->
                            val start = 0.18f + i * 0.07f
                            val c = window(p, start, start + 0.24f, Overshoot::transform)
                            if (c > 0f) {
                                val from = CORNER_FROM[i] * (14f * (1f - c))
                                translate(from.x, from.y) {
                                    drawPath(corner, Ink.accent, alpha = window(p, start, start + 0.08f), style = Stroke(5f, cap = StrokeCap.Round))
                                }
                            }
                        }
                        val play = window(p, 0.42f, 0.7f, Overshoot::transform)
                        if (play > 0f) {
                            rotate(-120f * (1f - play), Offset(54.5f, 54f)) {
                                scale(play, Offset(54.5f, 54f)) {
                                    drawPath(PLAY, Ink.teal, style = Fill)
                                    drawPath(PLAY, Ink.teal, style = Stroke(3f, join = StrokeJoin.Round))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                "Kadar".forEachIndexed { i, ch ->
                    val start = 0.55f + i * 0.05f
                    val l = window(p, start, start + 0.25f)
                    Text(
                        ch.toString(),
                        color = Ink.text,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.graphicsLayer {
                            alpha = l
                            translationY = (1f - l) * 22.dp.toPx()
                        },
                    )
                }
            }
            Text(
                "Edit anything. Keep everything.",
                color = Ink.dim,
                fontSize = 14.sp,
                modifier = Modifier.graphicsLayer { alpha = window(p, 0.78f, 1f) },
            )
        }
    }
}
