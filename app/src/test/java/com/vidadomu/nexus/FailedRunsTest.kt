package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.generations.HistoryRow
import com.vidadomu.nexus.ui.generations.groupFailedRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedRunsTest {
    private fun job(id: String, status: GenerationStatus = GenerationStatus.FAILED, model: String = "magnific", prompt: String = "") =
        GenerationJob(id, providerId = "p", modelId = model, modality = Modality.VIDEO, prompt = prompt, status = status)

    @Test fun threeOrMoreSameFailuresFoldIntoOneHeader() {
        val rows = groupFailedRuns(listOf(job("a"), job("b"), job("c"), job("d", GenerationStatus.COMPLETED)))
        assertEquals(2, rows.size)
        val header = rows[0] as HistoryRow.Header
        assertEquals(listOf("a", "b", "c"), header.jobs.map { it.id })
        assertEquals("d", (rows[1] as HistoryRow.Job).job.id)
    }

    @Test fun expandedRunShowsEveryAttemptBelowItsHeader() {
        val rows = groupFailedRuns(listOf(job("a"), job("b"), job("c")), expanded = setOf("a"))
        assertEquals(4, rows.size)
        assertTrue(rows[0] is HistoryRow.Header)
        assertEquals(listOf("a", "b", "c"), rows.drop(1).map { (it as HistoryRow.Job).job.id })
    }

    @Test fun shortRunsAndDifferentAttemptsAreLeftAlone() {
        assertTrue(groupFailedRuns(listOf(job("a"), job("b"))).all { it is HistoryRow.Job })
        val mixed = groupFailedRuns(listOf(job("a"), job("b", model = "otro"), job("c"), job("d", prompt = "x")))
        assertTrue("distinto modelo o texto no es la misma racha", mixed.all { it is HistoryRow.Job })
    }

    @Test fun aSuccessInTheMiddleBreaksTheRunAndKeysStayUnique() {
        val rows = groupFailedRuns(listOf(job("a"), job("b"), job("ok", GenerationStatus.COMPLETED), job("c"), job("d"), job("e")), expanded = setOf("c"))
        assertEquals(listOf("a", "b", "ok", "run-c", "c", "d", "e"), rows.map { it.key })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
