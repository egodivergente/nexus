package com.vidadomu.nexus.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Result of loading a library thumbnail from the phone's own files. */
internal sealed interface Thumb {
    data object Loading : Thumb
    data object Failed : Thumb
    class Ready(val bitmap: Bitmap) : Thumb
}

/**
 * Thumbnails read straight from the local copy (images decoded at tile size, videos from one frame), with a small
 * in-memory cache. It reports a failure instead of staying blank, so a tile either shows its picture or says why not.
 */
internal object LibraryThumbnails {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 10).toInt().coerceAtLeast(8 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val gate = Semaphore(3)
    private const val TARGET = 400

    private fun fileOf(uri: String): File? = runCatching {
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            "file", null -> File(parsed.path ?: return null)
            else -> null
        }
    }.getOrNull()

    suspend fun load(uri: String, video: Boolean): Thumb {
        cache.get(uri)?.let { return Thumb.Ready(it) }
        val file = fileOf(uri)
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                runCatching {
                    val bitmap = if (video) videoFrame(file, uri) else imageAt(file, uri)
                    if (bitmap != null) { cache.put(uri, bitmap); Thumb.Ready(bitmap) } else Thumb.Failed
                }.getOrDefault(Thumb.Failed)
            }
        }
    }

    private fun imageAt(file: File?, uri: String): Bitmap? {
        if (file == null || !file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET && bounds.outHeight / (sample * 2) >= TARGET) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun videoFrame(file: File?, uri: String): Bitmap? {
        if (file == null || !file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val scale = TARGET.toFloat() / maxOf(frame.width, frame.height)
            if (scale < 1f) Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true) else frame
        } finally { runCatching { retriever.release() } }
    }
}

@Composable
internal fun rememberThumb(uri: String?, video: Boolean): Thumb {
    val state by produceState<Thumb>(if (uri == null) Thumb.Failed else Thumb.Loading, uri, video) {
        value = if (uri == null) Thumb.Failed else LibraryThumbnails.load(uri, video)
    }
    return state
}
