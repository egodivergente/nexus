package com.vidadomu.nexus.ui.cut

import com.vidadomu.nexus.cut.model.FrameRate
import java.util.Locale
import kotlin.math.abs

/**
 * Textos cortos de lo que se está arrastrando, en segundos legibles con coma decimal. Por debajo de un
 * segundo se añaden fotogramas, que es la unidad con la que se afina un corte.
 */
internal object CutDragText {
    private val es = Locale.forLanguageTag("es-ES")

    fun seconds(us: Long): String {
        val a = abs(us)
        return if (a >= 60_000_000L) {
            val totalCentis = a / 10_000L
            val m = totalCentis / 6000
            val rest = (totalCentis % 6000) / 100.0
            String.format(es, "%d:%05.2f", m, rest)
        } else String.format(es, "%.2f s", a / 1_000_000.0)
    }

    fun signed(us: Long, rate: FrameRate?): String {
        val sign = if (us < 0) "−" else "+"
        // Aritmética exacta del fotograma (a 24 fps, 0,5 s son 12 fotogramas y no 11 por redondear la duración).
        val frames = rate?.frameAt(abs(us))
        return if (rate != null && abs(us) < 1_000_000L) "$sign$frames f" else sign + seconds(us)
    }

    fun trim(newDurationUs: Long, deltaUs: Long, rate: FrameRate?): String =
        if (deltaUs == 0L) seconds(newDurationUs) else "${seconds(newDurationUs)} · ${signed(deltaUs, rate)}"

    fun move(startUs: Long, deltaUs: Long, trackOffset: Int, rate: FrameRate?): String {
        val track = when {
            trackOffset > 0 -> " · pista +$trackOffset"
            trackOffset < 0 -> " · pista $trackOffset"
            else -> ""
        }
        return "${Clock.short(startUs)} · ${signed(deltaUs, rate)}$track"
    }

    fun fade(us: Long, fadeIn: Boolean): String = (if (fadeIn) "Entrada " else "Salida ") + seconds(us)

    /** Reloj del montaje: minutos y segundos, con fotogramas aparte si se piden. */
    object Clock {
        fun short(us: Long): String {
            val s = (us.coerceAtLeast(0L) / 1_000_000L)
            return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
            else String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
        }

        /** Fotograma dentro del segundo, con dos cifras («07»). */
        fun frameInSecond(us: Long, rate: FrameRate): String {
            val t = us.coerceAtLeast(0L)
            val f = rate.frameAt(t) - rate.frameAt(t / 1_000_000L * 1_000_000L)
            return String.format(Locale.ROOT, "%02d", f)
        }
    }
}
