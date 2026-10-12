@file:OptIn(UnstableApi::class, ExperimentalApi::class)

package com.ndelic.kadar.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.ndelic.kadar.media.await
import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.Steady
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.model.animationSpans
import com.ndelic.kadar.model.drawLayer
import com.ndelic.kadar.model.filterMatrix
import com.ndelic.kadar.model.glRgbMatrix
import com.ndelic.kadar.model.motionAt
import com.ndelic.kadar.model.renderLayers
import com.ndelic.kadar.model.segmentBounds
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Music(val uri: Uri, val name: String, val durationMs: Long, val startMs: Long = 0, val volume: Float = 1f)

data class VideoClip(
    val uri: Uri,
    val durationMs: Long,
    val startMs: Long = 0,
    val endMs: Long = durationMs,
    val speed: Float = 1f,
    /** Volume of the video's own sound; 0 drops it. */
    val volume: Float = 1f,
    /** Speeding up raises the pitch (chipmunk voice) unless this corrects it back. */
    val keepPitch: Boolean = true,
    val music: Music? = null,
    /** The video's own display size (rotation applied). */
    val width: Int = 0,
    val height: Int = 0,
    /** Output aspect ratio (width / height), or null to keep the video's own; the rest is black, or [blur]. */
    val canvas: Float? = null,
    /** Fills the canvas around the video with a blurred, zoomed copy of it instead of black. */
    val blur: Boolean = false,
    val adjust: Adjust = Adjust(),
    /** The video's average frames per second, 0 when unknown. */
    val frameRate: Float = 0f,
) {
    val outputDurationMs get() = ((endMs - startMs) / speed).toLong()

    /** Output frame size; also the project canvas, so layers line up with the exported frame. */
    val outputSize get() = canvasSize(width, height, canvas)

    /** Whether the composition gets the blurred copy as a second sequence (so the preview needs the compositor too). */
    val blurred get() = blur && canvas != null
}

/**
 * Size of a [ratio] canvas for a [w]×[h] video: the long side stays (capped at 1920 so phones can encode it), dimensions even.
 * Null [ratio] keeps the video's own size.
 */
fun canvasSize(w: Int, h: Int, ratio: Float?): Pair<Int, Int> {
    if (ratio == null) return w to h
    val long = minOf(maxOf(w, h), 1920)
    fun even(v: Float) = (v / 2).roundToInt() * 2
    return if (ratio >= 1) even(long.toFloat()) to even(long / ratio) else even(long * ratio) to even(long.toFloat())
}

/**
 * Size the blurred background of a [w]×[h] canvas is blurred at, long side [BLUR_LONG_SIDE]: blurring a small frame and
 * scaling it up looks the same as a wide blur at full size, at a fraction of the GPU work.
 */
fun blurSize(w: Int, h: Int): Pair<Int, Int> {
    val s = BLUR_LONG_SIDE / maxOf(w, h).toFloat()
    return maxOf(2, (w * s).roundToInt()) to maxOf(2, (h * s).roundToInt())
}

private const val BLUR_LONG_SIDE = 320

/**
 * The edit as a Media3 composition: used for both the live preview and the export. [overlay] draws the text/sticker
 * layers; it sits on the composition, not the clip, so it gets output timestamps (after trim and speed) like the layers.
 * [videos] are picture-in-picture layers, each a sequence of its own (the export's; the preview draws them itself).
 */
