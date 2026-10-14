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
import androidx.media3.effect.Crop
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.ndelic.kadar.media.await
import com.ndelic.kadar.model.Adjust
import com.ndelic.kadar.model.ExportQuality
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.Loop
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

/** A song on its own track: [part] says where it plays. It loops from [startMs] into the song to the song's end. */
data class Music(val uri: Uri, val name: String, val durationMs: Long, val startMs: Long = 0, val part: AudioPart = AudioPart())

/** A voiceover recorded in the editor: an audio [file] in the project folder [durationMs] long, played as [part] says. */
data class VoiceClip(val file: File, val durationMs: Long, val part: AudioPart)

/**
 * One clip of the main track: [startMs]..[endMs] of its source (source time) played at [speed]. A photo ([isImage],
 * a file in the project folder) is a still whose trimmed length is how long it shows, up to [durationMs].
 */
data class Segment(
    val uri: Uri,
    val durationMs: Long,
    /** The source's display size (rotation applied). */
    val width: Int,
    val height: Int,
    val startMs: Long = 0,
    val endMs: Long = durationMs,
    val speed: Float = 1f,
    /** Volume of the clip's own sound; 0 drops it. */
    val volume: Float = 1f,
    /** Speeding up raises the pitch (chipmunk voice) unless this corrects it back. */
    val keepPitch: Boolean = true,
    val adjust: Adjust = Adjust(),
    val isImage: Boolean = false,
    /** The transition into this clip from the one before it; the first clip's is unused. */
    val transition: Transition = Transition(),
) {
    val outputDurationMs get() = outputMs(startMs, endMs, speed)
}

