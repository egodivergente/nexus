package com.vidadomu.corte.timeline.render

/** A .cube file that cannot be used, with a reason a user can act on. */
class CubeLutException(message: String) : Exception(message)

/**
 * A 3D colour lookup table in the Adobe/Resolve .cube format. [data] holds size^3 RGB triples in
 * file order (red varies fastest): index = r + g * size + b * size^2.
 * Sampling is trilinear, which is what Media3's LUT shader computes as well.
 */
class CubeLut(val size: Int, val data: FloatArray, val title: String) {
    init {
        require(size in MIN_SIZE..MAX_SIZE) { "LUT size $size" }
        require(data.size == size * size * size * 3) { "LUT data has ${data.size} values" }
    }

    /** Looks up (r, g, b) in [0, 1] (clamped). Writes the result into [out]. */
    fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val n1 = size - 1
        val fr = r.coerceIn(0f, 1f) * n1
        val fg = g.coerceIn(0f, 1f) * n1
        val fb = b.coerceIn(0f, 1f) * n1
        val r0 = minOf(fr.toInt(), n1 - 1)
        val g0 = minOf(fg.toInt(), n1 - 1)
        val b0 = minOf(fb.toInt(), n1 - 1)
        val dr = fr - r0
        val dg = fg - g0
        val db = fb - b0
        for (ch in 0..2) {
            val c000 = at(r0, g0, b0, ch); val c100 = at(r0 + 1, g0, b0, ch)
            val c010 = at(r0, g0 + 1, b0, ch); val c110 = at(r0 + 1, g0 + 1, b0, ch)
            val c001 = at(r0, g0, b0 + 1, ch); val c101 = at(r0 + 1, g0, b0 + 1, ch)
            val c011 = at(r0, g0 + 1, b0 + 1, ch); val c111 = at(r0 + 1, g0 + 1, b0 + 1, ch)
            val c00 = c000 + (c100 - c000) * dr
            val c10 = c010 + (c110 - c010) * dr
            val c01 = c001 + (c101 - c001) * dr
            val c11 = c011 + (c111 - c011) * dr
            val c0 = c00 + (c10 - c00) * dg
            val c1 = c01 + (c11 - c01) * dg
            out[ch] = (c0 + (c1 - c0) * db).coerceIn(0f, 1f)
        }
    }

    /** Value of the grid node (r, g, b) for channel [ch]. */
    fun at(r: Int, g: Int, b: Int, ch: Int): Float = data[((b * size + g) * size + r) * 3 + ch]

    companion object {
        const val MIN_SIZE = 2
        const val MAX_SIZE = 65

        fun parse(text: String): CubeLut {
            var size = -1
            var title = ""
            val min = floatArrayOf(0f, 0f, 0f)
            val max = floatArrayOf(1f, 1f, 1f)
            var values: FloatArray? = null
            var count = 0
            for ((lineNo, raw) in text.lineSequence().withIndex()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0].uppercase()) {
                    "TITLE" -> title = line.substringAfter(' ', "").trim().trim('"')
                    "LUT_1D_SIZE" -> throw CubeLutException("Es un LUT 1D; solo se admiten LUT 3D.")
                    "LUT_3D_SIZE" -> {
                        size = parts.getOrNull(1)?.toIntOrNull() ?: throw CubeLutException("LUT_3D_SIZE inválido (línea ${lineNo + 1}).")
                        if (size !in MIN_SIZE..MAX_SIZE) throw CubeLutException("Tamaño $size no admitido (de $MIN_SIZE a $MAX_SIZE).")
                        values = FloatArray(size * size * size * 3)
                    }
                    "DOMAIN_MIN", "DOMAIN_MAX" -> {
                        val dst = if (parts[0].uppercase() == "DOMAIN_MIN") min else max
                        for (i in 0..2) dst[i] = parts.getOrNull(i + 1)?.toFloatOrNull() ?: throw CubeLutException("${parts[0]} inválido (línea ${lineNo + 1}).")
                    }
                    "LUT_3D_INPUT_RANGE" -> {
                        val lo = parts.getOrNull(1)?.toFloatOrNull(); val hi = parts.getOrNull(2)?.toFloatOrNull()
                        if (lo == null || hi == null) throw CubeLutException("LUT_3D_INPUT_RANGE inválido (línea ${lineNo + 1}).")
                        min.fill(lo); max.fill(hi)
                    }
                    else -> {
                        val v = values ?: throw CubeLutException("Faltan datos antes de LUT_3D_SIZE (línea ${lineNo + 1}).")
                        if (parts.size != 3) throw CubeLutException("Línea ${lineNo + 1}: se esperaban 3 números.")
                        if (count >= v.size / 3) throw CubeLutException("Hay más de ${v.size / 3} entradas.")
                        for (i in 0..2) v[count * 3 + i] = parts[i].toFloatOrNull() ?: throw CubeLutException("Línea ${lineNo + 1}: «${parts[i]}» no es un número.")
                        count++
                    }
                }
            }
            val v = values ?: throw CubeLutException("No es un archivo .cube 3D (falta LUT_3D_SIZE).")
            if (count != v.size / 3) throw CubeLutException("Faltan entradas: hay $count de ${v.size / 3}.")
            for (i in 0..2) if (max[i] <= min[i]) throw CubeLutException("DOMAIN_MAX debe ser mayor que DOMAIN_MIN.")
            // A non-default domain maps input [min, max] onto the grid; normalise so that sampling
            // always uses [0, 1]. Only the output values are stored, so a domain other than [0, 1]
            // is resampled here on the grid.
            if (min.any { it != 0f } || max.any { it != 1f }) return resampleDomain(size, v, min, max, title)
            return CubeLut(size, v, title)
        }

        /** Builds an equivalent LUT whose input domain is [0, 1]. */
        private fun resampleDomain(size: Int, v: FloatArray, min: FloatArray, max: FloatArray, title: String): CubeLut {
            val src = CubeLut(size, v, title)
            val out = FloatArray(v.size)
            val tmp = FloatArray(3)
            val n1 = (size - 1).toFloat()
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val x = floatArrayOf(r / n1, g / n1, b / n1)
                for (i in 0..2) x[i] = ((x[i] - min[i]) / (max[i] - min[i])).coerceIn(0f, 1f)
                src.sample(x[0], x[1], x[2], tmp)
                val o = ((b * size + g) * size + r) * 3
                out[o] = tmp[0]; out[o + 1] = tmp[1]; out[o + 2] = tmp[2]
            }
            return CubeLut(size, out, title)
        }

        fun identity(size: Int): CubeLut {
            val v = FloatArray(size * size * size * 3)
            val n1 = (size - 1).toFloat()
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val o = ((b * size + g) * size + r) * 3
                v[o] = r / n1; v[o + 1] = g / n1; v[o + 2] = b / n1
            }
            return CubeLut(size, v, "identity")
        }
    }
}
