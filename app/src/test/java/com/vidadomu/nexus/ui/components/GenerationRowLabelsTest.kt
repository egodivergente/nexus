package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.create.ComparisonPlan
import org.junit.Assert.*
import org.junit.Test

class GenerationRowLabelsTest {
    private fun job(id: String, prompt: String, startedAt: Long, model: String = "seedream", params: Map<String, String> = emptyMap()) =
        GenerationJob(id, providerId = "mock_provider", modelId = model, modality = Modality.IMAGE, prompt = prompt,
            status = GenerationStatus.COMPLETED, startedAt = startedAt, parameters = params)

    @Test fun templateHeadingsAreSkippedForTheFirstRealLine() {
        assertEquals("Una mujer camina bajo la lluvia", generationRowTitle("SUBJECT & SCENE\nUna mujer camina bajo la lluvia\nSTYLE: cine", "Modelo"))
        assertEquals("A woman in red", generationRowTitle("**SUBJECT & SCENE:** A woman in red", "Modelo"))
        assertEquals("Plano general del puerto", generationRowTitle("## ESCENA:\n- Plano general del puerto", "Modelo"))
    }

    @Test fun neverReturnsOnlyEllipsisAndFallsBackHonestly() {
        assertEquals("Modelo X", generationRowTitle("...\n…\n---", "Modelo X"))
        assertEquals("Modelo X", generationRowTitle("   ", "Modelo X"))
        assertEquals("SUBJECT & SCENE", generationRowTitle("SUBJECT & SCENE:\n...", "Modelo X"))
        val long = generationRowTitle("palabra ".repeat(60), "M")
        assertTrue(long.length <= 121 && long.endsWith("…") && long.any { it.isLetter() })
    }

    @Test fun ordinaryPromptsAndRatiosAreKept() {
        assertEquals("16:9 cinematic shot of a harbour", generationRowTitle("16:9 cinematic shot of a harbour", "M"))
        assertEquals("Prueba de historial", generationRowTitle("Prueba de historial", "M"))
    }

    @Test fun tenAttemptsOfTheSamePromptAreNumberedByStartTime() {
        val prompt = "SUBJECT & SCENE\nRetrato en estudio"
        val history = (1..10).map { job("j$it", prompt, startedAt = 1_000L * (11 - it)) } + job("other", "Otro", 5) + job("m2", prompt, 7, model = "flux")
        val labels = history.take(10).map { generationVariantLabel(it, history, historyComplete = true) }
        assertEquals(10, labels.toSet().size)
        assertEquals("Intento 1 de 10", generationVariantLabel(history[9], history, true))
        assertEquals("Intento 10 de 10", generationVariantLabel(history[0], history, true))
        assertNull(generationVariantLabel(history[10], history, true))
        assertNull(generationVariantLabel(history[11], history, true))
    }

    @Test fun incompleteHistoryIsNotNumbered() {
        val history = listOf(job("a", "x", 1), job("b", "x", 2))
        assertNull(generationVariantLabel(history[0], history, historyComplete = false))
    }

    @Test fun comparisonNumberIsUsedEvenWhileLoading() {
        val j = job("c", "x", 1, params = mapOf(ComparisonPlan.NUMBER to "2", ComparisonPlan.MODEL_SIZE to "4"))
        assertEquals("Variante 2 de 4", generationVariantLabel(j, listOf(j), historyComplete = false))
        val bad = job("d", "x", 1, params = mapOf(ComparisonPlan.NUMBER to "0"))
        assertNull(comparisonVariantLabel(bad))
    }

    @Test fun snapshotMapMatchesPerRowLabelsForEveryJob() {
        val prompt = "SUBJECT & SCENE\nRetrato"
        val history = (1..10).map { job("j$it", prompt, startedAt = 1_000L * (11 - it)) } +
            job("solo", "Único", 3) + job("blank", " ", 4) + job("m2", prompt, 7, model = "flux") +
            job("cmp", prompt, 8, model = "otro", params = mapOf(ComparisonPlan.NUMBER to "1", ComparisonPlan.MODEL_SIZE to "2")) +
            job("cmp2", prompt, 9, model = "otro")
        listOf(true, false).forEach { complete ->
            val map = generationVariantLabels(history, complete)
            history.forEach { assertEquals(it.id, generationVariantLabel(it, history, complete), map[it.id]) }
        }
        assertEquals("Variante 1 de 2", generationVariantLabels(history, false)["cmp"])
        assertEquals(setOf("cmp"), generationVariantLabels(history, false).keys)
    }

    @Test fun snapshotMapIsRecomputedFromTheNewSnapshot() {
        val first = listOf(job("a", "x", 1), job("b", "x", 2))
        assertEquals("Intento 2 de 2", generationVariantLabels(first, true)["b"])
        val afterDelete = first.take(1)
        assertTrue(generationVariantLabels(afterDelete, true).isEmpty())
    }
}