data class VideoClip(
    /** The main track, played end to end; never empty. */
    val segments: List<Segment>,
    /** Songs, each a part on its own track (a split song is two). */
    val music: List<Music> = emptyList(),
    /** The first clip's display size, set when the project is made; every clip is fitted into it. */
    val width: Int = 0,
    val height: Int = 0,
    /** Output aspect ratio (width / height), or null to keep the video's own; the rest is black, or [blur]. */
    val canvas: Float? = null,
    /** Fills the canvas around the video with a blurred, zoomed copy of it instead of black. */
    val blur: Boolean = false,
    /** The first clip's average frames per second, 0 when unknown. */
    val frameRate: Float = 0f,
    val voices: List<VoiceClip> = emptyList(),
) {
    /** How much of each clip's start an overlapping transition plays over the clip before it (output time). */
    val headsMs get() = headsMs(segments.map { it.outputDurationMs }, segments.map { it.transition })

    /** The clips as the main track plays them: each without its head, which plays on top of the clip before. */
    val played get() = segments.zip(headsMs) { s, head -> s.copy(startMs = s.startMs + (head * s.speed).toLong()) }

    /** Where each transition plays, in output time. */
    val transitionSpans get() = transitionSpans(segments.map { it.outputDurationMs }, segments.map { it.transition })

    /** The clips end to end, shortened by every overlapping transition. */
    val outputDurationMs get() = played.sumOf { it.outputDurationMs }

    /**
     * Index of the clip the main track plays at output time [positionMs], and how far into it (output time, counted
     * from its very start, so its head comes first): during an overlapping transition, still the clip before.
     */
    fun segmentAt(positionMs: Long): Pair<Int, Long> {
        val (i, into) = clipAt(played.map { it.outputDurationMs }, positionMs)
        return i to into + headsMs[i]
    }

    /** Where clip [index] starts, in output time: its head plays over the end of the clip before it. */
    fun segmentStart(index: Int) = played.take(index).sumOf { it.outputDurationMs } - headsMs[index]

    /** Output frame size; also the project canvas, so layers line up with the exported frame. */
    val outputSize get() = canvasSize(width, height, canvas)

    /** Whether the composition gets the blurred copy as a second sequence (the export only; the preview draws it with BlurBackgroundPreview). */
    val blurred get() = blur && canvas != null

    /** Whether the output has sound: from a clip, the music or a voiceover. */
    val hasSound get() = hasSound(segments) || music != null || voices.isNotEmpty()
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
 * The edit as a Media3 composition: used for both the live preview and the export. [layers] (the export's; the preview
 * draws them itself) stack in their order: video layers are picture-in-picture sequences of their own; the other
 * layers above the topmost video layer are one [timedOverlay] on the composition, not the clip, so it gets output
 * timestamps (after trim and speed) like the layers, and those under a video layer are drawn into a sequence of their
 * own. [incoming] adds the heads of overlapping transitions as a sequence over the clips (the preview leaves it out and
 * draws them itself, with IncomingClipPreview). [dropFrames] keeps sped-up clips at the clip's frame rate when they set
 * the output's frame times (the preview turns it off). [frameClock] times the output by the frame clock at [VideoClip.frameRate]
 * even without video layers or transitions, for an export at a chosen frame rate. Call it on the main thread, like [timedOverlay].
 */
fun VideoClip.composition(
    layers: List<Layer> = emptyList(),
    shortSide: Int? = null,
    incoming: Boolean = true,
    dropFrames: Boolean = true,
    frameClock: Boolean = false,
    previewCopies: Map<Uri, Uri> = emptyMap(),
): Composition {
    // Media3's compositor takes every output frame time from sequence 0 and draws it on top, then the others in order.
    // So sequence 0 is an invisible frame clock at the clip's rate, then the overlays (topmost layer first), each padded
    // with gaps to the output's full length and followed by the layers drawn right under it, then the heads of
    // overlapping transitions, then the clips, and under them the blurred background. The letterbox bars are
    // transparent (Media3 clears to transparent black), so the background shows through them.
    val (w, h) = outputSize
    fun VideoLayer.span() = overlaySpan(startMs, endMs, playableMs, outputDurationMs)
    val shown = layers.filter { !it.hidden && (it !is VideoLayer || it.span() != null) }
    val groups = drawnGroups(shown) { it is VideoLayer }
    val videos = shown.filterIsInstance<VideoLayer>()
    val spans = transitionSpans
    val overlapping = spans.filter { it.kind.overlaps }.takeIf { incoming && it.isNotEmpty() }
    // Compositor inputs after sequence 0, topmost first: how each is placed at an output time (us).
    val inputs = mutableListOf<(Long) -> OverlaySettings>()
    val sequences = mutableListOf<EditedMediaItemSequence>()
    val clocked = frameClock || videos.isNotEmpty() || overlapping != null
    if (clocked) sequences += blankStill()
    videos.indices.reversed().forEach { i ->
        val span = videos[i].span()!!
        inputs += layerSettings(videos[i], span)
        sequences += videos[i].sequence(span, outputDurationMs)
        if (groups[i].isNotEmpty()) {
            inputs += { FULL_FRAME }
            val drawn = OverlayEffect(listOf(timedOverlay(w, h, groups[i], outputDurationMs)))
            sequences += blankStill(listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT), drawn, Unpremultiply))
        }
    }
    val overlay = groups.last().takeIf { it.isNotEmpty() }?.let { timedOverlay(w, h, it, outputDurationMs) }
    val played = this.played
    overlapping?.let { o ->
        val tails = headTailsMs(o)
        val fps = outputFrameRate(frameRate)
        inputs += incomingSettings(o, tails, fps)
        sequences += incomingSequence(o, tails, played, fps)
        inputs += outgoingSettings(o, fps)
    }
    // The clips set the output's frame times only when they are sequence 0; with a frame clock its rate already holds.
    sequences += mainSequence(played, spans, dropFrames && sequences.isEmpty(), previewCopies)
    if (blurred) sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItems(played.map { blurredBackground(it) }).build()
    // Audio only, after every video sequence, so the compositor's input numbers above stay as they are.
    sequences += music.flatMap { audioSequences(it.uri, it.startMs, it.durationMs, it.part, outputDurationMs) }
    sequences += voices.flatMap { audioSequences(Uri.fromFile(it.file), 0, it.durationMs, it.part, outputDurationMs) }
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
        .apply { if (clocked) setVideoCompositorSettings(overlaySettings(inputs)) }
        .build()
}

