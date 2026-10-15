package com.ndelic.kadar.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.ndelic.kadar.video.AudioPart
import com.ndelic.kadar.video.ClipAnimKind
import com.ndelic.kadar.video.ClipAnimation
import com.ndelic.kadar.video.ClipPlacement
import com.ndelic.kadar.video.DEFAULT_CLIP_ANIM_MS
import com.ndelic.kadar.video.MUSIC_FADE_MS
import com.ndelic.kadar.video.Music
import com.ndelic.kadar.video.DEFAULT_TRANSITION_MS
import com.ndelic.kadar.video.Segment
import com.ndelic.kadar.video.Transition
import com.ndelic.kadar.video.TransitionKind
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.VoiceClip
import com.ndelic.kadar.video.VoiceEffect
import java.io.File
import java.security.MessageDigest
import java.io.OutputStream
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class Project(
    val width: Int,
    val height: Int,
    /** Height before any aspect-ratio change, for the "Original" option. */
    val originalHeight: Int = height,
    val background: Int = Color.WHITE,
    val layers: List<Layer> = emptyList(),
    val video: VideoClip? = null,
    /** Export size for this project, or null to use the app-wide default from Settings. */
    val exportSize: ExportSize? = null,
    /** Set from the home screen; null shows [defaultName]. */
    val name: String? = null,
    /** Photos only: the canvas shows the bottom picture blurred behind everything ([backdrop]) instead of [background]. */
    val blur: Boolean = false,
    /** The layer id of the photo the project was opened with, whose crop is the canvas's ([fittedTo]); null for blank canvases and videos. */
    val photoId: Long? = null,
) {
    /** The canvas cut to [photo]'s picture as placed, every layer kept at its spot on it (the "Original" aspect too). */
    fun fittedTo(photo: ImageLayer): Project {
        // A stretched photo keeps its stretch at scale 1, so the canvas is its stretched size.
        val (w, h) = fittedCanvas(photo.width * photo.t.stretch, photo.height, photo.t)
        return copy(width = w, height = h, originalHeight = h, layers = layers.map { it.withTransform(it.t.onFittedCanvas(photo.t, w, h)) })
    }

    /** Every look and color adjustment left out (image layers, clips, video layers): what Hold to compare shows. */
    fun withoutAdjust() = copy(
        layers = layers.map {
            when (it) {
                is ImageLayer -> it.copy(adjust = Adjust())
                is VideoLayer -> it.copy(adjust = Adjust())
                else -> it
            }
        },
        video = video?.withoutAdjust(),
    )

    /** [adjust] on layer [layerId] (an image or video layer) or, with none, on clip [clip]: a look shown while it's changed. */
    fun withAdjust(layerId: Long?, clip: Int, adjust: Adjust) = if (layerId != null) {
        copy(
            layers = layers.map {
                when {
                    it.id != layerId -> it
                    it is ImageLayer -> it.copy(adjust = adjust)
                    it is VideoLayer -> it.copy(adjust = adjust)
                    else -> it
                }
            },
        )
    } else {
        copy(video = video?.let { v -> v.copy(segments = v.segments.mapIndexed { i, s -> if (i == clip) s.copy(adjust = adjust) else s }) })
    }
}

data class SavedProject(val dir: File, val modified: Long, val thumb: Bitmap?, val isVideo: Boolean, val name: String? = null) {
    /** Folder names are creation timestamps (see [ProjectStore.create]). */
    fun label(words: LabelWords) = name ?: defaultName(isVideo, dir.name.toLongOrNull() ?: modified, words)
}

/**
 * One folder per project under [root]: `project.json`, a thumbnail, and every image the project uses
 * (photos, cutouts, web downloads), so images never land in the phone's gallery. Videos and music stay
 * where they are and are referenced by their content URI.
 */
class ProjectStore(private val root: File, private val fonts: () -> List<Font>) {

    /** A new, empty project folder; names are timestamps, bumped when several are made in the same millisecond. */
    fun create(): File {
        var stamp = System.currentTimeMillis()
        while (File(root, stamp.toString()).exists()) stamp++
        return File(root, stamp.toString()).also { it.mkdirs() }
    }

