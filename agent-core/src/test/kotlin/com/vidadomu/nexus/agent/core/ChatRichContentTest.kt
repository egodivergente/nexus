package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.chat.*
import kotlin.test.*

class ChatRichContentTest {
    @Test fun tablesWithoutOuterPipesAndEscapedCellsRenderAsTables() {
        val table=ChatMarkdown.parse("Modelo | Nota\n--- | ---\nA \\| B | `x|y`").single() as MdBlock.Table
        assertEquals(2,table.header.size)
        assertEquals(2,table.rows.single().size)
        assertEquals("A | B",table.rows.single()[0].joinToString("") {it.text})
        assertEquals("x|y",table.rows.single()[1].single().text)
    }
    @Test fun completeVisualsRenderWhilePartialStreamingCodeWaits() {
        for(language in listOf("mermaid","html","svg")) {
            assertTrue(ChatMarkdown.parse("```$language\ncontenido\n```").single() is MdBlock.Visual)
            assertTrue(ChatMarkdown.parse("```$language\ncontenido",streaming=true).isEmpty())
            assertTrue(ChatMarkdown.parse("```$language\ncontenido").single() is MdBlock.Code)
        }
    }
}
