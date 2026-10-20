package com.vidadomu.corte.model

import kotlinx.serialization.Serializable

/** Every animatable parameter goes through the same generic keyframe engine. */
@Serializable
enum class AnimParam(val min: Double, val max: Double) {
    POSITION_X(-4.0, 4.0),
    POSITION_Y(-4.0, 4.0),
    SCALE(0.01, 20.0),
    ROTATION(-3600.0, 3600.0),
    OPACITY(0.0, 1.0),
    GAIN_DB(-96.0, 24.0),
}

/** Interpolation used for the segment that LEAVES a keyframe. */
@Serializable
enum class Interpolation { LINEAR, HOLD, EASE_IN, EASE_OUT, EASE_IN_OUT }

/**
 * [timeUs] is relative to the clip start (clip-local timeline time). Keyframes may lie outside
 * [0, duration): that is what lets split and trim preserve curves exactly, including eased segments.
 */
@Serializable
data class Keyframe(val timeUs: Long, val value: Double, val interpolation: Interpolation = Interpolation.LINEAR)

@Serializable
data class KeyframeTrack(val keys: List<Keyframe> = emptyList()) {
    init {
        for (i in 1 until keys.size) require(keys[i - 1].timeUs < keys[i].timeUs) { "keyframes must be strictly sorted" }
    }

    val isEmpty: Boolean get() = keys.isEmpty()

    fun valueAt(timeUs: Long): Double? {
        if (keys.isEmpty()) return null
        if (timeUs <= keys.first().timeUs) return keys.first().value
        if (timeUs >= keys.last().timeUs) return keys.last().value
        var lo = 0
        var hi = keys.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (keys[mid].timeUs <= timeUs) lo = mid else hi = mid
        }
        val a = keys[lo]
        val b = keys[hi]
        val t = (timeUs - a.timeUs).toDouble() / (b.timeUs - a.timeUs).toDouble()
        val e = ease(a.interpolation, t)
        return a.value + (b.value - a.value) * e
    }

    fun withKey(key: Keyframe): KeyframeTrack {
        val list = keys.filter { it.timeUs != key.timeUs } + key
        return KeyframeTrack(list.sortedBy { it.timeUs })
    }

    fun withoutKeyAt(timeUs: Long): KeyframeTrack = KeyframeTrack(keys.filter { it.timeUs != timeUs })

    fun shifted(deltaUs: Long): KeyframeTrack =
        if (deltaUs == 0L) this else KeyframeTrack(keys.map { it.copy(timeUs = it.timeUs + deltaUs) })

    /**
     * Drops keys that cannot influence values inside [0, durationUs): keeps everything inside plus the
     * nearest key on each side. The evaluated curve inside the range is unchanged.
     */
    fun prunedTo(durationUs: Long): KeyframeTrack {
        if (keys.size <= 2) return this
        val lastBefore = keys.indexOfLast { it.timeUs < 0 }
        val firstAfter = keys.indexOfFirst { it.timeUs >= durationUs }
        val from = if (lastBefore == -1) 0 else lastBefore
        val to = if (firstAfter == -1) keys.size - 1 else firstAfter
        if (from == 0 && to == keys.size - 1) return this
        return KeyframeTrack(keys.subList(from, to + 1).toList())
    }

    companion object {
        fun ease(interpolation: Interpolation, t: Double): Double = when (interpolation) {
            Interpolation.LINEAR -> t
            Interpolation.HOLD -> 0.0
            Interpolation.EASE_IN -> t * t
            Interpolation.EASE_OUT -> 1 - (1 - t) * (1 - t)
            Interpolation.EASE_IN_OUT -> if (t < 0.5) 2 * t * t else 1 - Math.pow(-2 * t + 2, 2.0) / 2
        }
    }
}
