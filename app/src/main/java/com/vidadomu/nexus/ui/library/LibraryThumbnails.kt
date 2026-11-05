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

    suspend fun load(context: android.content.Context, uri: String, video: Boolean): Thumb {
        cache.get(uri)?.let { return Thumb.Ready(it) }
        val file = fileOf(uri)
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                runCatching {
                    val bitmap = if (video) videoFrame(context, file, uri) else imageAt(context, file, uri)
                    if (bitmap != null) { cache.put(uri, bitmap); Thumb.Ready(bitmap) } else Thumb.Failed
                }.getOrDefault(Thumb.Failed)
            }
        }
    }

    private fun imageAt(context: android.content.Context, file: File?, uri: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val parsed = Uri.parse(uri)
        fun <T> open(block: (java.io.InputStream) -> T): T? =
            if (file != null) (if (file.isFile) file.inputStream().use(block) else null)
            else context.contentResolver.openInputStream(parsed)?.use(block)
        open { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET && bounds.outHeight / (sample * 2) >= TARGET) sample *= 2
        return open { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun videoFrame(context: android.content.Context, file: File?, uri: String): Bitmap? {
        if (file != null && !file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            if (file != null) retriever.setDataSource(file.absolutePath) else retriever.setDataSource(context, Uri.parse(uri))
            val frame = representativeFrame(retriever) ?: return null
            val scale = TARGET.toFloat() / maxOf(frame.width, frame.height)
            if (scale < 1f) Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true) else frame
        } finally { runCatching { retriever.release() } }
    }

    /**
     * Auditoria 05-11: el fotograma de 0,5 s con CLOSEST_SYNC caia en el primer fotograma clave, que en muchos videos es
     * un fundido a negro o a blanco (portadas vacias en Biblioteca y carpetas). Se prueba el 10 % del video (fotograma
     * clave, barato) y, solo si sale casi liso, el 30 % y el 60 % con decodificacion exacta; gana el de mas contraste.
     */
    private fun representativeFrame(retriever: MediaMetadataRetriever): Bitmap? {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
        var best: Bitmap? = null
        var bestSpread = -1
        for ((ms, option) in VideoFrameQuality.candidates(durationMs)) {
            val frame = runCatching { retriever.getFrameAtTime(ms * 1000L, option) }.getOrNull() ?: continue
            // Si no se puede medir, se acepta el fotograma: un fallo aqui no debe dejar el video sin portada ni ocultarlo.
            val spread = runCatching { VideoFrameQuality.lumaSpread(frame) }.getOrDefault(VideoFrameQuality.MIN_SPREAD)
            if (spread > bestSpread) { best?.recycle(); best = frame; bestSpread = spread } else frame.recycle()
            if (bestSpread >= VideoFrameQuality.MIN_SPREAD) break
        }
        return best ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }
}

/** Eleccion del fotograma de portada: momentos a probar y cuanto contraste tiene un fotograma. */
internal object VideoFrameQuality {
    /** Diferencia (0-255) entre los tonos del 5 % mas oscuro y del 5 % mas claro: por debajo, el fotograma es casi liso. */
    const val MIN_SPREAD = 40

    fun candidates(durationMs: Long?): List<Pair<Long, Int>> = if (durationMs == null) listOf(
        500L to MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        1_500L to MediaMetadataRetriever.OPTION_CLOSEST,
        3_000L to MediaMetadataRetriever.OPTION_CLOSEST)
    else listOf(
        (durationMs / 10).coerceAtLeast(500L).coerceAtMost(durationMs) to MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        durationMs * 3 / 10 to MediaMetadataRetriever.OPTION_CLOSEST,
        durationMs * 6 / 10 to MediaMetadataRetriever.OPTION_CLOSEST)

    fun lumaSpread(frame: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(frame, 16, 16, true)
        val pixels = IntArray(16 * 16)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        if (small !== frame) small.recycle()
        return spreadOf(pixels)
    }

    internal fun spreadOf(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0
        val luma = pixels.map { ((it shr 16 and 0xFF) * 299 + (it shr 8 and 0xFF) * 587 + (it and 0xFF) * 114) / 1000 }.sorted()
        val low = luma[luma.size * 5 / 100]
        val high = luma[(luma.size * 95 / 100).coerceAtMost(luma.size - 1)]
        return high - low
    }
}

/**
 * Local files that cannot be opened (deleted, moved or never copied to this phone). They are hidden from the grid
 * instead of leaving an empty placeholder. Only local files are recorded: a remote URL may fail just because there
 * is no connection, and that must not hide the item.
 */
internal object LibraryUnavailable {
    private val keys = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    fun count(): Int = keys.size
    fun mark(key: String) { if (!keys.containsKey(key)) keys[key] = true }
    fun isUnavailable(key: String?): Boolean = key != null && keys.containsKey(key)
}

@Composable
internal fun rememberThumb(uri: String?, video: Boolean): Thumb {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val state by produceState<Thumb>(if (uri == null) Thumb.Failed else Thumb.Loading, uri, video) {
        value = if (uri == null) Thumb.Failed else LibraryThumbnails.load(context, uri, video)
        if (uri != null && value is Thumb.Failed) LibraryUnavailable.mark(uri)
    }
    return state
}
