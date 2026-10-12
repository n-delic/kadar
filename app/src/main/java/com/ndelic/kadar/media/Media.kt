package com.ndelic.kadar.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
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
        url.startsWith("https://") || url.startsWith("http://") -> {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
                if (connection.responseCode !in 200..299) throw UserError(R.string.error_download_http, connection.responseCode)
                if (connection.contentLengthLong > MAX_DOWNLOAD) throw UserError(R.string.error_image_too_large)
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
        else -> throw UserError(R.string.error_cant_download)
    }
    return try {
        decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxSide)
    } catch (e: IOException) {
        throw UserError(UiText.Res(R.string.error_not_image), e)
    }
}

private const val MAX_DOWNLOAD = 50L * 1024 * 1024

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

/** A frame near [timeMs], small enough for a thumbnail. */
fun videoFrame(context: Context, uri: Uri, timeMs: Long): Bitmap? = MediaMetadataRetriever().use { r ->
    r.setDataSource(context, uri)
    r.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
}

fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "Music"

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