fun VideoClip.composition(
    overlay: TextureOverlay?,
    shortSide: Int? = null,
    videos: List<VideoLayer> = emptyList(),
): Composition {
    // CompositionPlayer needs each item's full (unclipped) duration up front.
    val video = EditedMediaItem.Builder(clipped(uri, startMs, endMs))
        .setDurationUs(durationMs * 1000)
        .setRemoveAudio(volume == 0f)
        .setSpeed(ConstantSpeed(speed))
        .setEffects(Effects(listOfNotNull(gain(volume), pitchFix(speed).takeIf { keepPitch && speed != 1f }), videoEffects()))
        .build()
    // Media3's compositor takes every output frame time from sequence 0 and draws it on top, then the others in order.
    // So sequence 0 is an invisible frame clock at the clip's rate, then the overlays (topmost layer first), each padded
    // with gaps to the clip's full length, then the clip, and under it the blurred background. The clip's letterbox
    // bars are transparent (Media3 clears to transparent black), so the background shows through them.
    val overlays = videos.asReversed().filter { !it.hidden }.mapNotNull { v -> overlaySpan(v.startMs, v.endMs, v.playableMs, outputDurationMs)?.let { v to it } }
    val sequences = mutableListOf<EditedMediaItemSequence>()
    if (overlays.isNotEmpty()) sequences += frameClock()
    sequences += overlays.map { (v, span) -> v.sequence(span, outputDurationMs) }
    sequences += EditedMediaItemSequence.Builder(video).build()
    if (blurred) sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItem(blurredBackground()).build()
    music?.let { m ->
        val track = EditedMediaItem.Builder(clipped(m.uri, m.startMs, C.TIME_END_OF_SOURCE))
            .setDurationUs(m.durationMs * 1000)
            .setRemoveVideo(true)
            .setEffects(Effects(listOf(gain(m.volume)), emptyList()))
            .build()
        // A looping sequence repeats or gets cut to the length of the video.
        sequences += EditedMediaItemSequence.Builder(track).setIsLooping(true).build()
    }
    return Composition.Builder(sequences)
        // Downscale after the overlay: the layers are rendered at full canvas size and must line up first.
        .setEffects(
            Effects(
                emptyList(),
                listOfNotNull(
                    overlay?.let { OverlayEffect(listOf(it)) },
                    shortSide?.takeIf { it < minOf(outputSize.first, outputSize.second) }?.let { Presentation.createForShortSide(it) },
                ),
            ),
        )
        // HDR phone footage turns washed out on most apps once posted; memes go out as plain SDR.
        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        .apply { if (overlays.isNotEmpty()) setVideoCompositorSettings(overlaySettings(overlays)) }
        .build()
}

/**
 * Sequence 0 when there are video layers: a blank still repeated at the clip's frame rate, which the compositor hides
 * (alpha 0). It makes the output frame times steady and the clip's own, whatever the overlays' frame rates.
 */
private fun VideoClip.frameClock(): EditedMediaItemSequence {
    val still = MediaItem.Builder().setUri(BLANK_PNG).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(outputDurationMs).build()
    val item = EditedMediaItem.Builder(still).setDurationUs(outputDurationMs * 1000).setFrameRate(outputFrameRate(frameRate)).build()
    return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItem(item).build()
}

/** The clip again, muted, with the same trim and speed so it stays in sync, cropped to fill the canvas, blurred and darkened. */
private fun VideoClip.blurredBackground(): EditedMediaItem {
    val (w, h) = outputSize
    val (bw, bh) = blurSize(w, h)
    val effects = listOfNotNull(
        adjustEffect(),
        Presentation.createForWidthAndHeight(bw, bh, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
        GaussianBlur(BLUR_SIGMA),
        // Darker, so captions over the background stay readable.
        Brightness(-0.15f),
        Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT),
    )
    return EditedMediaItem.Builder(clipped(uri, startMs, endMs))
        .setDurationUs(durationMs * 1000)
        .setRemoveAudio(true)
        .setSpeed(ConstantSpeed(speed))
        .setEffects(Effects(emptyList(), effects))
        .build()
}

/** In pixels of [blurSize]'s frame: about 5% of the canvas's long side. */
private const val BLUR_SIGMA = 16f

/** A transparent 2×2 PNG. */
private const val BLANK_PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAC0lEQVR4nGNgQAcAABIAAXfx+gAAAAAASUVORK5CYII="

/** One overlay's sequence: a gap until it starts, its clip from [VideoLayer.clipStartMs], then a gap to [totalMs]. */
private fun VideoLayer.sequence(span: Pair<Long, Long>, totalMs: Long): EditedMediaItemSequence {
    val (start, end) = span
    val clip = EditedMediaItem.Builder(clipped(uri, clipStartMs, clipStartMs + end - start))
        .setDurationUs(durationMs * 1000)
        .setRemoveAudio(volume == 0f)
        // Presented at the layer's own size, so the compositor's scale is the layer's scale.
        .setEffects(Effects(listOf(gain(volume)), listOf(Presentation.createForWidthAndHeight(videoWidth, videoHeight, Presentation.LAYOUT_STRETCH_TO_FIT))))
        .build()
    val tracks = if (volume == 0f) setOf(C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)
    return EditedMediaItemSequence.Builder(tracks)
        .apply { if (start > 0) addGap(start * 1000) }
        .addItem(clip)
        .apply { if (totalMs > end) addGap((totalMs - end) * 1000) }
        .build()
}

/**
 * Hides the frame clock (sequence 0) and places overlay `i` (sequence `i + 1`) by its layer; the clip after them keeps
 * the default full-frame settings.
 */
