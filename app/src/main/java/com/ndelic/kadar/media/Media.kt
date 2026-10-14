package com.ndelic.kadar.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.FontFamily
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextPaint
import android.util.Base64
import android.webkit.CookieManager
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.ndelic.kadar.R
import com.ndelic.kadar.model.UiText
import com.ndelic.kadar.model.UserError
import com.ndelic.kadar.model.opaqueBounds
import com.ndelic.kadar.video.Music
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap = decode(ImageDecoder.createSource(context.contentResolver, uri), maxSide)

/**
 * Downloads an image picked in the in-app browser (an http(s) or base64 `data:` URL, as search pages often inline thumbnails).
 * Sends the browser's cookies so images behind a login or consent page still load.
 */
fun downloadImage(url: String, maxSide: Int): Bitmap {
    val bytes = when {
        url.startsWith("data:") && url.substringBefore(',').endsWith(";base64") -> Base64.decode(url.substringAfter(','), Base64.DEFAULT)
        url.startsWith("https://") || url.startsWith("http://") -> fetch(url)
        else -> throw UserError(R.string.error_cant_download)
    }
    return try {
        decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxSide)
    } catch (e: IOException) {
        throw UserError(UiText.Res(R.string.error_not_image), e)
    }
}

private const val MAX_DOWNLOAD = 50L * 1024 * 1024

/**
 * GETs an http(s) URL with the browser's cookies for that exact URL. Redirects are followed by hand so cookies are
 * re-read per host instead of being carried to wherever a redirect points; the body is capped at [MAX_DOWNLOAD].
 */
private fun fetch(start: String): ByteArray {
    var url = start
    repeat(5) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
            val code = connection.responseCode
            if (code in 300..399) {
                val next = connection.getHeaderField("Location") ?: throw UserError(R.string.error_download_http, code)
                url = URL(URL(url), next).toString()
                if (!url.startsWith("https://") && !url.startsWith("http://")) throw UserError(R.string.error_cant_download)
                return@repeat
            }
            if (code !in 200..299) throw UserError(R.string.error_download_http, code)
            if (connection.contentLengthLong > MAX_DOWNLOAD) throw UserError(R.string.error_image_too_large)
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_DOWNLOAD) throw UserError(R.string.error_image_too_large)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
    throw UserError(R.string.error_cant_download)
}

/** Decodes into a software ARGB_8888 bitmap (drawable on a plain Canvas and readable by ML Kit), longest side at most [maxSide]. */
private fun decode(source: ImageDecoder.Source, maxSide: Int): Bitmap {
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val f = maxSide / max(info.size.width, info.size.height).toFloat()
        if (f < 1) decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
    }
    return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
}

fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
    val f = maxSide / max(bitmap.width, bitmap.height).toFloat()
    if (f >= 1) return bitmap
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt(), (bitmap.height * f).roundToInt(), true)
}

/**
 * [bitmap] mirrored ([flipX], [flipY]) and turned by [degrees] about its center into a new [outW]×[outH] bitmap whose
 * center is ([dx], [dy]) from the turned picture's center; what the picture doesn't cover is transparent.
 */
fun turnBitmap(bitmap: Bitmap, degrees: Float, outW: Int, outH: Int, dx: Float = 0f, dy: Float = 0f, flipX: Boolean = false, flipY: Boolean = false): Bitmap =
    Bitmap.createBitmap(outW.coerceAtLeast(1), outH.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply {
            translate(outW / 2f - dx, outH / 2f - dy)
            rotate(degrees)
            scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
            drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        // Quarter turns of an opaque photo leave no empty corners: keep it a JPEG in the project.
        if (degrees % 90f == 0f && !bitmap.hasAlpha()) it.setHasAlpha(false)
    }

/** Crops away transparent borders. Returns the crop and its `[left, top, right, bottom)` in [src], or null if nothing is visible. */
fun trimTransparent(src: Bitmap): Pair<Bitmap, IntArray>? {
    val pixels = IntArray(src.width * src.height)
    src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
    val b = opaqueBounds(pixels, src.width, src.height) ?: return null
    return Bitmap.createBitmap(src, b[0], b[1], b[2] - b[0], b[3] - b[1]) to b
}

/** Draws [emoji] from the system emoji font into a tightly cropped bitmap. */
fun renderEmoji(emoji: String, size: Float = 512f): Bitmap {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size }
    val metrics = paint.fontMetricsInt
    val bitmap = Bitmap.createBitmap(
        ceil(paint.measureText(emoji)).toInt().coerceAtLeast(1),
        (metrics.descent - metrics.ascent).coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    Canvas(bitmap).drawText(emoji, 0f, -metrics.ascent.toFloat(), paint)
    return trimTransparent(bitmap)?.first ?: bitmap
}

/** Display size (rotation applied), duration and average frame rate; width, height and frame rate are 0 for audio. */
data class MediaInfo(val width: Int, val height: Int, val durationMs: Long, val frameRate: Float)

fun mediaInfo(context: Context, uri: Uri): MediaInfo = MediaMetadataRetriever().use { r ->
    r.setDataSource(context, uri)
    fun int(key: Int) = r.extractMetadata(key)?.toIntOrNull() ?: 0
    val w = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
    val h = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
    val rotated = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
    val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
    val fps = if (duration > 0) int(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT) * 1000f / duration else 0f
    if (rotated) MediaInfo(h, w, duration, fps) else MediaInfo(w, h, duration, fps)
}

/** The MIME types of [uri]'s tracks, such as "video/av01" and "audio/opus". */
fun trackMimes(context: Context, uri: Uri): List<String> {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        return (0 until extractor.trackCount).mapNotNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
    } finally {
        extractor.release()
    }
}

