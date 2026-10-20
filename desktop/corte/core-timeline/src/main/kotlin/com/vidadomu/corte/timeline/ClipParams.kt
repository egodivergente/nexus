package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.AnimParam
import com.vidadomu.corte.model.Clip
import com.vidadomu.corte.model.Keyframe
import com.vidadomu.corte.model.KeyframeTrack
import kotlin.math.abs

/** The static (non-animated) value behind each animatable parameter, and its user-facing name. */
object ClipParams {
    fun label(p: AnimParam): String = when (p) {
        AnimParam.POSITION_X -> "Posición X"
        AnimParam.POSITION_Y -> "Posición Y"
        AnimParam.SCALE -> "Escala"
        AnimParam.ROTATION -> "Rotación"
        AnimParam.OPACITY -> "Opacidad"
        AnimParam.GAIN_DB -> "Volumen"
    }

    fun staticValue(c: Clip, p: AnimParam): Double = when (p) {
        AnimParam.POSITION_X -> c.video.positionX
        AnimParam.POSITION_Y -> c.video.positionY
        AnimParam.SCALE -> c.video.scale
        AnimParam.ROTATION -> c.video.rotationDeg
        AnimParam.OPACITY -> c.video.opacity
        AnimParam.GAIN_DB -> c.audio.gainDb
    }

    fun withStatic(c: Clip, p: AnimParam, value: Double): Clip {
        val v = value.coerceIn(p.min, p.max)
        return when (p) {
            AnimParam.POSITION_X -> c.copy(video = c.video.copy(positionX = v))
            AnimParam.POSITION_Y -> c.copy(video = c.video.copy(positionY = v))
            AnimParam.SCALE -> c.copy(video = c.video.copy(scale = v))
            AnimParam.ROTATION -> c.copy(video = c.video.copy(rotationDeg = v))
            AnimParam.OPACITY -> c.copy(video = c.video.copy(opacity = v))
            AnimParam.GAIN_DB -> c.copy(audio = c.audio.copy(gainDb = v))
        }
    }

    /** Value at timeline time [timelineUs]: the curve if the parameter is animated, else the static value. */
    fun valueAt(c: Clip, p: AnimParam, timelineUs: Long): Double = c.keyframeValue(p, timelineUs) ?: staticValue(c, p)

    fun isAnimated(c: Clip, p: AnimParam): Boolean = c.keyframes[p]?.isEmpty == false

    /** Whether parameter [p] has a keyframe exactly at timeline time [timelineUs]. */
    fun hasKeyAt(c: Clip, p: AnimParam, timelineUs: Long): Boolean =
        c.keyframes[p]?.keys?.any { it.timeUs == timelineUs - c.timelineStartUs } == true

    /**
     * The timeline time the inspector edits at for a playhead. An existing key within
     * [toleranceUs] of the playhead wins (keys can sit off the frame grid after a sub-frame move of
     * the clip); otherwise [snappedUs], the playhead on the frame grid, unless snapping pushed it
     * out of a clip the playhead is in (last partial frame), where the raw playhead is used.
     */
    fun cursor(c: Clip, playheadUs: Long, snappedUs: Long, toleranceUs: Long): Long {
        val near = keyTimes(c).minByOrNull { abs(it - playheadUs) }
        if (near != null && abs(near - playheadUs) <= toleranceUs) return near
        if (!c.contains(snappedUs) && c.contains(playheadUs)) return playheadUs
        return snappedUs
    }

    /**
     * Key times rescaled for a new clip duration (a speed change): the animation stays attached to
     * the same picture. Keys that collide after rounding keep the first one.
     */
    fun rescaleKeys(c: Clip, newDurationUs: Long): Clip {
        if (c.keyframes.isEmpty() || c.durationUs <= 0 || newDurationUs == c.durationUs) return c
        val f = newDurationUs.toDouble() / c.durationUs
        val scaled = c.keyframes.mapValues { (_, t) ->
            val out = ArrayList<Keyframe>(t.keys.size)
            for (k in t.keys) {
                val nt = Math.round(k.timeUs * f).coerceIn(0L, maxOf(0L, newDurationUs - 1))
                if (out.isEmpty() || nt > out.last().timeUs) out += k.copy(timeUs = nt)
            }
            KeyframeTrack(out)
        }
        return c.copy(keyframes = scaled)
    }

    /** Timeline times of every keyframe of the clip that lies inside it, sorted, without repeats. */
    fun keyTimes(c: Clip): List<Long> =
        c.keyframes.values.flatMap { t -> t.keys.map { it.timeUs } }.filter { it in 0 until c.durationUs }
            .distinct().sorted().map { it + c.timelineStartUs }
}