    /** Copies a whole project (images included) to a new folder. Videos and music are shared references, not copied. */
    fun duplicate(dir: File): File = create().also { dir.copyRecursively(it, overwrite = true) }

    fun list(): List<SavedProject> = root.listFiles().orEmpty()
        .mapNotNull { dir ->
            val json = File(dir, PROJECT)
            if (!json.exists()) return@mapNotNull null
            val j = runCatching { JSONObject(json.readText()) }.getOrNull()
            SavedProject(dir, json.lastModified(), BitmapFactory.decodeFile(File(dir, THUMB).path), j?.has("video") == true, j?.optString("name")?.ifEmpty { null })
        }
        .sortedByDescending { it.modified }

    /**
     * Sets or clears (null) a project's name in place, leaving every other field as written (even ones this version
     * doesn't know). Keeps the modified time: a rename isn't an edit, so it shouldn't jump to the top of "Recent".
     */
    fun rename(dir: File, name: String?) {
        val file = File(dir, PROJECT)
        val modified = file.lastModified()
        val j = JSONObject(file.readText())
        if (name == null) j.remove("name") else j.put("name", name)
        writeAtomic(file) { it.write(j.toString().toByteArray()) }
        file.setLastModified(modified)
    }

    /** Stores [bitmap] in the project folder and returns its file name. */
    fun writeImage(dir: File, bitmap: Bitmap): String {
        val alpha = bitmap.hasAlpha()
        val name = "${System.nanoTime()}.${if (alpha) "png" else "jpg"}"
        File(dir, name).outputStream().use { bitmap.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        return name
    }

    /** A new, not yet existing file in the project folder for a voice recording (AAC in MP4, so `.m4a`). */
    fun newVoiceFile(dir: File) = File(dir, "${System.nanoTime()}$VOICE_EXTENSION")

    /** The same for text to speech, which the engine writes as WAV. */
    fun newSpeechFile(dir: File) = File(dir, "${System.nanoTime()}$SPEECH_EXTENSION")

    /** Where the preview copy of the video at [uri] goes in the project folder (see `needsPreviewCopy`). */
    fun previewCopyFile(dir: File, uri: Uri): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$PREVIEW_COPY_PREFIX${hash.take(16)}.mp4")
    }

    /** Deletes images, voice recordings and preview copies nothing uses any more (after undo history is gone, so nothing can bring them back). */
    fun cleanUp(dir: File, project: Project) {
        val used = project.layers.filterIsInstance<ImageLayer>().flatMap { listOfNotNull(it.file, it.source) }.toSet() +
            project.video?.voices.orEmpty().map { it.file.name } +
            project.video?.segments.orEmpty().filter { it.isImage }.map { File(it.uri.path!!).name } +
            project.video?.segments.orEmpty().filter { !it.isImage }.map { previewCopyFile(dir, it.uri).name } + THUMB
        dir.listFiles().orEmpty()
            .filter { (it.name.endsWith(".png") || it.name.endsWith(".jpg") || it.name.endsWith(VOICE_EXTENSION) || it.name.endsWith(SPEECH_EXTENSION) || it.name.startsWith(PREVIEW_COPY_PREFIX)) && it.name !in used }
            .forEach { it.delete() }
    }

