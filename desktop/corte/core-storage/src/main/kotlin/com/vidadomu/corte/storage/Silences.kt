package com.vidadomu.corte.storage

import com.vidadomu.corte.model.Clip
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * Silencios a partir de los picos ya calculados ([Waveform], nivel 0 = 256 muestras por bloque): sin volver a
 * decodificar el audio. Un tramo es silencio si TODOS sus picos quedan por debajo del umbral durante al menos [minUs].
 */
object Silences {
    data class Range(val startUs: Long, val endUs: Long)

    /** Silencios en tiempo de la FUENTE (dentro del archivo) entre [fromUs] y [toUs]. */
    fun inSource(w: Waveform, thresholdDb: Double = -40.0, minUs: Long = 400_000, fromUs: Long = 0, toUs: Long = w.durationUs): List<Range> {
        val data = w.levels.firstOrNull() ?: return emptyList()
        val bucketFrames = w.bucketFrames(0)
        val bucketUs = bucketFrames * 1_000_000L / w.sampleRate
        val buckets = data.size / 2
        val first = (fromUs / bucketUs).toInt().coerceIn(0, buckets)
        val last = ((toUs + bucketUs - 1) / bucketUs).toInt().coerceIn(0, buckets)
        val out = ArrayList<Range>()
        var runStart = -1
        fun close(end: Int) {
            if (runStart < 0) return
            val s = max(runStart * bucketUs, fromUs)
            val e = minOf(end * bucketUs, toUs)
            if (e - s >= minUs) out += Range(s, e)
            runStart = -1
        }
        for (b in first until last) {
            val peak = max(abs(data[2 * b].toInt()), abs(data[2 * b + 1].toInt())) / 32768.0
            val db = if (peak <= 0.0) -120.0 else 20 * log10(peak)
            if (db <= thresholdDb) { if (runStart < 0) runStart = b } else close(b)
        }
        close(last)
        return out
    }

    /**
     * Silencios de un clip en tiempo de la LÍNEA DE TIEMPO, recortados [padUs] por cada lado para no comerse
     * el arranque o el final de una palabra.
     */
    fun inTimeline(clip: Clip, w: Waveform, thresholdDb: Double = -40.0, minUs: Long = 400_000, padUs: Long = 120_000): List<Range> =
        inSource(w, thresholdDb, minUs, clip.sourceInUs, clip.sourceOutUs).mapNotNull { r ->
            val s = clip.timelineStartUs + clip.speed.inverseScale(r.startUs - clip.sourceInUs) + padUs
            val e = clip.timelineStartUs + clip.speed.inverseScale(r.endUs - clip.sourceInUs) - padUs
            if (e - s > 0) Range(s.coerceAtLeast(clip.timelineStartUs), e.coerceAtMost(clip.endUs)) else null
        }
}