/**
 * A blank still as long as the output, repeated at the clip's frame rate, with [effects]. Without effects it is
 * sequence 0 when there are video layers, the frame clock the compositor hides (alpha 0): it makes the output frame
 * times steady and the clip's own, whatever the overlays' frame rates. With effects it carries drawn layers; with no
 * speed change and starting at 0, its frame times are output time like the layers'.
 */
private fun VideoClip.blankStill(effects: List<Effect> = emptyList()): EditedMediaItemSequence {
    val still = MediaItem.Builder().setUri(BLANK_PNG).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(outputDurationMs).build()
    val item = EditedMediaItem.Builder(still)
        .setDurationUs(outputDurationMs * 1000)
        .setFrameRate(outputFrameRate(frameRate))
        .setEffects(Effects(emptyList(), effects))
        .build()
    return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItem(item).build()
}

/**
 * The [played] clips end to end, with the centered transitions among [spans] as effects on the clips either side of
 * each cut. With sound in any clip every clip gets an audio track (silence for photos and silent clips), so the
 * sequence's audio runs through without a break. With [dropFrames] a sped-up clip keeps the clip's frame rate instead of
 * handing the encoder speed times as many (300 fps at 10x). Media3 1.11's preview runs on past the end with the frame
 * drop effect, so the preview leaves it out; the frames it shows are the same.
 */
private fun VideoClip.mainSequence(played: List<Segment>, spans: List<TransitionSpan>, dropFrames: Boolean, copies: Map<Uri, Uri>): EditedMediaItemSequence {
    val sound = hasSound(played)
    val dips = spans.filter { !it.kind.overlaps }
    val items = played.mapIndexed { i, s ->
        val drop = if (dropFrames && s.speed > 1f && !s.isImage) listOf(FrameDropEffect.createDefaultFrameDropEffect(outputFrameRate(frameRate).toFloat())) else emptyList()
        val effects = drop + dipEffects(dips.filter { it.clip == i || it.clip == i + 1 }, s.width, s.height)
        // A preview copy is smaller than its source, so it is always fitted into the frame.
        val copy = copies[s.uri]
        // Fades out under the head of an overlapping transition into the next clip, which fades in (incomingSequence).
        val out = spans.firstOrNull { it.kind.overlaps && it.clip == i + 1 }?.let { it.endMs - it.startMs } ?: 0
        val audio = audioEffects(s, sound, fadeOutUs = out * 1000)
        segmentItem(s.copy(uri = copy ?: s.uri), Effects(audio, videoEffects(s, effects, fit = copy != null)), removeAudio = !sound)
    }
    return EditedMediaItemSequence.Builder(tracks(sound)).addItems(items).build()
}

/**
 * The heads of the clips coming in through the overlapping transitions [spans], each at its transition's time with
 * gaps between and to the end, over the main track: faded, moved or wiped in by [incomingSettings] and [WipeMask].
 * Each plays on for its [tails] past the cut, the same picture as the main track there, muted.
 */
private fun VideoClip.incomingSequence(spans: List<TransitionSpan>, tails: List<Long>, played: List<Segment>, fps: Int): EditedMediaItemSequence {
    val heads = spans.mapIndexed { k, span -> segments[span.clip].let { it.copy(endMs = played[span.clip].startMs + (tails[k] * it.speed).toLong()) } }
    val sound = hasSound(heads)
    val builder = EditedMediaItemSequence.Builder(tracks(sound))
    var at = 0L
    spans.forEachIndexed { k, span ->
        val s = heads[k]
        if (span.startMs > at) builder.addGap((span.startMs - at) * 1000)
        at = maxOf(at, span.startMs) + s.outputDurationMs
        val wipe = listOfNotNull(WipeMask(span, fps).takeIf { span.kind.wipes })
        val spanUs = (span.endMs - span.startMs) * 1000
        builder.addItem(segmentItem(s, Effects(audioEffects(s, sound, mutedFromUs = spanUs, fadeInUs = spanUs), videoEffects(s) + wipe), removeAudio = !sound))
    }
    if (outputDurationMs > at) builder.addGap((outputDurationMs - at) * 1000)
    return builder.build()
}