    fun save(dir: File, project: Project, thumb: Bitmap) {
        writeAtomic(File(dir, THUMB)) { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        writeAtomic(File(dir, PROJECT)) { it.write(project.toJson().toString().toByteArray()) }
    }

    fun load(dir: File): Project {
        val j = JSONObject(File(dir, PROJECT).readText())
        val layers = j.getJSONArray("layers").let { a -> (0 until a.length()).mapNotNull { layerFromJson(dir, a.getJSONObject(it)) } }
        return Project(
            width = j.getInt("width"),
            height = j.getInt("height"),
            originalHeight = j.getInt("originalHeight"),
            background = j.getInt("background"),
            layers = layers,
            // Projects from before the video canvas existed: the canvas was the video's own size.
            video = j.optJSONObject("video")?.let { clipFromJson(dir, it, j.getInt("width"), j.getInt("height")) },
            exportSize = j.optString("exportSize").ifEmpty { null }?.let { enumOr<ExportSize>(it, ExportSize.Original) },
            name = j.optString("name").ifEmpty { null },
            blur = j.optBoolean("blur"),
            photoId = if (j.has("photoId")) j.getLong("photoId") else null,
        )
    }

    private fun layerFromJson(dir: File, j: JSONObject): Layer? {
        val t = Transform(
            j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), j.getDouble("scale").toFloat(), j.getDouble("rotation").toFloat(),
            flipX = j.optBoolean("flipX"), flipY = j.optBoolean("flipY"), stretch = j.optDouble("stretch", 1.0).toFloat(),
        )
        val id = j.getLong("id")
        // Absent in projects saved before layers had timing: they show for the whole video.
        val start = j.optLong("startMs", 0)
        val end = j.optLong("endMs", Long.MAX_VALUE)
        val animIn = enumOr(j.optString("animIn"), Anim.None)
        val animOut = enumOr(j.optString("animOut"), Anim.None)
        val loop = enumOr(j.optString("loop"), Loop.None)
        // Absent before animation lengths were adjustable: each kind's own length.
        val inMs = if (j.has("animInMs")) j.getLong("animInMs") else null
        val outMs = if (j.has("animOutMs")) j.getLong("animOutMs") else null
        return when (j.getString("type")) {
            "image" -> {
                val file = j.getString("file")
                // A missing image drops that one layer rather than the whole project.
                BitmapFactory.decodeFile(File(dir, file).path)?.let {
                    ImageLayer(
                        id, t, it, file,
                        source = j.optString("source").ifEmpty { null },
                        sourceX = j.optInt("sourceX"),
                        sourceY = j.optInt("sourceY"),
                        adjust = adjustFromJson(j),
                        hidden = j.optBoolean("hidden"),
                        locked = j.optBoolean("locked"),
                        startMs = start,
                        endMs = end,
                        animIn = animIn,
                        animOut = animOut,
                        animLoop = loop,
                        opacity = j.optDouble("opacity", 1.0).toFloat(),
                        animInMs = inMs,
                        animOutMs = outMs,
                        outline = if (j.has("outline")) j.getInt("outline") else null,
                        outlineStyle = enumOr(j.optString("outlineStyle"), OutlineStyle.Solid),
                        blend = enumOr(j.optString("blend"), LayerBlend.Normal),
                        effect = Effect(
                            enumOr(j.optString("effect"), EffectKind.None),
                            j.optDouble("effectAmount", 0.5).toFloat(),
                            j.optInt("duotoneDark", DUOTONES[0].first),
                            j.optInt("duotoneLight", DUOTONES[0].second),
                        ),
                        outlineWidth = j.optDouble("outlineWidth", DEFAULT_OUTLINE_WIDTH.toDouble()).toFloat(),
                    )
                }
            }
            "text" -> TextLayer(
                id = id,
                t = t,
                text = j.getString("text"),
                // An imported font that was removed falls back to the default one.
                font = fonts().let { f -> f.firstOrNull { it.name == j.getString("font") } ?: f.first() },
                size = j.getDouble("size").toFloat(),
                color = j.getInt("color"),
                outline = if (j.has("outline")) j.getInt("outline") else null,
                box = if (j.has("box")) j.getInt("box") else null,
                boxOpacity = j.optDouble("boxOpacity", 1.0).toFloat(),
                bubble = enumOr(j.optString("bubble"), Bubble.Box),
                boxOutline = if (j.has("boxOutline")) j.getInt("boxOutline") else null,
                tail = if (j.has("tailX")) j.getDouble("tailX").toFloat() to j.getDouble("tailY").toFloat() else null,
                // Before shadow colors "shadow" was true or false.
                shadow = if (j.has("shadowColor")) j.getInt("shadowColor") else DEFAULT_SHADOW.takeIf { j.optBoolean("shadow") },
                shadowBlur = j.optDouble("shadowBlur", 0.08).toFloat(),
                shadowDistance = j.optDouble("shadowDistance", 0.04).toFloat(),
                outlineWidth = j.optDouble("outlineWidth", 0.16).toFloat(),
                bold = j.optBoolean("bold"),
                italic = j.optBoolean("italic"),
                underline = j.optBoolean("underline"),
                caps = j.optBoolean("caps"),
                spacing = j.optDouble("spacing", 0.0).toFloat(),
                align = runCatching { TextAlign.valueOf(j.optString("align")) }.getOrDefault(TextAlign.Center),
                glow = if (j.has("glow")) j.getInt("glow") else null,
                glowSize = j.optDouble("glowSize", 0.5).toFloat(),
                gradient = if (j.has("gradient")) j.getInt("gradient") else null,
                opacity = j.optDouble("opacity", 1.0).toFloat(),
                lineSpacing = j.optDouble("lineSpacing", 1.0).toFloat(),
                wrap = if (j.has("wrap")) j.getDouble("wrap").toFloat() else null,
                curve = j.optDouble("curve", 0.0).toFloat(),
                hidden = j.optBoolean("hidden"),
                locked = j.optBoolean("locked"),
                startMs = start,
                endMs = end,
                animIn = animIn,
                animOut = animOut,
                animLoop = loop,
                animInMs = inMs,
                animOutMs = outMs,
            )
            // Versions before video layers skip this type (the `else` below) rather than fail the whole project.
            "video" -> VideoLayer(
                id = id,
                t = t,
                uri = Uri.parse(j.getString("uri")),
                durationMs = j.getLong("durationMs"),
                videoWidth = j.getInt("videoWidth"),
                videoHeight = j.getInt("videoHeight"),
                volume = j.optDouble("volume", 0.0).toFloat(),
                hidden = j.optBoolean("hidden"),
                locked = j.optBoolean("locked"),
                startMs = start,
                endMs = end,
                opacity = j.optDouble("opacity", 1.0).toFloat(),
                clipStartMs = j.optLong("clipStartMs"),
                crop = cropFromJson(j),
                adjust = adjustFromJson(j),
                key = if (j.has("key")) j.getInt("key") else null,
                keyStrength = j.optDouble("keyStrength", DEFAULT_KEY_STRENGTH.toDouble()).toFloat(),
                keySoftness = j.optDouble("keySoftness", DEFAULT_KEY_SOFTNESS.toDouble()).toFloat(),
            )
            // Versions before drawings skip this type too.
            "draw" -> DrawLayer(
                id = id,
                t = t,
                strokes = j.getJSONArray("strokes").let { a ->
                    (0 until a.length()).map { i ->
                        val stroke = a.getJSONObject(i)
                        val p = stroke.getJSONArray("points")
                        PenStroke(stroke.getInt("color"), stroke.getDouble("width").toFloat(), FloatArray(p.length() / 2 * 2) { p.getDouble(it).toFloat() }, enumOr(stroke.optString("style"), PenStyle.Solid))
                    }
                },
                hidden = j.optBoolean("hidden"),
                locked = j.optBoolean("locked"),
                startMs = start,
                endMs = end,
            )
            // Versions before shapes skip this type too.
            "shape" -> ShapeLayer(
                id = id,
                t = t,
                kind = enumOr(j.optString("kind"), ShapeKind.Rect),
                w = j.getDouble("w").toFloat(),
                h = j.getDouble("h").toFloat(),
                fill = if (j.has("fill")) j.getInt("fill") else null,
                outline = if (j.has("outline")) j.getInt("outline") else null,
                stroke = j.optDouble("stroke", 0.0).toFloat(),
                opacity = j.optDouble("opacity", 1.0).toFloat(),
                hidden = j.optBoolean("hidden"),
                locked = j.optBoolean("locked"),
                startMs = start,
                endMs = end,
                animIn = animIn,
                animOut = animOut,
                animLoop = loop,
                animInMs = inMs,
                animOutMs = outMs,
            )
            // Versions before blur areas skip this type too.
            "censor" -> CensorLayer(
                id = id,
                t = t,
                w = j.getDouble("w").toFloat(),
                h = j.getDouble("h").toFloat(),
                kind = enumOr(j.optString("kind"), CensorKind.Blur),
                shape = enumOr(j.optString("shape"), CensorShape.Ellipse),
                strength = j.optDouble("strength", 0.5).toFloat(),
                hidden = j.optBoolean("hidden"),
                locked = j.optBoolean("locked"),
                startMs = start,
                endMs = end,
            )
            else -> null
        }
    }

