package com.ndelic.kadar

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.CookieManager
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

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
                if (connection.responseCode !in 200..299) error("Download failed (HTTP ${connection.responseCode})")
                if (connection.contentLengthLong > MAX_DOWNLOAD) error("Image is too large")
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
        else -> error("This image can't be downloaded")
    }
    return try {
        decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxSide)
    } catch (e: IOException) {
        throw IllegalStateException("That link isn't an image Kadar can open", e)
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

/** Display size (rotation applied) and duration; width and height are 0 for audio. */
data class MediaInfo(val width: Int, val height: Int, val durationMs: Long)

fun mediaInfo(context: Context, uri: Uri): MediaInfo = MediaMetadataRetriever().use { r ->
    r.setDataSource(context, uri)
    fun int(key: Int) = r.extractMetadata(key)?.toIntOrNull() ?: 0
    val w = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
    val h = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
    val rotated = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
    val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
    if (rotated) MediaInfo(h, w, duration) else MediaInfo(w, h, duration)
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
    val uri = resolver.insert(collection, values) ?: error("Could not create a file in the gallery")
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
            } ?: error("The background remover is still downloading. Check the internet and try again.")
        }
        return segmenter.process(InputImage.fromBitmap(bitmap, 0)).await().foregroundBitmap
            ?: error("No subject found")
    } finally {
        segmenter.close()
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
