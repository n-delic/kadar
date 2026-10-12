@file:OptIn(UnstableApi::class)

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
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
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

data class Music(val uri: Uri, val name: String, val durationMs: Long, val startMs: Long = 0, val volume: Float = 1f)

data class VideoClip(
    val uri: Uri,
    val durationMs: Long,
    val startMs: Long = 0,
    val endMs: Long = durationMs,
    val speed: Float = 1f,
    /** Volume of the video's own sound; 0 drops it. */
    val volume: Float = 1f,
    val music: Music? = null,
) {
    val outputDurationMs get() = ((endMs - startMs) / speed).toLong()
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
        .setEffects(Effects(listOf(gain(volume)), emptyList()))
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

private fun clipped(uri: Uri, startMs: Long, endMs: Long) = MediaItem.Builder()
    .setUri(uri)
    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
    .build()

private fun gain(volume: Float) = GainProcessor(DefaultGainProvider.Builder(volume).build())

private class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long) = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
}
