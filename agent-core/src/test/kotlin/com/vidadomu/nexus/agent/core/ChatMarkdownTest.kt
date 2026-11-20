package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.chat.CalloutKind
import com.vidadomu.nexus.agent.core.chat.ChatMarkdown
import com.vidadomu.nexus.agent.core.chat.ChatWidgets
import com.vidadomu.nexus.agent.core.chat.MdBlock
import com.vidadomu.nexus.agent.core.chat.MdSpan
import com.vidadomu.nexus.agent.core.chat.WidgetOption
import com.vidadomu.nexus.agent.core.chat.WidgetSpec
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

    @Test fun `un elemento de lista no lleva doble vineta`() {
        // Captura de Vidal del 18-11: «● · Kling Pro». El punto sobrante llega del modelo o queda al ocultar un id.
        val b = ChatMarkdown.parse("Encargos:\n- · Kling Pro, 5 s\n- • **Seedance** listo\n· Suelto con punto medio\n1. · Numerado\n- Normal · con punto dentro")
        val l = assertIs<MdBlock.ListBlock>(b[1])
        assertEquals(listOf("Kling Pro, 5 s", "Seedance listo", "Suelto con punto medio", "Numerado", "Normal · con punto dentro"), l.items.map { raw(it.spans) })
        assertEquals(MdSpan("Seedance", bold = true), l.items[1].spans[0])
        assertEquals(listOf(null, null, null, "1", null), l.items.map { it.number })
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

    @Test fun `recuadro semantico cerrado con su contenido`() {
        val b = ChatMarkdown.parse("Intro\n\n:::warn\nEsto **gasta** crédito.\n- Una\n:::\n\nFin")
        assertEquals(3, b.size)
        val c = assertIs<MdBlock.Callout>(b[1])
        assertEquals(CalloutKind.WARN, c.kind)
        assertIs<MdBlock.Paragraph>(c.blocks[0])
        assertIs<MdBlock.ListBlock>(c.blocks[1])
        assertEquals("Fin", raw((b[2] as MdBlock.Paragraph).spans))
    }

    @Test fun `recuadro sin cerrar llega a la linea en blanco y no se traga el resto`() {
        val b = ChatMarkdown.parse(":::tldr\nResumen corto\n\nDetalle posterior")
        assertEquals(CalloutKind.TLDR, assertIs<MdBlock.Callout>(b[0]).kind)
        assertEquals("Detalle posterior", raw((b[1] as MdBlock.Paragraph).spans))
        assertFalse(ChatMarkdown.plain(":::tldr\nResumen corto").contains(":::"))
    }

    @Test fun `un recuadro sin cerrar no se traga al siguiente`() {
        val b = ChatMarkdown.parse(":::tip\nUno\n:::warn\nDos\n:::")
        assertEquals(listOf(CalloutKind.TIP, CalloutKind.WARN), b.filterIsInstance<MdBlock.Callout>().map { it.kind })
    }

    @Test fun `cierre suelto y tipo desconocido no rompen`() {
        val b = ChatMarkdown.parse("Hola\n:::\n\n:::otro\nTexto")
        assertTrue(b.none { it is MdBlock.Callout })
        assertFalse(ChatMarkdown.plain("Hola\n:::").contains(":::"))
    }

    @Test fun `resaltado solo con marcadores pegados y fuera de palabras`() {
        assertEquals(listOf(MdSpan("Ojo ", ), MdSpan("clave", mark = true), MdSpan(" aquí")), ChatMarkdown.inline("Ojo ==clave== aquí"))
        assertEquals("si a == b entonces c == d", raw(ChatMarkdown.inline("si a == b entonces c == d")))
        assertEquals("x==y==z", raw(ChatMarkdown.inline("x==y==z")))
        assertTrue(ChatMarkdown.inline("x==y==z").none { it.mark })
        assertTrue(ChatMarkdown.inline("`==no==`").single().code)
        assertEquals("==a==", raw(ChatMarkdown.inline("\\==a\\==")))
    }

    @Test fun `panel de encuesta valido se convierte en widget`() {
        val md = "Elige:\n\n```nexus-ui\n{\"type\":\"poll\",\"id\":\"fmt\",\"question\":\"¿Formato?\",\"multi\":true," +
            "\"options\":[{\"id\":\"a\",\"label\":\"16:9\",\"hint\":\"horizontal\"},\"9:16\"]}\n```"
        val w = assertIs<MdBlock.Widget>(ChatMarkdown.parse(md)[1])
        val poll = assertIs<WidgetSpec.Poll>(w.spec)
        assertEquals("fmt", poll.id)
        assertTrue(poll.multi)
        assertEquals(listOf(WidgetOption("a", "16:9", "horizontal"), WidgetOption("o1", "9:16")), poll.options)
        val plain = ChatMarkdown.plain(md)
        assertTrue(plain.contains("¿Formato?") && plain.contains("16:9 — horizontal") && !plain.contains("nexus-ui"))
    }

    @Test fun `json invalido o fuera de limites cae a bloque de codigo`() {
        for (bad in listOf("{no es json", "{\"type\":\"poll\",\"question\":\"¿?\",\"options\":[\"solo una\"]}", "{\"type\":\"desconocido\"}", "[]")) {
            val b = ChatMarkdown.parse("```nexus-ui\n$bad\n```")
            assertIs<MdBlock.Code>(b.single(), bad)
        }
    }

    @Test fun `panel a medias se oculta mientras llega y se enseña si el texto termino asi`() {
        val partial = "Mira:\n\n```nexus-ui\n{\"type\":\"poll\",\"que"
        assertEquals(1, ChatMarkdown.parse(partial, streaming = true).size)
        assertIs<MdBlock.Code>(ChatMarkdown.parse(partial, streaming = false).last())
    }

    @Test fun `steps y compare con limites`() {
        val steps = ChatWidgets.parse("{\"type\":\"steps\",\"title\":\"Antes de rodar\",\"items\":[\"Guion\",\"Referencias\",\"\"]}")
        assertEquals(listOf("Guion", "Referencias"), assertIs<WidgetSpec.Steps>(steps).items)
        val many = (1..9).joinToString(",") { "\"Modelo $it\"" }
        val compare = assertIs<WidgetSpec.Compare>(ChatWidgets.parse("{\"type\":\"compare\",\"options\":[$many]}"))
        assertEquals(6, compare.options.size)
        assertEquals(compare.id, assertIs<WidgetSpec.Compare>(ChatWidgets.parse("{\"type\":\"compare\",\"options\":[$many]}")).id)
    }

    // --- Texto que todavía está llegando (turno real del 19-11: una negrita sin cerrar se veía con sus asteriscos) ---

    private fun shown(blocks: List<MdBlock>): String = blocks.joinToString("\n") { b ->
        when (b) {
            is MdBlock.Paragraph -> raw(b.spans)
            is MdBlock.Heading -> raw(b.spans)
            is MdBlock.Quote -> raw(b.spans)
            is MdBlock.ListBlock -> b.items.joinToString("\n") { raw(it.spans) }
            is MdBlock.Callout -> shown(b.blocks)
            else -> ""
        }
    }

    @Test fun `mientras llega, la negrita abierta ya va en negrita y sin asteriscos`() {
        val arriving = "Las tres imágenes son:\n\n**Sobre el historial de generaciones, no pue"
        val live = ChatMarkdown.parse(arriving, streaming = true)
        assertEquals(listOf(MdSpan("Sobre el historial de generaciones, no pue", bold = true)), (live[1] as MdBlock.Paragraph).spans)
        // Guardado así, el marcador sin cerrar sigue siendo texto: solo lo que está llegando se da por cerrado.
        assertEquals("**Sobre el historial de generaciones, no pue", raw((ChatMarkdown.parse(arriving)[1] as MdBlock.Paragraph).spans))
        // Con el cierre ya puesto, en vivo y guardado se leen igual.
        val closed = arriving + "do confirmarte el número**: la consulta no devuelve un contador."
        assertEquals(ChatMarkdown.parse(closed), ChatMarkdown.parse(closed, streaming = true))
    }

    @Test fun `mientras llega, ningun corte del texto ensena marcadores`() {
        val answer = "Las tres **imágenes más recientes** del proyecto son:\n\n" +
            "- **qa-imagen.png**: de *ayer* mismo\n" +
            "- `Imagen-Nexus-close_up.png`, que sustituye a ~~la anterior~~\n" +
            "1. __Primero__ y _después_\n\n" +
            "> Una *cita* con **peso**\n\n" +
            "## Sobre el **historial**\n" +
            "**Sobre el historial de generaciones**, no puedo confirmarte el *número total* exacto."
        for (cut in 1..answer.length) {
            val text = shown(ChatMarkdown.parse(answer.substring(0, cut), streaming = true))
            assertFalse(text.contains('*') || text.contains('`') || text.contains("~~") || text.contains("__"),
                "corte $cut: «${answer.substring(0, cut).takeLast(24)}» se pinta «${text.takeLast(40)}»")
        }
        // El texto completo se pinta igual en vivo que guardado.
        assertEquals(ChatMarkdown.parse(answer), ChatMarkdown.parse(answer, streaming = true))
    }

    @Test fun `mientras llega, cursiva, tachado, resalte y codigo abiertos tambien valen, y anidados`() {
        fun last(text: String) = (ChatMarkdown.parse(text, streaming = true).last() as MdBlock.Paragraph).spans
        assertEquals(listOf(MdSpan("Es "), MdSpan("muy impor", italic = true)), last("Es *muy impor"))
        assertEquals(listOf(MdSpan("Es "), MdSpan("muy impor", italic = true)), last("Es _muy impor"))
        assertEquals(listOf(MdSpan("Ya no "), MdSpan("vale", strike = true)), last("Ya no ~~vale"))
        assertEquals(listOf(MdSpan("Ojo: "), MdSpan("no borres nada", mark = true)), last("Ojo: ==no borres nada"))
        assertEquals(listOf(MdSpan("Usa "), MdSpan("search_ass", code = true)), last("Usa `search_ass"))
        assertEquals(listOf(MdSpan("negrita y ", bold = true), MdSpan("cod", bold = true, code = true)), last("**negrita y `cod"))
        assertEquals(listOf(MdSpan("las dos", bold = true, italic = true)), last("***las dos"))
        assertEquals(listOf(MdSpan("a ", bold = true), MdSpan("b", bold = true, italic = true)), last("**a *b*"))
    }

    @Test fun `mientras llega, el marcador recien llegado o el cierre a medias no se ensenan`() {
        fun live(text: String) = shown(ChatMarkdown.parse(text, streaming = true))
        assertEquals("Texto ", live("Texto **"))
        assertEquals("Texto ", live("Texto *"))
        assertEquals("Texto ", live("Texto `"))
        assertEquals("negrita", live("**negrita*"))
        assertEquals("tachado", live("~~tachado~"))
        assertEquals("", live("**"))
    }

    @Test fun `mientras llega, lo que no abre al empezar palabra se queda literal`() {
        for (literal in listOf("El total es 2**10", "Son 5 * 3 = 15", "Si a == b entonces", "usa snake_case_largo", "x==y", "precio*", "it`s", "uno ** dos")) {
            assertEquals(literal, shown(ChatMarkdown.parse(literal, streaming = true)), literal)
            assertEquals(ChatMarkdown.parse(literal), ChatMarkdown.parse(literal, streaming = true), literal)
        }
    }

    @Test fun `mientras llega, solo la linea que se esta escribiendo queda abierta`() {
        // Una viñeta anterior con el marcador sin cerrar ya no va a cerrarse: literal, como guardada.
        val list = assertIs<MdBlock.ListBlock>(ChatMarkdown.parse("- **uno\n- dos y *tr", streaming = true).single())
        assertEquals("**uno", raw(list.items[0].spans))
        assertEquals(listOf(MdSpan("dos y "), MdSpan("tr", italic = true)), list.items[1].spans)
        // Un salto de línea no cierra el párrafo (puede seguir); una línea en blanco, sí.
        assertEquals("abierto", shown(ChatMarkdown.parse("**abierto\n", streaming = true)))
        assertEquals("sigue en la otra línea", shown(ChatMarkdown.parse("**sigue en\nla otra línea", streaming = true)).replace("\n", " "))
        assertEquals("**abierto", shown(ChatMarkdown.parse("**abierto\n\n", streaming = true)))
        assertEquals("**abierto\nOtro párrafo", shown(ChatMarkdown.parse("**abierto\n\nOtro párrafo", streaming = true)))
    }

    @Test fun `mientras llega, vale en titulos, citas y recuadros sin cerrar`() {
        assertEquals(MdBlock.Heading(2, listOf(MdSpan("Plan de ", bold = false), MdSpan("rodaje", bold = true))),
            ChatMarkdown.parse("## Plan de **rodaje", streaming = true).single())
        assertEquals(MdBlock.Quote(listOf(MdSpan("una "), MdSpan("cita", italic = true))), ChatMarkdown.parse("> una *cita", streaming = true).single())
        val callout = assertIs<MdBlock.Callout>(ChatMarkdown.parse(":::warn\n**Ojo con", streaming = true).single())
        assertEquals(listOf(MdSpan("Ojo con", bold = true)), (callout.blocks.single() as MdBlock.Paragraph).spans)
        // Un recuadro ya cerrado no deja nada abierto dentro.
        val closedBox = assertIs<MdBlock.Callout>(ChatMarkdown.parse(":::warn\n**Ojo con\n:::\nSigue", streaming = true).first())
        assertEquals("**Ojo con", raw((closedBox.blocks.single() as MdBlock.Paragraph).spans))
    }

    @Test fun `mientras llega, una linea en negrita no se da por etiqueta hasta que hay otra detras`() {
        // «**Sobre el historial**, no puedo…»: al llegar el cierre la frase puede seguir; no debe pasar a etiqueta y volver.
        val label = "**Sobre el historial de generaciones**"
        assertEquals(listOf(MdSpan("Sobre el historial de generaciones", bold = true)),
            (ChatMarkdown.parse(label, streaming = true).single() as MdBlock.Paragraph).spans)
        assertIs<MdBlock.Paragraph>(ChatMarkdown.parse(label + "\n", streaming = true).single())
        assertIs<MdBlock.Paragraph>(ChatMarkdown.parse("$label, no puedo confirmarte", streaming = true).single())
        // Con otra línea detrás, o ya guardada, es la etiqueta de siempre.
        assertEquals(MdBlock.Heading(3, listOf(MdSpan("Sobre el historial de generaciones"))), ChatMarkdown.parse("$label\nTexto", streaming = true).first())
        assertEquals(MdBlock.Heading(3, listOf(MdSpan("Sobre el historial de generaciones"))), ChatMarkdown.parse(label).single())
    }
}
