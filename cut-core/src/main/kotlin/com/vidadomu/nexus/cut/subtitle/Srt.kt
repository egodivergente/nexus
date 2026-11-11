package com.vidadomu.nexus.cut.subtitle

/** Lectura tolerante de SubRip (.srt) y de WebVTT sencillo: lo que producen Whisper y los editores habituales. */
object Srt {
    data class Cue(val startUs: Long, val endUs: Long, val text: String)

    private val TIMING = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val TAG = Regex("<[^>]+>|\\{\\\\[^}]*\\}")

    /** Devuelve los subtítulos con duración positiva, ordenados. Los bloques ilegibles se saltan. */
    fun parse(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        val lines = text.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val m = TIMING.find(lines[i])
            if (m == null) { i++; continue }
            val g = m.groupValues
            val start = us(g[1], g[2], g[3], g[4]); val end = us(g[5], g[6], g[7], g[8])
            i++
            val body = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank() && !TIMING.containsMatchIn(lines[i])) { body += lines[i].trim(); i++ }
            // El número del bloque siguiente puede ir pegado sin línea en blanco: no es texto.
            if (i < lines.size && TIMING.containsMatchIn(lines[i]) && body.lastOrNull()?.all(Char::isDigit) == true) body.removeAt(body.size - 1)
            val content = body.joinToString("\n") { it.replace(TAG, "") }.trim()
            if (end > start && content.isNotEmpty()) out += Cue(start, end, content)
        }
        return out.sortedBy { it.startUs }
    }

    private fun us(h: String, m: String, s: String, ms: String): Long =
        ((h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + ms.padEnd(3, '0').toLong()) * 1000
}
