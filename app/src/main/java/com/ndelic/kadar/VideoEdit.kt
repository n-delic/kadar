@file:OptIn(UnstableApi::class, ExperimentalApi::class)

package com.ndelic.kadar

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Effect
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

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
    /** Output aspect ratio (width / height), or null to keep the video's own; the rest is black. */
    val canvas: Float? = null,
    val adjust: Adjust = Adjust(),
) {
    val outputDurationMs get() = ((endMs - startMs) / speed).toLong()

    /** Output frame size; also the project canvas, so layers line up with the exported frame. */
    val outputSize get() = canvasSize(width, height, canvas)
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
 * The edit as a Media3 composition: used for both the live preview and the export. [overlay] draws the text/sticker
 * layers; it sits on the composition, not the clip, so it gets output timestamps (after trim and speed) like the layers.
 */
fun VideoClip.composition(overlay: TextureOverlay?): Composition {
    // CompositionPlayer needs each item's full (unclipped) duration up front.
    val video = EditedMediaItem.Builder(clipped(uri, startMs, endMs))
        .setDurationUs(durationMs * 1000)
        .setRemoveAudio(volume == 0f)
        .setSpeed(ConstantSpeed(speed))
        .setEffects(Effects(listOfNotNull(gain(volume), pitchFix(speed).takeIf { keepPitch && speed != 1f }), videoEffects()))
        .build()
    val sequences = mutableListOf(EditedMediaItemSequence.Builder(video).build())
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
        .setEffects(Effects(emptyList(), listOfNotNull(overlay?.let { OverlayEffect(listOf(it)) })))
        // HDR phone footage turns washed out on most apps once posted; memes go out as plain SDR.
        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        .build()
}

/**
 * Layers that come and go: one full-frame bitmap per stretch of time where the visible set doesn't change.
 * Rendered up front on the caller's (main) thread, because layer paints are shared with the on-screen editor.
 * shortcut: every stretch is a full-frame bitmap in memory (8 MB at 1080p); fine for a handful of layers, render lazily if projects get dozens.
 */
fun timedOverlay(width: Int, height: Int, layers: List<Layer>, durationMs: Long): TextureOverlay {
    val stretches = segmentBounds(layers.map { it.startMs to it.endMs }, durationMs).mapNotNull { (start, end) ->
        val visible = layers.filter { it.visibleAt((start + end) / 2) }
        if (visible.isEmpty()) null else Triple(start * 1000, end * 1000, renderLayers(width, height, android.graphics.Color.TRANSPARENT, visible))
    }
    val nothing = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    return object : BitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long) =
            stretches.firstOrNull { presentationTimeUs >= it.first && presentationTimeUs < it.second }?.third ?: nothing
    }
}

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
    if (adjust.brightness != 0f) add(Brightness(adjust.brightness))
    if (adjust.contrast != 0f) add(Contrast(adjust.contrast))
    if (adjust.saturation != 0f) add(HslAdjustment.Builder().adjustSaturation(adjust.saturation * 100).build())
    if (canvas != null) {
        val (w, h) = outputSize
        add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
    }
}

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