private fun hasSound(clips: List<Segment>) = clips.any { !it.isImage && it.volume > 0f }

private fun tracks(sound: Boolean) = if (sound) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)

/** Clip [s]'s volume and pitch fix; crossfaded in over its first [fadeInUs] and out over its last [fadeOutUs], silent from [mutedFromUs] into it, if given. */
private fun audioEffects(s: Segment, sound: Boolean, mutedFromUs: Long? = null, fadeInUs: Long = 0, fadeOutUs: Long = 0) =
    if (sound && !s.isImage) {
        listOfNotNull(gain(s.volume, mutedFromUs, fadeInUs, fadeOutUs, s.outputDurationMs * 1000), pitchFix(s.speed).takeIf { s.keepPitch && s.speed != 1f })
    } else {
        emptyList()
    }

/** Clip [s] as a sequence item: its trimmed source at its speed, or a photo shown for its length at the clip's frame rate. */
private fun VideoClip.segmentItem(s: Segment, effects: Effects, removeAudio: Boolean): EditedMediaItem {
    if (s.isImage) {
        val ms = s.outputDurationMs
        val mime = if (s.uri.path.orEmpty().endsWith(".png")) MimeTypes.IMAGE_PNG else MimeTypes.IMAGE_JPEG
        val still = MediaItem.Builder().setUri(s.uri).setMimeType(mime).setImageDurationMs(ms).build()
        return EditedMediaItem.Builder(still).setDurationUs(ms * 1000).setFrameRate(outputFrameRate(frameRate)).setEffects(effects).build()
    }
    // CompositionPlayer needs each item's full (unclipped) duration up front.
    return EditedMediaItem.Builder(clipped(s.uri, s.startMs, s.endMs))
        .setDurationUs(s.durationMs * 1000)
        .setRemoveAudio(removeAudio)
        .setSpeed(ConstantSpeed(s.speed))
        .setEffects(effects)
        .build()
}

/** Clip [s] again, muted, with the same trim and speed so it stays in sync, cropped to fill the canvas, blurred and darkened. */
private fun VideoClip.blurredBackground(s: Segment): EditedMediaItem {
    val (w, h) = outputSize
    val (bw, bh) = blurSize(w, h)
    val effects = listOfNotNull(
        adjustEffect(s.adjust),
        Presentation.createForWidthAndHeight(bw, bh, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
        GaussianBlur(BLUR_SIGMA),
        // Darker, so captions over the background stay readable.
        Brightness(-0.15f),
        Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT),
    )
    return segmentItem(s, Effects(emptyList(), effects), removeAudio = true)
}

/** In pixels of [blurSize]'s frame: about 5% of the canvas's long side. */
private const val BLUR_SIGMA = 16f

/** A transparent 2×2 PNG. */
private const val BLANK_PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAC0lEQVR4nGNgQAcAABIAAXfx+gAAAAAASUVORK5CYII="

/**
 * Audio [part] of a source [durationMs] long that loops from [loopFromMs] to its end, cut at [totalMs]: one sequence per
 * repeat, a gap until it plays, that stretch of the source, then a gap to [totalMs]. Not a looping sequence: that restarts
 * its item's clock on every repeat, and the fades need to know where in the output each repeat plays.
 */
