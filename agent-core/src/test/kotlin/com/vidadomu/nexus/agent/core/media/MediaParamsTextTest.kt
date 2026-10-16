package com.vidadomu.nexus.agent.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaParamsTextTest {
    @Test fun `una linea por parametro con todos sus campos`() {
        val p = MediaParamsText.parse("""
            # comentario
            duration | Duración (s) | duration | 5 | 5, 10
            aspect_ratio | Formato | | 16:9 | 16:9, 9:16
            style* | Estilo | text
            seed | Semilla | seed | -1 | | avanzado
        """.trimIndent()).getOrThrow()
        assertEquals(listOf("duration", "aspect_ratio", "style", "seed"), p.map { it.key })
        assertEquals(listOf("5", "10"), p[0].options)
        assertEquals("enum", p[1].type, "con opciones y sin tipo es un enum")
        assertTrue(p[2].required && p[2].type == "text")
        assertTrue(p[3].advanced)
    }

    @Test fun `solo la clave es obligatoria y el formato ida y vuelta conserva todo`() {
        assertEquals("text", MediaParamsText.parse("negative_prompt").getOrThrow().single().type)
        val original = listOf(
            MediaParam("duration", "Duración", "duration", "5", listOf("5", "10")),
            MediaParam("seed", "seed", "seed", "-1", advanced = true),
            MediaParam("style", "Estilo", "text", required = true),
        )
        assertEquals(original, MediaParamsText.parse(MediaParamsText.format(original)).getOrThrow())
    }

    @Test fun `los errores dicen la linea y que corregir`() {
        fun err(s: String) = MediaParamsText.parse(s).exceptionOrNull()?.message.orEmpty()
        assertTrue("Línea 2" in err("a\nb c d"), err("a\nb c d"))
        assertTrue("repetida" in err("a\na"))
        assertTrue("no existe" in err("a | A | color"))
        assertTrue("necesita opciones" in err("a | A | enum"))
        assertTrue("no está entre las opciones" in err("a | A | enum | 7 | 5, 10"))
    }

    @Test fun `las listas aceptan comas, punto y coma y saltos de linea`() {
        assertEquals(listOf("a", "b", "c", "d"), MediaParamsText.list("a, b;c\nd,, "))
    }
}
