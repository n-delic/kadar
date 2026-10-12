package com.ndelic.kadar.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.ndelic.kadar.video.Music
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.VoiceClip
import java.io.File
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
)

data class SavedProject(val dir: File, val modified: Long, val thumb: Bitmap?, val isVideo: Boolean, val name: String? = null) {
    /** Folder names are creation timestamps (see [ProjectStore.create]). */
    fun label(words: LabelWords) = name ?: defaultName(isVideo, dir.name.toLongOrNull() ?: modified, words)
}

/**
 * One folder per project under [root]: `project.json`, a thumbnail, and every image the project uses
 * (photos, cutouts, web downloads), so images never land in the phone's gallery. Videos and music stay
 * where they are and are referenced by their content URI.
 */
class ProjectStore(private val root: File, private val fonts: List<Font>) {

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

    /** Deletes images and voice recordings nothing uses any more (after undo history is gone, so nothing can bring them back). */
    fun cleanUp(dir: File, project: Project) {
        val used = project.layers.filterIsInstance<ImageLayer>().flatMap { listOfNotNull(it.file, it.source) }.toSet() +
            project.video?.voices.orEmpty().map { it.file.name } + THUMB
        dir.listFiles().orEmpty()
            .filter { (it.name.endsWith(".png") || it.name.endsWith(".jpg") || it.name.endsWith(VOICE_EXTENSION)) && it.name !in used }
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
        )
    }

    private fun layerFromJson(dir: File, j: JSONObject): Layer? {
        val t = Transform(j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), j.getDouble("scale").toFloat(), j.getDouble("rotation").toFloat())
        val id = j.getLong("id")
        // Absent in projects saved before layers had timing: they show for the whole video.
        val start = j.optLong("startMs", 0)
        val end = j.optLong("endMs", Long.MAX_VALUE)
        val animIn = enumOr(j.optString("animIn"), Anim.None)
        val animOut = enumOr(j.optString("animOut"), Anim.None)
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
                    )
                }
            }
            "text" -> TextLayer(
                id = id,
                t = t,
                text = j.getString("text"),
                font = fonts.firstOrNull { it.name == j.getString("font") } ?: fonts.first(),
                size = j.getDouble("size").toFloat(),
                color = j.getInt("color"),
                outline = if (j.has("outline")) j.getInt("outline") else null,
                box = if (j.has("box")) j.getInt("box") else null,
                shadow = j.optBoolean("shadow"),
                spacing = j.optDouble("spacing", 0.0).toFloat(),
                align = runCatching { TextAlign.valueOf(j.optString("align")) }.getOrDefault(TextAlign.Center),
                hidden = j.optBoolean("hidden"),
                locked = j.optBoolean("locked"),
                startMs = start,
                endMs = end,
                animIn = animIn,
                animOut = animOut,
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
                crop = j.optJSONArray("crop")?.let { c -> CropRect(c.getDouble(0).toFloat(), c.getDouble(1).toFloat(), c.getDouble(2).toFloat(), c.getDouble(3).toFloat()) } ?: CropRect(),
            )
            // Versions before drawings skip this type too.
            "draw" -> DrawLayer(
                id = id,
                t = t,
                strokes = j.getJSONArray("strokes").let { a ->
                    (0 until a.length()).map { i ->
                        val stroke = a.getJSONObject(i)
                        val p = stroke.getJSONArray("points")
                        PenStroke(stroke.getInt("color"), stroke.getDouble("width").toFloat(), FloatArray(p.length() / 2 * 2) { p.getDouble(it).toFloat() })
                    }
                },
                hidden = j.optBoolean("hidden"),
                locked = j.optBoolean("locked"),
                startMs = start,
                endMs = end,
            )
            else -> null
        }
    }

    private fun clipFromJson(dir: File, j: JSONObject, projectWidth: Int, projectHeight: Int) = VideoClip(
        uri = Uri.parse(j.getString("uri")),
        durationMs = j.getLong("durationMs"),
        startMs = j.getLong("startMs"),
        endMs = j.getLong("endMs"),
        speed = j.getDouble("speed").toFloat(),
        volume = j.getDouble("volume").toFloat(),
        keepPitch = j.optBoolean("keepPitch", true),
        frameRate = j.optDouble("frameRate", 0.0).toFloat(),
        width = j.optInt("videoWidth", projectWidth),
        height = j.optInt("videoHeight", projectHeight),
        canvas = if (j.has("canvas")) j.getDouble("canvas").toFloat() else null,
        blur = j.optBoolean("blur"),
        adjust = adjustFromJson(j),
        music = j.optJSONObject("music")?.let {
            Music(
                Uri.parse(it.getString("uri")), it.getString("name"), it.getLong("durationMs"), it.getLong("startMs"), it.getDouble("volume").toFloat(),
                fadeIn = it.optBoolean("fadeIn"),
                fadeOut = it.optBoolean("fadeOut"),
            )
        },
        // A missing recording drops only that voice.
        voices = j.optJSONArray("voices")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val v = a.getJSONObject(i)
                File(dir, v.getString("file")).takeIf { it.exists() }?.let { VoiceClip(it, v.getLong("startMs"), v.getLong("durationMs"), v.optDouble("volume", 1.0).toFloat()) }
            }
        }.orEmpty(),
    )

    private companion object {
        const val PROJECT = "project.json"
        const val THUMB = "thumb.jpg"
        const val VOICE_EXTENSION = ".m4a"
    }
}