private fun audioSequences(uri: Uri, loopFromMs: Long, durationMs: Long, part: AudioPart, totalMs: Long): List<EditedMediaItemSequence> {
    val from = part.atMs
    val to = part.endMs(totalMs)
    val loopMs = durationMs - loopFromMs
    return repeats(from, to, part.offsetMs, loopMs).map { r ->
        val gain = DefaultGainProvider.Builder(part.volume)
        fadeWindows(r.fromMs, r.toMs, from, to, part.fadeInMs, part.fadeOutMs).forEach { (start, end) ->
            // The provider gets sample counts into the window, and inside it its gain replaces the default, so volume goes in here.
            gain.addFadeAt(start * 1000, (end - start) * 1000) { samples, ofSamples ->
                part.volume * fadeGain(r.fromMs + start + (end - start) * samples / ofSamples, from, to, part.fadeInMs, part.fadeOutMs)
            }
        }
        val sourceFrom = loopFromMs + r.loopMs
        // A repeat to the loop's end runs to the source's own end, which can be a little off its reported length.
        val sourceTo = if (r.loopMs + r.toMs - r.fromMs < loopMs) sourceFrom + r.toMs - r.fromMs else C.TIME_END_OF_SOURCE
        val item = EditedMediaItem.Builder(clipped(uri, sourceFrom, sourceTo))
            .setDurationUs(durationMs * 1000)
            .setEffects(Effects(listOf(GainProcessor(gain.build())), emptyList()))
            .build()
        EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            .apply { if (r.fromMs > 0) addGap(r.fromMs * 1000) }
            .addItem(item)
            .apply { if (totalMs > r.toMs) addGap((totalMs - r.toMs) * 1000) }
            .build()
    }
}

/** One overlay's sequence: a gap until it starts, its clip from [VideoLayer.clipStartMs], then a gap to [totalMs]. */
private fun VideoLayer.sequence(span: Pair<Long, Long>, totalMs: Long): EditedMediaItemSequence {
    val (start, end) = span
    val (left, right, bottom, top) = cropNdc(crop)
    val clip = EditedMediaItem.Builder(clipped(uri, clipStartMs, clipStartMs + end - start))
        .setDurationUs(durationMs * 1000)
        .setRemoveAudio(volume == 0f)
        // Its look, cropped, then presented at the layer's own size, so the compositor's scale is the layer's scale.
        .setEffects(Effects(listOf(gain(volume)), listOfNotNull(adjustEffect(adjust), Crop(left, right, bottom, top), Presentation.createForWidthAndHeight(width.roundToInt(), height.roundToInt(), Presentation.LAYOUT_STRETCH_TO_FIT))))
        .build()
    val tracks = if (volume == 0f) setOf(C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)
    return EditedMediaItemSequence.Builder(tracks)
        .apply { if (start > 0) addGap(start * 1000) }
        .addItem(clip)
        .apply { if (totalMs > end) addGap((totalMs - end) * 1000) }
        .build()
}

/**
 * Hides the frame clock (sequence 0) and places input `i` (sequence `i + 1`) by [inputs]; the ones after them (the
 * clips without transitions on top, the blurred background) keep the default full-frame settings.
 */
private fun VideoClip.overlaySettings(inputs: List<(Long) -> OverlaySettings>) =
    object : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: List<Size>) = Size(outputSize.first, outputSize.second)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long) =
            if (inputId == 0) HIDDEN else inputs.getOrNull(inputId - 1)?.invoke(presentationTimeUs) ?: FULL_FRAME
    }

/** Video layer [layer] placed by its transform while it plays its [span]. */
private fun VideoClip.layerSettings(layer: VideoLayer, span: Pair<Long, Long>): (Long) -> OverlaySettings {
    val p = overlayPlacement(layer.t, outputSize.first, outputSize.second)
    return { us ->
        StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(p.x, p.y)
            .setScale(p.scale, p.scale)
            .setRotationDegrees(p.rotation)
            // Gaps are filled with small black frames; only the clip's own frames may show.
            .setAlphaScale(overlayAlpha(layer.opacity, span, us))
            .build()
    }
}