    private fun clipFromJson(dir: File, j: JSONObject, projectWidth: Int, projectHeight: Int) = VideoClip(
        // Version 1 had one clip, its fields on the video itself.
        segments = j.optJSONArray("clips")?.let { a -> (0 until a.length()).map { segmentFromJson(dir, a.getJSONObject(it), projectWidth, projectHeight) } }
            ?: listOf(segmentFromJson(dir, j, projectWidth, projectHeight)),
        frameRate = j.optDouble("frameRate", 0.0).toFloat(),
        width = j.optInt("videoWidth", projectWidth),
        height = j.optInt("videoHeight", projectHeight),
        canvas = if (j.has("canvas")) j.getDouble("canvas").toFloat() else null,
        blur = j.optBoolean("blur"),
        blurStrength = j.optDouble("blurStrength", 1.0).toFloat(),
        placement = ClipPlacement(j.optDouble("clipZoom", 1.0).toFloat(), j.optDouble("clipX", 0.0).toFloat(), j.optDouble("clipY", 0.0).toFloat()),
        // Before version 3 a project had at most one song, from the start of the video to its end.
        music = (j.optJSONArray("music")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: listOfNotNull(j.optJSONObject("music"))).map {
            Music(Uri.parse(it.getString("uri")), it.getString("name"), it.getLong("durationMs"), it.getLong("startMs"), audioPartFromJson(it, it.optLong("atMs"), Long.MAX_VALUE))
        },
        // A missing recording drops only that voice.
        voices = j.optJSONArray("voices")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val v = a.getJSONObject(i)
                val durationMs = v.getLong("durationMs")
                File(dir, v.getString("file")).takeIf { it.exists() }?.let {
                    VoiceClip(it, durationMs, audioPartFromJson(v, v.getLong("startMs"), durationMs), enumOr(v.optString("effect"), VoiceEffect.Normal))
                }
            }
        }.orEmpty(),
    )

    /** [atMs] and the [defaultLengthMs] of a part from before version 3 come from the music's or voice's own fields. */
    private fun audioPartFromJson(j: JSONObject, atMs: Long, defaultLengthMs: Long) = AudioPart(
        atMs = atMs,
        offsetMs = j.optLong("offsetMs"),
        lengthMs = j.optLong("lengthMs", defaultLengthMs),
        volume = j.optDouble("volume", 1.0).toFloat(),
        // Before version 3 music faded in and out over MUSIC_FADE_MS or not at all.
        fadeInMs = j.optLong("fadeInMs", if (j.optBoolean("fadeIn")) MUSIC_FADE_MS else 0),
        fadeOutMs = j.optLong("fadeOutMs", if (j.optBoolean("fadeOut")) MUSIC_FADE_MS else 0),
    )

    private fun segmentFromJson(dir: File, j: JSONObject, projectWidth: Int, projectHeight: Int) = Segment(
        // A photo is stored by name: the folder moves with the project (duplicates load it from their own copy).
        uri = if (j.has("file")) Uri.fromFile(File(dir, j.getString("file"))) else Uri.parse(j.getString("uri")),
        durationMs = j.getLong("durationMs"),
        width = j.optInt("videoWidth", projectWidth),
        height = j.optInt("videoHeight", projectHeight),
        startMs = j.getLong("startMs"),
        endMs = j.getLong("endMs"),
        speed = j.getDouble("speed").toFloat(),
        volume = j.getDouble("volume").toFloat(),
        keepPitch = j.optBoolean("keepPitch", true),
        adjust = adjustFromJson(j),
        isImage = j.has("file"),
        transition = Transition(
            TransitionKind.entries.firstOrNull { it.name == j.optString("transition") } ?: TransitionKind.None,
            j.optLong("transitionMs", DEFAULT_TRANSITION_MS),
        ),
        crop = cropFromJson(j),
        flipX = j.optBoolean("flipX"),
        flipY = j.optBoolean("flipY"),
        turns = j.optInt("turns"),
        animIn = clipAnimFromJson(j, "animIn"),
        animOut = clipAnimFromJson(j, "animOut"),
    )

    private companion object {
        const val PROJECT = "project.json"
        const val THUMB = "thumb.jpg"
        const val VOICE_EXTENSION = ".m4a"
        const val SPEECH_EXTENSION = ".wav"
        const val PREVIEW_COPY_PREFIX = "preview_"
    }
}

