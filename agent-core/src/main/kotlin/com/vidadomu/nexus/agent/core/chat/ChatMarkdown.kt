package com.vidadomu.nexus.agent.core.chat

/**
 * Markdown ligero para los bocadillos del asistente. Convierte lo que devuelve el LLM
 * en bloques tipográficos para que la app nunca muestre `**`, `#` ni `-` crudos.
 * Tolerante: un marcador sin cerrar se queda como texto literal, nunca rompe.
 */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

sealed interface MdBlock {
    /** level 1-2 = título de sección; 3+ = etiqueta (también una línea sola en negrita). */
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class ListBlock(val items: List<ListItem>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
    data object Rule : MdBlock
}

/** [number] null = viñeta; [depth] 0 = primer nivel. */
data class ListItem(val depth: Int, val number: String?, val spans: List<MdSpan>)

object ChatMarkdown {
    private val heading = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val bullet = Regex("^(\\s*)[-*+•]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val fence = Regex("^\\s*(```|~~~)\\s*([\\w+#.-]*)\\s*$")
    private val rule = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val boldLine = Regex("^\\s*(\\*\\*|__)([^*_].*?)\\1\\s*:?\\s*$")
    private val tableSep = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun parse(text: String): List<MdBlock> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = mutableListOf<MdBlock>()
        val para = mutableListOf<String>()
        val items = mutableListOf<ListItem>()
        val quote = mutableListOf<String>()

        fun flushPara() { if (para.isNotEmpty()) { out += MdBlock.Paragraph(inline(para.joinToString("\n"))); para.clear() } }
        fun flushList() { if (items.isNotEmpty()) { out += MdBlock.ListBlock(items.toList()); items.clear() } }
        fun flushQuote() { if (quote.isNotEmpty()) { out += MdBlock.Quote(inline(quote.joinToString("\n"))); quote.clear() } }
        fun flushAll() { flushPara(); flushList(); flushQuote() }
        fun depthOf(indent: String): Int = (indent.replace("\t", "    ").length / 2).coerceAtMost(3)

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val f = fence.matchEntire(line)
            if (f != null) {
                flushAll()
                val marker = f.groupValues[1]
                val lang = f.groupValues[2].ifBlank { null }
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) { body += lines[i]; i++ }
                out += MdBlock.Code(lang, body.joinToString("\n").trimEnd())
                i++ // salta el cierre (o el final si no se cerró)
                continue
            }
            if (line.isBlank()) { flushAll(); i++; continue }

            if (line.trimStart().startsWith("|") && i + 1 < lines.size && tableSep.matches(lines[i + 1])) {
                flushAll()
                val header = cells(line)
                i += 2
                val rows = mutableListOf<List<List<MdSpan>>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) { rows += cells(lines[i]); i++ }
                out += MdBlock.Table(header, rows)
                continue
            }
            val h = heading.matchEntire(line)
            if (h != null) { flushAll(); out += MdBlock.Heading(h.groupValues[1].length, inline(h.groupValues[2])); i++; continue }
            if (rule.matches(line)) { flushAll(); out += MdBlock.Rule; i++; continue }
            val bl = if (para.isEmpty() && items.isEmpty()) boldLine.matchEntire(line) else null
            if (bl != null) { flushAll(); out += MdBlock.Heading(3, inline(bl.groupValues[2].trim().removeSuffix(":"))); i++; continue }
            if (line.trimStart().startsWith(">")) {
                flushPara(); flushList()
                quote += line.trimStart().removePrefix(">").removePrefix(" ")
                i++; continue
            }
            val b = bullet.matchEntire(line)
            val o = if (b == null) ordered.matchEntire(line) else null
            if (b != null || o != null) {
                flushPara(); flushQuote()
                items += if (b != null) ListItem(depthOf(b.groupValues[1]), null, inline(b.groupValues[2]))
                else ListItem(depthOf(o!!.groupValues[1]), o.groupValues[2], inline(o.groupValues[3]))
                i++; continue
            }
            if (items.isNotEmpty()) {
                // continuación de la última viñeta
                val last = items.removeAt(items.lastIndex)
                items += last.copy(spans = last.spans + MdSpan(" ") + inline(line.trim()))
                i++; continue
            }
            if (quote.isNotEmpty()) flushQuote()
            para += line.trimEnd()
            i++
        }
        flushAll()
        return out
    }

    private fun cells(line: String): List<List<MdSpan>> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { inline(it.trim()) }

    /** Inline: `código`, **negrita**, __negrita__, *cursiva*, _cursiva_, ~~tachado~~, [texto](url). */
    fun inline(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        parseInto(text, MdSpan(""), out)
        return merge(out)
    }

    private fun parseInto(s: String, style: MdSpan, out: MutableList<MdSpan>) {
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out += style.copy(text = buf.toString()); buf.clear() } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_~[]()#>-|") { buf.append(s[i + 1]); i += 2; continue }
            if (c == '`') {
                val end = s.indexOf('`', i + 1)
                if (end > i + 1) { flush(); out += style.copy(text = s.substring(i + 1, end), code = true); i = end + 1; continue }
            }
            if (c == '[') {
                val close = s.indexOf("](", i + 1)
                val paren = if (close > 0) s.indexOf(')', close + 2) else -1
                if (close > i && paren > close) {
                    flush()
                    parseInto(s.substring(i + 1, close), style.copy(link = s.substring(close + 2, paren).trim()), out)
                    i = paren + 1; continue
                }
            }
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
            if (two == "**" || two == "__" || two == "~~") {
                val end = findClose(s, two, i + 2)
                if (end > 0 && (two != "__" || boundary(s, i, end + 2))) {
                    flush()
                    val inner = s.substring(i + 2, end)
                    parseInto(inner, when (two) { "~~" -> style.copy(strike = true); else -> style.copy(bold = true) }, out)
                    i = end + 2; continue
                }
            }
            if ((c == '*' || c == '_') && two != "**" && two != "__") {
                val end = findClose(s, c.toString(), i + 1)
                val okOpen = i + 1 < s.length && !s[i + 1].isWhitespace()
                if (end > 0 && okOpen && !s[end - 1].isWhitespace() && (c != '_' || boundary(s, i, end + 1))) {
                    flush()
                    parseInto(s.substring(i + 1, end), style.copy(italic = true), out)
                    i = end + 1; continue
                }
            }
            buf.append(c); i++
        }
        flush()
    }