/**
 * The incoming heads of [spans]: only during their transitions and [tails] (the gaps hold black frames), faded or
 * moved in. Each input's frame is placed for the output frame it lands in (at [fps]), so heads and the main track
 * move in step.
 */
private fun incomingSettings(spans: List<TransitionSpan>, tails: List<Long>, fps: Int): (Long) -> OverlaySettings = { us ->
    val ms = outputFrameUs(us, fps) / 1000
    val k = spans.indices.firstOrNull { ms >= spans[it].startMs && ms < spans[it].endMs + tails[it] }
    if (k == null) {
        HIDDEN
    } else {
        val span = spans[k]
        val p = span.progress(ms)
        shifted(span.kind.incomingOffset(p)).setAlphaScale(span.kind.incomingAlpha(p)).build()
    }
}

/** The main track, pushed out during a push among [spans] (placed for its output frame, like [incomingSettings]). */
private fun outgoingSettings(spans: List<TransitionSpan>, fps: Int): (Long) -> OverlaySettings = { us ->
    val ms = outputFrameUs(us, fps) / 1000
    val span = spans.firstOrNull { it.kind.pushes && ms in it }
    if (span == null) {
        FULL_FRAME
    } else {
        shifted(span.kind.outgoingOffset(span.progress(ms))).build()
    }
}

/**
 * A canvas-sized input moved by [offset] (canvas fractions, y down). Anchors only go from -1 to 1 (normalized device
 * coordinates, y up), so a whole canvas away is the overlay's far edge on the background's near one: half each way.
 */
private fun shifted(offset: Pair<Float, Float>) = StaticOverlaySettings.Builder()
    .setBackgroundFrameAnchor(offset.first, -offset.second)
    .setOverlayFrameAnchor(-offset.first, offset.second)

// Lazy: building one needs Android classes, which the JVM tests of this file's pure functions don't have.
private val HIDDEN by lazy { StaticOverlaySettings.Builder().setAlphaScale(0f).build() }
private val FULL_FRAME by lazy { StaticOverlaySettings.Builder().build() }

/**
 * Layers that come and go: one full-frame bitmap per stretch of time where the visible set doesn't change, rendered up
 * front on the caller's (main) thread, because layer paints are shared with the on-screen editor. Stretches where a layer
 * animates are drawn frame by frame instead, into one reused bitmap, from copies of the layers that own their paints.
 * shortcut: every still stretch is a full-frame bitmap in memory (8 MB at 1080p); fine for a handful of layers, render lazily if projects get dozens.
 */
fun timedOverlay(width: Int, height: Int, layers: List<Layer>, durationMs: Long): TextureOverlay {
    val shown = layers.filter { !it.hidden }
    fun Layer.until() = minOf(endMs, durationMs)
    val bounds = shown.flatMap { listOf(it.startMs to it.endMs) + animationSpans(it.animIn, it.animOut, it.startMs, it.until(), it.animLoop, it.animInMs, it.animOutMs) }
    val stretches = segmentBounds(bounds, durationMs).mapNotNull { (start, end) ->
        val mid = (start + end) / 2
        val visible = shown.filter { it.visibleAt(mid) }
        when {
            visible.isEmpty() -> null
            // A loop can be at rest right at [mid], so it counts as animated all through.
            visible.any { it.animLoop != Loop.None || it.motionAt(mid, it.until()) != Steady } -> Stretch(start * 1000, end * 1000, null, visible.map { it.withTransform(it.t) })
            else -> Stretch(start * 1000, end * 1000, renderLayers(width, height, android.graphics.Color.TRANSPARENT, visible).straightAlpha(), visible)
        }
    }
    val nothing = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val frame by lazy { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
    val straight by lazy { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { isPremultiplied = false } }
    val pixels by lazy { IntArray(width * height) }
    return object : BitmapOverlay() {
        // BitmapOverlay uploads again when the bitmap's generation id changes, which every redraw of [frame] does.
        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val s = stretches.firstOrNull { presentationTimeUs >= it.startUs && presentationTimeUs < it.endUs } ?: return nothing
            s.bitmap?.let { return it }
            frame.eraseColor(android.graphics.Color.TRANSPARENT)
            val canvas = Canvas(frame)
            val ms = presentationTimeUs / 1000
            s.layers.forEach { canvas.drawLayer(it, it.motionAt(ms, it.until()), height) }
            frame.getPixels(pixels, 0, width, 0, 0, width, height)
            straight.setPixels(pixels, 0, width, 0, 0, width, height)
            return straight
        }
    }
}