private fun VideoClip.overlaySettings(overlays: List<Pair<VideoLayer, Pair<Long, Long>>>) =
    object : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: List<Size>) = Size(outputSize.first, outputSize.second)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            if (inputId == 0) return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
            val (layer, span) = overlays.getOrNull(inputId - 1) ?: return StaticOverlaySettings.Builder().build()
            val p = overlayPlacement(layer.t, outputSize.first, outputSize.second)
            return StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(p.x, p.y)
                .setScale(p.scale, p.scale)
                .setRotationDegrees(p.rotation)
                // Gaps are filled with small black frames; only the clip's own frames may show.
                .setAlphaScale(overlayAlpha(layer.opacity, span, presentationTimeUs))
                .build()
        }
    }

/**
 * Layers that come and go: one full-frame bitmap per stretch of time where the visible set doesn't change, rendered up
 * front on the caller's (main) thread, because layer paints are shared with the on-screen editor. Stretches where a layer
 * animates are drawn frame by frame instead, into one reused bitmap, from copies of the layers that own their paints.
 * shortcut: every still stretch is a full-frame bitmap in memory (8 MB at 1080p); fine for a handful of layers, render lazily if projects get dozens.
 */
fun timedOverlay(width: Int, height: Int, layers: List<Layer>, durationMs: Long): TextureOverlay {
    val shown = layers.filter { !it.hidden }
    fun Layer.until() = minOf(endMs, durationMs)
    val bounds = shown.flatMap { listOf(it.startMs to it.endMs) + animationSpans(it.animIn, it.animOut, it.startMs, it.until()) }
    val stretches = segmentBounds(bounds, durationMs).mapNotNull { (start, end) ->
        val mid = (start + end) / 2
        val visible = shown.filter { it.visibleAt(mid) }
        when {
            visible.isEmpty() -> null
            visible.any { it.motionAt(mid, it.until()) != Steady } -> Stretch(start * 1000, end * 1000, null, visible.map { it.withTransform(it.t) })
            else -> Stretch(start * 1000, end * 1000, renderLayers(width, height, android.graphics.Color.TRANSPARENT, visible), visible)
        }
    }
    val nothing = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val frame by lazy { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
    return object : BitmapOverlay() {
        // BitmapOverlay uploads again when the bitmap's generation id changes, which every redraw of [frame] does.
        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val s = stretches.firstOrNull { presentationTimeUs >= it.startUs && presentationTimeUs < it.endUs } ?: return nothing
            s.bitmap?.let { return it }
            frame.eraseColor(android.graphics.Color.TRANSPARENT)
            val canvas = Canvas(frame)
            val ms = presentationTimeUs / 1000
            s.layers.forEach { canvas.drawLayer(it, it.motionAt(ms, it.until()), height) }
            return frame
        }
    }
}

/** A stretch of [timedOverlay]: its pre-rendered [bitmap], or null when [layers] animate and are drawn per frame. */
private class Stretch(val startUs: Long, val endUs: Long, val bitmap: Bitmap?, val layers: List<Layer>)

/** Encodes [composition] to an H.264/AAC MP4 at [out]. Must be called on the main thread. */
suspend fun exportVideo(context: Context, composition: Composition, out: File, onProgress: (Int) -> Unit) = coroutineScope {
    val done = CompletableDeferred<Unit>()
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                done.complete(Unit)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                done.completeExceptionally(exportException)
            }
        })
        .build()
    transformer.start(composition, out.path)
    val progress = ProgressHolder()
    val poll = launch {
        while (true) {
            if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress)
            delay(250)
        }
    }
    try {
        done.await()
    } finally {
        poll.cancel()
        if (!done.isCompleted) transformer.cancel()
    }
}

private fun VideoClip.videoEffects(): List<Effect> = buildList {
    adjustEffect()?.let(::add)
    if (canvas != null) {
        val (w, h) = outputSize
        add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
    }
}

/** The same matrix as an image layer's paint, so a look or adjustment matches between photo and video. */
private fun VideoClip.adjustEffect() = if (adjust.isNone) null else glRgbMatrix(adjust.filterMatrix()).let { m -> RgbMatrix { _, _ -> m } }

private fun clipped(uri: Uri, startMs: Long, endMs: Long) = MediaItem.Builder()
    .setUri(uri)
    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
    .build()

private fun gain(volume: Float) = GainProcessor(DefaultGainProvider.Builder(volume).build())

/** The speed change scales every frequency by [speed]; shifting pitch by 1/[speed] cancels it without touching the length. */
private fun pitchFix(speed: Float) = SonicAudioProcessor().apply { setPitch(1 / speed) }

private class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long) = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
}
