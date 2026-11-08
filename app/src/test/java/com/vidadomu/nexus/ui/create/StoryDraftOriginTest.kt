package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.agent.core.assets.StoryGenerationContext
import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Provenance must follow the source operation, through comparison, history reuse and undo. */
class StoryDraftOriginTest {
    private fun origin(project: String = "project-a", shot: String = "shot-a") =
        StoryGenerationContext(project, "scene-$project", shot, 7, "Contexto capturado", listOf("reference-a"), "fingerprint-a")

    private fun model(id: String) = NexusModel(id, "Modelo $id", "fixture", organization = "Fixture",
        modalities = listOf(Modality.IMAGE), capabilities = listOf(ModelCapability.TEXT_TO_IMAGE), parameters = emptyList())

    private fun job(parameters: Map<String, String> = emptyMap()) = GenerationJob("history-job", providerId = "fixture",
        modelId = "model", modality = Modality.IMAGE, prompt = "Otro trabajo", parameters = parameters)

    @Test fun comparisonKeepsImmutableShotOriginWhenAdaptingToAnotherModel() {
        val captured = origin()
        val source = CreateUiState(selectedModel = model("source"), prompt = "La mano tiembla", schemaReady = true,
            storyContext = captured)
        val adapted = ComparisonPlan.adapt(source, model("target"))
        assertEquals("target", adapted.state.selectedModel?.id)
        assertEquals(captured, adapted.state.storyContext)
        assertEquals(captured, source.storyContext)
    }

    @Test fun historyReuseUsesItsOwnOriginAndOrdinaryHistoryExplicitlyHasNone() {
        val other = origin("project-b", "shot-b")
        val metadata = mapOf("__project_id" to other.projectId,
            "__story_context" to Json.encodeToString(StoryGenerationContext.serializer(), other))
        assertEquals(other, historyStoryOrigin(job(metadata)))
        assertNull(historyStoryOrigin(job()))
    }

    @Test fun corruptOrMismatchedHistoryCannotInventAnOrigin() {
        assertNull(historyStoryOrigin(job(mapOf("__story_context" to "not JSON"))))
        assertNull(historyStoryOrigin(job(mapOf("__project_id" to "different-project",
            "__story_context" to Json.encodeToString(StoryGenerationContext.serializer(), origin())))))
    }

    @Test fun undoModelSwitchRestoresOriginalShotEvenAfterDraftOriginWasReassigned() {
        val original = CreateUiState(selectedModel = model("source"), availableModels = listOf(model("source")),
            prompt = "Original", schemaReady = true, storyContext = origin())
        val snapshot = requireNotNull(ModelSwitchUndo.snapshot(original))
        val changed = original.copy(selectedModel = model("target"), prompt = "Nuevo", storyContext = origin("project-b", "shot-b"))
        val restored = ModelSwitchUndo.restore(changed, snapshot)
        assertEquals("Original", restored.prompt)
        assertEquals("source", restored.selectedModel?.id)
        assertEquals(original.storyContext, restored.storyContext)
    }
}
