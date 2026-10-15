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
import com.ndelic.kadar.media.cutToShape
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
import com.ndelic.kadar.media.trackMimes
import com.ndelic.kadar.media.trimTransparent
import com.ndelic.kadar.media.turnBitmap
import com.ndelic.kadar.media.videoFrame
import com.ndelic.kadar.media.shapedBitmap
import com.ndelic.kadar.media.videoStill
import com.ndelic.kadar.model.appliedTo
import com.ndelic.kadar.model.Anim
import com.ndelic.kadar.model.AppSettings
import com.ndelic.kadar.model.CensorLayer
import com.ndelic.kadar.model.CropRect
import com.ndelic.kadar.model.CutShape
import com.ndelic.kadar.model.DrawLayer
import com.ndelic.kadar.model.ExportQuality
import com.ndelic.kadar.model.ExportSize
import com.ndelic.kadar.model.PhotoFormat
import com.ndelic.kadar.model.transparent
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
import com.ndelic.kadar.model.backdrop
import com.ndelic.kadar.model.defaultTextSize
import com.ndelic.kadar.model.newShape
import com.ndelic.kadar.model.renderLayers
import com.ndelic.kadar.model.sideDragged
import com.ndelic.kadar.model.styled
import com.ndelic.kadar.model.turned
import com.ndelic.kadar.model.turnedBounds
import com.ndelic.kadar.model.turnedSourceOffset
import com.ndelic.kadar.model.withStrokes
import com.ndelic.kadar.ui.editor.Timeline
import com.ndelic.kadar.ui.editor.label
import com.ndelic.kadar.video.AudioPart
import com.ndelic.kadar.video.AudioRef
import com.ndelic.kadar.video.ClipAnimation
import com.ndelic.kadar.video.GIF_FPS
import com.ndelic.kadar.video.GIF_MAX_MS
import com.ndelic.kadar.video.Music
import com.ndelic.kadar.video.Segment
import com.ndelic.kadar.video.Transition
import com.ndelic.kadar.video.VideoClip
import com.ndelic.kadar.video.VoiceClip
import com.ndelic.kadar.video.audioPart
import com.ndelic.kadar.video.audioStartMs
import com.ndelic.kadar.video.makePreviewCopy
import com.ndelic.kadar.video.needsPreviewCopy
import com.ndelic.kadar.video.composition
import com.ndelic.kadar.video.exportVideo
import com.ndelic.kadar.video.gifShortSide
import com.ndelic.kadar.video.highBitrate
import com.ndelic.kadar.video.layerMovedTo
import com.ndelic.kadar.video.layerSplitPoint
import com.ndelic.kadar.video.moved
import com.ndelic.kadar.video.mp4ToGif
import com.ndelic.kadar.video.outputFrameRate
import com.ndelic.kadar.video.sourceMs
import com.ndelic.kadar.video.splitAt
import com.ndelic.kadar.video.splitPoint
import com.ndelic.kadar.video.withAudio
import java.io.File
import kotlin.math.ceil
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
    /** Evenly spaced frames of each clip's source (a photo's one picture), for the timeline. */
    val filmstrip: Map<Uri, List<Bitmap>> = emptyMap(),
    /** Clip sources the preview plays from a copy instead (`needsPreviewCopy`), by source; the export uses the sources. */
    val previewCopies: Map<Uri, Uri> = emptyMap(),
    /** Index of the clip picked on the timeline's clip strip; never set together with [selectedId]. */
    val selectedClip: Int? = null,
    /** The music or voiceover track picked on the timeline; never set together with [selectedId] or [selectedClip]. */
    val selectedAudio: AudioRef? = null,
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
    /** One preview copy at a time: each is a Transformer run with a hardware decoder and encoder. */
    private val copyLock = Mutex()
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
        open(dir, Project(bitmap.width, bitmap.height, layers = listOf(layer), photoId = layer.id))
    }

    /** A blank canvas of [ratio] (width / height), 1080 px on its short side. */
    fun newBlank(ratio: Float) {
        val (w, h) = if (ratio >= 1) (BLANK_SIDE * ratio).roundToInt() to BLANK_SIDE else BLANK_SIDE to (BLANK_SIDE / ratio).roundToInt()
        open(store.create(), Project(w, h))
    }

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
        val clip = VideoClip(listOf(Segment(uri, info.durationMs, info.width, info.height)), width = info.width, height = info.height, frameRate = info.frameRate)
        open(dir, Project(info.width, info.height, background = Color.BLACK, video = clip), frame)
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
            val first = v.segments.firstOrNull { !it.isImage } ?: return@withContext p
            p.copy(video = v.copy(frameRate = runCatching { mediaInfo(app, first.uri).frameRate }.getOrDefault(0f)))
        }
        val frame = project.video?.segments?.first()?.let { s -> withContext(Dispatchers.IO) { segmentFrame(s, s.startMs) } }
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
        _uiState.update { it.copy(projectDir = dir, project = project, selectedId = null, selectedClip = null, selectedAudio = null, canUndo = false, canRedo = false, filmstrip = emptyMap(), previewCopies = emptyMap()) }
        project.video?.segments?.distinctBy { it.uri }?.forEach {
            loadFilmstrip(dir, it)
            loadPreviewCopy(dir, it)
        }
    }

    /** Makes clip [s]'s preview copy in the background if its codec needs one, and lets the preview use it once whole. */
    private fun loadPreviewCopy(dir: File, s: Segment) {
        if (s.isImage || s.uri in _uiState.value.previewCopies) return
        viewModelScope.launch {
            val file = store.previewCopyFile(dir, s.uri)
            val made = file.exists() || copyLock.withLock {
                val mimes = withContext(Dispatchers.IO) { runCatching { trackMimes(app, s.uri) }.getOrDefault(emptyList()) }
                if (!needsPreviewCopy(mimes) || _uiState.value.projectDir != dir) return@withLock false
                // Without a copy the preview still plays: EditorScreen gets it past the stalls with a seek.
                runCatching { makePreviewCopy(app, s.uri, s.width, s.height, mimes.any { it.startsWith("audio/") }, file) }.isSuccess
            }
            if (made) _uiState.update { if (it.projectDir == dir) it.copy(previewCopies = it.previewCopies + (s.uri to Uri.fromFile(file))) else it }
        }
    }

    private fun loadFilmstrip(dir: File, s: Segment) {
        if (s.uri in _uiState.value.filmstrip) return
        viewModelScope.launch {
            val frames = withContext(Dispatchers.IO) {
                if (s.isImage) listOfNotNull(segmentFrame(s, 0))
                else runCatching { (0 until FILMSTRIP_FRAMES).mapNotNull { videoFrame(app, s.uri, s.durationMs * it / FILMSTRIP_FRAMES) } }.getOrDefault(emptyList())
            }
            _uiState.update { if (it.projectDir == dir) it.copy(filmstrip = it.filmstrip + (s.uri to frames)) else it }
        }
    }

    /** A small picture of clip [s] at source time [ms]; null when its file can't be read. Call off the main thread. */
    private fun segmentFrame(s: Segment, ms: Long): Bitmap? = runCatching {
        if (s.isImage) loadBitmap(app, s.uri, FRAME_SIZE) else videoFrame(app, s.uri, ms)
    }.getOrNull()

    /** [frame], a picture of this clip's whole source, cropped, mirrored and turned as the clip shows it. */
    private fun Segment.shaped(frame: Bitmap) = shapedBitmap(frame, crop, flipX, flipY, turns)

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
        // The first clip as it shows now: it may have been cropped or turned since the frame was read.
        val frame = videoFrame?.let { f -> project.video?.segments?.first()?.shaped(f) ?: f }
        val thumb = renderLayers(project.width, project.height, project.background, project.layers, THUMB_SIZE / max(project.width, project.height), frame, project.backdrop(), frame?.let { project.video?.frameRect(it.width, it.height) })
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

    fun select(id: Long?) = _uiState.update { it.copy(selectedId = id, selectedClip = null, selectedAudio = null) }

    /** Shows [text] the way the ViewModel's own messages show, for the UI's notes (e.g. about the preview player). */
    fun notify(text: UiText) {
        _effects.trySend(EditorEffect.Message(text))
    }

    fun addText(text: String, color: Int) {
        val p = _uiState.value.project ?: return
        // Black text gets a white outline so it stays readable; everything else gets the classic black one.
        val outline = if (color == Color.BLACK) Color.WHITE else Color.BLACK
        addLayer { id ->
            val layer = TextLayer(id, Transform(p.width / 2f, p.height / 2f), text, fonts.first(), defaultTextSize(p.width, p.height), color, outline)
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

    /**
     * Result of the draw screen, one undo step: [strokes] (canvas pixels) become a new layer, or replace drawing [id]'s
     * (keeping its rotation and scale); a drawing erased to nothing is deleted.
     */
    fun applyDrawing(id: Long?, strokes: List<PenStroke>) {
        when {
            id == null -> if (strokes.isNotEmpty()) addLayer { DrawLayer(it, Transform(0f, 0f), emptyList()).withStrokes(strokes) }
            strokes.isEmpty() -> {
                updateProject { p -> p.copy(layers = p.layers.filter { it.id != id }) }
                select(null)
            }
            else -> updateLayer(id) { (it as? DrawLayer)?.copy(strokes = emptyList())?.withStrokes(strokes) ?: it }
        }
    }

    fun addShape(kind: ShapeKind) {
        val p = _uiState.value.project ?: return
        addLayer { newShape(it, kind, Transform(p.width / 2f, p.height / 2f), min(p.width, p.height) * 0.4f) }
    }

    /** A blur area over the middle of the video, for its whole length. */
    fun addCensor() {
        val p = _uiState.value.project ?: return
        val size = min(p.width, p.height) * 0.3f
        addLayer { CensorLayer(it, Transform(p.width / 2f, p.height / 2f), size, size) }
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

    fun editCensor(change: CensorLayer.() -> CensorLayer) = updateSelected { if (it is CensorLayer) it.change() else it }

    /** Tail drags on the stage; like gestures, only the first update of a drag ([first]) becomes an undo step. */
    fun setTail(x: Float, y: Float, first: Boolean) = updateSelected(record = first) { if (it is TextLayer) it.copy(tail = x to y) else it }

    /** Side-handle drags of [from] as the drag started ([sideDragged]); only the first update of a drag ([first]) is an undo step. */
    fun dragSide(from: Layer, vertical: Boolean, side: Float, d: Float, first: Boolean) =
        updateSelected(record = first) { if (it.id == from.id) from.sideDragged(vertical, side, d) else it }

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
        addLayer { id -> layer.copied(id).withTransform(t).withFlags(hidden = false, locked = false) }
    }

    private fun Layer.copied(id: Long, animIn: Anim = this.animIn, animOut: Anim = this.animOut) = when (this) {
        is ImageLayer -> copy(id = id, animIn = animIn, animOut = animOut)
        is TextLayer -> copy(id = id, animIn = animIn, animOut = animOut)
        is VideoLayer -> copy(id = id)
        is DrawLayer -> copy(id = id)
        is ShapeLayer -> copy(id = id, animIn = animIn, animOut = animOut)
        is CensorLayer -> copy(id = id)
    }

    /** Cuts the selected layer at [atMs] (output time) into two with the same look, one ending and one starting there. */
    fun splitSelected(atMs: Long) {
        val total = _uiState.value.project?.video?.outputDurationMs ?: return
        val layer = _uiState.value.selected ?: return
        val at = layerSplitPoint(layer.startMs, layer.endMs, total, atMs)
        if (at == null) {
            _effects.trySend(EditorEffect.Message(UiText.Res(R.string.split_layer_outside)))
            return
        }
        val id = nextId++
        // The parts join without an exit and entrance at the cut; a video layer's second part plays on from where the first stopped in its clip.
        val first = layer.copied(layer.id, animOut = Anim.None).withTime(layer.startMs, at)
        val second = (if (layer is VideoLayer) layer.copy(id = id, clipStartMs = layer.clipStartMs + at - layer.startMs) else layer.copied(id, animIn = Anim.None)).withTime(at, layer.endMs)
        updateProject { p -> p.copy(layers = p.layers.flatMap { if (it.id == layer.id) listOf(first, second) else listOf(it) }) }
        select(id)
    }

    /** Moves the selected layer to start at [atMs] (output time), as long as before. */
    fun moveSelectedTo(atMs: Long) = updateSelected { l -> layerMovedTo(l.startMs, l.endMs, atMs).let { (s, e) -> l.withTime(s, e) } }

    /** Mirrors the selected layer left to right ([horizontal]) or top to bottom. */
    fun flipSelected(horizontal: Boolean) = updateSelected { l -> l.withTransform(if (horizontal) l.t.copy(flipX = !l.t.flipX) else l.t.copy(flipY = !l.t.flipY)) }

    fun sendToBack() {
        val layer = _uiState.value.selected ?: return
        updateProject { p -> p.copy(layers = listOf(layer) + p.layers.filter { it.id != layer.id }) }
    }

    /** Timeline drags; like gestures, only the first update of a drag ([first]) becomes an undo step. */
    fun setLayerTime(id: Long, startMs: Long, endMs: Long, first: Boolean) = updateLayer(id, record = first) { it.withTime(startMs, endMs) }

    /** A layer's start dragged on its track; a video layer is trimmed, so its video starts later too. */
    fun trimLayerStart(id: Long, startMs: Long, first: Boolean) =
        updateLayer(id, record = first) { (it as? VideoLayer)?.trimmedStart(startMs) ?: it.withTime(startMs, it.endMs) }

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
        val t = layer.t.shiftedContent((b[0] + b[2]) / 2f - layer.width / 2, (b[1] + b[3]) / 2f - layer.height / 2)
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

    /** The stage's picture at [positionMs] (output time; ignored for photos), at most [PICK_SIZE] wide or tall, for the eyedropper. */
    suspend fun stagePicture(positionMs: Long): Bitmap? {
        val p = _uiState.value.project ?: return null
        val video = _uiState.value.selected as? VideoLayer
        // shortcut: other video layers and the blurred canvas are left out (players draw them); render their frames if users pick from them.
        // The selected video layer is its raw frame, unkeyed and without its look, so its key color can be picked from it.
        val layers = if (p.video == null) p.layers else p.layers.filter { it.visibleAt(positionMs) || it.id == video?.id }.map { l ->
            if (l.id == video?.id) layerFrame(video, positionMs)?.let { rawFrameLayer(video, it) } ?: l else l
        }
        val frame = frameAt(positionMs)
        return renderLayers(p.width, p.height, p.background, layers, min(1f, PICK_SIZE / max(p.width, p.height)), frame, underAt = frame?.let { p.video?.frameRect(it.width, it.height) })
    }

    /** Video layer [l]'s [frame], cropped like it, as a picture drawn where it shows. */
    private fun rawFrameLayer(l: VideoLayer, frame: Bitmap): ImageLayer {
        val c = l.crop
        val w = (c.width * frame.width).roundToInt().coerceIn(1, frame.width)
        val h = (c.height * frame.height).roundToInt().coerceIn(1, frame.height)
        val cut = Bitmap.createBitmap(frame, (c.left * frame.width).roundToInt().coerceIn(0, frame.width - w), (c.top * frame.height).roundToInt().coerceIn(0, frame.height - h), w, h)
        return ImageLayer(l.id, l.t.copy(scale = l.t.scale * l.width / w), cut, "")
    }

    /** The video frame under the playhead ([positionMs], output time), as a guide for the draw screen; null for photos. */
    suspend fun frameAt(positionMs: Long): Bitmap? {
        val v = _uiState.value.project?.video ?: return null
        val (i, into) = v.segmentAt(positionMs)
        val s = v.segments[i]
        return withContext(Dispatchers.IO) { segmentFrame(s, sourceMs(s.startMs, s.speed, into))?.let { s.shaped(it) } }
    }

    /**
     * Clip [index]'s whole frame under the playhead ([positionMs]), else at its start, mirrored and turned as it shows,
     * to crop on; null (with a message) if unreadable.
     */
    suspend fun clipFrame(index: Int, positionMs: Long): Bitmap? {
        val v = _uiState.value.project?.video ?: return null
        val s = v.segments.getOrNull(index) ?: return null
        val (i, into) = v.segmentAt(positionMs)
        val ms = if (i == index) sourceMs(s.startMs, s.speed, into).coerceAtMost(s.endMs) else s.startMs
        val frame = withContext(Dispatchers.IO) { segmentFrame(s, ms)?.let { s.copy(crop = CropRect()).shaped(it) } }
        if (frame == null) _effects.trySend(EditorEffect.Message(UiText.Res(R.string.error_read_video)))
        return frame
    }

    /** Shows only [crop] of clip [index]'s source frame. */
    fun cropClip(index: Int, crop: CropRect) = updateSegment(index) { copy(crop = crop) }

    /** Result of the eraser screen: one undo step for the whole session. Same size as before, so the source offset still holds. */
    fun applyErase(id: Long, bitmap: Bitmap) = work(R.string.busy_saving) {
        val dir = _uiState.value.projectDir ?: return@work
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        updateLayer(id) { (it as ImageLayer).copy(bitmap = bitmap, file = file) }
    }

    /**
     * Crops the layer to `[left, top, right, bottom)`, keeping the kept part where it was on screen. Unturned, that is in
     * its bitmap's pixels. Turned by [turns] quarter turns and [angle] degrees, it is in the crop screen's frame (the
     * picture as shown, flipped like the layer, its box after the quarter turns, same center), and the flip is baked
     * into the new bitmap and source. With a [shape], the crop is cut to it as shown; the source stays whole, so the
     * restore brush can bring the rest back.
     */
    fun applyCrop(id: Long, left: Int, top: Int, right: Int, bottom: Int, turns: Int = 0, angle: Float = 0f, shape: CutShape? = null) = work(R.string.busy_cropping) {
        val layer = _uiState.value.project?.layers?.firstOrNull { it.id == id } as? ImageLayer ?: return@work
        val dir = _uiState.value.projectDir ?: return@work
        val degrees = turns * 90 + angle
        if (degrees % 360f == 0f) {
            val box = Bitmap.createBitmap(layer.bitmap, left, top, right - left, bottom - top)
            // This rectangle is in the bitmap, which the layer draws mirrored, so the shape is mirrored to show upright.
            val cut = shape?.let { withContext(Dispatchers.Default) { cutToShape(box, it, layer.t.flipX, layer.t.flipY) } } ?: box
            val file = withContext(Dispatchers.IO) { store.writeImage(dir, cut) }
            val t = layer.t.shiftedContent((left + right) / 2f - layer.width / 2, (top + bottom) / 2f - layer.height / 2)
            putCropped(layer.copy(t = t, bitmap = cut, file = file, source = layer.source ?: layer.file, sourceX = layer.sourceX + left, sourceY = layer.sourceY + top))
            return@work
        }
        val (fw, fh) = if (turns % 2 == 0) layer.width to layer.height else layer.height to layer.width
        // The crop's center from the frame's center, in the turned picture.
        val dx = (left + right) / 2f - fw / 2
        val dy = (top + bottom) / 2f - fh / 2
        // The restore brush paints from the source by offset only, so the source turns with the layer.
        val original = loadSource(layer)
        val src = original ?: layer.bitmap
        val (tw, th) = turnedBounds(src.width.toFloat(), src.height.toFloat(), degrees).let { (w, h) -> ceil(w).toInt() to ceil(h).toInt() }
        val (fx, fy) = layer.t.flipX to layer.t.flipY
        val (sx, sy) = turnedSourceOffset(
            layer.bitmap.width, layer.bitmap.height, if (original != null) layer.sourceX else 0, if (original != null) layer.sourceY else 0,
            src.width, src.height, degrees, tw, th, left - fw / 2, top - fh / 2, fx, fy,
        )
        val cut = withContext(Dispatchers.Default) {
            turnBitmap(layer.bitmap, degrees, right - left, bottom - top, dx, dy, fx, fy).let { b -> shape?.let { cutToShape(b, it) } ?: b }
        }
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, cut) }
        val source = withContext(Dispatchers.IO) { store.writeImage(dir, turnBitmap(src, degrees, tw, th, flipX = fx, flipY = fy)) }
        // The frame is the picture as shown in the layer's box, so the shift is in box pixels, and the new bitmap is unflipped.
        val (lx, ly) = turned(dx, dy, -degrees)
        val t = layer.t.shiftedLocal(lx, ly).copy(flipX = false, flipY = false)
        putCropped(layer.copy(t = t, bitmap = cut, file = file, source = source, sourceX = sx.roundToInt(), sourceY = sy.roundToInt()))
    }

    /** One undo step: the cropped [layer], and for the photo the project was opened with, the canvas cut to it. */
    private fun putCropped(layer: ImageLayer) = updateProject { p ->
        val cropped = p.copy(layers = p.layers.map { if (it.id == layer.id) layer else it })
        if (layer.id == p.photoId && p.video == null) cropped.fittedTo(layer) else cropped
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

    fun setBackground(color: Int) = updateProject { it.copy(background = color, blur = false, video = it.video?.copy(blur = false)) }

    /** Photos: the canvas shows the bottom picture blurred behind everything, until a color is picked. */
    fun setBlur() = updateProject { it.copy(blur = true) }

    // Video

    fun updateClip(record: Boolean = true, change: VideoClip.() -> VideoClip) = updateProject(record) { p -> p.copy(video = p.video?.change()) }

    /** Changes clip [index] of the main track (trim, speed, volume, adjust). */
    fun updateSegment(index: Int, change: Segment.() -> Segment) =
        updateClip { copy(segments = segments.mapIndexed { i, s -> if (i == index) s.change() else s }) }

    /** Changes every clip of the main track at once, as one undo step (Apply to all). */
    fun updateAllSegments(change: Segment.() -> Segment) = updateClip { copy(segments = segments.map { it.change() }) }

    /** Picks clip [index] on the clip strip, or none; a picked clip and a selected layer exclude each other. */
    fun selectClip(index: Int?) = _uiState.update { it.copy(selectedClip = index, selectedId = null, selectedAudio = null) }

    /** Adds a video, or a photo as a still of [PHOTO_CLIP_MS], after the last clip and picks it. */
    fun addClip(uri: Uri) = work(R.string.busy_adding_clip) {
        val dir = _uiState.value.projectDir ?: return@work
        val segment = newSegment(dir, uri)
        updateClip { copy(segments = segments + segment) }
        _uiState.value.project?.video?.let { selectClip(it.segments.lastIndex) }
        loadFilmstrip(dir, segment)
        loadPreviewCopy(dir, segment)
    }

    /** Adds a clip of plain [color], canvas-sized, as a still of [PHOTO_CLIP_MS] after the last clip, and picks it. */
    fun addColorClip(color: Int) = work(R.string.busy_adding_clip) {
        val dir = _uiState.value.projectDir ?: return@work
        val (w, h) = _uiState.value.project?.video?.outputSize ?: return@work
        val scale = minOf(1f, MAX_PHOTO / maxOf(w, h).toFloat())
        val bitmap = Bitmap.createBitmap((w * scale).roundToInt().coerceAtLeast(2), (h * scale).roundToInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        val segment = Segment(Uri.fromFile(File(dir, file)), MAX_PHOTO_CLIP_MS, bitmap.width, bitmap.height, endMs = PHOTO_CLIP_MS, isImage = true)
        updateClip { copy(segments = segments + segment) }
        _uiState.value.project?.video?.let { selectClip(it.segments.lastIndex) }
        loadFilmstrip(dir, segment)
    }

    /**
     * Puts the video or photo at [uri] in clip [index]'s place, keeping its length (as far as the new one goes), speed,
     * volume, look and transition.
     */
    fun replaceClip(index: Int, uri: Uri) = work(R.string.busy_adding_clip) {
        val dir = _uiState.value.projectDir ?: return@work
        val old = _uiState.value.project?.video?.segments?.getOrNull(index) ?: return@work
        val new = newSegment(dir, uri)
        val length = old.outputDurationMs
        val segment = if (new.isImage) {
            new.copy(endMs = length.coerceAtMost(MAX_PHOTO_CLIP_MS), volume = old.volume, adjust = old.adjust, transition = old.transition, animIn = old.animIn, animOut = old.animOut)
        } else {
            val speed = if (old.isImage) 1f else old.speed
            new.copy(endMs = minOf(new.durationMs, (length * speed).toLong()), speed = speed, volume = old.volume, keepPitch = old.keepPitch, adjust = old.adjust, transition = old.transition, animIn = old.animIn, animOut = old.animOut)
        }
        updateSegment(index) { segment }
        loadFilmstrip(dir, segment)
        loadPreviewCopy(dir, segment)
    }

    /** A clip of the video at [uri], whole, or of the photo there as a still of [PHOTO_CLIP_MS], copied into [dir]. */
    private suspend fun newSegment(dir: File, uri: Uri): Segment =
        if (app.contentResolver.getType(uri)?.startsWith("image/") == true) {
            // Copied into the project like image layers, decoded upright and at a size the encoder takes.
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(app, uri, MAX_PHOTO) }
            val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
            Segment(Uri.fromFile(File(dir, file)), MAX_PHOTO_CLIP_MS, bitmap.width, bitmap.height, endMs = PHOTO_CLIP_MS, isImage = true)
        } else {
            keepAccess(uri)
            val info = withContext(Dispatchers.IO) { mediaInfo(app, uri) }
            if (info.width == 0 || info.durationMs == 0L) throw UserError(R.string.error_read_video)
            Segment(uri, info.durationMs, info.width, info.height)
        }

    /** Cuts the clip under the playhead ([atMs], output time) in two there; a message when too close to its ends. */
    fun splitClip(atMs: Long) {
        val v = _uiState.value.project?.video ?: return
        val (i, into) = v.segmentAt(atMs)
        val s = v.segments[i]
        val at = splitPoint(s.startMs, s.endMs, s.speed, into)
        if (at == null) {
            _effects.trySend(EditorEffect.Message(UiText.Res(R.string.split_too_close)))
            return
        }
        // A cut, not a transition, joins the two halves, with no exit and entrance there.
        updateClip { copy(segments = segments.take(i) + s.copy(endMs = at, animOut = ClipAnimation()) + s.copy(startMs = at, transition = Transition(), animIn = ClipAnimation()) + segments.drop(i + 1)) }
        selectClip(i + 1)
    }

    /**
     * Freezes the frame under the playhead ([atMs], output time): the clip is cut there and a still of that frame,
     * [PHOTO_CLIP_MS] long, goes between the halves (before or after the whole clip when the playhead is at its edge).
     */
    fun freezeFrame(atMs: Long) = work(R.string.busy_freezing) {
        val dir = _uiState.value.projectDir ?: return@work
        val v = _uiState.value.project?.video ?: return@work
        val (i, into) = v.segmentAt(atMs)
        val s = v.segments[i]
        if (s.isImage) return@work
        val at = sourceMs(s.startMs, s.speed, into).coerceIn(s.startMs, s.endMs)
        val bitmap = clipStill(s, at, MAX_PHOTO)
        val file = withContext(Dispatchers.IO) { store.writeImage(dir, bitmap) }
        val still = Segment(Uri.fromFile(File(dir, file)), MAX_PHOTO_CLIP_MS, bitmap.width, bitmap.height, endMs = PHOTO_CLIP_MS, isImage = true, adjust = s.adjust, crop = s.crop, flipX = s.flipX, flipY = s.flipY, turns = s.turns)
        // The parts and where the still is among them.
        val (parts, at0) = when {
            splitPoint(s.startMs, s.endMs, s.speed, into) != null -> listOf(s.copy(endMs = at, animOut = ClipAnimation()), still, s.copy(startMs = at, transition = Transition(), animIn = ClipAnimation())) to 1
            // At the clip's start the still takes its transition and entrance; at its end it comes right after it, with its exit.
            into < s.outputDurationMs / 2 -> listOf(still.copy(transition = s.transition, animIn = s.animIn), s.copy(transition = Transition(), animIn = ClipAnimation())) to 0
            else -> listOf(s.copy(animOut = ClipAnimation()), still.copy(animOut = s.animOut)) to 1
        }
        updateClip { copy(segments = segments.take(i) + parts + segments.drop(i + 1)) }
        selectClip(i + at0)
        loadFilmstrip(dir, still)
    }

    /**
     * Saves the frame under the playhead ([atMs], output time), as its clip has it (crop, turn, look; no layers or blur areas),
     * to the gallery as a JPEG.
     */
    fun saveFrame(atMs: Long) = work(R.string.busy_saving_frame) {
        val v = _uiState.value.project?.video ?: return@work
        val (i, into) = v.segmentAt(atMs)
        val s = v.segments[i]
        if (s.isImage) return@work
        val bitmap = clipStill(s, sourceMs(s.startMs, s.speed, into).coerceIn(s.startMs, s.endMs), Int.MAX_VALUE)
        val uri = withContext(Dispatchers.IO) { saveToGallery(app, "image/jpeg") { s.adjust.appliedTo(s.shaped(bitmap)).compress(Bitmap.CompressFormat.JPEG, 95, it) } }
        _effects.send(EditorEffect.Saved(uri, "image/jpeg", share = false))
    }

    /** Video clip [s]'s frame at source time [ms], at its own size but at most [maxSide] long. Call from [work]. */
    private suspend fun clipStill(s: Segment, ms: Long, maxSide: Int) = withContext(Dispatchers.IO) {
        runCatching { videoStill(app, s.uri, ms, minOf(maxSide, maxOf(s.width, s.height))) }.getOrNull()
    } ?: throw UserError(R.string.error_read_video)

    /** Sets the transition into clip [index] from the one before it, or with [all] into every clip after the first. */
    fun setTransition(index: Int, transition: Transition, all: Boolean = false) =
        updateClip { copy(segments = segments.mapIndexed { i, s -> if (i > 0 && (all || i == index)) s.copy(transition = transition) else s }) }

    fun duplicateClip(index: Int) {
        updateClip { copy(segments = segments.take(index + 1) + segments[index] + segments.drop(index + 1)) }
        selectClip(index + 1)
    }

    /** Removes clip [index], unless it's the only one. */
    fun deleteClip(index: Int) {
        updateClip { if (segments.size > 1) copy(segments = segments.filterIndexed { i, _ -> i != index }) else this }
        selectClip(null)
    }

    fun moveClip(from: Int, to: Int) {
        if (from == to) return
        updateClip { copy(segments = segments.moved(from, to)) }
        selectClip(to)
    }

    /** Adds a song on a track of its own from [atMs] (output time; the start when under a second is left) to the end. */
    fun addMusic(uri: Uri, atMs: Long) = work(R.string.busy_adding_music) {
        keepAccess(uri)
        val (info, name) = withContext(Dispatchers.IO) { mediaInfo(app, uri) to displayName(app, uri) }
        if (info.durationMs == 0L) throw UserError(R.string.error_read_audio)
        updateClip { copy(music = music + Music(uri, name, info.durationMs, part = AudioPart(atMs = audioStartMs(atMs, outputDurationMs)))) }
        selectAudio(AudioRef(voice = false, index = _uiState.value.project?.video?.music?.lastIndex ?: 0))
    }

    /** Picks a music or voiceover track on the timeline, or none; it excludes a selected layer and a picked clip. */
    fun selectAudio(ref: AudioRef?) = _uiState.update { it.copy(selectedAudio = ref, selectedId = null, selectedClip = null) }

    /** Sets audio track [ref]'s part (a timeline drag, once at its end). */
    fun setAudioPart(ref: AudioRef, part: AudioPart) = updateClip { withAudio(ref) { listOf(part) } }

    /** Changes the selected audio track's part (volume, fades). */
    fun editAudio(change: AudioPart.() -> AudioPart) {
        val ref = _uiState.value.selectedAudio ?: return
        updateClip { withAudio(ref) { listOf(it.change()) } }
    }

    /** Where the selected song starts (and loops back to) in the song. */
    fun setSongStart(startMs: Long) {
        val ref = _uiState.value.selectedAudio?.takeIf { !it.voice } ?: return
        updateClip { copy(music = music.mapIndexed { i, m -> if (i == ref.index) m.copy(startMs = startMs) else m }) }
    }

    /** Cuts the selected audio track at [atMs] (output time) into two that sound as it did, and picks the second. */
    fun splitAudio(atMs: Long) {
        val ref = _uiState.value.selectedAudio ?: return
        val video = _uiState.value.project?.video ?: return
        val parts = video.audioPart(ref)?.splitAt(atMs, video.outputDurationMs)
        if (parts == null) {
            _effects.trySend(EditorEffect.Message(UiText.Res(R.string.split_audio_outside)))
            return
        }
        updateClip { withAudio(ref) { parts.toList() } }
        selectAudio(ref.copy(index = ref.index + 1))
    }

    fun deleteAudio() {
        val ref = _uiState.value.selectedAudio ?: return
        updateClip { withAudio(ref) { emptyList() } }
        selectAudio(null)
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
        updateClip { copy(voices = voices + VoiceClip(file, durationMs, AudioPart(atMs = start, lengthMs = durationMs))) }
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

    /** The project's export size by the short side, or null for the canvas size. */
    val exportShortSide get() = _uiState.value.project?.let { (it.exportSize ?: _uiState.value.settings.exportSize).shortSide }

    /** Saves the photo as [format] (remembered for next time); a transparent canvas is always a PNG. */
    fun exportPhoto(share: Boolean, format: PhotoFormat) = work(R.string.busy_saving) {
        val p = _uiState.value.project ?: return@work
        val shortSide = exportShortSide
        val scale = shortSide?.let { min(1f, it / min(p.width, p.height).toFloat()) } ?: 1f
        val backdrop = p.backdrop()
        val bitmap = renderLayers(p.width, p.height, p.background, p.layers, scale, backdrop = backdrop)
        if (p.transparent) {
            save(PhotoFormat.Png, bitmap, share)
        } else {
            updateSettings { copy(photoFormat = format) }
            save(format, bitmap, share)
        }
    }

    private suspend fun save(format: PhotoFormat, bitmap: Bitmap, share: Boolean) {
        val compress = if (format == PhotoFormat.Png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val uri = withContext(Dispatchers.IO) { saveToGallery(app, format.mime) { bitmap.compress(compress, 95, it) } }
        _effects.send(EditorEffect.Saved(uri, format.mime, share))
    }

    /** Saves the video as an MP4 at [fps]: the clip's own rate keeps its frames as they are, any other goes through the frame clock. */
    fun exportVideo(share: Boolean, fps: Int, quality: ExportQuality) = work(R.string.busy_saving) {
        val p = _uiState.value.project ?: return@work
        val video = p.video ?: return@work
        val shortSide = exportShortSide
        val forced = fps != outputFrameRate(video.frameRate)
        val clip = if (forced) video.copy(frameRate = fps.toFloat()) else video
        val (w, h) = video.outputSize
        val bitrate = if (quality == ExportQuality.High) highBitrate(w, h, shortSide, clip.frameRate) else null
        saveRendered("mp4", "video/mp4", R.string.busy_saving_video, share) { out ->
            exportVideo(app, clip.composition(p.layers, shortSide, frameClock = forced, background = p.background), out, bitrate) { pct -> _uiState.update { it.copy(progress = pct) } }
        }
    }

    /** Saves the video as a looping GIF, small ([gifShortSide]), at [GIF_FPS] fps, no sound, at most [GIF_MAX_MS] long. */
    fun exportGif(share: Boolean) = work(R.string.busy_saving) {
        val p = _uiState.value.project ?: return@work
        val video = p.video ?: return@work
        if (video.outputDurationMs > GIF_MAX_MS) throw UserError(R.string.gif_too_long, GIF_MAX_MS / 1000)
        val (w, h) = video.outputSize
        val shortSide = gifShortSide(w, h)
        val clip = video.copy(frameRate = GIF_FPS.toFloat())
        val mp4 = File(app.cacheDir, "gif.mp4")
        try {
            saveRendered("gif", "image/gif", R.string.busy_saving_gif, share) { out ->
                // Rendered small by Media3 first, so the GIF shows exactly what the MP4 export would; half the ring each.
                exportVideo(app, clip.composition(p.layers, shortSide, frameClock = true, background = p.background), mp4, highBitrate(w, h, shortSide, clip.frameRate)) { pct ->
                    _uiState.update { it.copy(progress = pct / 2) }
                }
                out.outputStream().buffered().use { o -> mp4ToGif(mp4, o) { f -> _uiState.update { it.copy(progress = 50 + (f * 50).toInt()) } } }
            }
        } finally {
            mp4.delete()
        }
    }

    /**
     * Makes a file with [render] behind a cancellable progress ring, then copies it to the gallery as [mime]. A cancel
     * during the gallery copy would leave a half-written file there; the copy takes a moment at most.
     */
    private suspend fun saveRendered(extension: String, mime: String, @StringRes label: Int, share: Boolean, render: suspend (File) -> Unit) {
        val out = File(app.cacheDir, "export.$extension")
        try {
            _uiState.update { it.copy(busy = UiText.Res(label), progress = 0, cancellable = true) }
            render(out)
            _uiState.update { it.copy(cancellable = false) }
            val uri = withContext(Dispatchers.IO) { saveToGallery(app, mime) { o -> out.inputStream().use { it.copyTo(o) } } }
            _effects.send(EditorEffect.Saved(uri, mime, share))
        } finally {
            out.delete()
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
        const val BLANK_SIDE = 1080
        const val MAX_LAYER = 1440
        const val THUMB_SIZE = 360f
        const val PICK_SIZE = 1080f
        const val MAX_UNDO = 50
        const val FILMSTRIP_FRAMES = 10
        const val FRAME_SIZE = 480
        const val MIN_VIDEO_LAYER_MS = 1000L
        const val PHOTO_CLIP_MS = 3000L
        /** How long a photo clip can be trimmed out to. */
        const val MAX_PHOTO_CLIP_MS = 30_000L
    }
}
