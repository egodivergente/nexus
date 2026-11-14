package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.chat.MdSpan
import org.junit.Assert.*
import org.junit.Test

class ChatCleanupRegressionTest {
    @Test fun hideOrderIdsStripsInternalIdsAndOrphanPunctuationWithoutBreakingProse() {
        val raw = "Listo (ord_1122aabb, ast_78b4dac4e403, att_9900eeff, job_1234abcd). Mira los cambios, por favor."
        val cleaned = hideOrderIds(raw)
        assertEquals("Listo. Mira los cambios, por favor.", cleaned)
        assertFalse(cleaned.contains("("))
        assertFalse(cleaned.contains("ord_"))
        assertFalse(cleaned.contains("ast_"))
        assertFalse(cleaned.contains("att_"))
        assertFalse(cleaned.contains("job_"))
    }

    @Test fun hideOrderIdsKeepsSurroundingMeaningfulTextInsideParentheses() {
        val mixed = "Escena 1 (plano general, ast_78b4dac4e403, ord_1122aabb) actualizada."
        val cleaned = hideOrderIds(mixed)
        assertEquals("Escena 1 (plano general) actualizada.", cleaned)
    }

    @Test fun readableSpansHidesBacktickedAssetAndOrderIdsInsteadOfTurningThemIntoToolWords() {
        val spans = listOf(
            MdSpan("Activos ("),
            MdSpan("ast_78b4dac4e403", code = true),
            MdSpan(", "),
            MdSpan("ord_a1b2c3d4e5", code = true),
            MdSpan(", "),
            MdSpan("att_98765432", code = true),
            MdSpan(") creados con "),
            MdSpan("create_plan", code = true),
            MdSpan(".")
        )
        val rendered = readableSpans(spans).joinToString("") { it.text }
        assertEquals("Activos creados con create plan.", rendered)
        assertFalse(rendered.contains("ast "))
        assertFalse(rendered.contains(", ,"))
        assertFalse(rendered.contains("()"))
    }
}
