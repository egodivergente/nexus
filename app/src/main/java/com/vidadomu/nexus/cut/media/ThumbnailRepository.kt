package com.vidadomu.nexus.cut.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Timeline thumbnails extracted from the real media. Times are quantised to [stepUs] multiples so a
 * zoom level reuses frames; memory is bounded by an LRU cache. [version] ticks when new frames
 * arrive so the timeline redraws.
 *
 * La línea de tiempo pide en cada dibujo las miniaturas que tiene a la vista. Las que se pidieron y ya no
 * se ven (se pasó de largo al desplazar) se descartan al llegarles el turno: manda lo que hay en pantalla.
 */
class ThumbnailRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val inFlight = HashSet<String>()
    /** Se lee al dibujar (hilo principal) y se escribe en el trabajador. */
    private val failedMedia: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** Solo en el trabajador. Orden de acceso: se cierra el que lleva más tiempo sin usarse. */
    private val retrievers = LinkedHashMap<String, MediaMetadataRetriever>(8, 0.75f, true)
    /** Última vez que el dibujo pidió cada miniatura pendiente, y última petición de cualquiera. */
    private val wantedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var lastRequestAt = 0L
    @Volatile private var released = false
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** La altura forma parte de la clave: una pista plegada no reutiliza (ni deja) miniaturas de otro tamaño. */
    private fun key(mediaId: String, t: Long, heightPx: Int) = "$mediaId@$t@${heightPx.coerceIn(24, 240)}"

    /** Returns the cached bitmap or schedules extraction and returns null. */
    fun get(ref: MediaRef, sourceUs: Long, stepUs: Long, heightPx: Int): Bitmap? {
        if (ref.kind == MediaKind.AUDIO || ref.id in failedMedia) return null
        val q = if (ref.kind == MediaKind.IMAGE) 0 else (sourceUs / stepUs) * stepUs
        val k = key(ref.id, q, heightPx)
        val now = System.nanoTime()
        lastRequestAt = now
        cache.get(k)?.let { return it }
        if (released) return null
        wantedAt[k] = now
        synchronized(inFlight) {
            if (!inFlight.add(k)) return null
            if (inFlight.size > 64) { inFlight.remove(k); wantedAt.remove(k); return null }
        }
        scope.launch(worker) {
            try {
                if (isStale(wantedAt[k], lastRequestAt)) return@launch
                val bmp = extract(ref, q, heightPx)
                if (bmp != null) { cache.put(k, bmp); _version.value++ }
            } finally {
                wantedAt.remove(k)
                synchronized(inFlight) { inFlight.remove(k) }
            }
        }
        return null
    }

    private fun extract(ref: MediaRef, t: Long, heightPx: Int): Bitmap? {
        val h = heightPx.coerceIn(24, 240)
        val aspect = if (ref.displayHeight > 0) ref.displayWidth.toFloat() / ref.displayHeight else 16f / 9f
        val w = (h * aspect).toInt().coerceAtLeast(1)
        if (ref.kind == MediaKind.IMAGE) {
            return try {
                appContext.contentResolver.openInputStream(Uri.parse(ref.uri))?.use { s ->
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = maxOf(1, ref.height / (h * 2))
                    }
                    android.graphics.BitmapFactory.decodeStream(s, null, opts)?.let { Bitmap.createScaledBitmap(it, w, h, true) }
                }
            } catch (e: java.io.IOException) {
                Log.w(TAG, "image thumb", e); failedMedia += ref.id; null
            }
        }
        val mmr = retriever(ref) ?: return null
        return try {
            mmr.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h)
        } catch (e: RuntimeException) {
            Log.w(TAG, "frame at $t of ${ref.displayName}", e)
            null
        }
    }

    private fun retriever(ref: MediaRef): MediaMetadataRetriever? {
        retrievers[ref.id]?.let { return it }
        val m = MediaMetadataRetriever()
        return try {
            m.setDataSource(appContext, Uri.parse(ref.uri))
            retrievers[ref.id] = m
            while (retrievers.size > 4) {
                val first = retrievers.keys.first()
                retrievers.remove(first)?.release()
            }
            m
        } catch (e: RuntimeException) {
            Log.w(TAG, "retriever for ${ref.displayName}", e)
            m.release()
            failedMedia += ref.id
            null
        }
    }

    fun invalidate(mediaId: String) {
        scope.launch(worker) {
            retrievers.remove(mediaId)?.release()
            failedMedia.remove(mediaId)
            cache.snapshot().keys.filter { it.startsWith("$mediaId@") }.forEach { cache.remove(it) }
            _version.value++
        }
    }

    /**
     * Cierra los lectores de vídeo. Se llama al destruirse el editor, cuando [scope] ya está cancelado: por eso
     * no se lanza en él (no llegaría a ejecutarse y los lectores quedarían abiertos).
     */
    fun release() {
        released = true
        CoroutineScope(worker + NonCancellable).launch {
            retrievers.values.forEach { runCatching { it.release() } }
            retrievers.clear()
            cache.evictAll()
        }
    }

    internal companion object {
        private const val TAG = "Thumbs"
        private const val STALE_NANOS = 300_000_000L

        /** Pendiente que los dibujos posteriores ya no han vuelto a pedir: se salta. */
        internal fun isStale(wantedAt: Long?, lastRequestAt: Long): Boolean =
            wantedAt == null || lastRequestAt - wantedAt > STALE_NANOS
    }
}