private fun Project.toJson() = JSONObject().apply {
    // 2: the video's clips are a list ("clips"); version 1 had the one clip's fields on the video.
    // 3: music is a list of parts ("music" an array), music and voices have offsets, lengths and fade lengths.
    // 4: "photoId", the photo the project was opened with (absent before: no layer crops the canvas).
    // 5: "censor" layers (blur areas); optional fields: text curve and gradient, layer stretch, image outline, effect and
    // blend, pen styles, adjust vignette and grain, video layer key, clip crop, flips, turns and animations, the video
    // canvas's blur strength and clip placement. All read with defaults, so a version 4 file reads the same as before.
    put("version", 5)
    put("width", width)
    put("height", height)
    put("originalHeight", originalHeight)
    put("background", background)
    put("layers", JSONArray(layers.map { it.toJson() }))
    video?.let { put("video", it.toJson()) }
    exportSize?.let { put("exportSize", it.name) }
    name?.let { put("name", it) }
    if (blur) put("blur", true)
    photoId?.let { put("photoId", it) }
}

private fun Layer.toJson() = JSONObject().apply {
    put("id", id)
    put("x", t.x.toDouble())
    put("y", t.y.toDouble())
    put("scale", t.scale.toDouble())
    put("rotation", t.rotation.toDouble())
    if (t.flipX) put("flipX", true)
    if (t.flipY) put("flipY", true)
    if (t.stretch != 1f) put("stretch", t.stretch.toDouble())
    put("startMs", startMs)
    put("endMs", endMs)
    put("hidden", hidden)
    put("locked", locked)
    if (animIn != Anim.None) put("animIn", animIn.name)
    if (animOut != Anim.None) put("animOut", animOut.name)
    if (animLoop != Loop.None) put("loop", animLoop.name)
    animInMs?.let { put("animInMs", it) }
    animOutMs?.let { put("animOutMs", it) }
    when (val layer = this@toJson) {
        is ImageLayer -> {
            put("type", "image")
            if (layer.blend != LayerBlend.Normal) put("blend", layer.blend.name)
            if (layer.effect.kind != EffectKind.None) {
                put("effect", layer.effect.kind.name)
                put("effectAmount", layer.effect.amount.toDouble())
                put("duotoneDark", layer.effect.dark)
                put("duotoneLight", layer.effect.light)
            }
            put("file", layer.file)
            putAdjust(layer.adjust)
            put("opacity", layer.opacity.toDouble())
            layer.outline?.let {
                put("outline", it)
                put("outlineStyle", layer.outlineStyle.name)
                put("outlineWidth", layer.outlineWidth.toDouble())
            }
            layer.source?.let {
                put("source", it)
                put("sourceX", layer.sourceX)
                put("sourceY", layer.sourceY)
            }
        }
        is TextLayer -> {
            put("type", "text")
            put("text", layer.text)
            put("font", layer.font.name)
            put("size", layer.size.toDouble())
            put("color", layer.color)
            layer.outline?.let { put("outline", it) }
            layer.box?.let { put("box", it) }
            put("boxOpacity", layer.boxOpacity.toDouble())
            put("bubble", layer.bubble.name)
            layer.boxOutline?.let { put("boxOutline", it) }
            layer.tail?.let { (x, y) ->
                put("tailX", x.toDouble())
                put("tailY", y.toDouble())
            }
            put("shadow", layer.shadow != null)
            layer.shadow?.let { put("shadowColor", it) }
            put("shadowBlur", layer.shadowBlur.toDouble())
            put("shadowDistance", layer.shadowDistance.toDouble())
            put("outlineWidth", layer.outlineWidth.toDouble())
            put("bold", layer.bold)
            put("italic", layer.italic)
            put("underline", layer.underline)
            put("caps", layer.caps)
            put("spacing", layer.spacing.toDouble())
            put("align", layer.align.name)
            layer.glow?.let { put("glow", it) }
            layer.gradient?.let { put("gradient", it) }
            put("glowSize", layer.glowSize.toDouble())
            put("opacity", layer.opacity.toDouble())
            put("lineSpacing", layer.lineSpacing.toDouble())
            layer.wrap?.let { put("wrap", it.toDouble()) }
            if (layer.curve != 0f) put("curve", layer.curve.toDouble())
        }
        is VideoLayer -> {
            put("type", "video")
            put("uri", layer.uri.toString())
            put("durationMs", layer.durationMs)
            put("videoWidth", layer.videoWidth)
            put("videoHeight", layer.videoHeight)
            put("volume", layer.volume.toDouble())
            put("opacity", layer.opacity.toDouble())
            put("clipStartMs", layer.clipStartMs)
            putAdjust(layer.adjust)
            putCrop(layer.crop)
            layer.key?.let {
                put("key", it)
                put("keyStrength", layer.keyStrength.toDouble())
                put("keySoftness", layer.keySoftness.toDouble())
            }
        }
        is DrawLayer -> {
            put("type", "draw")
            put("strokes", JSONArray(layer.strokes.map { stroke ->
                JSONObject().apply {
                    put("color", stroke.color)
                    put("width", stroke.width.toDouble())
                    if (stroke.style != PenStyle.Solid) put("style", stroke.style.name)
                    // Flat x, y list rounded to a tenth of a pixel: finer than anyone can see, and keeps the file small.
                    put("points", JSONArray(stroke.points.map { (it * 10).roundToInt() / 10.0 }))
                }
            }))
        }
        is ShapeLayer -> {
            put("type", "shape")
            put("kind", layer.kind.name)
            put("w", layer.w.toDouble())
            put("h", layer.h.toDouble())
            layer.fill?.let { put("fill", it) }
            layer.outline?.let { put("outline", it) }
            put("stroke", layer.stroke.toDouble())
            put("opacity", layer.opacity.toDouble())
        }
        is CensorLayer -> {
            put("type", "censor")
            put("w", layer.w.toDouble())
            put("h", layer.h.toDouble())
            put("kind", layer.kind.name)
            put("shape", layer.shape.name)
            put("strength", layer.strength.toDouble())
        }
    }
}

