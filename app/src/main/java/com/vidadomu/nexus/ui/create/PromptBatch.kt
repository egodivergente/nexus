package com.vidadomu.nexus.ui.create

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lee una lista de prompts para lanzar en lote desde el Lienzo.
 * - CSV/TSV (coma, punto y coma o tabulador; comillas con saltos de línea): la columna con cabecera
 *   «prompt» (o «texto», «instrucción», «descripción»); si no hay cabecera, la primera.
 * - XLSX: la misma regla sobre la primera hoja.
 * - TXT/MD: un prompt por bloque separado por línea en blanco; sin bloques, uno por línea.
 * Quita la numeración inicial («1.», «2)») y descarta vacíos y duplicados exactos.
 */
object PromptBatch {
    const val MAX_PROMPTS = 100
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    internal const val MAX_XML_BYTES = 8 * 1024 * 1024

    fun readInput(input: java.io.InputStream): ByteArray = readBounded(input, MAX_FILE_BYTES)
    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= limit) { "El archivo supera el límite de lectura ($limit bytes)." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private val HEADERS = setOf("prompt", "prompts", "texto", "text", "instruccion", "instrucción", "descripcion", "descripción")
    private val NUMBERING = Regex("""^\s*(?:\d{1,3}[.)\-:]|[-*•])\s+""")

    fun parse(name: String, bytes: ByteArray, maxPrompts: Int = MAX_PROMPTS): List<String> {
        require(bytes.size <= MAX_FILE_BYTES) { "El archivo supera 5 MB." }
        val lower = name.lowercase()
        val rows: List<List<String>>? = when {
            lower.endsWith(".xlsx") -> xlsxRows(bytes)
            lower.endsWith(".csv") || lower.endsWith(".tsv") -> csvRows(decode(bytes))
            else -> null
        }
        val prompts = if (rows != null) column(rows) else textBlocks(decode(bytes))
        return prompts.map { it.replace(NUMBERING, "").trim() }.filter { it.isNotEmpty() }.distinct().take(if (maxPrompts <= 0) Int.MAX_VALUE else maxPrompts)
    }

    private fun decode(bytes: ByteArray): String = String(bytes, Charsets.UTF_8).removePrefix("﻿")

    private fun column(rows: List<List<String>>): List<String> {
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().indexOfFirst { it.trim().lowercase() in HEADERS }
        return if (header >= 0) rows.drop(1).mapNotNull { it.getOrNull(header) } else rows.mapNotNull { it.firstOrNull() }
    }

    internal fun textBlocks(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        val blocks = normalized.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        return if (blocks.size > 1) blocks.map { it.replace(Regex("""\s*\n\s*"""), " ") } else normalized.lines()
    }

    internal fun csvRows(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(';', '\t', ',').maxByOrNull { d -> firstLine.count { it == d } }
            ?.takeIf { d -> firstLine.count { it == d } > 0 } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                !quoted && c == delimiter -> { row.add(cell.toString()); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.clear(); rows.add(row); row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    internal fun xlsxRows(bytes: ByteArray): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") entries[entry.name] = readBounded(zip, MAX_XML_BYTES)
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: throw IllegalArgumentException("El Excel no tiene una primera hoja legible.")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        // Android's DOM parser does not support every Xerces flag. Refuse DTD/entity declarations
        // before parsing, including UTF-16 XML, so unsupported flags never enable external reads.
        entries.values.forEach { xml ->
            require(!Regex("<\\s*!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
                .containsMatchIn(String(xml, Charsets.UTF_8).replace("\u0000", ""))) { "El Excel contiene declaraciones XML no permitidas." }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let { xml ->
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val items = doc.getElementsByTagName("si")
            (0 until items.length).map { index ->
                val texts = (items.item(index) as org.w3c.dom.Element).getElementsByTagName("t")
                (0 until texts.length).joinToString("") { texts.item(it).textContent }
            }
        }.orEmpty()
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(sheet))
        val rows = doc.getElementsByTagName("row")
        return (0 until rows.length).map { r ->
            val cells = (rows.item(r) as org.w3c.dom.Element).getElementsByTagName("c")
            val values = sortedMapOf<Int, String>()
            for (c in 0 until cells.length) {
                val cellNode = cells.item(c) as org.w3c.dom.Element
                val column = columnIndex(cellNode.getAttribute("r"))
                require(column in 0..16383) { "Columna Excel inválida." }
                val value = when (cellNode.getAttribute("t")) {
                    "s" -> cellNode.getElementsByTagName("v").item(0)?.textContent?.toIntOrNull()?.let { shared.getOrNull(it) }
                    "inlineStr" -> cellNode.getElementsByTagName("t").item(0)?.textContent
                    else -> cellNode.getElementsByTagName("v").item(0)?.textContent
                }.orEmpty()
                values[column] = value
            }
            val last = values.keys.maxOrNull() ?: -1
            (0..last).map { values[it].orEmpty() }
        }.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun columnIndex(reference: String): Int =
        reference.takeWhile { it.isLetter() }.take(4).fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
}