private fun Project.toJson() = JSONObject().apply {
    put("version", 1)
    put("width", width)
    put("height", height)
    put("originalHeight", originalHeight)
    put("background", background)
    put("layers", JSONArray(layers.map { it.toJson() }))
    video?.let { put("video", it.toJson()) }
    exportSize?.let { put("exportSize", it.name) }
    name?.let { put("name", it) }
}

private fun Layer.toJson() = JSONObject().apply {
    put("id", id)
    put("x", t.x.toDouble())
    put("y", t.y.toDouble())
    put("scale", t.scale.toDouble())
    put("rotation", t.rotation.toDouble())
    put("startMs", startMs)
    put("endMs", endMs)
    put("hidden", hidden)
    put("locked", locked)
    if (animIn != Anim.None) put("animIn", animIn.name)
    if (animOut != Anim.None) put("animOut", animOut.name)
    when (val layer = this@toJson) {
        is ImageLayer -> {
            put("type", "image")
            put("file", layer.file)
            putAdjust(layer.adjust)
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
            put("shadow", layer.shadow)
            put("spacing", layer.spacing.toDouble())
            put("align", layer.align.name)
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
            layer.crop.run { put("crop", JSONArray(listOf(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble()))) }
        }
        is DrawLayer -> {
            put("type", "draw")
            put("strokes", JSONArray(layer.strokes.map { stroke ->
                JSONObject().apply {
                    put("color", stroke.color)
                    put("width", stroke.width.toDouble())
                    // Flat x, y list rounded to a tenth of a pixel: finer than anyone can see, and keeps the file small.
                    put("points", JSONArray(stroke.points.map { (it * 10).roundToInt() / 10.0 }))
                }
            }))
        }
    }
}

private fun VideoClip.toJson() = JSONObject().apply {
    put("uri", uri.toString())
    put("durationMs", durationMs)
    put("startMs", startMs)
    put("endMs", endMs)
    put("speed", speed.toDouble())
    put("volume", volume.toDouble())
    put("keepPitch", keepPitch)
    put("frameRate", frameRate.toDouble())
    put("videoWidth", width)
    put("videoHeight", height)
    canvas?.let { put("canvas", it.toDouble()) }
    put("blur", blur)
    putAdjust(adjust)
    music?.let { m ->
        put("music", JSONObject().apply {
            put("uri", m.uri.toString())
            put("name", m.name)
            put("durationMs", m.durationMs)
            put("startMs", m.startMs)
            put("volume", m.volume.toDouble())
            put("fadeIn", m.fadeIn)
            put("fadeOut", m.fadeOut)
        })
    }
    if (voices.isNotEmpty()) put("voices", JSONArray(voices.map { v ->
        JSONObject().apply {
            // Only the name: the folder moves with the project (duplicates load it from their own copy).
            put("file", v.file.name)
            put("startMs", v.startMs)
            put("durationMs", v.durationMs)
            put("volume", v.volume.toDouble())
        }
    }))
}

private fun adjustFromJson(j: JSONObject) = Adjust(
    j.optDouble("brightness", 0.0).toFloat(),
    j.optDouble("contrast", 0.0).toFloat(),
    j.optDouble("saturation", 0.0).toFloat(),
    runCatching { Look.valueOf(j.optString("look")) }.getOrDefault(Look.None),
    j.optDouble("lookAmount", 1.0).toFloat(),
)

private fun JSONObject.putAdjust(a: Adjust) {
    put("brightness", a.brightness.toDouble())
    put("contrast", a.contrast.toDouble())
    put("saturation", a.saturation.toDouble())
    put("look", a.look.name)
    put("lookAmount", a.amount.toDouble())
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