    /** Cierre del marcador sin cruzar código inline; exige contenido no vacío. */
    private fun findClose(s: String, marker: String, from: Int): Int {
        var j = from
        while (j <= s.length - marker.length) {
            if (s[j] == '`') { val e = s.indexOf('`', j + 1); if (e < 0) return -1; j = e + 1; continue }
            if (s.startsWith(marker, j) && j > from) {
                // para `*` simple no confundir con `**`
                if (marker.length == 1 && j + 1 < s.length && s[j + 1] == marker[0]) { j += 2; continue }
                // `***` al cerrar: la cursiva interior se lleva el primero, la negrita los dos últimos
                if (marker.length == 2 && j + 2 < s.length && s[j + 2] == marker[0]) { j++; continue }
                return j
            }
            j++
        }
        return -1
    }

    /** `_` solo cuenta como marcador fuera de palabras (snake_case se queda literal). */
    private fun boundary(s: String, start: Int, endExclusive: Int): Boolean {
        val before = if (start > 0) s[start - 1] else ' '
        val after = if (endExclusive < s.length) s[endExclusive] else ' '
        return !before.isLetterOrDigit() && !after.isLetterOrDigit()
    }

    private fun merge(spans: List<MdSpan>): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        for (sp in spans) {
            if (sp.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.copy(text = "") == sp.copy(text = "")) out[out.lastIndex] = last.copy(text = last.text + sp.text)
            else out += sp
        }
        return out
    }

    /** Texto plano sin marcas (para copiar o accesibilidad). */
    fun plain(text: String): String = parse(text).joinToString("\n\n") { b ->
        when (b) {
            is MdBlock.Heading -> b.spans.joinToString("") { it.text }
            is MdBlock.Paragraph -> b.spans.joinToString("") { it.text }
            is MdBlock.Quote -> b.spans.joinToString("") { it.text }
            is MdBlock.Code -> b.code
            is MdBlock.ListBlock -> b.items.joinToString("\n") { "  ".repeat(it.depth) + (it.number?.let { n -> "$n. " } ?: "• ") + it.spans.joinToString("") { s -> s.text } }
            is MdBlock.Table -> (listOf(b.header) + b.rows).joinToString("\n") { r -> r.joinToString(" | ") { c -> c.joinToString("") { it.text } } }
            MdBlock.Rule -> "—"
        }
    }
}