private fun Segment.toJson() = JSONObject().apply {
    if (isImage) put("file", File(uri.path!!).name) else put("uri", uri.toString())
    put("durationMs", durationMs)
    put("videoWidth", width)
    put("videoHeight", height)
    put("startMs", startMs)
    put("endMs", endMs)
    put("speed", speed.toDouble())
    put("volume", volume.toDouble())
    put("keepPitch", keepPitch)
    putAdjust(adjust)
    if (crop != CropRect()) putCrop(crop)
    if (flipX) put("flipX", true)
    if (flipY) put("flipY", true)
    if (turns != 0) put("turns", turns)
    if (transition.kind != TransitionKind.None) {
        put("transition", transition.kind.name)
        put("transitionMs", transition.durationMs)
    }
    putClipAnim("animIn", animIn)
    putClipAnim("animOut", animOut)
}

private fun JSONObject.putClipAnim(key: String, a: ClipAnimation) {
    if (a.kind == ClipAnimKind.None) return
    put(key, a.kind.name)
    put(key + "Ms", a.durationMs)
}

private fun clipAnimFromJson(j: JSONObject, key: String) =
    ClipAnimation(ClipAnimKind.entries.firstOrNull { it.name == j.optString(key) } ?: ClipAnimKind.None, j.optLong(key + "Ms", DEFAULT_CLIP_ANIM_MS))

