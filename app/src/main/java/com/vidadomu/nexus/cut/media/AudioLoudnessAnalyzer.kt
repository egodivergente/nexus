package com.vidadomu.nexus.cut.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.vidadomu.nexus.cut.command.CutAudioAnalyzer
import com.vidadomu.nexus.cut.command.ErrorCode
import com.vidadomu.nexus.cut.command.LoudnessEnvelope
import com.vidadomu.nexus.cut.command.cutFail
import com.vidadomu.nexus.cut.model.MediaRef
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Volumen real del audio de un archivo, decodificado con MediaCodec (el mismo camino que las formas de
 * onda). Da un valor RMS en dBFS por ventana, sobre la mezcla de los canales; las ventanas sin muestras
 * no se inventan: el sobre termina donde termina el archivo.
 */
class AudioLoudnessAnalyzer(context: Context) : CutAudioAnalyzer {
    private val app = context.applicationContext

    override suspend fun loudness(media: MediaRef, fromUs: Long, toUs: Long, windowUs: Long): LoudnessEnvelope = withContext(Dispatchers.IO) {
        require(windowUs > 0) { "la ventana debe ser positiva" }
        val from = fromUs.coerceAtLeast(0)
        val windows = if (toUs <= from) 0 else ((toUs - from + windowUs - 1) / windowUs).toInt()
        if (windows == 0) return@withContext LoudnessEnvelope(from, windowUs, emptyList())
        if (!MediaProbe.isReachable(app, media.uri)) cutFail(ErrorCode.NOT_FOUND, "El archivo de «${media.displayName}» ya no está disponible.")
        val meter = LoudnessMeter(from, toUs, windowUs, windows)
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try { ex.setDataSource(app, Uri.parse(media.uri), null) }
            catch (e: IOException) { cutFail(ErrorCode.NOT_FOUND, "No se pudo abrir «${media.displayName}».") }
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: cutFail(ErrorCode.PRECONDITION_FAILED, "«${media.displayName}» no tiene pista de sonido.")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            ex.seekTo(from, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var samples = FloatArray(0)
            while (!meter.full) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime > toUs + 500_000) {
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
                    }
                    o >= 0 -> {
                        val out = c.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val n = if (floatPcm) {
                            val fb = out.asFloatBuffer(); val k = fb.remaining()
                            if (samples.size < k) samples = FloatArray(k)
                            fb.get(samples, 0, k); k
                        } else {
                            val sb = out.asShortBuffer(); val k = sb.remaining()
                            if (samples.size < k) samples = FloatArray(k)
                            for (j in 0 until k) samples[j] = sb.get() / 32768f
                            k
                        }
                        meter.feed(info.presentationTimeUs, samples, n, channels.coerceAtLeast(1), sampleRate.coerceAtLeast(1))
                        c.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } catch (e: IllegalStateException) {
            cutFail(ErrorCode.PRECONDITION_FAILED, "No se pudo decodificar el sonido de «${media.displayName}».")
        } catch (e: IllegalArgumentException) {
            cutFail(ErrorCode.PRECONDITION_FAILED, "El formato de sonido de «${media.displayName}» no se puede analizar.")
        } finally {
            try { codec?.stop() } catch (_: IllegalStateException) {}
            codec?.release()
            ex.release()
        }
        meter.envelope()
    }
}

/**
 * Acumula energía por ventana a partir de bloques PCM entrelazados con su instante. Puro, para probarlo
 * en la JVM: la decodificación queda fuera.
 */
internal class LoudnessMeter(private val fromUs: Long, private val toUs: Long, private val windowUs: Long, windows: Int) {
    private val sumSq = DoubleArray(windows)
    private val count = IntArray(windows)
    private var last = -1
    /** Ya se ha pasado del final pedido. */
    var full = false
        private set

    fun feed(ptsUs: Long, interleaved: FloatArray, length: Int, channels: Int, sampleRate: Int) {
        val frames = length / channels
        for (f in 0 until frames) {
            val t = ptsUs + f * 1_000_000L / sampleRate
            if (t < fromUs) continue
            if (t >= toUs) { full = true; return }
            var mix = 0f
            for (ch in 0 until channels) mix += interleaved[f * channels + ch]
            mix /= channels
            val w = ((t - fromUs) / windowUs).toInt()
            if (w >= sumSq.size) { full = true; return }
            sumSq[w] += (mix * mix).toDouble()
            count[w]++
            if (w > last) last = w
        }
    }

    fun envelope(): LoudnessEnvelope {
        val values = (0..last).map { w ->
            if (count[w] == 0) FLOOR_DB else (10.0 * kotlin.math.log10(sumSq[w] / count[w])).coerceAtLeast(FLOOR_DB)
        }
        return LoudnessEnvelope(fromUs, windowUs, values)
    }

    companion object { const val FLOOR_DB = -120.0 }
}
