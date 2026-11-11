package com.vidadomu.nexus.cut.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.storage.Waveform
import com.vidadomu.nexus.cut.storage.WaveformBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Real waveforms decoded from the media (never drawn from random data). Peaks are cached on disk
 * by content fingerprint, so they survive restarts and are shared between projects; the cache can
 * be deleted at any time.
 */
class WaveformRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val dir = File(context.cacheDir, "waveforms").apply { mkdirs() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val requested = HashSet<String>()

    private val _waveforms = MutableStateFlow<Map<String, Waveform>>(emptyMap())
    /** mediaId -> peaks. */
    val waveforms: StateFlow<Map<String, Waveform>> = _waveforms.asStateFlow()
    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    fun request(ref: MediaRef) {
        if (!ref.hasAudio) return
        synchronized(requested) { if (!requested.add(ref.id)) return }
        scope.launch(worker) {
            val file = File(dir, (ref.fingerprint.ifBlank { ref.id }) + ".cwf")
            val w = try {
                if (file.exists()) Waveform.readFrom(file) else decode(ref).also { it.writeTo(file) }
            } catch (e: IOException) {
                Log.w(TAG, "waveform failed for ${ref.displayName}", e)
                file.delete()
                null
            } catch (e: IllegalStateException) {
                Log.w(TAG, "waveform decoder failed for ${ref.displayName}", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "waveform format not supported for ${ref.displayName}", e)
                null
            }
            if (w != null) _waveforms.update { it + (ref.id to w) } else _failed.update { it + ref.id }
        }
    }

    /** Forget a media (e.g. after relink) so that it is decoded again. */
    fun invalidate(mediaId: String) {
        synchronized(requested) { requested.remove(mediaId) }
        _waveforms.update { it - mediaId }
        _failed.update { it - mediaId }
    }

    private suspend fun decode(ref: MediaRef): Waveform {
        val ex = MediaExtractor()
        ex.setDataSource(appContext, Uri.parse(ref.uri), null)
        var codec: MediaCodec? = null
        try {
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IOException("no audio track")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            var builder: WaveformBuilder? = null
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var shorts = ShortArray(0)
            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = c.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        builder = null
                    }
                    o >= 0 -> {
                        val b = builder ?: WaveformBuilder(sampleRate, channels).also { builder = it }
                        val out = c.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        if (floatPcm) {
                            val fb = out.asFloatBuffer()
                            val n = fb.remaining()
                            if (shorts.size < n) shorts = ShortArray(n)
                            for (k in 0 until n) shorts[k] = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            b.feed(shorts, n)
                        } else {
                            val sb = out.asShortBuffer()
                            val n = sb.remaining()
                            if (shorts.size < n) shorts = ShortArray(n)
                            sb.get(shorts, 0, n)
                            b.feed(shorts, n)
                        }
                        c.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return (builder ?: WaveformBuilder(sampleRate, channels)).build()
        } finally {
            try {
                codec?.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "codec stop", e)
            }
            codec?.release()
            ex.release()
        }
    }

    private companion object {
        const val TAG = "Waveform"
    }
}
