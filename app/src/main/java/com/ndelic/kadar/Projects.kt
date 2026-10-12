package com.ndelic.kadar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

data class Project(
    val width: Int,
    val height: Int,
    /** Height before any aspect-ratio change, for the "Original" option. */
    val originalHeight: Int = height,
    val background: Int = Color.WHITE,
    val layers: List<Layer> = emptyList(),
    val video: VideoClip? = null,
)

data class SavedProject(val dir: File, val modified: Long, val thumb: Bitmap?, val isVideo: Boolean)

/**
 * One folder per project under [root]: `project.json`, a thumbnail, and every image the project uses
 * (photos, cutouts, web downloads), so images never land in the phone's gallery. Videos and music stay
 * where they are and are referenced by their content URI.
 */
class ProjectStore(private val root: File, private val fonts: List<Font>) {

    fun create(): File = File(root, System.currentTimeMillis().toString()).also { it.mkdirs() }

    fun list(): List<SavedProject> = root.listFiles().orEmpty()
        .mapNotNull { dir ->
            val json = File(dir, PROJECT)
            if (!json.exists()) return@mapNotNull null
            val isVideo = runCatching { JSONObject(json.readText()).has("video") }.getOrDefault(false)
            SavedProject(dir, json.lastModified(), BitmapFactory.decodeFile(File(dir, THUMB).path), isVideo)
        }
        .sortedByDescending { it.modified }

    /** Stores [bitmap] in the project folder and returns its file name. */
    fun writeImage(dir: File, bitmap: Bitmap): String {
        val alpha = bitmap.hasAlpha()
        val name = "${System.nanoTime()}.${if (alpha) "png" else "jpg"}"
        File(dir, name).outputStream().use { bitmap.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        return name
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
            video = j.optJSONObject("video")?.let(::clipFromJson),
        )
    }

    private fun layerFromJson(dir: File, j: JSONObject): Layer? {
        val t = Transform(j.getDouble("x").toFloat(), j.getDouble("y").toFloat(), j.getDouble("scale").toFloat(), j.getDouble("rotation").toFloat())
        val id = j.getLong("id")
        // Absent in projects saved before layers had timing: they show for the whole video.
        val start = j.optLong("startMs", 0)
        val end = j.optLong("endMs", Long.MAX_VALUE)
        return when (j.getString("type")) {
            "image" -> {
                val file = j.getString("file")
                // A missing image drops that one layer rather than the whole project.
                BitmapFactory.decodeFile(File(dir, file).path)?.let { ImageLayer(id, t, it, file, start, end) }
            }
            "text" -> TextLayer(
                id = id,
                t = t,
                text = j.getString("text"),
                font = fonts.firstOrNull { it.name == j.getString("font") } ?: fonts.first(),
                size = j.getDouble("size").toFloat(),
                color = j.getInt("color"),
                outline = if (j.has("outline")) j.getInt("outline") else null,
                startMs = start,
                endMs = end,
            )
            else -> null
        }
    }

    private fun clipFromJson(j: JSONObject) = VideoClip(
        uri = Uri.parse(j.getString("uri")),
        durationMs = j.getLong("durationMs"),
        startMs = j.getLong("startMs"),
        endMs = j.getLong("endMs"),
        speed = j.getDouble("speed").toFloat(),
        volume = j.getDouble("volume").toFloat(),
        music = j.optJSONObject("music")?.let {
            Music(Uri.parse(it.getString("uri")), it.getString("name"), it.getLong("durationMs"), it.getLong("startMs"), it.getDouble("volume").toFloat())
        },
    )

    private companion object {
        const val PROJECT = "project.json"
        const val THUMB = "thumb.jpg"
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
}

private fun Layer.toJson() = JSONObject().apply {
    put("id", id)
    put("x", t.x.toDouble())
    put("y", t.y.toDouble())
    put("scale", t.scale.toDouble())
    put("rotation", t.rotation.toDouble())
    put("startMs", startMs)
    put("endMs", endMs)
    when (val layer = this@toJson) {
        is ImageLayer -> {
            put("type", "image")
            put("file", layer.file)
        }
        is TextLayer -> {
            put("type", "text")
            put("text", layer.text)
            put("font", layer.font.name)
            put("size", layer.size.toDouble())
            put("color", layer.color)
            layer.outline?.let { put("outline", it) }
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
    music?.let { m ->
        put("music", JSONObject().apply {
            put("uri", m.uri.toString())
            put("name", m.name)
            put("durationMs", m.durationMs)
            put("startMs", m.startMs)
            put("volume", m.volume.toDouble())
        })
    }
}

/** Write-then-rename, so a crash mid-save never leaves a half-written project. */
private fun writeAtomic(file: File, write: (OutputStream) -> Unit) {
    val tmp = File(file.path + ".tmp")
    tmp.outputStream().use(write)
    if (!tmp.renameTo(file)) error("Could not save ${file.name}")
}
