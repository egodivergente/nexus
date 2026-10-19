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
    private val HEADERS = setOf("prompt", "prompts", "texto", "text", "instruccion", "instrucción", "descripcion", "descripción")
    private val NUMBERING = Regex("""^\s*(?:\d{1,3}[.)\-:]|[-*•])\s+""")

    fun parse(name: String, bytes: ByteArray): List<String> {
        val lower = name.lowercase()
        val rows: List<List<String>>? = when {
            lower.endsWith(".xlsx") -> xlsxRows(bytes)
            lower.endsWith(".csv") || lower.endsWith(".tsv") -> csvRows(decode(bytes))
            else -> null
        }
        val prompts = if (rows != null) column(rows) else textBlocks(decode(bytes))
        return prompts.map { it.replace(NUMBERING, "").trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_PROMPTS)
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
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") entries[entry.name] = zip.readBytes()
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: throw IllegalArgumentException("El Excel no tiene una primera hoja legible.")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
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
        reference.takeWhile { it.isLetter() }.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
}
