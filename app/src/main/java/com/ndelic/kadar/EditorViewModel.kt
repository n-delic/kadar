@file:OptIn(UnstableApi::class)

package com.ndelic.kadar

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaRecorder
import android.net.Uri
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.ndelic.kadar.media.cutOutSubject
import com.ndelic.kadar.media.displayName
import com.ndelic.kadar.media.downloadImage
import com.ndelic.kadar.media.importFont
import com.ndelic.kadar.media.loadBitmap
import com.ndelic.kadar.media.loadFont
import com.ndelic.kadar.media.mediaInfo
import com.ndelic.kadar.media.renderEmoji
import com.ndelic.kadar.media.saveToGallery
import com.ndelic.kadar.media.scaleDown
import com.ndelic.kadar.media.startVoiceRecording
import com.ndelic.kadar.media.trimTransparent
import com.ndelic.kadar.media.videoFrame
import com.ndelic.kadar.model.AppSettings
import com.ndelic.kadar.model.CropRect
import com.ndelic.kadar.model.DrawLayer
import com.ndelic.kadar.model.ExportQuality
import com.ndelic.kadar.model.ExportSize
import com.ndelic.kadar.model.Font
import com.ndelic.kadar.model.ImageLayer
import com.ndelic.kadar.model.Layer
import com.ndelic.kadar.model.PenStroke
import com.ndelic.kadar.model.Project
import com.ndelic.kadar.model.ProjectStore
import com.ndelic.kadar.model.SavedProject
import com.ndelic.kadar.model.SettingsStore
import com.ndelic.kadar.model.ShapeKind
import com.ndelic.kadar.model.ShapeLayer
import com.ndelic.kadar.model.TextLayer
import com.ndelic.kadar.model.TextPreset
import com.ndelic.kadar.model.Transform
import com.ndelic.kadar.model.UiText
import com.ndelic.kadar.model.UserError
import com.ndelic.kadar.model.VideoLayer
import com.ndelic.kadar.model.newShape
import com.ndelic.kadar.model.renderLayers
import com.ndelic.kadar.model.styled
import com.ndelic.kadar.model.withStrokes
import com.ndelic.kadar.ui.editor.Timeline
import com.ndelic.kadar.ui.editor.label
import com.ndelic.kadar.video.Music
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.VoiceClip
import com.ndelic.kadar.video.composition
import com.ndelic.kadar.video.exportVideo
import com.ndelic.kadar.video.highBitrate
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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

data class Sticker(val file: File, val thumb: Bitmap)

data class EditorUiState(
    val projects: List<SavedProject> = emptyList(),
    /** False until the project list (and its thumbnails) is first read; the intro waits for it. */
    val projectsLoaded: Boolean = false,
    val projectDir: File? = null,
    val project: Project? = null,
    val selectedId: Long? = null,
    /** Label of the running long task; the UI blocks input while it is set. */
    val busy: UiText? = null,
    /** Percent done of the running task, for the overlay's ring; null when unknown. */
    val progress: Int? = null,
    /** Whether the running task can be stopped now ([EditorViewModel.cancelWork]). */
    val cancellable: Boolean = false,
    val stickers: List<Sticker> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Evenly spaced frames of the video, for the timeline. */
    val filmstrip: List<Bitmap> = emptyList(),
    /** Output time the running voice recording started at; null when not recording. */
    val recordingFromMs: Long? = null,
    /** The bundled fonts, then the imported ones by name. */
    val fonts: List<Font> = emptyList(),
) {
    val selected get() = project?.layers?.firstOrNull { it.id == selectedId }
}

sealed interface EditorEffect {
    data class Message(val text: UiText) : EditorEffect
    /** [share] opens the share sheet right away instead of offering it. */
    data class Saved(val uri: Uri, val mime: String, val share: Boolean) : EditorEffect
}

class EditorViewModel(private val app: Application) : AndroidViewModel(app) {

