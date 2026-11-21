package com.vidadomu.nexus.ui.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptationLinesTest {
    private val same = "Este modelo no admite el valor anterior y usa el suyo; el anterior sigue guardado en el modelo de antes."

    @Test fun aSharedReasonIsSaidOnce() {
        val (lines, shared) = adaptationLines(
            listOf(Triple("Resolución", "720p", "1K"), Triple("Formato", "adaptive", "16:9"), Triple("Output format", "mp4", "jpeg")),
            listOf(same, same, same))
        assertEquals(listOf("Resolución: 720p → 1K", "Formato: adaptive → 16:9", "Output format: mp4 → jpeg"), lines)
        assertEquals(same, shared)
    }

    @Test fun differentReasonsStayWithTheirLine() {
        val (lines, shared) = adaptationLines(
            listOf(Triple("Resolución", "720p", "1K"), Triple("Duración", "12 s", "10 s")),
            listOf(same, "El máximo de este modelo es 10 s."))
        assertEquals(listOf("Resolución: 720p → 1K. $same", "Duración: 12 s → 10 s. El máximo de este modelo es 10 s."), lines)
        assertNull(shared)
    }

    @Test fun aMissingReasonIsNotSharedByTheOthers() {
        val (lines, shared) = adaptationLines(listOf(Triple("A", "1", "2"), Triple("B", "3", "4")), listOf(same, ""))
        assertEquals(listOf("A: 1 → 2. $same", "B: 3 → 4"), lines)
        assertNull(shared)
    }
}
