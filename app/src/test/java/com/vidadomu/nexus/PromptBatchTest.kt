package com.vidadomu.nexus

import com.vidadomu.nexus.ui.create.PromptBatch
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PromptBatchTest {
    @Test fun csvWithSpanishSemicolonsUsesPromptColumnAndQuotes() {
        val csv = "id;Prompt;notas\n1;\"Un gato; naranja\nen la ventana\";x\n2;Un perro;y\n3;Un perro;z\n"
        assertEquals(listOf("Un gato; naranja\nen la ventana", "Un perro"), PromptBatch.parse("lote.csv", csv.toByteArray()))
    }

    @Test fun csvWithoutHeaderUsesFirstColumnAndStripsBom() {
        val csv = "﻿Una playa al amanecer,16:9\nUn bosque,1:1\n"
        assertEquals(listOf("Una playa al amanecer", "Un bosque"), PromptBatch.parse("lote.csv", csv.toByteArray()))
    }

    @Test fun textBlocksJoinLinesAndDropNumbering() {
        val txt = "1. Retrato de una mujer\nmedieval, luz suave\n\n2) Mercado de pueblo\n\n- Castillo en la niebla"
        assertEquals(listOf("Retrato de una mujer medieval, luz suave", "Mercado de pueblo", "Castillo en la niebla"),
            PromptBatch.parse("ideas.txt", txt.toByteArray()))
    }

    @Test fun textWithoutBlankLinesIsOnePromptPerLine() {
        assertEquals(listOf("uno", "dos", "tres"), PromptBatch.parse("ideas.txt", "uno\ndos\r\ntres\n".toByteArray()))
    }

    @Test fun xlsxSharedAndInlineStrings() {
        val shared = """<sst><si><t>Prompt</t></si><si><t>Ciudad de noche</t></si><si><r><t>Lago </t></r><r><t>helado</t></r></si></sst>"""
        val sheet = """<worksheet><sheetData>
            <row r="1"><c r="A1"><v>1</v></c><c r="B1" t="s"><v>0</v></c></row>
            <row r="2"><c r="A2"><v>1</v></c><c r="B2" t="s"><v>1</v></c></row>
            <row r="3"><c r="A3"><v>2</v></c><c r="B3" t="s"><v>2</v></c></row>
            <row r="4"><c r="B4" t="inlineStr"><is><t>Desierto rojo</t></is></c></row>
        </sheetData></worksheet>"""
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml")); zip.write(shared.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet.toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        assertEquals(listOf("Ciudad de noche", "Lago helado", "Desierto rojo"), PromptBatch.parse("lote.xlsx", bytes))
    }

    @Test fun capsTheBatch() {
        val many = (1..150).joinToString("\n") { "prompt $it" }
        assertEquals(PromptBatch.MAX_PROMPTS, PromptBatch.parse("x.txt", many.toByteArray()).size)
    }
    @Test fun readerStopsAtTheLimitInsteadOfLoadingTheWholeFile() {
        val stream=object:java.io.InputStream() {
            var read=0
            override fun read():Int {read++; return 65}
            override fun read(b:ByteArray,off:Int,len:Int):Int {read+=len; b.fill(65.toByte(),off,off+len);return len}
        }
        assertTrue(runCatching {PromptBatch.readInput(stream)}.isFailure)
        assertTrue(stream.read <= PromptBatch.MAX_FILE_BYTES+8192)
    }
    @Test fun compressedXmlExpansionIsBounded() {
        val bytes=ByteArrayOutputStream().also {out -> ZipOutputStream(out).use {zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val chunk=ByteArray(8192) {65}
            repeat(PromptBatch.MAX_XML_BYTES/8192+1) {zip.write(chunk)}
            zip.closeEntry()
        }}.toByteArray()
        assertTrue(runCatching {PromptBatch.parse("x.xlsx",bytes)}.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun rejectsExternalEntities() {
        val xml="""<!DOCTYPE worksheet [<!ENTITY xxe SYSTEM "file:///private">]><worksheet><sheetData>&xxe;</sheetData></worksheet>"""
        val bytes=ByteArrayOutputStream().also {out -> ZipOutputStream(out).use {zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"));zip.write(xml.toByteArray());zip.closeEntry()
        }}.toByteArray()
        assertTrue(runCatching {PromptBatch.parse("x.xlsx",bytes)}.isFailure)
    }

    @Test fun respectsAnEditedBatchLimitIncludingUnlimited() {
        val bytes=(1..150).joinToString("\n") {"prompt $it"}.toByteArray()
        assertEquals(3,PromptBatch.parse("x.txt",bytes,3).size)
        assertEquals(150,PromptBatch.parse("x.txt",bytes,0).size)
    }

}
