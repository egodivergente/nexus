package com.vidadomu.nexus.agent.core.chat

import kotlin.test.*

class ChatPresentationTest {
    @Test fun editorialCalloutsBecomeOrdinaryDialogueWithoutLosingTheirContents() {
        for (kind in listOf("tldr", "tip", "decision")) {
            val text = "Antes.\n\n:::$kind\nContenido **útil**.\n:::\n\nDespués."
            val result = ChatPresentation.blocks(text)
            assertEquals(3, result.size)
            assertTrue(result.all { it is MdBlock.Paragraph })
            assertEquals("Contenido útil.", (result[1] as MdBlock.Paragraph).spans.joinToString("") { it.text })
            assertTrue(ChatMarkdown.parse(text)[1] is MdBlock.Callout) // source/history stays intact
        }
    }

    @Test fun warningsResultsTablesAndCodeKeepTheirFunctionalStructure() {
        for (kind in listOf("warn", "result")) {
            assertTrue(ChatPresentation.blocks(":::$kind\nEstado.\n:::").single() is MdBlock.Callout)
        }
        val code = "```kotlin\nval x = 1\n```"
        assertEquals(ChatMarkdown.parse(code), ChatPresentation.blocks(code))
        val table = "| Parámetro | Valor |\n| --- | --- |\n| Resolución | 1080p |"
        assertTrue(ChatPresentation.blocks(table).single() is MdBlock.Table)
    }

    @Test fun streamingAndSavedEditorialRepliesShareTheSameTypography() {
        val text = ":::tip\nConsejo completo.\n:::"
        assertEquals(ChatPresentation.blocks(text), ChatPresentation.blocks(text, streaming = true))
    }
}
