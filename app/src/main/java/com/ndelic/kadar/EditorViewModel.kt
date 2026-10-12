package com.ndelic.kadar

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Sticker(val file: File, val thumb: Bitmap)

data class EditorUiState(
    val projects: List<SavedProject> = emptyList(),
    val projectDir: File? = null,
    val project: Project? = null,
    val selectedId: Long? = null,
    /** Label of the running long task; the UI blocks input while it is set. */
    val busy: String? = null,
    val stickers: List<Sticker> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Evenly spaced frames of the video, for the timeline. */
    val filmstrip: List<Bitmap> = emptyList(),
) {
    val selected get() = project?.layers?.firstOrNull { it.id == selectedId }
}

sealed interface EditorEffect {
    data class Message(val text: String) : EditorEffect
    /** [share] opens the share sheet right away instead of offering it. */
    data class Saved(val uri: Uri, val mime: String, val share: Boolean) : EditorEffect
}

class EditorViewModel(private val app: Application) : AndroidViewModel(app) {

    val fonts = listOf(
        Font("Meme", app.resources.getFont(R.font.anton)),
        Font("Sans", Typeface.DEFAULT_BOLD),
        Font("Serif", Typeface.create(Typeface.SERIF, Typeface.BOLD)),
        Font("Mono", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
        Font("Script", Typeface.create("cursive", Typeface.NORMAL)),
    )

    private val store = ProjectStore(File(app.filesDir, "projects"), fonts)
    private val stickerDir = File(app.filesDir, "stickers")
    private val saveLock = Mutex()
    private var videoFrame: Bitmap? = null
    /** What's on disk for the open project, so just opening one doesn't rewrite it (and bump it to the top). */
    private var lastSaved: Project? = null
    private var nextId = 1L
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refreshProjects()
        loadStickers()
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            uiState.map { it.projectDir to it.project }.distinctUntilChanged().debounce(800).collect { (dir, project) ->
                if (dir != null && project != null && project != lastSaved) save(dir, project)
            }
        }
    }

    // Projects

    fun newPhoto(uri: Uri) = work("Opening photo") { newPhoto(withContext(Dispatchers.IO) { loadBitmap(app, uri, MAX_PHOTO) }) }

    fun newPhotoFromUrl(url: String) = work("Downloading") { newPhoto(withContext(Dispatchers.IO) { downloadImage(url, MAX_PHOTO) }) }

    private suspend fun newPhoto(bitmap: Bitmap) {
        val dir = store.create()
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        val layer = ImageLayer(1, Transform(bitmap.width / 2f, bitmap.height / 2f), bitmap, file)
        open(dir, Project(bitmap.width, bitmap.height, layers = listOf(layer)))
    }

    fun newBlank() = open(store.create(), Project(1080, 1080))

    fun newVideo(uri: Uri) = work("Opening video") {
        keepAccess(uri)
        val (info, frame) = withContext(Dispatchers.IO) { mediaInfo(app, uri) to videoFrame(app, uri, 0) }
        if (info.width == 0 || info.durationMs == 0L) error("Can't read this video")
        open(store.create(), Project(info.width, info.height, background = Color.BLACK, video = VideoClip(uri, info.durationMs)), frame)
    }

    fun openProject(dir: File) = work("Opening") {
        val project = withContext(Dispatchers.IO) { store.load(dir) }
        val frame = project.video?.let { v -> withContext(Dispatchers.IO) { runCatching { videoFrame(app, v.uri, v.startMs) }.getOrNull() } }
        if (project.video != null && frame == null) _effects.send(EditorEffect.Message("This project's video is no longer on the phone"))
        open(dir, project, frame, saved = true)
    }

    fun deleteProject(dir: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dir.deleteRecursively() }
            refreshProjects()
        }
    }

    /** Saves right away (the autosave is debounced) and returns to the project list. */
    fun close() {
        val s = _uiState.value
        if (s.busy != null) return
        viewModelScope.launch {
            if (s.projectDir != null && s.project != null && s.project != lastSaved) save(s.projectDir, s.project)
            _uiState.update { it.copy(projectDir = null, project = null, selectedId = null) }
            refreshProjects()
        }
    }

    private fun open(dir: File, project: Project, frame: Bitmap? = null, saved: Boolean = false) {
        videoFrame = frame
        lastSaved = if (saved) project else null
        nextId = (project.layers.maxOfOrNull { it.id } ?: 0) + 1
        undoStack.clear()
        redoStack.clear()
        _uiState.update { it.copy(projectDir = dir, project = project, selectedId = null, canUndo = false, canRedo = false, filmstrip = emptyList()) }
        project.video?.let { v ->
            viewModelScope.launch {
                val frames = withContext(Dispatchers.IO) {
                    runCatching { (0 until FILMSTRIP_FRAMES).mapNotNull { videoFrame(app, v.uri, v.durationMs * it / FILMSTRIP_FRAMES) } }.getOrDefault(emptyList())
                }
                _uiState.update { if (it.projectDir == dir) it.copy(filmstrip = frames) else it }
            }
        }
    }

    // Undo

    fun undo() = step(from = undoStack, to = redoStack)

    fun redo() = step(from = redoStack, to = undoStack)

    private fun step(from: ArrayDeque<Project>, to: ArrayDeque<Project>) {
        val current = _uiState.value.project ?: return
        val target = from.removeLastOrNull() ?: return
        to.addLast(current)
        _uiState.update { it.copy(project = target, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    private suspend fun save(dir: File, project: Project) {
        val thumb = renderLayers(project.width, project.height, project.background, project.layers, THUMB_SIZE / max(project.width, project.height), videoFrame)
        try {
            saveLock.withLock { withContext(Dispatchers.IO) { store.save(dir, project, thumb) } }
            lastSaved = project
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _effects.send(EditorEffect.Message("Could not save the project: ${e.message}"))
        }
    }

    private fun refreshProjects() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { store.list() }
            _uiState.update { it.copy(projects = list) }
        }
    }

    /** Photo picker and document URIs expire when the app closes unless we keep the grant; saved projects need them later. */
    private fun keepAccess(uri: Uri) = runCatching {
        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // Layers

    fun select(id: Long?) = _uiState.update { it.copy(selectedId = id) }

    fun addText(text: String, color: Int) {
        val p = _uiState.value.project ?: return
        // Black text gets a white outline so it stays readable; everything else gets the classic black one.
        val outline = if (color == Color.BLACK) Color.WHITE else Color.BLACK
        addLayer { TextLayer(it, Transform(p.width / 2f, p.height / 2f), text, fonts.first(), min(p.width, p.height) * 0.1f, color, outline) }
    }

    fun addEmoji(emoji: String) {
        val p = _uiState.value.project ?: return
        addLayer { TextLayer(it, Transform(p.width / 2f, p.height / 2f), emoji, fonts[1], min(p.width, p.height) * 0.2f, Color.WHITE, null) }
    }

    fun addImage(uri: Uri) = work("Adding image") { addBitmapLayer(withContext(Dispatchers.IO) { loadBitmap(app, uri, MAX_LAYER) }) }

    fun addImageFromUrl(url: String) = work("Downloading") { addBitmapLayer(withContext(Dispatchers.IO) { downloadImage(url, MAX_LAYER) }) }

    fun addSticker(sticker: Sticker) = work("Adding sticker") {
        addBitmapLayer(withContext(Dispatchers.IO) { BitmapFactory.decodeFile(sticker.file.path) ?: error("This sticker is damaged") })
    }

    private suspend fun addBitmapLayer(bitmap: Bitmap) {
        val s = _uiState.value
        val p = s.project ?: return
        val dir = s.projectDir ?: return
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        val fit = min(p.width * 0.6f / bitmap.width, p.height * 0.6f / bitmap.height)
        addLayer { ImageLayer(it, Transform(p.width / 2f, p.height / 2f, fit), bitmap, file) }
    }

    private fun addLayer(make: (Long) -> Layer) {
        val layer = make(nextId++)
        updateProject { it.copy(layers = it.layers + layer) }
        select(layer.id)
    }

    /** Live gesture updates; only the first update of a gesture ([first]) becomes an undo step. */
    fun transformSelected(dx: Float, dy: Float, zoom: Float, rotation: Float, first: Boolean) = updateSelected(record = first) {
        it.withTransform(it.t.copy(x = it.t.x + dx, y = it.t.y + dy, scale = (it.t.scale * zoom).coerceIn(0.02f, 50f), rotation = it.t.rotation + rotation))
    }

    fun setSelectedTransform(t: Transform, first: Boolean) = updateSelected(record = first) { it.withTransform(t.copy(scale = t.scale.coerceIn(0.02f, 50f))) }

    fun editText(change: TextLayer.() -> TextLayer) = updateSelected { if (it is TextLayer) it.change() else it }

    fun bringToFront() {
        val layer = _uiState.value.selected ?: return
        updateProject { p -> p.copy(layers = p.layers.filter { it.id != layer.id } + layer) }
    }

    fun sendToBack() {
        val layer = _uiState.value.selected ?: return
        updateProject { p -> p.copy(layers = listOf(layer) + p.layers.filter { it.id != layer.id }) }
    }

    /** Timeline drags; like gestures, only the first update of a drag ([first]) becomes an undo step. */
    fun setLayerTime(id: Long, startMs: Long, endMs: Long, first: Boolean) = updateLayer(id, record = first) { it.withTime(startMs, endMs) }

    fun deleteSelected() {
        val id = _uiState.value.selectedId ?: return
        updateProject { p -> p.copy(layers = p.layers.filter { it.id != id }) }
        select(null)
    }

    fun removeBackground() = work("Removing background") {
        val layer = _uiState.value.selected as? ImageLayer ?: return@work
        val dir = _uiState.value.projectDir ?: return@work
        val subject = cutOutSubject(app, layer.bitmap)
        val (cut, b) = withContext(Dispatchers.Default) { trimTransparent(subject) } ?: error("No subject found")
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, cut) }
        // Keep the subject where it was on screen: the crop's center is off the old center.
        val t = layer.t.shiftedLocal((b[0] + b[2]) / 2f - layer.width / 2, (b[1] + b[3]) / 2f - layer.height / 2)
        updateLayer(layer.id) { layer.copy(t = t, bitmap = cut, file = file) }
    }

    // Stickers

    fun saveSticker() = work("Saving sticker") {
        val layer = _uiState.value.selected as? ImageLayer ?: return@work
        withContext(Dispatchers.IO) {
            val (cut, _) = trimTransparent(layer.bitmap) ?: error("Nothing to save")
            stickerDir.mkdirs()
            File(stickerDir, "${System.currentTimeMillis()}.png").outputStream().use { scaleDown(cut, 1024).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        loadStickers()
        _effects.send(EditorEffect.Message("Saved to My stickers"))
    }

    fun deleteSticker(sticker: Sticker) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sticker.file.delete() }
            loadStickers()
        }
    }

    private fun loadStickers() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                stickerDir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.mapNotNull { f ->
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.let { Sticker(f, it) }
                }
            }
            _uiState.update { it.copy(stickers = list) }
        }
    }

    // Canvas

    /** [ratio] is width / height; null restores the original size. Layers stay centered. */
    fun setAspect(ratio: Float?) = updateProject { p ->
        val h = ratio?.let { (p.width / it).roundToInt() } ?: p.originalHeight
        val dy = (h - p.height) / 2f
        p.copy(height = h, layers = p.layers.map { it.withTransform(it.t.copy(y = it.t.y + dy)) })
    }

    fun setBackground(color: Int) = updateProject { it.copy(background = color) }

    // Video

    fun updateClip(change: VideoClip.() -> VideoClip) = updateProject { p -> p.copy(video = p.video?.change()) }

    fun setMusic(uri: Uri) = work("Adding music") {
        keepAccess(uri)
        val (info, name) = withContext(Dispatchers.IO) { mediaInfo(app, uri) to displayName(app, uri) }
        if (info.durationMs == 0L) error("Can't read this audio file")
        updateClip { copy(music = Music(uri, name, info.durationMs)) }
    }

    // Export

    fun export(share: Boolean) = work("Saving") {
        val p = _uiState.value.project ?: return@work
        val video = p.video
        if (video == null) {
            val bitmap = renderLayers(p.width, p.height, p.background, p.layers)
            val transparent = p.background == Color.TRANSPARENT
            val mime = if (transparent) "image/png" else "image/jpeg"
            val uri = withContext(Dispatchers.IO) {
                saveToGallery(app, mime) { bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
            }
            _effects.send(EditorEffect.Saved(uri, mime, share))
        } else {
            val overlay = if (p.layers.isEmpty()) null else timedOverlay(p.width, p.height, p.layers, video.outputDurationMs)
            val out = File(app.cacheDir, "export.mp4")
            try {
                exportVideo(app, video.composition(overlay), out) { pct -> _uiState.update { it.copy(busy = "Saving video $pct%") } }
                val uri = withContext(Dispatchers.IO) { saveToGallery(app, "video/mp4") { o -> out.inputStream().use { it.copyTo(o) } } }
                _effects.send(EditorEffect.Saved(uri, "video/mp4", share))
            } finally {
                out.delete()
            }
        }
    }

    // Helpers

    /** Every edit goes through here; [record] = false is for the middle of a gesture. */
    private fun updateProject(record: Boolean = true, change: (Project) -> Project) {
        val before = _uiState.value.project ?: return
        val after = change(before)
        if (after == before) return
        if (record) {
            undoStack.addLast(before)
            if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            redoStack.clear()
        }
        _uiState.update { it.copy(project = after, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    private fun updateSelected(record: Boolean = true, change: (Layer) -> Layer) =
        _uiState.value.selectedId?.let { updateLayer(it, record, change) }

    private fun updateLayer(id: Long, record: Boolean = true, change: (Layer) -> Layer) =
        updateProject(record) { p -> p.copy(layers = p.layers.map { if (it.id == id) change(it) else it }) }

    /** Runs one long task at a time behind the busy overlay; failures become a message instead of a crash. */
    private fun work(label: String, block: suspend () -> Unit) {
        if (_uiState.value.busy != null) return
        _uiState.update { it.copy(busy = label) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(EditorEffect.Message(e.message ?: "Something went wrong"))
            } finally {
                _uiState.update { it.copy(busy = null) }
            }
        }
    }

    private companion object {
        const val MAX_PHOTO = 2160
        const val MAX_LAYER = 1440
        const val THUMB_SIZE = 360f
        const val MAX_UNDO = 50
        const val FILMSTRIP_FRAMES = 10
    }
}