private fun VideoClip.toJson() = JSONObject().apply {
    put("clips", JSONArray(segments.map { it.toJson() }))
    put("frameRate", frameRate.toDouble())
    put("videoWidth", width)
    put("videoHeight", height)
    canvas?.let { put("canvas", it.toDouble()) }
    put("blur", blur)
    put("blurStrength", blurStrength.toDouble())
    put("clipZoom", placement.zoom.toDouble())
    put("clipX", placement.x.toDouble())
    put("clipY", placement.y.toDouble())
    if (music.isNotEmpty()) put("music", JSONArray(music.map { m ->
        JSONObject().apply {
            put("uri", m.uri.toString())
            put("name", m.name)
            put("durationMs", m.durationMs)
            put("startMs", m.startMs)
            put("atMs", m.part.atMs)
            putAudioPart(m.part)
        }
    }))
    if (voices.isNotEmpty()) put("voices", JSONArray(voices.map { v ->
        JSONObject().apply {
            // Only the name: the folder moves with the project (duplicates load it from their own copy).
            put("file", v.file.name)
            put("startMs", v.part.atMs)
            put("durationMs", v.durationMs)
            if (v.effect != VoiceEffect.Normal) put("effect", v.effect.name)
            putAudioPart(v.part)
        }
    }))
}

