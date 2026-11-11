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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Timeline thumbnails extracted from the real media. Times are quantised to [stepUs] multiples so a
 * zoom level reuses frames; memory is bounded by an LRU cache. [version] ticks when new frames
 * arrive so the timeline redraws.
 */
class ThumbnailRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val inFlight = HashSet<String>()
    private val failedMedia = HashSet<String>()
    private val retrievers = LinkedHashMap<String, MediaMetadataRetriever>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun key(mediaId: String, t: Long) = "$mediaId@$t"

    /** Returns the cached bitmap or schedules extraction and returns null. */
    fun get(ref: MediaRef, sourceUs: Long, stepUs: Long, heightPx: Int): Bitmap? {
        if (ref.kind == MediaKind.AUDIO || ref.id in failedMedia) return null
        val q = if (ref.kind == MediaKind.IMAGE) 0 else (sourceUs / stepUs) * stepUs
        val k = key(ref.id, q)
        cache.get(k)?.let { return it }
        synchronized(inFlight) {
            if (!inFlight.add(k)) return null
            if (inFlight.size > 64) { inFlight.remove(k); return null }
        }
        scope.launch(worker) {
            try {
                val bmp = extract(ref, q, heightPx)
                if (bmp != null) { cache.put(k, bmp); _version.value++ }
            } finally {
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

    fun release() {
        scope.launch(worker) {
            retrievers.values.forEach { it.release() }
            retrievers.clear()
        }
    }

    private companion object {
        const val TAG = "Thumbs"
    }
}
