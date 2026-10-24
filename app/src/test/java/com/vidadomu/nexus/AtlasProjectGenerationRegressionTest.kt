package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.data.repository.LocalJobMetadata
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.ui.models.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AtlasProjectGenerationRegressionTest {
    // Public model contract returned by the installed app's read-only bridge on 2026-10-24.
    private fun sunburst(): NexusModel {
        val model=Json {ignoreUnknownKeys=true}.decodeFromString<NexusModel>(javaClass.getResource("/native/sunburst-text-model.json")!!.readText())
        return NativeModelSchema.decode(model,javaClass.getResource("/native/sunburst-input.json")!!.readText()).model
    }

    @Test fun projectConversationBatchAndAssistantContextAreAcceptedWithoutProviderOptions() {
        LocalJobMetadata.validate(mapOf("__project_id" to "project-1","__conversation_id" to "chat-1",
            "__comparison_id" to "batch-1","__assistant_origin" to "agent"))
        assertThrows(IllegalArgumentException::class.java) {LocalJobMetadata.validate(mapOf("quality" to "max"))}
        assertFalse(LocalJobMetadata.allowed("__project_unknown"))
    }

    @Test fun textOnlySunburstIsReadyWithoutAnyReferencesAndKeepsTheWrittenPrompt() {
        val model=sunburst()
        val values=model.parameters.associate {it.key to it.defaultValue}
        val state=CreateUiState(selectedModel=model,prompt="Una esfera de vidrio sobre mármol",parameters=values,schemaReady=true)
        assertTrue(state.missingRequiredFields.isEmpty())
        assertTrue(creationReadiness(state).canSubmit)
        assertTrue(CanvasInputPlan.fields(model).isEmpty())
        assertTrue(model.promptAccepted)
        assertEquals(CreationFixTarget.PROMPT,creationReadiness(state.copy(prompt="")).fixTarget)
    }

    @Test fun actualAtlasPayloadContainsOnlyTextContractFieldsAndTypedCount() {
        val model=sunburst()
        val values=model.parameters.associate {it.key to it.defaultValue}+mapOf("n" to "3","size" to "2048x1152","quality" to "max",
            "duration" to "8","__project_id" to "project-1","__conversation_id" to "chat-1")
        val request=NativeGenerationRequest.build(model,Modality.IMAGE,"Texto sin referencias",values,emptyList(),"fixture")
        val body=AtlasGenerationPayload.build(request)
        assertEquals(setOf("model","prompt","quality","size","background","output_format","moderation","n"),body.keys)
        assertEquals(JsonPrimitive(3),body["n"])
        assertEquals(JsonPrimitive("Texto sin referencias"),body["prompt"])
        assertFalse(body.keys.any {it.startsWith("__")})
        assertFalse(request.parameters.containsKey("duration"))
        // Even a legacy record with context mixed into parameters cannot put it on the wire.
        assertEquals(body,AtlasGenerationPayload.build(request.copy(parameters=request.parameters+mapOf("__project_id" to "p","__assistant_origin" to "a"))))
    }

    @Test fun sunburstAndFlareDeveloperNeverShareAnAmbiguousGroup() {
        val sun=sunburst()
        val flare=sun.copy(id="openai/gpt-image-2.5-flare-developer/text-to-image",displayName="GPT Image 2.5 Flare Developer")
        assertNotEquals(modelGroupTitle(sun),modelGroupTitle(flare))
        assertTrue(modelGroupTitle(sun).contains("Sunburst"))
        assertTrue(modelGroupTitle(flare).contains("Flare Developer"))
        assertEquals(listOf(CatalogWorkflow.CREATE_IMAGE),catalogWorkflows(sun))
        assertEquals(listOf(CatalogWorkflow.EDIT_IMAGE),catalogWorkflows(sun.copy(id="openai/gpt-image-2.5-sunburst/edit",capabilities=listOf(ModelCapability.TEXT_TO_IMAGE,ModelCapability.IMAGE_EDITING))))
    }
}