private fun JSONObject.putAudioPart(p: AudioPart) {
    put("offsetMs", p.offsetMs)
    put("lengthMs", p.lengthMs)
    put("volume", p.volume.toDouble())
    put("fadeInMs", p.fadeInMs)
    put("fadeOutMs", p.fadeOutMs)
}

private fun JSONObject.putCrop(c: CropRect) = put("crop", JSONArray(listOf(c.left.toDouble(), c.top.toDouble(), c.right.toDouble(), c.bottom.toDouble())))

private fun cropFromJson(j: JSONObject) =
    j.optJSONArray("crop")?.let { c -> CropRect(c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), c.getDouble(2).toFloat(), c.getDouble(3).toFloat()) } ?: CropRect()

private fun adjustFromJson(j: JSONObject) = Adjust(
    j.optDouble("brightness", 0.0).toFloat(),
    j.optDouble("contrast", 0.0).toFloat(),
    j.optDouble("saturation", 0.0).toFloat(),
    runCatching { Look.valueOf(j.optString("look")) }.getOrDefault(Look.None),
    j.optDouble("lookAmount", 1.0).toFloat(),
    warmth = j.optDouble("warmth", 0.0).toFloat(),
    tint = j.optDouble("tint", 0.0).toFloat(),
    hue = j.optDouble("hue", 0.0).toFloat(),
    fade = j.optDouble("fade", 0.0).toFloat(),
    vignette = j.optDouble("vignette", 0.0).toFloat(),
    grain = j.optDouble("grain", 0.0).toFloat(),
)

private fun JSONObject.putAdjust(a: Adjust) {
    put("brightness", a.brightness.toDouble())
    put("contrast", a.contrast.toDouble())
    put("saturation", a.saturation.toDouble())
    put("look", a.look.name)
    put("lookAmount", a.amount.toDouble())
    put("warmth", a.warmth.toDouble())
    put("tint", a.tint.toDouble())
    put("hue", a.hue.toDouble())
    put("fade", a.fade.toDouble())
    put("vignette", a.vignette.toDouble())
    put("grain", a.grain.toDouble())
}

/** Write-then-rename, so a crash mid-save never leaves a half-written project. */
private fun writeAtomic(file: File, write: (OutputStream) -> Unit) {
    val tmp = File(file.path + ".tmp")
    // Sync before the rename, so a power cut can't leave an empty project.json behind the new name.
    java.io.FileOutputStream(tmp).use { out ->
        write(out)
        out.fd.sync()
    }
    if (!tmp.renameTo(file)) error("Could not save ${file.name}")
}
