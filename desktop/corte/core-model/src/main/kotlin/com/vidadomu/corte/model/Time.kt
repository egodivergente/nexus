package com.vidadomu.corte.model

import kotlinx.serialization.Serializable

/**
 * All timeline time is integer microseconds (Long). Floats are never the source of truth.
 * 10 hours = 3.6e10 us, so products with frame-rate numerators (<= 120000) stay far below Long.MAX.
 */
const val US_PER_SECOND: Long = 1_000_000L

/** Rounds a/b to the nearest integer, halves away from zero. b must be > 0. */
fun roundDiv(a: Long, b: Long): Long {
    require(b > 0) { "divisor must be positive" }
    return if (a >= 0) (a + b / 2) / b else -((-a + b / 2) / b)
}

fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)

/** Exact rational number num/den with den > 0, always normalised. Used for speeds. */
@Serializable
data class Rational(val num: Long, val den: Long) : Comparable<Rational> {
    init {
        require(den > 0) { "den must be > 0" }
    }

    fun toDouble(): Double = num.toDouble() / den

    /** value * this, rounded to nearest. */
    fun scale(value: Long): Long = roundDiv(Math.multiplyExact(value, num), den)

    /** value / this, rounded to nearest. */
    fun inverseScale(value: Long): Long {
        require(num > 0) { "cannot divide by non-positive rational" }
        return roundDiv(Math.multiplyExact(value, den), num)
    }

    override fun compareTo(other: Rational): Int =
        Math.multiplyExact(num, other.den).compareTo(Math.multiplyExact(other.num, den))

    override fun toString(): String = if (den == 1L) "$num" else "$num/$den"

    companion object {
        val ONE = Rational(1, 1)

        fun of(num: Long, den: Long): Rational {
            require(den != 0L)
            val sign = if (den < 0) -1 else 1
            val g = gcd(Math.abs(num), Math.abs(den)).coerceAtLeast(1)
            return Rational(sign * num / g, sign * den / g)
        }

        /** Parses "0.25", "2", "1/3". */
        fun parse(text: String): Rational {
            val t = text.trim()
            if (t.contains('/')) {
                val (a, b) = t.split('/', limit = 2)
                return of(a.trim().toLong(), b.trim().toLong())
            }
            val bd = java.math.BigDecimal(t)
            val scale = bd.scale().coerceAtLeast(0)
            val den = java.math.BigInteger.TEN.pow(scale).toLong()
            return of(bd.movePointRight(scale).toLong(), den)
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }
}

/** Frame rate as an exact fraction, e.g. 24000/1001 for 23.976. */
@Serializable
data class FrameRate(val num: Int, val den: Int = 1) {
    init {
        require(num > 0 && den > 0) { "invalid frame rate $num/$den" }
    }

    val fps: Double get() = num.toDouble() / den

    private val usPerFrameNum: Long get() = den.toLong() * US_PER_SECOND

    /** Index of the frame that is displayed at [timeUs] (floor). */
    fun frameAt(timeUs: Long): Long = Math.floorDiv(Math.multiplyExact(timeUs, num.toLong()), usPerFrameNum)

    /** Exact start time of frame [index]; frameAt(timeOf(i)) == i holds for every i. */
    fun timeOf(index: Long): Long = ceilDiv(Math.multiplyExact(index, usPerFrameNum), num.toLong())

    /** Nearest frame boundary to [timeUs]. */
    fun snap(timeUs: Long): Long = timeOf(roundDiv(Math.multiplyExact(timeUs, num.toLong()), usPerFrameNum))

    val frameDurationUs: Long get() = timeOf(1)

    override fun toString(): String = if (den == 1) "$num" else String.format(java.util.Locale.ROOT, "%.3f", fps)

    companion object {
        val FPS_24 = FrameRate(24)
        val FPS_23_976 = FrameRate(24000, 1001)
        val FPS_25 = FrameRate(25)
        val FPS_29_97 = FrameRate(30000, 1001)
        val FPS_30 = FrameRate(30)
        val FPS_50 = FrameRate(50)
        val FPS_60 = FrameRate(60)
    }
}

/** Half-open interval [startUs, endUs). */
@Serializable
data class TimeRange(val startUs: Long, val endUs: Long) {
    init {
        require(endUs >= startUs) { "invalid range [$startUs, $endUs)" }
    }

    val durationUs: Long get() = endUs - startUs
    fun contains(t: Long): Boolean = t in startUs until endUs
    fun overlaps(other: TimeRange): Boolean = startUs < other.endUs && other.startUs < endUs
    fun intersect(other: TimeRange): TimeRange? {
        val s = maxOf(startUs, other.startUs)
        val e = minOf(endUs, other.endUs)
        return if (e > s) TimeRange(s, e) else null
    }
}

object Timecode {
    /** HH:MM:SS.mmm */
    fun formatMillis(timeUs: Long): String {
        val neg = timeUs < 0
        val ms = Math.abs(timeUs) / 1000
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val f = ms % 1000
        return (if (neg) "-" else "") + String.format(java.util.Locale.ROOT, "%02d:%02d:%02d.%03d", h, m, s, f)
    }

    /** HH:MM:SS:FF using the given frame rate (non-drop-frame counting). */
    fun formatFrames(timeUs: Long, rate: FrameRate): String {
        val frame = rate.frameAt(Math.max(0, timeUs))
        val nominal = Math.round(rate.fps).coerceAtLeast(1)
        val ff = frame % nominal
        val totalSeconds = frame / nominal
        return String.format(
            java.util.Locale.ROOT, "%02d:%02d:%02d:%02d",
            totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60, ff,
        )
    }
}
