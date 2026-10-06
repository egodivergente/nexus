package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.chat.ChatMarkdown
import com.vidadomu.nexus.agent.core.chat.MdBlock
import com.vidadomu.nexus.agent.core.chat.MdSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatMarkdownTest {
    private fun raw(spans: List<MdSpan>) = spans.joinToString("") { it.text }
    @Test fun `mensaje real de la captura sin asteriscos`() {
        val msg = "Hola. Soy el asistente de Nexus. Puedo ayudarte a:\n\n" +
            "- **Elegir modelo**: recomendaciones basadas en tu tarea (texto→vídeo, imagen→vídeo, imagen, audio…) y presupuesto.\n" +
            "- **Escribir prompts**: estructurados y listos para cada generador.\n" +
            "- **Preparar generaciones**: validar parámetros, estimar coste, mostrar tarjeta antes de pulsar GENERAR.\n" +
            "- **Organizar assets**: proyectos, escenas, planos, personajes, versiones y lineage sin tocar archivos.\n\n" +
            "¿Qué necesitas? (p. ej. «generarme un vídeo de…»)"
        val b = ChatMarkdown.parse(msg)
        assertEquals(3, b.size)
        assertIs<MdBlock.Paragraph>(b[0])
        val list = assertIs<MdBlock.ListBlock>(b[1])
        assertEquals(4, list.items.size)
        val first = list.items[0].spans
        assertEquals(MdSpan("Elegir modelo", bold = true), first[0])
        assertTrue(raw(first).startsWith("Elegir modelo: recomendaciones"))
        assertFalse(ChatMarkdown.plain(msg).contains("**"))
        assertIs<MdBlock.Paragraph>(b[2])
    }

    @Test fun `titulos, etiquetas en negrita sola y separador`() {
        val b = ChatMarkdown.parse("## Plan de rodaje\n**Modelo recomendado:**\nKling 3.0\n\n---\n### Detalle")
        assertEquals(MdBlock.Heading(2, listOf(MdSpan("Plan de rodaje"))), b[0])
        assertEquals(MdBlock.Heading(3, listOf(MdSpan("Modelo recomendado"))), b[1])
        assertEquals("Kling 3.0", raw((b[2] as MdBlock.Paragraph).spans))
        assertEquals(MdBlock.Rule, b[3])
        assertEquals(3, (b[4] as MdBlock.Heading).level)
    }

    @Test fun `listas numeradas, anidadas y con continuacion`() {
        val b = ChatMarkdown.parse("1. Primero\n2) Segundo\n   sigue aquí\n  - sub\n3. Tercero")
        val l = assertIs<MdBlock.ListBlock>(b.single())
        assertEquals(listOf("1", "2", null, "3"), l.items.map { it.number })
        assertEquals("Segundo sigue aquí", raw(l.items[1].spans))
        assertEquals(1, l.items[2].depth)
    }

    @Test fun `bloque de codigo conserva el prompt literal`() {
        val p = "Cinematic dolly-in, **no bold here**, snake_case_ok"
        val b = ChatMarkdown.parse("Prompt:\n```prompt\n$p\n```\nListo")
        val code = assertIs<MdBlock.Code>(b[1])
        assertEquals("prompt", code.language)
        assertEquals(p, code.code)
        assertEquals("Listo", raw((b[2] as MdBlock.Paragraph).spans))
    }

    @Test fun `codigo sin cerrar no se come el resto ni rompe`() {
        val b = ChatMarkdown.parse("```\nlinea 1\nlinea 2")
        assertEquals("linea 1\nlinea 2", assertIs<MdBlock.Code>(b.single()).code)
    }

    @Test fun `inline cursiva, codigo, tachado, enlace y anidado`() {
        val s = ChatMarkdown.inline("Usa *slow* `seed=42`, ~~veo~~ y [docs](https://x.y) con **muy *bonito***")
        assertTrue(MdSpan("slow", italic = true) in s)
        assertTrue(MdSpan("seed=42", code = true) in s)
        assertTrue(MdSpan("veo", strike = true) in s)
        assertTrue(MdSpan("docs", link = "https://x.y") in s)
        assertTrue(MdSpan("bonito", bold = true, italic = true) in s)
        assertFalse(raw(s).contains("*"))
    }

    @Test fun `marcadores sueltos se quedan literales`() {
        assertEquals("2 * 3 = 6 y **sin cerrar", raw(ChatMarkdown.inline("2 * 3 = 6 y **sin cerrar")))
        assertEquals("archivo_de_prueba_v2.png", raw(ChatMarkdown.inline("archivo_de_prueba_v2.png")))
        assertEquals("*literal*", raw(ChatMarkdown.inline("\\*literal\\*")))
    }

    @Test fun `tabla simple`() {
        val b = ChatMarkdown.parse("| Modelo | Coste |\n|---|:--:|\n| **Kling** | 0,40 € |\n| Seedance | 0,30 € |")
        val t = assertIs<MdBlock.Table>(b.single())
        assertEquals(listOf("Modelo", "Coste"), t.header.map(::raw))
        assertEquals(2, t.rows.size)
        assertEquals(MdSpan("Kling", bold = true), t.rows[0][0][0])
    }

    @Test fun `cita y saltos CRLF`() {
        val b = ChatMarkdown.parse("> Ojo: veo está excluido\r\n> por defecto\r\n\r\nVale")
        assertEquals("Ojo: veo está excluido\npor defecto", raw(assertIs<MdBlock.Quote>(b[0]).spans))
        assertEquals(2, b.size)
    }
}