    // Projects store a font by name: add new ones, never rename or remove these.
    private val fonts = listOf(
        Font("Meme", app.resources.getFont(R.font.anton)),
        Font("Tall", app.resources.getFont(R.font.bebas_neue)),
        Font("Narrow", app.resources.getFont(R.font.oswald_bold)),
        Font("Heavy", app.resources.getFont(R.font.archivo_black)),
        Font("Comic", app.resources.getFont(R.font.bangers)),
        Font("Marker", app.resources.getFont(R.font.caveat_brush)),
        Font("Casual", app.resources.getFont(R.font.comic_neue_bold)),
        Font("Retro", app.resources.getFont(R.font.pacifico)),
        Font("Horror", app.resources.getFont(R.font.creepster)),
        Font("Sans", Typeface.DEFAULT_BOLD),
        Font("Serif", Typeface.create(Typeface.SERIF, Typeface.BOLD)),
        Font("Mono", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
        Font("Script", Typeface.create("cursive", Typeface.NORMAL)),
    )

    private val store = ProjectStore(File(app.filesDir, "projects")) { _uiState.value.fonts }
    private val settingsStore = SettingsStore(app)
    private val stickerDir = File(app.filesDir, "stickers")
    /** Imported fonts, reused in every project like stickers; a file's name (without extension) is the font's name. */
    private val fontDir = File(app.filesDir, "fonts")
    private val saveLock = Mutex()
    private var videoFrame: Bitmap? = null
    /** What's on disk for the open project, so just opening one doesn't rewrite it (and bump it to the top). */
    private var lastSaved: Project? = null
    private var nextId = 1L
    private val undoStack = ArrayDeque<Project>()
    /** Start of a gesture whose first update changed nothing, so its undo step is recorded on its first real change. */
    private var gestureStart: Project? = null
    private val redoStack = ArrayDeque<Project>()
    private var task: Job? = null
    /** The running voice recording and its file; here rather than in the UI so it survives rotation. */
    private var recorder: Pair<MediaRecorder, File>? = null

    private val _uiState = MutableStateFlow(EditorUiState(settings = settingsStore.load(), fonts = fonts))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Before the project list, so a project using an imported font opens with it.
            loadFonts()
            refreshProjects()
        }
        loadStickers()
        viewModelScope.launch {
            @kotlin.OptIn(FlowPreview::class)
            uiState.map { it.projectDir to it.project }.distinctUntilChanged().debounce(800).collect { (dir, project) ->
                if (dir != null && project != null && project != lastSaved) save(dir, project)
            }
        }
    }

    // Projects

    fun newPhoto(uri: Uri) = work(R.string.busy_opening_photo) { newPhoto(withContext(Dispatchers.IO) { loadBitmap(app, uri, MAX_PHOTO) }) }

    fun newPhotoFromUrl(url: String) = work(R.string.busy_downloading) { newPhoto(withContext(Dispatchers.IO) { downloadImage(url, MAX_PHOTO) }) }

    private suspend fun newPhoto(bitmap: Bitmap) {
        val dir = store.create()
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        val layer = ImageLayer(1, Transform(bitmap.width / 2f, bitmap.height / 2f), bitmap, file)
        open(dir, Project(bitmap.width, bitmap.height, layers = listOf(layer)))
    }

    fun newBlank() = open(store.create(), Project(1080, 1080))

    fun newVideo(uri: Uri) = work(R.string.busy_opening_video) { newVideoNow(uri) }

    private suspend fun newVideoNow(shared: Uri) {
        val dir = store.create()
        // A video shared from another app comes with a one-time grant; keep a copy so the project still opens later.
        val uri = if (keepAccess(shared)) shared else withContext(Dispatchers.IO) {
            val copy = File(dir, "video.mp4")
            app.contentResolver.openInputStream(shared)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
            Uri.fromFile(copy)
        }
        val (info, frame) = withContext(Dispatchers.IO) { mediaInfo(app, uri) to videoFrame(app, uri, 0) }
        if (info.width == 0 || info.durationMs == 0L) {
            dir.deleteRecursively()
            throw UserError(R.string.error_read_video)
        }
        open(dir, Project(info.width, info.height, background = Color.BLACK, video = VideoClip(uri, info.durationMs, width = info.width, height = info.height, frameRate = info.frameRate)), frame)
    }

    /** "Share to Kadar" from the gallery or another app: saves whatever is open, then starts a project from it. */
    fun openShared(uri: Uri, mime: String) = work(R.string.busy_opening) {
        val s = _uiState.value
        if (s.projectDir != null && s.project != null && s.project != lastSaved) save(s.projectDir, s.project)
        try {
            if (mime.startsWith("video/")) newVideoNow(uri) else newPhoto(withContext(Dispatchers.IO) { loadBitmap(app, uri, MAX_PHOTO) })
        } catch (e: SecurityException) {
            throw UserError(UiText.Res(R.string.error_share_denied), e)
        }
    }

    fun openProject(dir: File) = work(R.string.busy_opening) {
        val project = withContext(Dispatchers.IO) {
            val p = store.load(dir)
            // Projects from before the frame rate was saved; the export with video layers runs at it.
            val v = p.video?.takeIf { it.frameRate == 0f } ?: return@withContext p
            p.copy(video = v.copy(frameRate = runCatching { mediaInfo(app, v.uri).frameRate }.getOrDefault(0f)))
        }
        val frame = project.video?.let { v -> withContext(Dispatchers.IO) { runCatching { videoFrame(app, v.uri, v.startMs) }.getOrNull() } }
        if (project.video != null && frame == null) _effects.send(EditorEffect.Message(UiText.Res(R.string.error_video_missing)))
        open(dir, project, frame, saved = true)
    }

    fun deleteProjects(dirs: List<File>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dirs.forEach { it.deleteRecursively() } }
            refreshProjects()
        }
    }

    fun duplicateProjects(dirs: List<File>) = work(if (dirs.size == 1) UiText.Res(R.string.busy_duplicating) else UiText.Plural(R.plurals.busy_duplicating_projects, dirs.size)) {
        withContext(Dispatchers.IO) { dirs.forEach { store.duplicate(it) } }
        refreshProjects()
    }

    /** A blank [name] goes back to the default label. */
    fun renameProject(dir: File, name: String) = work(R.string.busy_renaming) {
        saveLock.withLock { withContext(Dispatchers.IO) { store.rename(dir, name.trim().ifEmpty { null }) } }
        refreshProjects()
    }

    /** Saves right away (the autosave is debounced) and returns to the project list. */
    fun close() {
        if (_uiState.value.busy != null) return
        viewModelScope.launch {
            finishVoice()
            val s = _uiState.value
            if (s.projectDir != null && s.project != null) {
                // If saving fails, stay in the editor: leaving would lose the edits, and cleanUp could delete
                // images the older project.json on disk still uses.
                if (s.project != lastSaved && !save(s.projectDir, s.project)) return@launch
                withContext(Dispatchers.IO) { store.cleanUp(s.projectDir, s.project) }
            }
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

    /** True when the project is on disk. */
    private suspend fun save(dir: File, project: Project): Boolean {
        val thumb = renderLayers(project.width, project.height, project.background, project.layers, THUMB_SIZE / max(project.width, project.height), videoFrame)
        try {
            saveLock.withLock { withContext(Dispatchers.IO) { store.save(dir, project, thumb) } }
            lastSaved = project
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _effects.send(EditorEffect.Message(UiText.Res(R.string.error_save_project, e.message.orEmpty())))
            return false
        }
    }

    private fun refreshProjects() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { store.list() }
            _uiState.update { it.copy(projects = list, projectsLoaded = true) }
        }
    }

    /** Photo picker and document URIs expire when the app closes unless we keep the grant; saved projects need them later. */
    private fun keepAccess(uri: Uri) = runCatching {
        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

    // Layers

    fun select(id: Long?) = _uiState.update { it.copy(selectedId = id) }

    /** Shows [text] the way the ViewModel's own messages show, for the UI's notes (e.g. about the preview player). */
    fun notify(text: UiText) {
        _effects.trySend(EditorEffect.Message(text))
    }

    fun addText(text: String, color: Int) {
        val p = _uiState.value.project ?: return
        // Black text gets a white outline so it stays readable; everything else gets the classic black one.
        val outline = if (color == Color.BLACK) Color.WHITE else Color.BLACK
        addLayer { id ->
            val layer = TextLayer(id, Transform(p.width / 2f, p.height / 2f), text, fonts.first(), min(p.width, p.height) * 0.1f, color, outline)
            // Long captions start shrunk to fit the canvas width instead of running off both edges.
            layer.copy(t = layer.t.copy(scale = min(1f, p.width * 0.9f / layer.width)))
        }
    }

    /** Emoji become image stickers (drawn from the system emoji font), so they get sticker tools, not text tools. */
    fun addEmoji(emoji: String) = work(R.string.busy_adding_sticker) {
        val p = _uiState.value.project ?: return@work
        val bitmap = withContext(Dispatchers.Default) { renderEmoji(emoji) }
        addBitmapLayer(bitmap, height = min(p.width, p.height) * 0.25f)
    }

    fun addImage(uri: Uri) = work(R.string.busy_adding_image) { addBitmapLayer(withContext(Dispatchers.IO) { loadBitmap(app, uri, MAX_LAYER) }) }

    fun addImageFromUrl(url: String) = work(R.string.busy_downloading) { addBitmapLayer(withContext(Dispatchers.IO) { downloadImage(url, MAX_LAYER) }) }

    fun addSticker(sticker: Sticker) = work(R.string.busy_adding_sticker) {
        addBitmapLayer(withContext(Dispatchers.IO) { BitmapFactory.decodeFile(sticker.file.path) ?: throw UserError(R.string.error_sticker_damaged) })
    }

    /** Adds [bitmap] centered, at [height] canvas pixels tall, or fitted into 60% of the canvas by default. */
    private suspend fun addBitmapLayer(bitmap: Bitmap, height: Float? = null) {
        val s = _uiState.value
        val p = s.project ?: return
        val dir = s.projectDir ?: return
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        val fit = height?.let { it / bitmap.height } ?: min(p.width * 0.6f / bitmap.width, p.height * 0.6f / bitmap.height)
        addLayer { ImageLayer(it, Transform(p.width / 2f, p.height / 2f, fit), bitmap, file) }
    }

    /** Picture-in-picture: another video over the clip from [atMs] (the playhead), half the canvas wide. */
    fun addVideo(uri: Uri, atMs: Long) = work(R.string.busy_adding_video) {
        val p = _uiState.value.project ?: return@work
        val clip = p.video ?: return@work
        keepAccess(uri)
        val info = withContext(Dispatchers.IO) { mediaInfo(app, uri) }
        if (info.width == 0 || info.durationMs == 0L) throw UserError(R.string.error_read_video)
        // Too close to the end there'd be nothing left to see, so start at the beginning instead.
        val start = if (atMs < clip.outputDurationMs - MIN_VIDEO_LAYER_MS) atMs else 0
        addLayer { VideoLayer(it, Transform(p.width / 2f, p.height / 2f, p.width * 0.5f / info.width), uri, info.durationMs, info.width, info.height, startMs = start) }
    }

    /** Result of the draw screen, one undo step: [strokes] (canvas pixels) become a new layer, or go onto drawing [id]. */
    fun applyDrawing(id: Long?, strokes: List<PenStroke>) {
        if (strokes.isEmpty()) return
        if (id == null) addLayer { DrawLayer(it, Transform(0f, 0f), emptyList()).withStrokes(strokes) }
        else updateLayer(id) { (it as? DrawLayer)?.withStrokes(strokes) ?: it }
    }

    fun addShape(kind: ShapeKind) {
        val p = _uiState.value.project ?: return
        addLayer { newShape(it, kind, Transform(p.width / 2f, p.height / 2f), min(p.width, p.height) * 0.4f) }
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

    fun editImage(change: ImageLayer.() -> ImageLayer) = updateSelected { if (it is ImageLayer) it.change() else it }

    fun editVideo(change: VideoLayer.() -> VideoLayer) = updateSelected { if (it is VideoLayer) it.change() else it }

    fun editShape(change: ShapeLayer.() -> ShapeLayer) = updateSelected { if (it is ShapeLayer) it.change() else it }

    /** Tail drags on the stage; like gestures, only the first update of a drag ([first]) becomes an undo step. */
    fun setTail(x: Float, y: Float, first: Boolean) = updateSelected(record = first) { if (it is TextLayer) it.copy(tail = x to y) else it }

    /** Wrap-handle drags of text [from] as the drag started; only the first update of a drag ([first]) is an undo step. */
    fun dragWrap(from: TextLayer, side: Float, dx: Float, first: Boolean) = updateSelected(record = first) { if (it.id == from.id) from.wrapDragged(side, dx) else it }

    fun applyPreset(preset: TextPreset) = editText { styled(preset, _uiState.value.fonts) }

    fun setFlags(id: Long, hidden: Boolean, locked: Boolean) = updateLayer(id) { it.withFlags(hidden, locked) }

    /** Video canvas: [ratio] null keeps the video's shape. Layers keep their place relative to the center. */
    fun setVideoCanvas(ratio: Float?) = updateProject { p ->
        val clip = p.video?.copy(canvas = ratio) ?: return@updateProject p
        val (w, h) = clip.outputSize
        val dx = (w - p.width) / 2f
        val dy = (h - p.height) / 2f
        p.copy(width = w, height = h, originalHeight = h, video = clip, layers = p.layers.map { it.withTransform(it.t.copy(x = it.t.x + dx, y = it.t.y + dy)) })
    }

    fun bringToFront() {
        val layer = _uiState.value.selected ?: return
        updateProject { p -> p.copy(layers = p.layers.filter { it.id != layer.id } + layer) }
    }

    /** Moves the layer [by] places up (+) or down (-) the stack. */
    fun moveLayer(id: Long, by: Int) = updateProject { p ->
        val from = p.layers.indexOfFirst { it.id == id }
        if (from < 0) return@updateProject p
        val to = (from + by).coerceIn(0, p.layers.lastIndex)
        p.copy(layers = p.layers.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Copies the selected layer on top of the stack, nudged so it's visible, and selects the copy. Images share their file. */
    fun duplicateSelected() {
        val p = _uiState.value.project ?: return
        val layer = _uiState.value.selected ?: return
        val nudge = min(p.width, p.height) * 0.04f
        val t = layer.t.copy(x = layer.t.x + nudge, y = layer.t.y + nudge)
        addLayer { id ->
            when (layer) {
                is ImageLayer -> layer.copy(id = id, t = t, hidden = false, locked = false)
                is TextLayer -> layer.copy(id = id, t = t, hidden = false, locked = false)
                is VideoLayer -> layer.copy(id = id, t = t, hidden = false, locked = false)
                is DrawLayer -> layer.copy(id = id, t = t, hidden = false, locked = false)
                is ShapeLayer -> layer.copy(id = id, t = t, hidden = false, locked = false)
            }
        }
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

    fun removeBackground() = work(R.string.busy_removing_bg) {
        val layer = _uiState.value.selected as? ImageLayer ?: return@work
        val dir = _uiState.value.projectDir ?: return@work
        val subject = cutOutSubject(app, layer.bitmap)
        val (cut, b) = withContext(Dispatchers.Default) { trimTransparent(subject) } ?: throw UserError(R.string.error_no_subject)
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, cut) }
        // Keep the subject where it was on screen: the crop's center is off the old center.
        val t = layer.t.shiftedLocal((b[0] + b[2]) / 2f - layer.width / 2, (b[1] + b[3]) / 2f - layer.height / 2)
        updateLayer(layer.id) {
            layer.copy(t = t, bitmap = cut, file = file, source = layer.source ?: layer.file, sourceX = layer.sourceX + b[0], sourceY = layer.sourceY + b[1])
        }
    }

    /** The untouched original behind a cut-out or crop, for the restore brush; null if there is none. */
    suspend fun loadSource(layer: ImageLayer): Bitmap? {
        val dir = _uiState.value.projectDir ?: return null
        val name = layer.source ?: return null
        return withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(dir, name).path) }
    }

    /** The video frame under the playhead ([positionMs], output time), as a guide for the draw screen; null for photos. */
    suspend fun frameAt(positionMs: Long): Bitmap? {
        val v = _uiState.value.project?.video ?: return null
        return withContext(Dispatchers.IO) { runCatching { videoFrame(app, v.uri, v.startMs + (positionMs * v.speed).toLong()) }.getOrNull() }
    }

    /** Result of the eraser screen: one undo step for the whole session. Same size as before, so the source offset still holds. */
    fun applyErase(id: Long, bitmap: Bitmap) = work(R.string.busy_saving) {
        val dir = _uiState.value.projectDir ?: return@work
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        updateLayer(id) { (it as ImageLayer).copy(bitmap = bitmap, file = file) }
    }

    /** Crops the layer to `[left, top, right, bottom)` of its bitmap, keeping the kept part where it was on screen. */
    fun applyCrop(id: Long, left: Int, top: Int, right: Int, bottom: Int) = work(R.string.busy_cropping) {
        val layer = _uiState.value.project?.layers?.firstOrNull { it.id == id } as? ImageLayer ?: return@work
        val dir = _uiState.value.projectDir ?: return@work
        val cut = Bitmap.createBitmap(layer.bitmap, left, top, right - left, bottom - top)
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, cut) }
        val t = layer.t.shiftedLocal((left + right) / 2f - layer.width / 2, (top + bottom) / 2f - layer.height / 2)
        updateLayer(id) {
            layer.copy(t = t, bitmap = cut, file = file, source = layer.source ?: layer.file, sourceX = layer.sourceX + left, sourceY = layer.sourceY + top)
        }
    }

    /** A frame of video layer [layer] at the playhead ([positionMs]), else its nearest end, to crop on; null (with a message) if unreadable. */
    suspend fun layerFrame(layer: VideoLayer, positionMs: Long): Bitmap? {
        val ms = layer.clipStartMs + (positionMs - layer.startMs).coerceAtMost(layer.endMs - layer.startMs - 1).coerceAtLeast(0)
        val frame = withContext(Dispatchers.IO) { runCatching { videoFrame(app, layer.uri, ms) }.getOrNull() }
        if (frame == null) _effects.trySend(EditorEffect.Message(UiText.Res(R.string.error_read_video)))
        return frame
    }

    fun cropVideo(id: Long, crop: CropRect) = updateLayer(id) { (it as? VideoLayer)?.withCrop(crop) ?: it }

    // Stickers

    fun saveSticker() = work(R.string.busy_saving_sticker) {
        val layer = _uiState.value.selected as? ImageLayer ?: return@work
        withContext(Dispatchers.IO) {
            val (cut, _) = trimTransparent(layer.bitmap) ?: throw UserError(R.string.error_nothing_to_save)
            stickerDir.mkdirs()
            File(stickerDir, "${System.currentTimeMillis()}.png").outputStream().use { scaleDown(cut, 1024).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        loadStickers()
        _effects.send(EditorEffect.Message(UiText.Res(R.string.saved_to_stickers)))
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

    // Fonts

    /** Copies a picked font file into app storage, lists it and gives it to the selected text. */
    fun importFont(uri: Uri) = work(R.string.busy_importing_font) {
        val file = withContext(Dispatchers.IO) { importFont(app, uri, fontDir, _uiState.value.fonts.map { it.name }.toSet()) }
        loadFonts()
        _uiState.value.fonts.firstOrNull { it.name == file.nameWithoutExtension }?.let { f -> editText { copy(font = f) } }
    }

    private suspend fun loadFonts() {
        val imported = withContext(Dispatchers.IO) {
            fontDir.listFiles().orEmpty().filter { it.extension == "ttf" || it.extension == "otf" }.sortedBy { it.name.lowercase() }
                .mapNotNull { f -> loadFont(f)?.let { Font(f.nameWithoutExtension, it) } }
        }
        _uiState.update { it.copy(fonts = fonts + imported) }
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

    fun setMusic(uri: Uri) = work(R.string.busy_adding_music) {
        keepAccess(uri)
        val (info, name) = withContext(Dispatchers.IO) { mediaInfo(app, uri) to displayName(app, uri) }
        if (info.durationMs == 0L) throw UserError(R.string.error_read_audio)
        updateClip { copy(music = Music(uri, name, info.durationMs)) }
    }

    /** Starts recording a voiceover that will play from [atMs] (output time). False when the microphone can't be used. */
    fun startVoice(atMs: Long): Boolean {
        val dir = _uiState.value.projectDir ?: return false
        if (recorder != null) return true
        val file = store.newVoiceFile(dir)
        try {
            recorder = startVoiceRecording(app, file) to file
        } catch (e: Exception) {
            // IOException, or IllegalStateException/RuntimeException from a microphone another app holds.
            file.delete()
            _effects.trySend(EditorEffect.Message(UiText.Res(R.string.error_microphone)))
            return false
        }
        _uiState.update { it.copy(recordingFromMs = atMs) }
        return true
    }

    fun stopVoice() {
        viewModelScope.launch { finishVoice() }
    }

    /** Stops the recording, if any, and adds it to the clip as one undo step. */
    private suspend fun finishVoice() {
        val (r, file) = recorder ?: return
        recorder = null
        val start = _uiState.value.recordingFromMs ?: 0
        _uiState.update { it.copy(recordingFromMs = null) }
        val stopped = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            // stop() throws when no audio arrived yet (stopped right after starting).
            false
        } finally {
            r.release()
        }
        val durationMs = if (stopped) withContext(Dispatchers.IO) { runCatching { mediaInfo(app, Uri.fromFile(file)).durationMs }.getOrDefault(0) } else 0
        // Another project opened meanwhile (a share into Kadar): the file isn't this project's.
        if (durationMs <= 0 || file.parentFile != _uiState.value.projectDir) {
            file.delete()
            _effects.send(EditorEffect.Message(UiText.Res(R.string.error_nothing_recorded)))
            return
        }
        updateClip { copy(voices = voices + VoiceClip(file, start, durationMs)) }
    }

    override fun onCleared() {
        // The unfinished file stays unreferenced, so the project's next cleanUp removes it.
        recorder?.first?.release()
    }

    // Settings

    fun updateSettings(change: AppSettings.() -> AppSettings) {
        val next = _uiState.value.settings.change()
        settingsStore.save(next)
        _uiState.update { it.copy(settings = next) }
    }

    fun setProjectExportSize(size: ExportSize?) = updateProject { it.copy(exportSize = size) }

    // Export

    fun export(share: Boolean) = work(R.string.busy_saving) {
        val p = _uiState.value.project ?: return@work
        val video = p.video
        val shortSide = (p.exportSize ?: _uiState.value.settings.exportSize).shortSide
        if (video == null) {
            val scale = shortSide?.let { min(1f, it / min(p.width, p.height).toFloat()) } ?: 1f
            val bitmap = renderLayers(p.width, p.height, p.background, p.layers, scale)
            val transparent = p.background == Color.TRANSPARENT
            val mime = if (transparent) "image/png" else "image/jpeg"
            val uri = withContext(Dispatchers.IO) {
                saveToGallery(app, mime) { bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
            }
            _effects.send(EditorEffect.Saved(uri, mime, share))
        } else {
            val out = File(app.cacheDir, "export.mp4")
            val (w, h) = video.outputSize
            val bitrate = if (_uiState.value.settings.exportQuality == ExportQuality.High) highBitrate(w, h, shortSide, video.frameRate) else null
            try {
                _uiState.update { it.copy(busy = UiText.Res(R.string.busy_saving_video), progress = 0, cancellable = true) }
                exportVideo(app, video.composition(p.layers, shortSide), out, bitrate) { pct -> _uiState.update { it.copy(progress = pct) } }
                // A cancel during the gallery copy would leave a half-written file there; the copy takes a moment at most.
                _uiState.update { it.copy(cancellable = false) }
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
        if (after == before) {
            // A gesture's first update can be a no-op (a layer held by a snap); keep its starting point for the next change.
            if (record) gestureStart = before
            return
        }
        val recordFrom = if (record) before else gestureStart
        gestureStart = null
        if (recordFrom != null) {
            undoStack.addLast(recordFrom)
            if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            redoStack.clear()
        }
        _uiState.update { it.copy(project = after, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    private fun updateSelected(record: Boolean = true, change: (Layer) -> Layer) =
        _uiState.value.selectedId?.let { updateLayer(it, record, change) }

    private fun updateLayer(id: Long, record: Boolean = true, change: (Layer) -> Layer) =
        updateProject(record) { p -> p.copy(layers = p.layers.map { if (it.id == id) change(it) else it }) }

    /**
     * Stops the running task while it's [EditorUiState.cancellable]; its `finally` blocks clean up and [work] clears the
     * overlay. shortcut: only video export opts in (by setting `cancellable`), so the message names it; pass the message
     * through [work] once a second task can be cancelled.
     */
    fun cancelWork() {
        if (!_uiState.value.cancellable) return
        _uiState.update { it.copy(cancellable = false) }
        task?.cancel()
        _effects.trySend(EditorEffect.Message(UiText.Res(R.string.export_cancelled)))
    }

    /**
     * Runs one long task at a time behind the busy overlay; failures become a message instead of a crash. A task opts
     * in to [cancelWork] by setting `cancellable` while stopping it is safe.
     */
    private fun work(@StringRes label: Int, block: suspend () -> Unit) = work(UiText.Res(label), block)

    private fun work(label: UiText, block: suspend () -> Unit) {
        if (_uiState.value.busy != null) return
        _uiState.update { it.copy(busy = label) }
        task = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserError) {
                _effects.send(EditorEffect.Message(e.text))
            } catch (e: Exception) {
                _effects.send(EditorEffect.Message(e.message?.let(UiText::Raw) ?: UiText.Res(R.string.error_generic)))
            } finally {
                _uiState.update { it.copy(busy = null, progress = null, cancellable = false) }
            }
        }
    }

    private companion object {
        const val MAX_PHOTO = 2160
        const val MAX_LAYER = 1440
        const val THUMB_SIZE = 360f
        const val MAX_UNDO = 50
        const val FILMSTRIP_FRAMES = 10
        const val MIN_VIDEO_LAYER_MS = 1000L
    }
}