/** A frame near [timeMs], small enough for a thumbnail. */
fun videoFrame(context: Context, uri: Uri, timeMs: Long): Bitmap? = MediaMetadataRetriever().use { r ->
    r.setDataSource(context, uri)
    r.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
}

/** Starts recording the microphone into [file] as AAC in an MP4 container; the caller stops and releases the recorder. */
fun startVoiceRecording(context: Context, file: File): MediaRecorder {
    val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
    try {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioChannels(1)
        recorder.setAudioSamplingRate(44_100)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setOutputFile(file)
        recorder.prepare()
        recorder.start()
    } catch (e: Exception) {
        // A busy or missing microphone fails here; don't leak the recorder.
        recorder.release()
        throw e
    }
    return recorder
}

fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "Music"

private const val MAX_FONT = 20L * 1024 * 1024

/**
 * Copies a picked font file into [dir] as `<name>.ttf` or `.otf`, named after the file (made unique against [taken]),
 * and returns it. The file is untrusted: it's capped at [MAX_FONT] and kept only if Android can load it as a font.
 */
fun importFont(context: Context, uri: Uri, dir: File, taken: Set<String>): File {
    val picked = displayName(context, uri)
    val base = picked.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(32).ifEmpty { "Font" }
    var name = base
    var n = 2
    while (name in taken) name = "$base ${n++}"
    val ext = if (picked.endsWith(".otf", ignoreCase = true)) "otf" else "ttf"
    dir.mkdirs()
    val tmp = File(dir, "import.tmp")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: throw UserError(R.string.error_font_unreadable)
        input.use { i ->
            tmp.outputStream().use { o ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = i.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FONT) throw UserError(R.string.error_font_too_large)
                    o.write(buffer, 0, read)
                }
            }
        }
        loadFont(tmp) ?: throw UserError(R.string.error_font_unreadable)
        val file = File(dir, "$name.$ext")
        if (!tmp.renameTo(file)) throw UserError(R.string.error_font_unreadable)
        return file
    } finally {
        tmp.delete()
    }
}

/** The font in [file], or null if it isn't one Android can load. */
fun loadFont(file: File): Typeface? = try {
    Typeface.CustomFallbackBuilder(FontFamily.Builder(android.graphics.fonts.Font.Builder(file).build()).build()).build()
} catch (e: Exception) {
    // IOException for unreadable files, IllegalArgumentException for data that isn't a font.
    null
}

/** Writes a new file into Pictures/Kadar or Movies/Kadar. No storage permission needed on Android 10+. */
fun saveToGallery(context: Context, mime: String, write: (OutputStream) -> Unit): Uri {
    val video = mime.startsWith("video/")
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Kadar_${System.currentTimeMillis()}")
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Kadar")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values) ?: throw UserError(R.string.error_gallery_file)
    try {
        resolver.openOutputStream(uri)!!.use(write)
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
    return uri
}

/** Returns the subject on a transparent background, same size as [bitmap]. Runs on-device with ML Kit. */
suspend fun cutOutSubject(context: Context, bitmap: Bitmap): Bitmap {
    val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    try {
        // The model ships through Play services, not the APK; a sideloaded app has to ask for it once.
        val installer = ModuleInstall.getClient(context)
        if (!installer.areModulesAvailable(segmenter).await().areModulesAvailable()) {
            installer.installModules(ModuleInstallRequest.Builder().addApi(segmenter).build()).await()
            // The task above only means the download started; wait for it (a few MB) to finish.
            withTimeoutOrNull(120_000) {
                while (!installer.areModulesAvailable(segmenter).await().areModulesAvailable()) delay(1000)
            } ?: throw UserError(R.string.error_bg_model)
        }
        return segmenter.process(InputImage.fromBitmap(bitmap, 0)).await().foregroundBitmap
            ?: throw UserError(R.string.error_no_subject)
    } finally {
        segmenter.close()
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