/**
 * This bitmap with its colors not multiplied by alpha. Media3's overlay shader blends `rgb * a`, but an Android bitmap
 * is stored already multiplied, so see-through pixels (edges, shadows, fades, highlighter) would come out darker than
 * on the stage. getPixels returns straight colors and setPixels keeps them on a bitmap that isn't premultiplied.
 */
private fun Bitmap.straightAlpha(): Bitmap {
    val px = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        isPremultiplied = false
        setPixels(px, 0, width, 0, 0, width, height)
    }
}

/** A stretch of [timedOverlay]: its pre-rendered [bitmap], or null when [layers] animate and are drawn per frame. */
private class Stretch(val startUs: Long, val endUs: Long, val bitmap: Bitmap?, val layers: List<Layer>)

/** Encodes [composition] to an H.264/AAC MP4 at [out]. Must be called on the main thread. */
/** [bitrate] in bits per second, or null for Media3's own pick. Cancelling the coroutine stops the export. */
suspend fun exportVideo(context: Context, composition: Composition, out: File, bitrate: Int?, onProgress: (Int) -> Unit) = coroutineScope {
    val done = CompletableDeferred<Unit>()
    val transformer = Transformer.Builder(context)
        .apply {
            if (bitrate != null) {
                val settings = VideoEncoderSettings.Builder().setBitrate(bitrate).build()
                setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(settings).build())
            }
        }
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

/**
 * Whether a clip with these track MIME types plays from a preview copy: Media3 1.11's preview stalls when it plays into
 * an AV1 or VP9 clip that starts between keyframes (a trim, or the head a transition cuts off). Exports read them fine.
 */
fun needsPreviewCopy(trackMimes: List<String>) = trackMimes.any { it == MimeTypes.VIDEO_AV1 || it == MimeTypes.VIDEO_VP9 }

/**
 * Encodes [source] ([width]×[height]) to an H.264/AAC MP4 at [out] for the preview, its short side at most
 * [PREVIEW_COPY_SHORT_SIDE]; [out] appears only when the copy is whole. Must be called on the main thread.
 */
suspend fun makePreviewCopy(context: Context, source: Uri, width: Int, height: Int, sound: Boolean, out: File) {
    val tmp = File(out.path + ".tmp")
    val resize = Presentation.createForShortSide(PREVIEW_COPY_SHORT_SIDE).takeIf { minOf(width, height) > PREVIEW_COPY_SHORT_SIDE }
    val item = EditedMediaItem.Builder(MediaItem.fromUri(source)).setEffects(Effects(emptyList(), listOfNotNull(resize))).build()
    val composition = Composition.Builder(EditedMediaItemSequence.Builder(tracks(sound)).addItem(item).build())
        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        .build()
    try {
        exportVideo(context, composition, tmp, bitrate = null) {}
        check(tmp.renameTo(out))
    } finally {
        tmp.delete()
    }
}

private const val PREVIEW_COPY_SHORT_SIDE = 720

/**
 * Bitrate for a High quality export of a [width]×[height] canvas downscaled to [shortSide] (null keeps it): 0.28 bits
 * per pixel per frame, twice Media3's default and about what a phone camera records 1080p at, capped at 50 Mbps.
 * The encoder clamps it further to what it supports.
 */
fun highBitrate(width: Int, height: Int, shortSide: Int?, frameRate: Float) =
    minOf(bitrate(width, height, shortSide, frameRate, HIGH_BITS_PER_PIXEL), MAX_BITRATE).toInt()

