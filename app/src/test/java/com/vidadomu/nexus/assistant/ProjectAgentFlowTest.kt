package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import com.vidadomu.nexus.agent.core.assets.ProjectNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAgentFlowTest {
    private fun project(id: String = "p1", updatedAt: Long? = 10, rules: List<String> = listOf("Mateo nunca sonríe"),
        models: List<String> = listOf("Seedance 2.0", "Kling"), notes: List<ProjectNote> = listOf(ProjectNote(1, "a"), ProjectNote(2, "b"))) =
        Project(id, "Proyecto $id", "Drama", createdAt = 0, memory = ProjectMemory(synopsis = "Sinopsis", tone = "sobrio",
            visualStyle = "35 mm", rules = rules, preferredModels = models, notes = notes, updatedAt = updatedAt))

    @Test fun freshDraftIsCleanAndEditsMakeItDirty() {
        val p = project()
        val draft = ProjectMemoryDraft.from(p)
        assertFalse(draft.isDirty(p))
        assertTrue(draft.copy(synopsis = "Otra").isDirty(p))
        assertTrue(draft.copy(removedNotes = listOf(1)).isDirty(p))
    }

    @Test fun storageNormalisationDoesNotLookLikeAnEdit() {
        val p = project()
        val draft = ProjectMemoryDraft.from(p).copy(models = "Seedance 2.0, Kling", rules = "  Mateo nunca sonríe \n\n", description = " Drama ")
        assertFalse(draft.isDirty(p))
    }

    @Test fun keepNotesOnlyWhenSomethingWasRemoved() {
        val p = project()
        assertNull(ProjectMemoryDraft.from(p).keepNotes(p))
        assertEquals(setOf(2L), ProjectMemoryDraft.from(p).copy(removedNotes = listOf(1)).keepNotes(p))
    }

    @Test fun draftsSurviveSaveAndRestore() {
        val draft = ProjectMemoryDraft.from(project()).copy(synopsis = "Línea 1\nLínea 2, con coma", removedNotes = listOf(1, 2), saveRequested = true)
        assertEquals(draft, ProjectMemoryDraft.fromSaved(draft.toSaved()))
        val noBase = ProjectMemoryDraft.from(project(updatedAt = null))
        assertEquals(noBase, ProjectMemoryDraft.fromSaved(noBase.toSaved()))
        assertNull(ProjectMemoryDraft.fromSaved(listOf("x")))
    }

    @Test fun pruningKeepsEditedDraftsPerProjectAndNeverMixesThem() {
        val a = project("a"); val b = project("b")
        val drafts = mutableMapOf(
            "a" to ProjectMemoryDraft.from(a).copy(tone = "épico"),
            "b" to ProjectMemoryDraft.from(b),
            "gone" to ProjectMemoryDraft.from(project("gone")).copy(tone = "x"),
        )
        pruneProjectDrafts(drafts, listOf(a, b))
        assertEquals(setOf("a"), drafts.keys)
        assertEquals("épico", drafts.getValue("a").tone)
        assertEquals("sobrio", b.memory.tone)
    }

    @Test fun projectBeingEditedIsKeptEvenWithoutChanges() {
        val a = project("a")
        val drafts = mutableMapOf("a" to ProjectMemoryDraft.from(a))
        pruneProjectDrafts(drafts, listOf(a), keep = "a")
        assertEquals(setOf("a"), drafts.keys)
    }

    @Test fun confirmedSaveRemovesDraftButExternalChangeDoesNot() {
        val base = project(updatedAt = 10)
        val pending = ProjectMemoryDraft.from(base).copy(tone = "épico", saveRequested = true)
        val saved = base.copy(memory = base.memory.copy(tone = "épico", updatedAt = 11))
        val external = base.copy(memory = base.memory.copy(synopsis = "El agente añadió esto", updatedAt = 11))
        assertTrue(pending.saveConfirmed(saved))
        assertFalse(pending.saveConfirmed(external))
        assertFalse(pending.saveConfirmed(base))
        val drafts = mutableMapOf("p1" to pending)
        pruneProjectDrafts(drafts, listOf(external))
        assertEquals("épico", drafts.getValue("p1").tone)
        pruneProjectDrafts(drafts, listOf(saved))
        assertTrue(drafts.isEmpty())
    }

    @Test fun staleDraftIsDetectedWhenMemoryChangesAfterward() {
        val base = project(updatedAt = 10)
        val draft = ProjectMemoryDraft.from(base).copy(tone = "épico")
        assertFalse(draft.isStale(base))
        assertTrue(draft.isStale(base.copy(memory = base.memory.copy(updatedAt = 20))))
        assertFalse(draft.copy(saveRequested = true).isStale(base.copy(memory = base.memory.copy(updatedAt = 20))))
    }

    @Test fun errorNoticeShowsCauseAndKnownStateWithoutClaimingDelivery() {
        val retry = agentErrorNoticeContent("java.net.UnknownHostException: Unable to resolve host bedrock", canRetry = true)
        assertEquals("No hay conexión. Comprueba la red y vuelve a intentarlo.", retry.cause)
        assertTrue(retry.technical.contains("UnknownHostException"))
        val noRetry = agentErrorNoticeContent("algo raro", canRetry = false)
        assertEquals("El agente no ha podido completar esta respuesta.", noRetry.cause)
        listOf(retry, noRetry).forEach { c ->
            val visible = (c.cause + " " + c.status).lowercase()
            listOf("enviad", "no se envió", "no llegó", "recibid", "entregad").forEach { assertFalse("«$it» en «$visible»", it in visible) }
        }
        assertFalse(retry.status == noRetry.status)
    }

    @Test fun reasoningModesKeepPreviousMapping() {
        val standard = agentReasoningModes(antigravity = false)
        assertEquals(listOf("Auto", "Rápido", "Mínimo", "Bajo", "Medio", "Alto"), standard.map { it.name })
        assertEquals("Rápido", agentReasoningCurrent(standard, null, "disabled"))
        assertEquals("Medio", agentReasoningCurrent(standard, "medium", null))
        assertEquals("Auto", agentReasoningCurrent(standard, "unknown", null))
        val antigravity = agentReasoningModes(antigravity = true)
        assertEquals(listOf("Bajo", "Medio", "Alto"), antigravity.map { it.name })
        assertEquals("Auto", agentReasoningCurrent(antigravity, null, null))
    }

    @Test fun brainLabelIsTheFullNameWithoutShortening() {
        val options = listOf("direct" to "Bedrock", "openai" to "Compatible OpenAI", "brain:x" to "Mi cerebro propio largo")
        assertEquals("Compatible OpenAI", agentBrainLabel("openai", options))
        assertEquals("Mi cerebro propio largo", agentBrainLabel("brain:x", options))
        assertEquals("Cerebro", agentBrainLabel("missing", options))
    }
}
