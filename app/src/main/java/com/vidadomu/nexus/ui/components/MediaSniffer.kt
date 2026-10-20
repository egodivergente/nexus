package com.vidadomu.nexus.ui.components

import java.io.InputStream

/** Identifica el tipo real por la firma de bytes; solo se consulta cuando el MIME declarado no dice nada. */
object MediaSniffer {
    const val UNKNOWN = "application/octet-stream"
    const val HEADER_BYTES = 16

    private val genericTypes = setOf("", "*/*", "application/octet-stream", "binary/octet-stream", "application/unknown", "application/binary")

    fun isGeneric(mime: String?): Boolean {
        val m = normalize(mime)
        return m in genericTypes || m.endsWith("/*")
    }

    /** Primer MIME declarado concreto; si no hay, la firma; si no coincide, la familia comodín declarada (p. ej. image con comodín); si no, desconocido. */
    fun resolve(header: ByteArray?, vararg declared: String?): String {
        declared.firstOrNull { !isGeneric(it) }?.let { return it.trim() }
        header?.let(::sniff)?.let { return it }
        return declared.map(::normalize).firstOrNull { it.endsWith("/*") && it != "*/*" } ?: UNKNOWN
    }

    fun readHeader(input: InputStream): ByteArray {
        val buffer = ByteArray(HEADER_BYTES)
        var total = 0
        while (total < HEADER_BYTES) {
            val n = input.read(buffer, total, HEADER_BYTES - total)
            if (n < 0) break
            total += n
        }
        return buffer.copyOf(total)
    }

    fun sniff(bytes: ByteArray): String? {
        fun u(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        fun ascii(offset: Int, text: String) = bytes.size >= offset + text.length && text.indices.all { u(offset + it) == text[it].code }
        return when {
            u(0) == 0x89 && ascii(1, "PNG") -> "image/png"
            u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF -> "image/jpeg"
            ascii(0, "GIF8") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            ascii(0, "RIFF") && ascii(8, "WAVE") -> "audio/wav"
            ascii(4, "ftyp") -> isoBrand(bytes)
            u(0) == 0x1A && u(1) == 0x45 && u(2) == 0xDF && u(3) == 0xA3 -> "video/webm"
            ascii(0, "%PDF") -> "application/pdf"
            ascii(0, "ID3") -> "audio/mpeg"
            bytes.size >= 2 && u(0) == 0xFF && (u(1) and 0xF0) == 0xF0 -> if ((u(1) and 0x06) == 0) "audio/aac" else "audio/mpeg"
            else -> null
        }
    }

    private fun isoBrand(bytes: ByteArray): String {
        val brand = if (bytes.size >= 12) String(bytes, 8, 4, Charsets.US_ASCII) else ""
        return when (brand) {
            "qt  " -> "video/quicktime"
            "M4A ", "M4B " -> "audio/mp4"
            "heic", "heix", "mif1" -> "image/heic"
            "avif" -> "image/avif"
            else -> "video/mp4"
        }
    }

    private fun normalize(mime: String?) = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
}