/** Media3's own bitrate for a Standard export (DefaultEncoderFactory's suggestion), sized like [highBitrate]. */
fun standardBitrate(width: Int, height: Int, shortSide: Int?, frameRate: Float) =
    bitrate(width, height, shortSide, frameRate, STANDARD_BITS_PER_PIXEL).toInt()

private fun bitrate(width: Int, height: Int, shortSide: Int?, frameRate: Float, bitsPerPixel: Float): Float {
    val scale = shortSide?.let { minOf(1f, it / minOf(width, height).toFloat()) } ?: 1f
    return width * scale * height * scale * outputFrameRate(frameRate) * bitsPerPixel
}

/**
 * Roughly how many bytes a [durationMs] long MP4 at [videoBitrate] (bits per second) takes, with Media3's AAC sound
 * if [sound]. Encoders undershoot on still or simple pictures, so it's an upper estimate.
 */
fun estimatedBytes(durationMs: Long, videoBitrate: Int, sound: Boolean) = durationMs * (videoBitrate + if (sound) AAC_BITRATE else 0) / 8000

/** [estimatedBytes] of this video exported at [fps] and [quality], downscaled to [shortSide] (null keeps it). */
fun VideoClip.estimatedSize(shortSide: Int?, fps: Int, quality: ExportQuality): Long {
    val (w, h) = outputSize
    val bitrate = if (quality == ExportQuality.High) highBitrate(w, h, shortSide, fps.toFloat()) else standardBitrate(w, h, shortSide, fps.toFloat())
    return estimatedBytes(outputDurationMs, bitrate, hasSound)
}

private const val STANDARD_BITS_PER_PIXEL = 0.14f
private const val HIGH_BITS_PER_PIXEL = 0.28f
private const val MAX_BITRATE = 50_000_000f
private const val AAC_BITRATE = 131_072

/** Clip [s]'s look, then [transition] effects, then its fit into the canvas. */
private fun VideoClip.videoEffects(s: Segment, transition: List<Effect> = emptyList(), fit: Boolean = false): List<Effect> = buildList {
    adjustEffect(s.adjust)?.let(::add)
    addAll(transition)
    // Clips of another size or orientation than the first are fitted into its frame.
    if (fit || canvas != null || s.width != width || s.height != height) {
        val (w, h) = outputSize
        add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
    }
}

/** The same matrix as an image layer's paint, so a look or adjustment matches between photo and video. */
private fun adjustEffect(adjust: Adjust) = if (adjust.isNone) null else glRgbMatrix(adjust.filterMatrix()).let { m -> RgbMatrix { _, _ -> m } }

private fun clipped(uri: Uri, startMs: Long, endMs: Long) = MediaItem.Builder()
    .setUri(uri)
    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
    .build()

private fun gain(volume: Float, mutedFromUs: Long? = null, fadeInUs: Long = 0, fadeOutUs: Long = 0, lengthUs: Long = 0) =
    GainProcessor(
        DefaultGainProvider.Builder(volume).apply {
            // Inside a fade its gain replaces the default, so volume goes in here.
            if (fadeInUs > 0) addFadeAt(0, fadeInUs) { n, of -> volume * crossfadeGain(n.toFloat() / of) }
            if (fadeOutUs > 0) addFadeAt(lengthUs - fadeOutUs, fadeOutUs) { n, of -> volume * crossfadeGain(1 - n.toFloat() / of) }
            // A minute is past the end of anything muted this way (a head's tail).
            mutedFromUs?.let { addFadeAt(it, 60_000_000) { _, _ -> 0f } }
        }.build(),
    )

/** The speed change scales every frequency by [speed]; shifting pitch by 1/[speed] cancels it without touching the length. */
private fun pitchFix(speed: Float) = SonicAudioProcessor().apply { setPitch(1 / speed) }

private class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long) = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
}
